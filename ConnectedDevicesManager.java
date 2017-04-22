import java.util.*;
import java.io.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Executors;
import java.util.concurrent.*;

//this class manages connected devices and provides thread-safe methods for checking whether a device is connected, removing a device etc
class ConnectedDevicesManager{

	//lock for thread-safe access
	ReadWriteLock devicesLock;

	//HashMap<Integer, Device> disconnectedDevices;

	//hashmap that ties device id to a device
	HashMap<Integer, Device> connectedDevices;
	//thread that disconnects aged devices
	ScheduledExecutorService oldDeviceCleaner;

	//maximum unrefreshed age of device before connection is timed out
	int connectionTimeout;

	public ConnectedDevicesManager(){
		this(15);
	}

	public ConnectedDevicesManager(int mtime){
		devicesLock	= new ReentrantReadWriteLock();
		connectedDevices = new HashMap<Integer, Device>();
		oldDeviceCleaner = Executors.newSingleThreadScheduledExecutor();
		connectionTimeout = mtime;
		oldDeviceCleaner.scheduleAtFixedRate(new CleanerService(), 0, 5, TimeUnit.SECONDS); //set service to run every 5 seconds
	}

	//thread that removes old devices from the list of connected devices
	private class CleanerService implements Runnable{
		@Override
		public void run(){
			ArrayList<Integer> flaggedForRemoval = new ArrayList<Integer>();
			devicesLock.writeLock().lock();
			//find old devices and flag them for removal
			for (int deviceKey : connectedDevices.keySet()) {
				if (connectedDevices.get(deviceKey).secondsSinceCreated() > connectionTimeout) {
					flaggedForRemoval.add(deviceKey);
				}
			}
			//remove old devices
			for (int key : flaggedForRemoval) {
				connectedDevices.remove(key);
			}
			devicesLock.writeLock().unlock();

		}
	}



	public void addDevice(Device deviceToAdd){
		devicesLock.writeLock().lock();
		connectedDevices.put(deviceToAdd.getDeviceID(), deviceToAdd);
		devicesLock.writeLock().unlock();
	}

	public void addDevice(int id, double latitude, double longitude){
		addDevice(new Device(id, latitude, longitude));
	}

	//get devices that map to a list of ids
	public ArrayList<Device> getDevices(ArrayList<Integer> ids){
		ArrayList<Device> retval = new ArrayList<Device>();
		devicesLock.readLock().lock();
		for (int deviceKey : ids) {
			retval.add(connectedDevices.get(deviceKey).clone()); //return clones of objects so that other threads can't modify them after they've been acquired
		}
		devicesLock.readLock().unlock();
		return retval;
	}


	//get a specific device based on id
	public Device getDevice(int id){
		Device retval;
		devicesLock.readLock().lock();
		retval = connectedDevices.get(id);
		if (retval != null) {
			retval = retval.clone();
		}
		devicesLock.readLock().unlock();
		return retval;
	}

	//get a list of all connected devices
	public ArrayList<Device> getAllConnectedDevices(){
		ArrayList<Device> retval = new ArrayList<Device>();
		devicesLock.readLock().lock();
		for (Device dev : connectedDevices.values()) {
			retval.add(dev.clone());
		}
		devicesLock.readLock().unlock();
		return retval;
	}

	//forcibly disconnect a device
	public void disconnectDevice(int id){
		devicesLock.writeLock().lock();
		connectedDevices.remove(id);
		devicesLock.writeLock().unlock();
	}

	//get ids of all connected devices
	public ArrayList<Integer> getAllConnectedDeviceIDs(){
		ArrayList<Integer> retval = new ArrayList<Integer>();
		devicesLock.readLock().lock();
		for (Device dev : connectedDevices.values()) {
			retval.add(dev.getDeviceID());
		}
		devicesLock.readLock().unlock();
		return retval;
	}


}