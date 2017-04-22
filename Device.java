import java.util.*;

//This class stores info about a connected user
class Device{
	private double latitude;
	private double longitude;
	private int deviceID;
	private long timeStamp; //In milliseconds

	private String firstName;
	private String middleName;
	private String lastName;

	private String occupation;
	private String gender;
	private Date birthDate;

	private String currentArea;

	private boolean nameStored; //
	private boolean visible;

	public Device(int mID){
		deviceID = mID;
		latitude = -1;
		longitude = -1;
		timeStamp = System.currentTimeMillis();

		firstName = null;
		middleName = null;
		lastName = null;
		occupation = null;
		gender = null;
		birthDate = null;

		nameStored = false; //is name chached from database
		visible = false;
	}

	public Device(int mID, double lat, double lng){
		deviceID = mID;
		latitude = lat;
		longitude = lng;
		timeStamp = System.currentTimeMillis();

		firstName = null;
		middleName = null;
		lastName = null;
		occupation = null;
		gender = null;
		birthDate = null;

		nameStored = false; //is name chached from database
		visible = true;
	}

	private Device(Device caller){
		deviceID = caller.deviceID;
		latitude = caller.latitude;
		longitude = caller.longitude;
		timeStamp = caller.timeStamp;

		firstName = caller.firstName;
		middleName = caller.middleName;
		lastName = caller.lastName;
		occupation = caller.occupation;
		gender = caller.gender;
		birthDate = caller.birthDate;

		nameStored = caller.nameStored;

		currentArea = caller.currentArea;
		visible = caller.visible;
	}

	//get age of this object on the server (in seconds)
	public int secondsSinceCreated(){
		return (int) (System.currentTimeMillis() - timeStamp)/1000;
	}

	public double getLongitude(){
		return longitude;
	}

	public double getLatitude(){
		return latitude;
	}

	public int getDeviceID(){
		return deviceID;
	}

	public Device clone(){
		return new Device(this);
	}

	public String getFirstName(){
		return firstName;
	}

	public void setFirstName(String fn){
		firstName = fn;
	}

	public String getMiddleName(){
		return middleName;
	}

	public void setMiddleName(String mn){
		middleName = mn;
	}

	public String getLastName(){
		return lastName;
	}

	public void setLastName(String ln){
		lastName = ln;
	}

	public String getOccupation(){
		return occupation;
	}

	public void setOccupation(String oc){
		occupation = oc;
	}

	//get first/last name combined
	public String getName(){
		if (firstName == null) {
			return "N/A";
		}
		else if (lastName == null) {
			return firstName;
		}
		else{
			return firstName + " " + lastName;
		}
	}

	public boolean isNameCached(){
		return nameStored;
	}

	public void setNameCached(boolean nc){
		nameStored = nc;
	}

	public void setArea(String area){
		currentArea = area;
	}

	public String getArea(){
		return currentArea;
	}

	public boolean isVisible(){
		return visible;
	}

	public void setVisible(boolean vis){
		visible	= vis;
	}
}