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
	public DeviceConnection(Socket sock, int id) throws IOException{
		mySocket = sock;
		mySocket.setSoTimeout(2000);
		reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
		writer = new PrintWriter(mySocket.getOutputStream(), true);
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
		String clientRequest;
		String clntmsg = "default";
		JSONObject clientMessage;
		JSONObject response = new JSONObject();
		JSONObject person;
		JSONObject location;
		JSONArray array;
		int deviceID = -1;
		try{
			clntmsg = reader.readLine();
			clientMessage = new JSONObject(clntmsg);
			if (clientMessage.has("sessionid")) {
				deviceID = deviceSessionManager.getDeviceID(clientMessage.getString("sessionid"));
				System.out.println("Connected to user id: " + deviceID + "!");
			}
			else{
				System.out.println("Connected to device!");
			}
			clientRequest = clientMessage.getString("type");
			//System.out.println(clientMessage.toString(4));
			if (clientRequest.equals("QUIT")) {
				handleQuit(clientMessage);
			}
			else if (clientRequest.equals("SEND_LOCATION")) {
				response = handleSendLocation(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("GET_LOCATIONS")) { //Deprecated
				response = handleGetLocations(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("LOGIN")){
				response = handleLogin(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("SEARCH_PEOPLE")){
				response = handleFriendSearch(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("SEND_REQUEST")){
				response = handleFriendRequest(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("GET_REQUESTS")){
				response = handleGetFriendRequests(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("RESPOND_REQUEST")){
				response = handleRespondRequest(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("GET_FRIENDS")){
				response = handleGetFriends(clientMessage);
				writer.println(response);
				System.out.println(response.toString(4));
			}
			else if (clientRequest.equals("UPDATE_PROFILE")){
				response = handleUpdateProfile(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("GET_PROFILE")){
				response = handleGetProfile(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("SIGNUP")){
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
			if (deviceID > -1) {
				System.out.println("Disconnected from user: " + deviceID + ".");
			}
			else{
				System.out.println("Disconnected from device.");
			}
			return;
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

	private JSONArray searchUsers(String searchQuery, int deviceID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;
		JSONArray rows = new JSONArray();
		String query1 = "select * from user_accounts where FirstName like ? " +
						"or MiddleName like ? " +
						"or LastName like ?";
		String query2 = "select * from friend_requests where ? in(Sender)";
		String query3 = "select * from friends where ? in(FriendID1)";



		//TODO make it so you can actually search for full name

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query1);
			for (int i = 0; i < 3; i++) {
				stmt.setString(i+1, searchQuery + "%");
			}
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}

			System.out.println("SEARCH FOR PEOPLE.. query: " + searchQuery);

			while(result.next()){
				if (result.getInt("AccountID") == deviceID) {
					continue; //Don't list user that performed search
				}
				JSONObject row = new JSONObject();
				String val = result.getString("FirstName");
				String name = "";
				row.put("firstname", val == null ? "NULL" : val);
				name += (val == null ? "" : val);
				val = result.getString("MiddleName");
				row.put("middlename", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				val = result.getString("LastName");
				row.put("lastname", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				name.trim();
				row.put("name", name);
				row.put("deviceID", result.getInt("AccountID"));
				rows.put(row);
				System.out.println("FOUND PERSON: " + name);
			}
			/*if (rows.size() < 1) {
				conn.close();
				return rows;
			}

			stmt = conn.prepareStatement(query2);*/

			conn.close();

			




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
		String status = json.getString("occupation");
		String fName = user.split("@")[0];
		String dbquery = "insert into user_accounts (Email, Password, Gender, Status, Salt, FirstName) values(?, ?, ?, ?, ?, ?)";
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
			stmt.setString(6, fName);
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
		System.out.println("SEND LOCATIONS");
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		double latitude;
		double longitude;

		Device temp;

		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
			System.out.println("INVALID");
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
			System.out.println("TIMEOUT");
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
			System.out.println("REFRESH");
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				latitude = request.getJSONObject("location").getDouble("latitude");
				longitude = request.getJSONObject("location").getDouble("longitude");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				connectedDevicesManager.addDevice(deviceID, latitude, longitude);

				//NAMESTUFF
				//TODO MAKE THIS MORE ELEGANT
				temp = connectedDevicesManager.getDevice(deviceID);
				temp.setFirstName(getNamesFromDatabase(deviceID)[0]);
				connectedDevicesManager.addDevice(temp);
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
			System.out.println("ACTIVE");
				response.put("sessionstatus", "active");

				latitude = request.getJSONObject("location").getDouble("latitude");
				longitude = request.getJSONObject("location").getDouble("longitude");
				deviceID = deviceSessionManager.getDeviceID(sessionID);
				connectedDevicesManager.addDevice(deviceID, latitude, longitude);

				//NAMESTUFF
				temp = connectedDevicesManager.getDevice(deviceID);
				temp.setFirstName(getNamesFromDatabase(deviceID)[0]);
				connectedDevicesManager.addDevice(temp);
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
				response.put("sessionstatus", "active");
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

				response.put("people", searchUsers(request.getString("query"), deviceID));

			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				response.put("people", searchUsers(request.getString("query"), deviceID));
			break;
		}
		response.put("type", "SEARCH_PEOPLE");
		return response;
	}

	private String[] getNamesFromDatabase(int devID){
		System.out.println("START GET NAMES FROM DB");
		String query = "select * from user_accounts where ? in(AccountID)";
		JSONObject result = (JSONObject) performQuery(query, Integer.toString(devID)).get(0);
		String[] names = null;
		try{
			names = new String[]{result.getString("FirstName"), result.getString("MiddleName"), result.getString("LastName")};
		}
		catch (JSONException e){
			e.printStackTrace();
		}
		/*if (names != null) {
			System.out.println("NAMES: " + names[0] + names[1] + names[2]);
		}*/
		return names;
	}

	private int registerRequest(int senderID, int receiverID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		String dbquery = "insert ignore into friend_requests(Sender, Receiver) values(?, ?)";
		String dbquery2 = "select * from friends where FriendID1 = ? and FriendID2 = ? or FriendID1 = ? and FriendID2 = ?";
		boolean requestRegistered = false;
		boolean isFriends = false;
		ResultSet result = null;

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			PreparedStatement stmt2 = conn.prepareStatement(dbquery2);
			stmt.setInt(1, senderID);
			stmt.setInt(2, receiverID);
			stmt2.setInt(1, senderID);
			stmt2.setInt(2, receiverID);
			stmt2.setInt(3, receiverID);
			stmt2.setInt(4, senderID);
			stmt2.execute();
			result = stmt2.getResultSet();
			if (result.isBeforeFirst()) {
				isFriends = true;
			}
			else{
				requestRegistered = stmt.executeUpdate() > 0;
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			if (isFriends) {
				return 3;
			}
			else if(!requestRegistered){
				return 2;
			}
			else{
				return 1;
			}
		}
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

				response.put("request_registered", registerRequest(deviceID, request.getInt("target")));				
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				response.put("request_registered", registerRequest(deviceID, request.getInt("target")));	
			break;
		}
		response.put("type", "SEND_REQUEST");
		return response;
	}

	private JSONArray getRequests(int deviceID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;
		JSONArray rows = new JSONArray();
		String 	query = "select * from friend_requests join user_accounts ";
				query+= "on friend_requests.Sender = user_accounts.AccountID ";
				query+= "where friend_requests.Receiver = ?";



		//TODO make it so you can actually search for full name

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query);
			stmt.setInt(1, deviceID);
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}

			while(result.next()){
				if (result.getInt("AccountID") == deviceID) {
					continue; //Don't list user that performed search
				}
				JSONObject row = new JSONObject();
				String val = result.getString("user_accounts.FirstName");
				String name = "";
				row.put("firstname", val == null ? "NULL" : val);
				name += (val == null ? "" : val);
				val = result.getString("user_accounts.MiddleName");
				row.put("middlename", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				val = result.getString("user_accounts.LastName");
				row.put("lastname", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				name.trim();
				row.put("name", name);
				row.put("deviceID", result.getInt("user_accounts.AccountID"));
				rows.put(row);
				System.out.println("ROW: " + row);
			}
			/*if (rows.size() < 1) {
				conn.close();
				return rows;
			}

			stmt = conn.prepareStatement(query2);*/

			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return rows;
		}
	}



	private JSONObject handleGetFriendRequests(JSONObject request){
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

				response.put("people", getRequests(deviceID));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				response.put("people", getRequests(deviceID));
			break;
		}
		response.put("type", "GET_REQUESTS");
		return response;
	}

	private boolean friendRequestResponse(JSONObject response, int senderID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		String dbquery = "insert ignore into friends(FriendID1, FriendID2) values(?, ?)";
		String dbquery2 = "delete from friend_requests where Sender = ? and Receiver = ? or Sender = ? and Receiver = ?";
		boolean requestRegistered = false;
		boolean isFriends = false;
		ResultSet result = null;
		int receiverID;


		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			PreparedStatement stmt2 = conn.prepareStatement(dbquery2);
			receiverID = response.getInt("target");
			stmt.setInt(1, senderID);
			stmt.setInt(2, receiverID);
			stmt2.setInt(1, senderID);
			stmt2.setInt(2, receiverID);
			stmt2.setInt(3, receiverID);
			stmt2.setInt(4, senderID);
			if (response.getString("response").equals("accept")) {
				stmt.execute();
			}
			stmt2.execute();
			conn.close();
			isFriends = true;
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return isFriends;
		}
	}

	private JSONObject handleRespondRequest(JSONObject request){
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

				response.put("friends", friendRequestResponse(request, deviceID));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				response.put("friends", friendRequestResponse(request, deviceID));
			break;
		}
		response.put("type", "RESPOND_REQUEST");
		return response;
	}

	private JSONArray getFriends(int deviceID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;
		JSONArray rows = new JSONArray();
		String 	query = "select * from friends join user_accounts ";
				query+= "on friends.FriendID1 = user_accounts.AccountID ";
				query+= "where FriendID2 = ?";
		String 	query2 ="select * from friends join user_accounts ";
				query2+="on friends.FriendID2 = user_accounts.AccountID ";
				query2+="where FriendID1 = ?";
		//TODO make it so you can actually search for full name

		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query);
			PreparedStatement stmt2 = conn.prepareStatement(query2);
			stmt.setInt(1, deviceID);
			stmt2.setInt(1, deviceID);
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}

			ArrayList<Integer> online = connectedDevicesManager.getAllConnectedDeviceIDs();

			while(result.next()){
				JSONObject row = new JSONObject();
				String val = result.getString("user_accounts.FirstName");
				String name = "";
				row.put("firstname", val == null ? "NULL" : val);
				name += (val == null ? "" : val);
				val = result.getString("user_accounts.MiddleName");
				row.put("middlename", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				val = result.getString("user_accounts.LastName");
				row.put("lastname", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				name.trim();
				row.put("name", name);
				row.put("deviceID", result.getInt("user_accounts.AccountID"));
				if (online.contains(result.getInt("user_accounts.AccountID"))) {
					row.put("online", true);
					JSONObject location = new JSONObject();
					Device	dev = connectedDevicesManager.getDevice(result.getInt("user_accounts.AccountID"));
					//TODO decide "visible"
					row.put("visible", true);
					location.put("latitude",  dev.getLatitude());
					location.put("longitude", dev.getLongitude());
					row.put("location", location);
				}
				else{
					row.put("online", false);
				}
				rows.put(row);
			}


			if (stmt2.execute()) {
				result = stmt2.getResultSet();
				System.out.println("Requester: " + deviceID);
			}
			while(result.next()){
				JSONObject row = new JSONObject();
				String val = result.getString("user_accounts.FirstName");
				String name = "";
				row.put("firstname", val == null ? "NULL" : val);
				name += (val == null ? "" : val);
				val = result.getString("user_accounts.MiddleName");
				row.put("middlename", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				val = result.getString("user_accounts.LastName");
				row.put("lastname", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);
				name.trim();
				row.put("name", name);
				row.put("deviceID", result.getInt("user_accounts.AccountID"));
				if (online.contains(result.getInt("user_accounts.AccountID"))) {
					row.put("online", true);
					JSONObject location = new JSONObject();
					Device	dev = connectedDevicesManager.getDevice(result.getInt("user_accounts.AccountID"));
					//TODO decide "visible"
					row.put("visible", true);
					location.put("latitude",  dev.getLatitude());
					location.put("longitude", dev.getLongitude());
					row.put("location", location);
				}
				else{
					row.put("online", false);
				}
				rows.put(row);
			}
			/*if (rows.size() < 1) {
				conn.close();
				return rows;
			}

			stmt = conn.prepareStatement(query2);*/

			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return rows;
		}
	}


	/*
	 * This function will first verify the user's session status. If it is valid, it will then 
	 * call another function that returns a given user's friends from the database. It returns a 
	 * JSONObject containing this information, which may be sent back to the user's client.
	 */ 
	private JSONObject handleGetFriends(JSONObject request){
		
		// response is a JSONObject that will contain the list of friends and other information that is to be sent to the client.
		JSONObject response = new JSONObject();
		
		// sessionID is a randomized string that uniquely maps to one user ID.
		String sessionID = request.getString("sessionid");

		// Check the status of the user's session id using the deviceSessionManager object.
		int sessionStatus = deviceSessionManager.validateSession(sessionID);

		// deviceID is the user's unique ID.
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
			// If the session identifier does not exist on the server, ignore the client's request and mark the session status as "invalid" in the response.
			// Client-side, this will sign the user out.
				response.put("sessionstatus", "invalid");
			break;

			case DeviceSessionManager.SESSION_TIMEOUT:
			// If the session identifier is too old, meaning that it has been too long since the client last interacted with the server, ignore the client's
			// request and mark the session status as "timeout" in the response. Remove the deprecated session identifier from the server.
			// Client-side, this will sign the user out.
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;

			case DeviceSessionManager.SESSION_REFRESH:
			// If the session identifier is old, but not old enough to be deprecated, generate a new session id that maps to the user's id and remove the old
			// one from the server. In the response, mark the session status as "update" and include the new session id.
			// Client-side, this will update the session id stored on the device
 				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);
			// Get the user id that the session identifier maps to.
				deviceID = deviceSessionManager.getDeviceID(sessionID);
			// Fetch a list of this user's friends from the database and place it in the response.
			// This is used to populate the client's friends list and contains info such as each friend's online status and location.
				response.put("people", getFriends(deviceID));
			break;
			
			case DeviceSessionManager.SESSION_ACTIVE:
			// If the session identifier is active, mark the session status as "active" and proceed with the request.
				response.put("sessionstatus", "active");
			// Get the user id that the session identifier maps to.
				deviceID = deviceSessionManager.getDeviceID(sessionID);
			// Fetch a list of this user's friends from the database and place it in the response.
			// This is used to populate the client's friends list and contains info such as each friend's online status and location.
				response.put("people", getFriends(deviceID));
			break;
		}
		
		//Set the response type to be the same as the request type
		response.put("type", "GET_FRIENDS");

		//return server response, which is passed to the client in the main routine
		return response;
	}

	private boolean updateProfile(int deviceID, JSONObject profileInfo){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		String dbquery = "update user_accounts set ";
		boolean updated = false;
		ArrayList<String> parameters = new ArrayList<String>();
		ArrayList<String> interests = new ArrayList<String>();
		
		//modify mysql string and list of parameters
		dbquery += checkAndPrepare(parameters, "FirstName", "firstname", profileInfo);
		dbquery += checkAndPrepare(parameters, "MiddleName", "middlename", profileInfo);
		dbquery += checkAndPrepare(parameters, "LastName", "lastname", profileInfo);
		dbquery += checkAndPrepare(parameters, "Gender", "gender", profileInfo);
		dbquery += checkAndPrepare(parameters, "Status", "occupation", profileInfo);
		dbquery += checkAndPrepare(parameters, "StatusMessage", "statusmessage", profileInfo);
		dbquery += checkAndPrepare(parameters, "YearOfBirth", "yearofbirth", profileInfo);

		//place interests into single String
		dbquery += checkAndPrepareInterests(parameters, profileInfo);

		//finish query string
		dbquery += " where AccountID = ?";


		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			int count = 1;
			for (String columnVal : parameters) {
				stmt.setString(count, columnVal);
				count++;
			}
			stmt.setInt(count, deviceID);

			if (stmt.execute()) {
				updated = true;
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return updated;
		}
	}

	//Check if jsonobject has field and modify parameter array accordingly. Return string segment for mysql statement
	private String checkAndPrepare(ArrayList<String> array, String column, String name, JSONObject json){
		String field = json.optString(name, null);
		try{
			if (field != null) {
				array.add(field);
				if (array.size() == 1) {
					return column + " = ?";
				}
				else{
					return ", " + column + " = ?";
				}
			}
			else{
				return "";
			}
		}
		catch (Exception e){
			e.printStackTrace();
			return "";
		}
		
	}

	private String checkAndPrepareInterests(ArrayList<String> array, JSONObject json){
		try{
			JSONArray interests = json.getJSONArray("interests");
			String interestTags = "";
			for (int i = 0; i < interests.length(); i++) {
				interestTags += ((i == 0 ? "" : "&_&") + (String) interests.get(i));
			}
			array.add(interestTags);
			if (array.size() == 1) {
				return "Interests = ?";
			}
			else{
				return ", Interests = ?";
			}
		}
		catch (Exception e){
			e.printStackTrace();
			return "";
		}
	}

	private JSONObject handleUpdateProfile(JSONObject request){
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

				response.put("updated", updateProfile(deviceID, request.getJSONObject("person")));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				response.put("updated", updateProfile(deviceID, request.getJSONObject("person")));
			break;
		}
		response.put("type", "UPDATE_PROFILE");
		return response;
	}

	private void putInfoFromResultToJSON(JSONObject json, String name, String column, ResultSet resSet){
		try{
			String colVal = resSet.getString(column);
			if (colVal != null) {
				json.put(name, colVal);
			}
		}
		catch (SQLException e){
			e.printStackTrace();
		}
		
	}

	private void putInfoFromResultToJSON(JSONObject json, String name, String column, ResultSet resSet, int length){
		try{
			String colVal = resSet.getString(column);
			if (colVal != null) {
				json.put(name, colVal.substring(0, length));
			}
		}
		catch (SQLException e){
			e.printStackTrace();
		}
		
	}

	//return profile info for specified user id
	private JSONObject getProfile(int deviceID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		String dbquery = "select FirstName, MiddleName, LastName, Gender, Status, YearOfBirth, Interests, StatusMessage from user_accounts where AccountID = ?";
		ResultSet result = null;
		JSONObject profile = new JSONObject();
		try{
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			stmt.setInt(1, deviceID);
			if (stmt.execute()) {
				result = stmt.getResultSet();
				if (!result.isBeforeFirst()) {
					return profile;
				}
				result.next();
				putInfoFromResultToJSON(profile, "firstname", "FirstName", result);
				putInfoFromResultToJSON(profile, "middlename", "MiddleName", result);
				putInfoFromResultToJSON(profile, "lastname", "LastName", result);
				putInfoFromResultToJSON(profile, "gender", "Gender", result);
				putInfoFromResultToJSON(profile, "occupation", "Status", result);
				putInfoFromResultToJSON(profile, "yearofbirth", "YearOfBirth", result, 4); //only get year
				putInfoFromResultToJSON(profile, "statusmessage", "StatusMessage", result);
				String interests = result.getString("Interests");
				JSONArray interestTags = new JSONArray();
				//get individual interests and place in array
				if (interests != null) {
					for (String interest : interests.split("&_&")) {
						interestTags.put(interest);
					}
				}
				profile.put("interests", interestTags);
			}
			conn.close();

		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return profile;
		}
	}
	
	//check user session status and get user profile info if session is valid
	private JSONObject handleGetProfile(JSONObject request){
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

				response.put("person", getProfile(deviceID));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				response.put("sessionstatus", "active");
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				response.put("person", getProfile(deviceID));
			break;
		}
		response.put("type", "");
		return response;
	}


	private void handleQuit(JSONObject request){
		String sessionID = request.getString("sessionid");
		int deviceID = deviceSessionManager.getDeviceID(sessionID);
		connectedDevicesManager.disconnectDevice(deviceID);
		deviceSessionManager.removeSession(sessionID);
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