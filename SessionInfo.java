import java.util.*;

//this class stores information about a session
class SessionInfo{
	//device id that this session represents
	private int deviceID;

	//session id associated with this device id
	private String sessionID;

	//age of session
	private Long timeStamp;

	public SessionInfo(int did, String sid){
		deviceID = did;
		sessionID = sid;
		timeStamp = System.currentTimeMillis();
	}

	private SessionInfo(SessionInfo ssn){
		deviceID = ssn.deviceID;
		sessionID = ssn.sessionID;
		timeStamp = ssn.timeStamp;
	}


	//get age of session in seconds
	public int secondsSinceCreated(){
		return (int) (System.currentTimeMillis() - timeStamp)/1000;
	}

	public int getDeviceID(){
		return deviceID;
	}

	//create a clone of session
	public SessionInfo clone(){
		return new SessionInfo(this);
	}
}