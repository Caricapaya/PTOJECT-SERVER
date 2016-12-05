import java.util.*;
import java.io.*;
import java.io.BufferedReader;
import java.net.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.math.BigInteger;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.json.*;


public class DeviceConnection extends Thread{
	//static stuff
	private static final Object lock = new Object();
	private static final HashMap<Integer, String> locations = new HashMap<Integer, String>();
	private static final List<DeviceConnection> connectedDevices = new ArrayList<DeviceConnection>();
	private static final SecureRandom secureGen = new SecureRandom();
	private static final ConnectedDevicesManager connectedDevicesManager = new ConnectedDevicesManager();
	private static final DeviceSessionManager deviceSessionManager = new DeviceSessionManager();

	Socket mySocket;
	BufferedReader reader;
	PrintWriter writer;
	LocationSender myThread;
	int myid;
	String name;
	public DeviceConnection(Socket sock, int id) throws IOException{
		mySocket = sock;
		mySocket.setSoTimeout(3000);
		reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
		writer = new PrintWriter(mySocket.getOutputStream(), true);
		myid = id;
		name = "DEFAULT_NAME";
		connectedDevices.add(this);
		//myThread = new LocationSender(writer);
		//myThread.start();
	}

		//for testing
	public DeviceConnection(int id, String nm, String loc){
		Device temp = new Device(id, Double.parseDouble(loc.split(",")[0]), Double.parseDouble(loc.split(",")[1]));
		temp.setFirstName(nm.split(" ")[0]);
		temp.setLastName(nm.split(" ")[1]);
		connectedDevicesManager.addDevice(temp);
	}


	public void run(){
		System.out.println("Connected to: " + myid + "!");
		String clientRequest;
		String clntmsg = "default";
		JSONObject clientMessage;
		JSONObject response = new JSONObject();
		JSONObject person;
		JSONObject location;
		JSONArray array;
		int deviceID = 0;
		try{
			clntmsg = reader.readLine();
			clientMessage = new JSONObject(clntmsg);
			clientRequest = clientMessage.getString("type");
			System.out.println(clientMessage);
			if (clientRequest.equals("QUIT")) {
				return;
			}
			else if (clientRequest.equals("SEND_LOCATION")) {
				response = handleSendLocation(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("GET_LOCATIONS")) {
				response = handleGetLocations(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("LOGIN")){
				response = handleLogin(clientMessage);
				writer.println(response);

			}
			else if(clientRequest.equals("SIGNUP")){
				response = new JSONObject();
				response.put("type", "SIGNUP");
				if (emailTaken(clientMessage.getString("username"))) {
					response.put("signupsuccessful", false);
					response.put("why", "E-mail address is already in use");
				}
				else{
					response.put("signupsuccessful", true);
					createUser(clientMessage);
				}
				writer.println(response);
			}
			else{
				response = new JSONObject();
				response.put("type", "DEFAULT");
				writer.println(response);
			}
		}
		catch(org.json.JSONException e){
			System.out.println("someolbullshit");
			e.printStackTrace();
		}  
		catch (IOException e){
			System.out.println("IOException");
		}
		catch(Exception e){
			System.out.println("HELLO ");
			e.printStackTrace();
		}
		finally{
			System.out.println("Disconnected from: " + myid);
			connectedDevices.remove(this);
			return;
		}
	}

	public int getID(){
		return myid;
	}

	public String getDeviceName(){
		return name;
	}


	private class LocationSender extends Thread{
		private PrintWriter printer;
		public LocationSender(PrintWriter pbrrttt){
			printer = pbrrttt;
		}

		public void run(){
			while(true){
				synchronized (lock){
					for (DeviceConnection someID : connectedDevices) {
						if (someID.getID() != myid) {
							printer.println(myid + "," + locations.get(myid));
						}
					}
				}
				try{
					Thread.sleep(5000);
				}
				catch (InterruptedException e){
					return;
				}
				
			}
		}
	}

	public static void createFakeDevices(){
		new DeviceConnection(1000, "Henry V", "10.0,10.0");
		new DeviceConnection(2000, "Carl Barks", "20.0,20.0");
		new DeviceConnection(3000, "Don Rosa", "30.0,30.0");
		new DeviceConnection(4000, "Paul Sane", "40.0,40.0");
	}

	/*private ResultSet performQuery(String query, String... params){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query);
			for (int i = 0; i < params.length; i++) {
				stmt.setString(i+1, params[i]);
			}
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return result;
		}
	}*/

	private JSONArray performQuery(String query, String... params){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;
		JSONArray rows = new JSONArray();

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query);
			for (int i = 0; i < params.length; i++) {
				stmt.setString(i+1, params[i]);
			}
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}
			ResultSetMetaData meta = result.getMetaData();
			int columns = meta.getColumnCount();
			String columnName[] = new String[columns];
			for (int i = 0; i < columns;) {
				columnName[i] = meta.getColumnLabel(++i);
			}

			while(result.next()){
				JSONObject row = new JSONObject();
				for (int i = 0; i < columns; i++) {
					String val = result.getString(columnName[i]);
					if (val == null) {
						row.put(columnName[i], "NULL");
					}
					else{
						row.put(columnName[i], val);
					}
				}
				rows.put(row);
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return rows;
		}
	}

	JSONArray searchUsers(String searchQuery int deviceID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;
		JSONArray rows = new JSONArray();
		String query1 = "select * from user_accounts where" +
						"match FirstName against(?) or"
						"match MiddleName against(?) or"
						"match LastName against(?)";
		String query2 = "select * from friend_requests where ? in(Sender)";
		String query3 = "select * from friends where ? in(FriendID1)";



		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query1);
			for (int i = 0; i < 3; i++) {
				stmt.setString(i+1, searchQuery + "*");
			}
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}

			while(result.next()){
				JSONObject row = new JSONObject();
				String val = result.getString("FirstName");
				row.put("firstname", val == null ? "NULL", val));
				val = result.getString("MiddleName");
				row.put("middlename", val == null ? "NULL", val));
				val = result.getString("LastName");
				row.put("lastname", val == null ? "NULL", val));
				val = result.getInt("AccountID");
				row.put("accountid", result.getInt("AccountID");
				rows.put(row);
			}
			conn.close();

			if (rows.size() < 1) {
				return rows;
			}


		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return rows;
		}
	}

	boolean authenticate(String user, String pass){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		String dbquery = "select * from user_accounts where ? in(Email)";
		ResultSet result = null;
		boolean match = false;

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			stmt.setString(1, user);
			if (stmt.execute()) {
				result = stmt.getResultSet();
				if (result.isBeforeFirst()) {
					result.next();
					String dbpass = result.getString("Password");
					String salt = result.getString("Salt");
					String hash = createHash(pass + salt);
					if (dbpass.equals(hash)) {
						match = true;
					}
				}
				
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return match;
		}
	}

	boolean createUser(JSONObject json){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		String user = json.getString("username");
		String pass = json.getString("password");
		String salt = createSalt();
		String hash = createHash(pass + salt);
		String gender = json.getString("gender");
		String status = json.getString("status");
		String dbquery = "insert into user_accounts (Email, Password, Gender, Status, Salt) values(?, ?, ?, ?, ?)";
		ResultSet result = null;
		boolean created = false;

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			stmt.setString(1, user);
			stmt.setString(2, hash);
			stmt.setString(3, gender);
			stmt.setString(4, status);
			stmt.setString(5, salt);
			System.out.println(stmt);
			if (stmt.execute()) {
				created = true;
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return created;
		}
	}

	//check if email is already registered
	private boolean emailTaken(String email){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		String dbquery = "select * from user_accounts where ? in(Email)";
		ResultSet result = null;
		boolean match = true;

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			stmt.setString(1, email);
			if (stmt.execute()) {
				result = stmt.getResultSet();
				match = result.isBeforeFirst();
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return match;
		}
	}


	//Create sha-256 hash
	//code from http://stackoverflow.com/questions/6840206/sha2-password-hashing-in-java
	private String createHash(String toHash){
		byte[] retval = null;
		try{
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] toHashBytes = toHash.getBytes();
			retval = sha256.digest(toHashBytes);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		finally{
			return bytesToHex(retval);
		}
	}

	//Create hex string from byte array (hash)
	//code from http://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	private String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
    		int v = bytes[j] & 0xFF;
        	hexChars[j * 2] = hexArray[v >>> 4];
        	hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
	    return new String(hexChars);
	}

	//code from http://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string
	private String createSalt(){
		return new BigInteger(260, secureGen).toString(32);
	}

	private JSONObject handleSendLocation(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		double latitude;
		double longitude;

		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				latitude = request.getJSONObject("location").getDouble("latitude");
				longitude = request.getJSONObject("location").getDouble("longitude");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				connectedDevicesManager.addDevice(deviceID, latitude, longitude);
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");

				latitude = request.getJSONObject("location").getDouble("latitude");
				longitude = request.getJSONObject("location").getDouble("longitude");
				deviceID = deviceSessionManager.getDeviceID(sessionID);
				connectedDevicesManager.addDevice(deviceID, latitude, longitude);
			break;
		}

		response.put("type", "SEND_LOCATION");
		response.put("body", "Message received!");
		return response;
	}

	private JSONObject handleGetLocations(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		ArrayList<Device> visibleDevices;
		
		JSONArray array;
		System.out.println("GET_LOCATIONS: " + sessionStatus);
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				System.out.println("SESSION_REFRESH");
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				visibleDevices = connectedDevicesManager.getAllConnectedDevices();
				array = new JSONArray();
				for (Device dev : visibleDevices) {
					JSONObject otherDevice;
					JSONObject location;
					if (dev.getDeviceID() != deviceID) {
						otherDevice = new JSONObject();
						otherDevice.put("name", dev.getName());
						location = new JSONObject();
						location.put("latitude", dev.getLatitude());
						location.put("longitude", dev.getLongitude());
						otherDevice.put("location", location);
						otherDevice.put("deviceID", dev.getDeviceID());
						array.put(otherDevice);
					}
				}

				response.put("people", array);
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				System.out.println("SESSION_ACTIVE");
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);
				System.out.println("SESSION_ACTIVE: before getall");
				visibleDevices = connectedDevicesManager.getAllConnectedDevices();
				System.out.println("SESSION_ACTIVE: after getall");
				array = new JSONArray();
				for (Device dev : visibleDevices) {
					JSONObject otherDevice;
					JSONObject location;
					if (dev.getDeviceID() != deviceID) {
						otherDevice = new JSONObject();
						otherDevice.put("name", dev.getName());
						location = new JSONObject();
						location.put("latitude", dev.getLatitude());
						location.put("longitude", dev.getLongitude());
						otherDevice.put("location", location);
						otherDevice.put("deviceID", dev.getDeviceID());
						array.put(otherDevice);
					}
				}
				response.put("people", array);
			break;
		}
		System.out.println(response);
		response.put("type", "GET_LOCATIONS");
		return response;
	}

	private JSONObject handleLogin(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		String query = "select * from user_accounts where ? in(Email)";

		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
			case DeviceSessionManager.SESSION_REFRESH:
			case DeviceSessionManager.SESSION_ACTIVE:
				deviceSessionManager.removeSession(sessionID);
			break;
		}
		String username = request.getString("username");
		String password = request.getString("password");


		if (!authenticate(username, password)) {
			response.put("loginSuccessful", false);
		}
		else{

			response.put("loginSuccessful", true);
			JSONObject user = (JSONObject) performQuery(query, username).get(0);
			System.out.println(user);
			response.put("firstname", user.getString("FirstName"));
			response.put("middlename", user.getString("MiddleName"));
			response.put("lastname", user.getString("LastName"));

			deviceID = user.getInt("AccountID");
			sessionID = deviceSessionManager.newSession(deviceID);
			response.put("sessionstatus", "update");
			response.put("sessionid", sessionID);
		}

		
		response.put("type", "LOGIN");
		return response;
	}

	private JSONObject handleFriendSearch(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;


		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);
				deviceID = deviceSessionManager.getDeviceID(sessionID);



				
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

			break;
		}
		response.put("type", "");
		return response;
	}


	//TODO
	private JSONObject handleFriendRequest(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

			break;
		}
		response.put("type", "");
		return response;
	}

	//TEMPLATE

	/*	private JSONObject handle(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

			break;
		}
		response.put("type", "");
		return response;
	}*/
}