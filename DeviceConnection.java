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

//This class takes a socket connection from the main routine and handles the user request
//NOTE: "user", "account", "client" and "device" is used somewhat interchangeably throughout the code
public class DeviceConnection extends Thread{
	//static stuff
	private static final Object lock = new Object();
	private static final HashMap<Integer, String> locations = new HashMap<Integer, String>();
	private static final List<DeviceConnection> connectedDevices = new ArrayList<DeviceConnection>();
	private static final SecureRandom secureGen = new SecureRandom();
	private static final ConnectedDevicesManager connectedDevicesManager = new ConnectedDevicesManager();
	private static final DeviceSessionManager deviceSessionManager = new DeviceSessionManager();
	private static final DesignatedAreaManager designatedAreaManager = new DesignatedAreaManager();
	private static final ImageManager imageManager = new ImageManager();

	//IO stuff
	Socket mySocket;
	BufferedReader reader;
	PrintWriter writer;

	//main constructor. Takes a socket and an id number (id number deprecated)
	public DeviceConnection(Socket sock, int id) throws IOException{
		mySocket = sock;
		mySocket.setSoTimeout(2000);
		reader = new BufferedReader(new InputStreamReader(mySocket.getInputStream()));
		writer = new PrintWriter(mySocket.getOutputStream(), true);
	}

	//for testing connection timeouts and device visiblity to other devices. Takes a preset location in addition to normal params
	public DeviceConnection(int id, String nm, String loc){
		Device temp = new Device(id, Double.parseDouble(loc.split(",")[0]), Double.parseDouble(loc.split(",")[1]));
		temp.setFirstName(nm.split(" ")[0]);
		temp.setLastName(nm.split(" ")[1]);
		connectedDevicesManager.addDevice(temp);
	}


	public void run(){
		String clientRequest;
		String clntmsg = "default";

		//decoded json message from client
		JSONObject clientMessage;

		//encoded json message to send to client
		JSONObject response = new JSONObject();

		//objects for to-be-deprecated response structure. Now only used for SIGNUP 
		JSONObject person;
		JSONObject location;
		JSONArray array;

		//user account id
		int deviceID = -1;
		try{
			//read and decode message from client
			clntmsg = reader.readLine();
			clientMessage = new JSONObject(clntmsg);

			//acknowledge connection in terminal
			if (clientMessage.has("sessionid")) {
				deviceID = deviceSessionManager.getDeviceID(clientMessage.getString("sessionid"));
				System.out.println("Connected to user id: " + deviceID + "!");
			}
			else{
				System.out.println("Connected to device!");
			}

			//get message request type
			clientRequest = clientMessage.getString("type");

			//pass request to request handlers depending on request type
			if (clientRequest.equals("QUIT")) {
				handleQuit(clientMessage);
			}
			else if (clientRequest.equals("SEND_LOCATION")) {
				response = handleSendLocation(clientMessage);
				writer.println(response); //Pass response back to user
			}
			else if (clientRequest.equals("GET_LOCATIONS")) { //Deprecated. Request GET_FRIENDS instead
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
			}
			else if (clientRequest.equals("UPDATE_PROFILE")){
				response = handleUpdateProfile(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("GET_PROFILE")){
				response = handleGetProfile(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("UPLOAD_IMAGE")){
				response = handleUploadImage(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("GET_IMAGES")){
				response = handleGetImages(clientMessage);
				writer.println(response);
			}
			else if (clientRequest.equals("SIGNUP")){
				//handle create new user account
				response = new JSONObject();
				//set response type to be same as request type
				response.put("type", "SIGNUP");

				//check if email address is already in use
				if (emailTaken(clientMessage.getString("username"))) {
					//if in use, mark the signup as unsuccessful with a message explaining why
					response.put("signupsuccessful", false);
					response.put("why", "E-mail address is already in use");
				}
				else{
					//if not in use, mark the signup as successful and proceed to create the new user
					response.put("signupsuccessful", true);
					createUser(clientMessage);
				}
				writer.println(response);
			}
			else{
				response = new JSONObject();
				//put response type for debugging purposes
				response.put("type", "DEFAULT");
				writer.println(response);
			}
			mySocket.close();
		}
		catch(org.json.JSONException e){
			e.printStackTrace();
		}  
		catch (IOException e){
			e.printStackTrace();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			//acknowledge user disconnect in terminal
			if (deviceID > -1) {
				System.out.println("Disconnected from user: " + deviceID + ".");
			}
			else{
				System.out.println("Disconnected from device.");
			}
			return;
		}
	}

	//preset connected devices for testing purposes
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

	//generic method for handling mysql select queries. returns a jsonarray of results
	private JSONArray performQuery(String query, String... params){
		//params for connection to database
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//variables for storing results
		ResultSet result = null;
		JSONArray rows = new JSONArray();

		try{
			//connect to database
			Connection conn = DriverManager.getConnection(url, username, password);

			//prepare query
			PreparedStatement stmt = conn.prepareStatement(query);

			//set query parameters
			for (int i = 0; i < params.length; i++) {
				stmt.setString(i+1, params[i]);
			}

			//store results if query executed successfully
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}

			//get column headers
			ResultSetMetaData meta = result.getMetaData();
			int columns = meta.getColumnCount();
			String columnName[] = new String[columns];
			for (int i = 0; i < columns;) {
				columnName[i] = meta.getColumnLabel(++i);
			}

			//get result rows and store in jsonArray
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

	//Search database for users with name that matches query. Skip over user that requested the search. 
	//Return a jsonArray of matching users and their info
	private JSONArray searchUsers(String searchQuery, int deviceID){
		//params for database connection
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//store results
		ResultSet result = null;
		JSONArray rows = new JSONArray();

		//main mysql query
		String query1 = "select * from user_accounts where FirstName like ? " +
						"or MiddleName like ? " +
						"or LastName like ?";
		//queries for marking certain certain results as "already friends" or "friend request send" etc
		//not yet in use
		String query2 = "select * from friend_requests where ? in(Sender)";
		String query3 = "select * from friends where ? in(FriendID1)";



		//TODO make it so you can actually search for full name

		try{
			//connect, execute and store results
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query1);
			for (int i = 0; i < 3; i++) {
				stmt.setString(i+1, searchQuery + "%");
			}
			if (stmt.execute()) {
				result = stmt.getResultSet();
			}

			//System.out.println("SEARCH FOR PEOPLE.. query: " + searchQuery);

			//store results and their info
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

	//method that checks if a given username and password is a match
	boolean authenticate(String user, String pass){
		//database connection params
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//mysql query
		String dbquery = "select * from user_accounts where ? in(Email)";
		ResultSet result = null;
		boolean match = false;

		try{
			//connect and prepare statement
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);

			//set username parameter
			stmt.setString(1, user);
			if (stmt.execute()) {
				result = stmt.getResultSet();
				//check if result is nonempty
				if (result.isBeforeFirst()) {
					//iterate to first row
					result.next();
					//get stored hashed password from result
					String dbpass = result.getString("Password");
					//get stored salt from result
					String salt = result.getString("Salt");
					//create a sha-256 hash based on plaintext password from client request and salt
					String hash = createHash(pass + salt);

					//check if generated hash and stored hash matches
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
		//database connection params
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//new user info
		String user = json.getString("username");
		String pass = json.getString("password");
		String salt = createSalt();
		String hash = createHash(pass + salt);
		String gender = json.getString("gender");
		String status = json.getString("occupation");

		String fName = user.split("@")[0]; //to be deprecated

		//mysql query for creating new user
		String dbquery = "insert into user_accounts (Email, Password, Gender, Status, Salt, FirstName) values(?, ?, ?, ?, ?, ?)";

		//new user created
		boolean created = false;

		try{
			//connect to db, prepare statement and set params
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			stmt.setString(1, user);
			stmt.setString(2, hash);
			stmt.setString(3, gender);
			stmt.setString(4, status);
			stmt.setString(5, salt);
			stmt.setString(6, fName);

			//set created as true if statement executed successfully
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
		//db connection params
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//db query
		String dbquery = "select * from user_accounts where ? in(Email)";

		//results
		ResultSet result = null;
		boolean match = true;

		try{
			//connect to db, prepare statement and set parameter
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			stmt.setString(1, email);

			//execute
			if (stmt.execute()) {
				//get result
				result = stmt.getResultSet();
				//if result is nonempty, set match as true
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
			//get instance of sha-256 hasher
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

			//get byte data of string to hash
			byte[] toHashBytes = toHash.getBytes();

			//generate hash bytes from input bytes
			retval = sha256.digest(toHashBytes);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		finally{
			//convert hash bytes to hex string ("[0-F]+")
			return bytesToHex(retval);
		}
	}

	//Create hex string from byte array (hash)
	//code from http://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	private String bytesToHex(byte[] bytes) {
		//4 bits of data per char
		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
			//get current byte
    		int v = bytes[j] & 0xFF;

    		//set chars
        	hexChars[j * 2] = hexArray[v >>> 4];
        	hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		//convert char array to string and return
	    return new String(hexChars);
	}

	//code from http://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string
	private String createSalt(){
		//create new 256+ bit string to be used as salt
		return new BigInteger(260, secureGen).toString(32);
	}

	//handles location updates from users
	private JSONObject handleSendLocation(JSONObject request){
		//response to be sent to user
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		double latitude;
		double longitude;

		Device temp;

		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				latitude = request.getJSONObject("location").getDouble("latitude");
				longitude = request.getJSONObject("location").getDouble("longitude");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				if (designatedAreaManager.isOnCampus(latitude, longitude)) {
					//add device to list of online devices with the given location if on campus
					connectedDevicesManager.addDevice(new Device(deviceID, latitude, longitude));
				}
				else{
					//ignore sent location otherwise
					connectedDevicesManager.addDevice(new Device(deviceID));
				}

				//NAMESTUFF
				//TODO MAKE THIS MORE ELEGANT

				//modify name of device
				temp = connectedDevicesManager.getDevice(deviceID);
				temp.setFirstName(getNamesFromDatabase(deviceID)[0]);

				//add modified device to list of online devices
				connectedDevicesManager.addDevice(temp);
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//set location of connected device
				latitude = request.getJSONObject("location").getDouble("latitude");
				longitude = request.getJSONObject("location").getDouble("longitude");

				if (designatedAreaManager.isOnCampus(latitude, longitude)) {
					//add device to list of online devices with the given location if on campus
					connectedDevicesManager.addDevice(new Device(deviceID, latitude, longitude));
				}
				else{
					//ignore sent location otherwise
					connectedDevicesManager.addDevice(new Device(deviceID));
				}
				

				//modify name of this device
				temp = connectedDevicesManager.getDevice(deviceID);
				temp.setFirstName(getNamesFromDatabase(deviceID)[0]);

				//add device to list of online devices
				connectedDevicesManager.addDevice(temp);
			break;
		}

		response.put("type", "SEND_LOCATION");
		response.put("body", "Message received!");
		return response;
	}

	//DEPRECATED
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
		response.put("type", "GET_LOCATIONS");
		return response;
	}

	//handle request to log in to service
	private JSONObject handleLogin(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		String query = "select * from user_accounts where ? in(Email)";

		switch (sessionStatus){
			//do nothing if no session id does not exist on server
			case DeviceSessionManager.SESSION_INVALID:
			break;
			//remove previous session id passed by client if it exists on server
			case DeviceSessionManager.SESSION_TIMEOUT:
			case DeviceSessionManager.SESSION_REFRESH:
			case DeviceSessionManager.SESSION_ACTIVE:
				deviceSessionManager.removeSession(sessionID);
			break;
		}

		//get username and password from client message
		String username = request.getString("username");
		String password = request.getString("password");


		//check if username and password combination is a match
		if (!authenticate(username, password)) {
			//if not, mark login as unsuccessful
			response.put("loginSuccessful", false);
		}
		else{
			//if successful, mark login as such
			response.put("loginSuccessful", true);

			//get user info from database
			JSONObject user = (JSONObject) performQuery(query, username).get(0);

			//put user profile info in response
			response.put("firstname", user.getString("FirstName"));
			response.put("middlename", user.getString("MiddleName"));
			response.put("lastname", user.getString("LastName"));

			//pass device id and session id to user
			deviceID = user.getInt("AccountID");
			sessionID = deviceSessionManager.newSession(deviceID);

			//since this is a new session identifier, tell client to store this one
			response.put("sessionstatus", "update");
			response.put("sessionid", sessionID);
		}

		
		response.put("type", "LOGIN");
		return response;
	}

	//handle user request to search for people
	private JSONObject handleFriendSearch(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;


		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//search database based on client query and put result in response
				response.put("people", searchUsers(request.getString("query"), deviceID));

			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//search database based on client query and put result in response
				response.put("people", searchUsers(request.getString("query"), deviceID));
			break;
		}
		response.put("type", "SEARCH_PEOPLE");
		return response;
	}

	//get full name of account associated with given device id
	private String[] getNamesFromDatabase(int devID){
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

	//register a friend request on the server
	private int registerRequest(int senderID, int receiverID){
		//database params 
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//query for registering new friend request
		String dbquery = "insert ignore into friend_requests(Sender, Receiver) values(?, ?)";

		//query for checking whether two users are already friends
		String dbquery2 = "select * from friends where FriendID1 = ? and FriendID2 = ? or FriendID1 = ? and FriendID2 = ?";

		boolean requestRegistered = false;
		boolean isFriends = false;
		ResultSet result = null;

		try{
			//connect, prepare and set params
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			PreparedStatement stmt2 = conn.prepareStatement(dbquery2);
			stmt.setInt(1, senderID);
			stmt.setInt(2, receiverID);
			stmt2.setInt(1, senderID);
			stmt2.setInt(2, receiverID);
			stmt2.setInt(3, receiverID);
			stmt2.setInt(4, senderID);

			//execute query 2 and check whether users are already friends 
			stmt2.execute();
			result = stmt2.getResultSet();
			if (result.isBeforeFirst()) {
				isFriends = true;
			}
			else{
				//if not register new request and set requestRegistered accordingly
				requestRegistered = stmt.executeUpdate() > 0;
			}
			conn.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			if (isFriends) {
				//return 3 if already friends
				return 3;
			}
			else if(!requestRegistered){
				//return 2 if there was en error in registering the request
				return 2;
			}
			else{
				//return 1 if successful
				return 1;
			}
		}
	}


	//this method handles user friend requests
	private JSONObject handleFriendRequest(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//attempt to register friend request and put the request_registered field accordingly
				response.put("request_registered", registerRequest(deviceID, request.getInt("target")));				
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//attempt to register friend request and put the request_registered field accordingly
				response.put("request_registered", registerRequest(deviceID, request.getInt("target")));	
			break;
		}
		response.put("type", "SEND_REQUEST");
		return response;
	}


	//this method returns pending friend requests to the given user id
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
			//connect, prepare and set params
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query);
			stmt.setInt(1, deviceID);
			if (stmt.execute()) {
				//get results if statement executed successfully
				result = stmt.getResultSet();
			}

			//for each friend request, place info about request in a json array
			while(result.next()){
				if (result.getInt("AccountID") == deviceID) {
					continue; //Don't list user that performed search
				}
				JSONObject row = new JSONObject();
				String val = result.getString("user_accounts.FirstName");
				String name = "";
				row.put("firstname", val == null ? "NULL" : val); //put first name if it exists
				name += (val == null ? "" : val);

				val = result.getString("user_accounts.MiddleName"); //put middle name if it exists
				row.put("middlename", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);

				val = result.getString("user_accounts.LastName"); //put last name if it exists
				row.put("lastname", val == null ? "NULL" : val);
				name += (val == null ? "" : " " + val);

				name.trim();
				row.put("name", name); //put full name
				row.put("deviceID", result.getInt("user_accounts.AccountID"));
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


	//this method handles and returns a user's request for pending friend requests
	private JSONObject handleGetFriendRequests(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//put pending friend requests in response
				response.put("people", getRequests(deviceID));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//put pending friend requests in response
				response.put("people", getRequests(deviceID));
			break;
		}
		response.put("type", "GET_REQUESTS");
		return response;
	}

	//this method registers new friends on server or removes the friend request if declined
	private boolean friendRequestResponse(JSONObject response, int receiverID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//query registering new friends
		String dbquery = "insert ignore into friends(FriendID1, FriendID2) values(?, ?)";

		//query for removing friend request
		String dbquery2 = "delete from friend_requests where Sender = ? and Receiver = ? or Sender = ? and Receiver = ?";

		//query to verify that request exists
		String dbquery3 = "select * from friend_requests where Sender = ?";
		boolean isFriends = false;
		ResultSet result = null;
		int senderID;

		try{
			//connect, prepare and set params
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			PreparedStatement stmt2 = conn.prepareStatement(dbquery2);
			PreparedStatement stmt3 = conn.prepareStatement(dbquery3);

			//target the user that sent the friend request
			senderID = response.getInt("target");
			stmt.setInt(1, receiverID);
			stmt.setInt(2, senderID);
			stmt2.setInt(1, receiverID);
			stmt2.setInt(2, senderID);
			stmt2.setInt(3, senderID);
			stmt2.setInt(4, receiverID);
			stmt3.setInt(1, senderID);

			if (stmt3.execute()) {
				if (!stmt3.getResultSet().isBeforeFirst()) {
					//stop client from attempting to become friends with someone that never sent a friend request
					return isFriends;
				}
			}
			if (response.getString("response").equals("accept")) {
				//register new friends 
				stmt.execute();
			}
			//remove old friend request(s)
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

	//this method handles a user response to a friend request
	private JSONObject handleRespondRequest(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//process response to friend request
				response.put("friends", friendRequestResponse(request, deviceID));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//process response to friend request
				response.put("friends", friendRequestResponse(request, deviceID));
			break;
		}
		response.put("type", "RESPOND_REQUEST");
		return response;
	}

	//this method returns a list of all friends of the given account id
	private JSONArray getFriends(int deviceID){
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";
		ResultSet result = null;
		JSONArray rows = new JSONArray();
		//queries for getting friends and their info
		String 	query = "select * from friends join user_accounts ";
				query+= "on friends.FriendID1 = user_accounts.AccountID ";
				query+= "where FriendID2 = ?";
		String 	query2 ="select * from friends join user_accounts ";
				query2+="on friends.FriendID2 = user_accounts.AccountID ";
				query2+="where FriendID1 = ?";
		//TODO make it so you can actually search for full name

		try{
			//connect, prepare and set params
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(query);
			PreparedStatement stmt2 = conn.prepareStatement(query2);
			stmt.setInt(1, deviceID);
			stmt2.setInt(1, deviceID);
			if (stmt.execute()) {
				//store result if executed successfully
				result = stmt.getResultSet();
			}

			//get a list of all connected devices to prevent repeated threaded calls to a shared resource
			ArrayList<Integer> online = connectedDevicesManager.getAllConnectedDeviceIDs();

			//fetch profile info for each friend
			while(result.next()){
				JSONObject row = new JSONObject();

				//put friend name
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

				//put friend device id
				row.put("deviceID", result.getInt("user_accounts.AccountID"));
				if (online.contains(result.getInt("user_accounts.AccountID"))) {
					//put friend online status
					row.put("online", true);
					JSONObject location = new JSONObject();
					Device	dev = connectedDevicesManager.getDevice(result.getInt("user_accounts.AccountID"));

					//set coordinates if visible to other users
					if (dev.isVisible()) {
						row.put("visible", true);
						location.put("latitude",  dev.getLatitude());
						location.put("longitude", dev.getLongitude());
					}
					else{
						row.put("visible", false);
					}
					location.put("name", designatedAreaManager.currentArea(dev));
					
					row.put("location", location);
				}
				else{
					row.put("online", false);
				}
				rows.put(row);
			}


			//SAME AS ABOVE for query 2
			if (stmt2.execute()) {
				result = stmt2.getResultSet();
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
			//connect to db, prepare statement and set params
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			int count = 1;
			//set each of the parameters
			for (String columnVal : parameters) {
				stmt.setString(count, columnVal);
				count++;
			}
			//set device id
			stmt.setInt(count, deviceID);

			//execute and set updated as true if successful
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
					//this is the first parameter in query
					return column + " = ?";
				}
				else{
					//not the first parameter in query
					return ", " + column + " = ?";
				}
			}
			else{
				//do not modify query
				return "";
			}
		}
		catch (Exception e){
			e.printStackTrace();
			//do not modify query
			return "";
		}
		
	}

	//interests go into a single column. This method separates different interests with "&_&" and places them into a signle string to be stored in the database
	//this will overwrite interests stored in database even when none are listed, so long as the request has an "interests" jsonArray
	private String checkAndPrepareInterests(ArrayList<String> array, JSONObject json){
		try{
			JSONArray interests = json.getJSONArray("interests");
			String interestTags = "";
			for (int i = 0; i < interests.length(); i++) {
				//create combined string of interests with "&_&" as separator
				interestTags += ((i == 0 ? "" : "&_&") + (String) interests.get(i));
			}
			array.add(interestTags);
			if (array.size() == 1) {
				//this is the first parameter in query
				return "Interests = ?";
			}
			else{
				//this is not the first parameter in query
				return ", Interests = ?";
			}
		}
		catch (Exception e){
			e.printStackTrace();
			//do not modify query
			return "";
		}
	}

	/*
	This method handles a request to update the user profile.
	If the user session identifier does not exist on the server, or if it is too old, ignore the request.
	*/
	private JSONObject handleUpdateProfile(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//update user profile info and tell user whether info was updated
				response.put("updated", updateProfile(deviceID, request.getJSONObject("person")));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//update user profile info and tell user whether info was updated
				response.put("updated", updateProfile(deviceID, request.getJSONObject("person")));
			break;
		}
		response.put("type", "UPDATE_PROFILE");
		return response;
	}

	//method to place result from resultset into jsonObject
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

	//overloaded version of above method that takes cap on string length as a parameter
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
		//database connection params
		String url = "jdbc:mysql://localhost:3306/accounts?autoReconnect=true&useSSL=false";
		String username = "java";
		String password = "password";

		//database query
		String dbquery = "select FirstName, MiddleName, LastName, Gender, Status, YearOfBirth, Interests, StatusMessage from user_accounts where AccountID = ?";

		//variable for storing query result
		ResultSet result = null;

		//profile jsonObject to pass back to client
		JSONObject profile = new JSONObject();
		try{
			//connect to database, prepare statement and set parameter
			Connection conn = DriverManager.getConnection(url, username, password);
			PreparedStatement stmt = conn.prepareStatement(dbquery);
			stmt.setInt(1, deviceID);


			if (stmt.execute()) {
				//statement executed successfully
				result = stmt.getResultSet();
				if (!result.isBeforeFirst()) {
					//if accountID did not match a real account, return empty profile (null)
					return profile;
				}
				//iterate to first
				result.next();

				//fetch user info and store in jsonobject
				putInfoFromResultToJSON(profile, "firstname", "FirstName", result);
				putInfoFromResultToJSON(profile, "middlename", "MiddleName", result);
				putInfoFromResultToJSON(profile, "lastname", "LastName", result);
				putInfoFromResultToJSON(profile, "gender", "Gender", result);
				putInfoFromResultToJSON(profile, "occupation", "Status", result);
				putInfoFromResultToJSON(profile, "yearofbirth", "YearOfBirth", result, 4); //only get year
				putInfoFromResultToJSON(profile, "statusmessage", "StatusMessage", result);

				//get individual interests and place in array
				String interests = result.getString("Interests");
				JSONArray interestTags = new JSONArray();
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

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//get user profile and place into response
				response.put("person", getProfile(deviceID));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//get user profile and place into response
				response.put("person", getProfile(deviceID));
			break;
		}
		response.put("type", "");
		return response;
	}

	//store image ussing the ImageManager class
	private boolean uploadImage(int deviceID, String encoded){
		return imageManager.storeProfilePicture(encoded, deviceID);
	}


	//check session status and store image if valid
	private JSONObject handleUploadImage(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//store image
				response.put("success", uploadImage(deviceID, request.getString("image")));
	
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				//store image
				response.put("success", uploadImage(deviceID, request.getString("image")));

			break;
		}
		response.put("type", "UPLOAD_IMAGE");
		return response;
	}


	//find and return a list of encoded images
	private JSONArray getImages(JSONArray requested){
		JSONArray images = new JSONArray();
		JSONObject tempRow;

		try{
			for (int i = 0; i < requested.length(); i++) {
				tempRow = new JSONObject();
				int deviceID = requested.getJSONObject(i).getInt("deviceID");
				String size = requested.getJSONObject(i).getString("size");

				//set id
				tempRow.put("deviceID", deviceID);

				//find encoded image of appropriate size
				if (size.equals("small")) {
					tempRow.put("image", imageManager.getSmallEncodedPicture(deviceID));
				}
				else if (size.equals("medium")){
					tempRow.put("image", imageManager.getMediumEncodedPicture(deviceID));
				}
				else if (size.equals("large")){
					tempRow.put("image", imageManager.getLargeEncodedPicture(deviceID));
				}
				else{
					tempRow.put("image", "NULL");
				}
				images.put(tempRow);
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			return images;
		}
		
	}

	//handle user request for a list of images based on their owners' account id
	private JSONObject handleGetImages(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get encoded images and place into jsonobject
				response.put("images", getImages(request.getJSONArray("requested")));
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get encoded images and place into jsonobject
				response.put("images", getImages(request.getJSONArray("requested")));
			break;
		}
		response.put("type", "GET_IMAGES");
		return response;
	}


	//handle request to sign out
	private void handleQuit(JSONObject request){
		//get device id from session id
		String sessionID = request.getString("sessionid");
		int deviceID = deviceSessionManager.getDeviceID(sessionID);

		//remove this device from list of online devices
		connectedDevicesManager.disconnectDevice(deviceID);

		//remove session identifier associated with this user
		deviceSessionManager.removeSession(sessionID);
	}

	//return a list of connected devices from shared manager
	public static ArrayList<Device> getAllConnectedDevices(){
		return connectedDevicesManager.getAllConnectedDevices();
	}



	//TEMPLATE

	/*	private JSONObject handle(JSONObject request){
		JSONObject response = new JSONObject();
		String sessionID = request.getString("sessionid");

		//get user session status from session manager
		int sessionStatus = deviceSessionManager.validateSession(sessionID);
		int deviceID;
		
		switch (sessionStatus){
			case DeviceSessionManager.SESSION_INVALID:
				//session id does not exist, ignore request
				response.put("sessionstatus", "invalid");
			break;
			case DeviceSessionManager.SESSION_TIMEOUT:
				//session is deprecated, ignore request
				deviceSessionManager.removeSession(sessionID);
				response.put("sessionstatus", "timeout");
			break;
			case DeviceSessionManager.SESSION_REFRESH:
				//session id is old, refresh session with new identifier and pass this to user
				sessionID = deviceSessionManager.refreshSession(sessionID);
				response.put("sessionstatus", "update");
				response.put("sessionid", sessionID);

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

				
			break;
			case DeviceSessionManager.SESSION_ACTIVE:
				//session is active
				response.put("sessionstatus", "active");

				//get device id based on session id
				deviceID = deviceSessionManager.getDeviceID(sessionID);

			break;
		}
		response.put("type", "");
		return response;
	}*/
}