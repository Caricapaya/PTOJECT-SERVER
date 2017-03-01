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

import org.json.*;


public class DeviceConnection extends Thread{
	//static stuff
	private static final Object lock = new Object();
	private static final HashMap<Integer, String> locations = new HashMap<Integer, String>();
	private static final List<DeviceConnection> connectedDevices = new ArrayList<DeviceConnection>();
	private static final SecureRandom secureGen = new SecureRandom();
	private static final ConnectedDevicesManager connectedDevicesManager = new ConnectedDevicesManager();

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
		myid = id;
		name = nm;
		locations.put(id, loc);
		connectedDevices.add(this);
	}


	public void run(){
		System.out.println("Connected to: " + myid + "!");
		String clientRequest;
		String clntmsg = "default";
		JSONObject clientMessage;
		JSONObject response;
		JSONObject person;
		JSONObject location;
		JSONArray array;
		try{
			clntmsg = reader.readLine();
			//clientMessage = new JSONObject(clntmsg);
			//System.out.println(clientMessage.toString(4));
			while(clntmsg != null){
				clientMessage = new JSONObject(clntmsg);
				clientRequest = clientMessage.getString("type");//clientMessage.split(":")[0];
				if (clientRequest.equals("QUIT")) {
					break;
				}
				else if (clientRequest.equals("SEND_LOCATION")) {
					System.out.println(clientMessage);
					double latitude = clientMessage.getJSONObject("location").getDouble("latitude");
					double longitude = clientMessage.getJSONObject("location").getDouble("longitude");
					//TODO
					/*synchronized(lock){
						locations.put(myid, clientMessage.getJSONObject("location").get("latitude") + "," + clientMessage.getJSONObject("location").get("longitude"));
					}*/
					response = new JSONObject();
					response.put("type", "SEND_LOCATION");
					response.put("body", "Message received!");
					writer.println(response);
				}
				else if (clientRequest.equals("GET_LOCATIONS")) {
					array = new JSONArray();
					synchronized (lock){
						for (DeviceConnection someID : connectedDevices) {
							if (someID.getID() != myid) {
								//writer.print(someID.getID() + ":" + locations.get(someID.getID()) + "&");
								person = new JSONObject();
								person.put("name", someID.getDeviceName());
								location = new JSONObject();
								location.put("latitude", Double.parseDouble(locations.get(someID.getID()).split(",")[0]));
								location.put("longitude", Double.parseDouble(locations.get(someID.getID()).split(",")[1]));
								person.put("location", location);
								person.put("deviceID", someID.getID());
								array.put(person);
							}
						}
					}
					response = new JSONObject();
					response.put("people", array);
					response.put("type", "GET_LOCATIONS");
					writer.println(response);
				}
				else if (clientRequest.equals("LOGIN")){
					String username = clientMessage.getString("username");
					String password = clientMessage.getString("password");
					String hashed = createHash(password);
					response = new JSONObject();
					response.put("type", "LOGIN");
					if (!authenticate(username, password)){
						response.put("loginSuccessful", false);
					}
					else{
						response.put("loginSuccessful", true);
					}
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
				clntmsg = reader.readLine();
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
		new DeviceConnection(1000, "Henry", "10.0,10.0");
		new DeviceConnection(2000, "Carl", "20.0,20.0");
		new DeviceConnection(3000, "Don Rosa", "30.0,30.0");
		new DeviceConnection(4000, "Paul", "40.0,40.0");
	}

	private ResultSet performQuery(String query, String... params){
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

	private JSONObject handleSendLocation(JSONObject request, JSONObject response){
		
	}
}