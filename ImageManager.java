import java.util.*;
import org.ini4j.*;
import java.io.*;

import java.awt.image.BufferedImage;
import java.awt.Graphics;
import javax.imageio.ImageIO;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.Executors;
import java.util.concurrent.*;


class ImageManager{
	private ReadWriteLock imageLock;

	String imageDirectory;
	final String imageDirectoryDefault = ".\\Profile_Pictures\\";

	//small image size
	int smallXSize = 0;
	int smallYSize = 0;
	final int smallXSizeDefault = 64;
	final int smallYSizeDefault = 64;
	final int SMALL = 0;

	//medium image size
	int mediumXSize = 0;
	int mediumYSize = 0;
	final int mediumXSizeDefault = 256;
	final int mediumYSizeDefault = 256;
	final int MEDIUM = 1;


	//max image size
	int maxXSize = 0;
	int maxYSize = 0;
	final int maxXSizeDefault = 1024;
	final int maxYSizeDefault = 1024;
	final int LARGE = 2;

	public ImageManager(){
		imageLock = new ReentrantReadWriteLock();

		try{
			//load sizes from config file
			Wini iniFile = new Wini(new File("config.ini"));
			imageDirectory = iniFile.get("profile pictures", "directory");

			smallXSize = iniFile.get("profile pictures", "small x-size", int.class);
			smallYSize = iniFile.get("profile pictures", "small y-size", int.class);

			mediumXSize = iniFile.get("profile pictures", "medium x-size", int.class);
			mediumYSize = iniFile.get("profile pictures", "medium y-size", int.class);

			maxXSize = iniFile.get("profile pictures", "max x-size", int.class);
			maxYSize = iniFile.get("profile pictures", "max y-size", int.class);
		}
		catch (Exception e){
			imageDirectory = imageDirectoryDefault;

			smallXSize = (smallXSize <= 0 ? smallXSizeDefault : smallXSize);
			smallYSize = (smallYSize <= 0 ? smallYSizeDefault : smallYSize);
			mediumXSize = (mediumXSize <= 0 ? mediumXSizeDefault : mediumXSize);
			mediumYSize = (mediumYSize <= 0 ? mediumYSizeDefault : mediumYSize);
			maxXSize = (maxXSize <= 0 ? maxXSizeDefault : maxXSize);
			maxYSize = (maxYSize <= 0 ? maxYSizeDefault : maxYSize);
			e.printStackTrace();
		}

		try{
			new File(imageDirectory).mkdirs();
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	//find filename based on user id and image size
	private String getFileName(int deviceID, int size){
		switch (size){
			case SMALL:
				return "profile_picture_" + deviceID + "_small.png";
			case MEDIUM: 
				return "profile_picture_" + deviceID + "_medium.png";
			case LARGE:
				return "profile_picture_" + deviceID + "_large.png";
			default:
				return "";
		}
	}

	//get full filepath including directory
	private String getFilePath(int deviceID, int size){
		return imageDirectory + getFileName(deviceID, size);
	}

	//decode data string (base64) to image
	private BufferedImage decodeImage(String rawString){
		//get raw byte data
		byte[] byteData = javax.xml.bind.DatatypeConverter.parseBase64Binary(rawString);

		//return value
		BufferedImage image = null;

		try{
			//read bytedata into image object
			ByteArrayInputStream byteStream = new ByteArrayInputStream(byteData);
			image = ImageIO.read(byteStream);
			byteStream.close();
		}
		catch (Exception e){
			e.printStackTrace();
		}
		

		return image;
	}


	//TODO SET THINGS AS PRIVATE
	private String encodeImage(BufferedImage img){
		String encoded = null;

		try{
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			ImageIO.write(img, "png", byteStream);
			byte[] bytes =  byteStream.toByteArray();
			encoded = javax.xml.bind.DatatypeConverter.printBase64Binary(bytes);
		}
		catch (Exception e){
			e.printStackTrace();
		}
		
		return encoded;
	}

	//decode and store 3 images of different sizes. File names are based on account id
	public boolean storeProfilePicture(String stringData, int deviceID){
		BufferedImage profilePicture = decodeImage(stringData);
		int baseHeight = profilePicture.getHeight();
		int baseWidth = profilePicture.getWidth();
		double ratio = (double)baseWidth / (double)baseHeight;

		System.out.println("height: " + baseHeight);
		System.out.println("width: " + baseWidth);

		while (profilePicture.getHeight() > maxYSize || profilePicture.getWidth() > maxXSize){
			int newHeight = (int) (profilePicture.getHeight() / Math.sqrt(2));
			int newWidth = (int) (profilePicture.getWidth() / Math.sqrt(2));

			System.out.println("height: " + newHeight);
			System.out.println("width: " + newWidth);


			profilePicture = resizeImage(profilePicture, newWidth, newHeight);
		}

		//scale height
		//leave ratio if height > width. Compress width if larger than height
		int scaledMediumX = (int) (mediumXSize*Math.min(1.0, ratio));
		int scaledSmallX = (int) (smallXSize*Math.min(1.0, ratio));

		BufferedImage largePicture = profilePicture;
		BufferedImage mediumPicture = resizeImage(profilePicture, scaledMediumX, mediumYSize);
		BufferedImage smallPicture = resizeImage(profilePicture, scaledSmallX, smallYSize);

		boolean success = false;
		try{
			imageLock.writeLock().lock();
			ImageIO.write(largePicture, "png", new File(getFilePath(deviceID, LARGE)));
			ImageIO.write(mediumPicture, "png", new File(getFilePath(deviceID, MEDIUM)));
			ImageIO.write(smallPicture, "png", new File(getFilePath(deviceID, SMALL)));
			success = true;
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			imageLock.writeLock().unlock();
			return success;
		}
	}

	//return an image resized to the specified size
	private BufferedImage resizeImage(BufferedImage image, int newWidth, int newHeight){
		BufferedImage tempImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
		Graphics graphics = tempImage.createGraphics();
		graphics.drawImage(image, 0, 0, newWidth, newHeight, null);
		graphics.dispose();
		return tempImage;
	}


	//load and return small image file based on account id
	public String getSmallEncodedPicture(int deviceID){
		imageLock.readLock().lock();
		File imageFile = new File(getFilePath(deviceID, SMALL));
		if (!imageFile.exists()) {
			return null;
		}
		String encodedImage = null;
		try{
			BufferedImage tempImage = ImageIO.read(imageFile);
			imageLock.readLock().unlock();
			encodedImage = encodeImage(tempImage);
		}
		catch(Exception e){
			imageLock.readLock().unlock();
			e.printStackTrace();
		}
		finally{
			return encodedImage;
		}
	}

	//load and return medium image file based on account id
	public String getMediumEncodedPicture(int deviceID){
		imageLock.readLock().lock();
		File imageFile = new File(getFilePath(deviceID, MEDIUM));
		if (!imageFile.exists()) {
			return null;
		}
		String encodedImage = null;
		try{
			BufferedImage tempImage = ImageIO.read(imageFile);
			imageLock.readLock().unlock();
			encodedImage = encodeImage(tempImage);
		}
		catch(Exception e){
			imageLock.readLock().unlock();
			e.printStackTrace();
		}
		finally{
			return encodedImage;
		}
	}

	//load and return large image file based on account id
	public String getLargeEncodedPicture(int deviceID){
		imageLock.readLock().lock();
		File imageFile = new File(getFilePath(deviceID, LARGE));
		if (!imageFile.exists()) {
			return null;
		}
		String encodedImage = null;
		try{
			BufferedImage tempImage = ImageIO.read(imageFile);
			imageLock.readLock().unlock();
			encodedImage = encodeImage(tempImage);
		}
		catch(Exception e){
			imageLock.readLock().unlock();
			e.printStackTrace();
		}
		finally{
			return encodedImage;
		}
	}
}