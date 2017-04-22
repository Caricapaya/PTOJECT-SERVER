import java.util.*;

class SessionInfo{
	private int deviceID;
	private String sessionID;
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

	public int secondsSinceCreated(){
		return (int) (System.currentTimeMillis() - timeStamp)/1000;
	}

	public int getDeviceID(){
		return deviceID;
	}

	public SessionInfo clone(){
		return new SessionInfo(this);
	}
}