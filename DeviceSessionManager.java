import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Executors;
import java.util.concurrent.*;

import java.security.SecureRandom;
import java.math.BigInteger;

//This class manages user session identifiers such that the client may identify itself with a unique, cryptographically secure session id. 
class DeviceSessionManager{
	//status codes
	public static final int SESSION_ACTIVE = 0;
	public static final int SESSION_REFRESH = 1;
	public static final int SESSION_TIMEOUT = 2;
	public static final int SESSION_INVALID = 3;

	//lock for thread safety
	private ReadWriteLock sessionLock;

	//secore random number generator
	private SecureRandom secureGen;
	private HashMap<String, SessionInfo> activeSessions;

	//maximum age before session deprecation
	private int sessionTimeout;
	//age before session refresh. Should be lower that sessionTimeout
	private int sessionRefresh;

	public DeviceSessionManager(int timeout, int refresh){
		sessionLock = new ReentrantReadWriteLock();
		secureGen = new SecureRandom();
		activeSessions = new HashMap<String, SessionInfo>();
		sessionTimeout = timeout;
		sessionRefresh = refresh;
	}

	public DeviceSessionManager(){
		this(604800, 86400); //default timeout is one week, default refresh is one day
	}

	//register a new session
	public String newSession(int deviceID){
		String sid = createSessionID();
		sessionLock.writeLock().lock();
		activeSessions.put(sid, new SessionInfo(deviceID, sid));
		sessionLock.writeLock().unlock();
		return sid;
	}


	//get the user id that a session id maps to
	public int getDeviceID(String sessionID){
		SessionInfo session;
		sessionLock.readLock().lock();
		session = activeSessions.get(sessionID);
		if (session != null) {
			session = session.clone();
		}
		sessionLock.readLock().unlock();

		if (session == null) {
			return -1;
		}
		else{
			return session.getDeviceID();
		}
	}

	//make a copy of a session
	public SessionInfo getSession(String sessionID){
		SessionInfo session;
		sessionLock.readLock().lock();
		session = activeSessions.get(sessionID);
		if (session != null) {
			session = session.clone();
		}
		sessionLock.readLock().unlock();
		return session;
	}

	//check session status based on the age of the session id
	public int validateSession(String sessionID){
		SessionInfo session = getSession(sessionID);
		if (session == null) {
			return SESSION_INVALID;
		}
		else if(session.secondsSinceCreated() > sessionTimeout){
			return SESSION_TIMEOUT;
		}
		else if(session.secondsSinceCreated() > sessionRefresh){
			return SESSION_REFRESH;
		}
		else{
			return SESSION_ACTIVE;
		}
	}

	//remove an old session id and register a new one
	public String refreshSession(String sessionID){
		SessionInfo temp = getSession(sessionID);
		removeSession(sessionID);
		return newSession(temp.getDeviceID());
	}

	
	//remove a given session id
	public void removeSession(String sessionID){
		sessionLock.writeLock().lock();
		activeSessions.remove(sessionID);
		sessionLock.writeLock().unlock();
	}

	//create a 130-bit session id and encode it to a string
	private String createSessionID(){
		return new BigInteger(130, secureGen).toString(32);
	}
}