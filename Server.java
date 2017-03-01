import java.util.*;
import java.io.*;
import java.io.BufferedReader;
import java.net.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class Server{
	public static void main(String[] args) throws IOException{
		int ids = 0;
		if (args.length != 1) {
			System.out.println("java ChatServer <listen port>");
			return;
		}

		final int port = Integer.parseInt(args[0]);

		ServerSocket listener = new ServerSocket(port);
		//SSLServerSocketFactory factory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
		//SSLServerSocket listener = (SSLServerSocket) factory.createServerSocket(port);

		DeviceConnection.createFakeDevices();

		try{
			while(true){
				new DeviceConnection(listener.accept(), ids++).start();
			}
		}
		catch (Exception e){
			System.out.println("Unexpected error...");
		}

		listener.close();

	}
}