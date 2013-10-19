import java.net.*;
import java.io.*;

public class WebProxy {

	public static void main(String[] args) {
		ServerSocket serversocket = null;
		int port = 0;
		
		if(args.length < 2 || args[0].equalsIgnoreCase("-p") == false){
			System.err.println("You need a port number in the form: -p [port#]");
			System.exit(1);
		} else {
			String tempPort = args[1].replaceAll("\\D+","");
			if(tempPort.length() == 0){
				System.err.println("You need a port number in the form: -p [port#]");
				System.exit(1);
			}
			port = Integer.parseInt(tempPort);
		}
		
		try {
			serversocket = new ServerSocket(port);
			System.out.println("Proxy server running on port " + port + ".");
		}
		catch(Exception e){
			System.err.println("Proxy server could not run on port " + port + ". Shutting down.");
			System.exit(1);
		}
		
		while (true){
			try {
				new ProxyThread(serversocket.accept(), port).start(); //After a socket connection is detected, send it to a new thread to handle the proxy code.
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
