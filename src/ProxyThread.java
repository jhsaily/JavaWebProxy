import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

class ProxyThread extends Thread {
	int portNumber = 80; //Set up server port number here. If none will be specified, 80 assumed.
	int thisPort = 0; //The port in which the proxy server is running on.
	DataOutputStream toClient = null; //Used to send data to client
	BufferedReader fromClient = null; //Used to read data from client
	BufferedWriter toServer = null; //Used to send data to server
	InputStream fromServer = null; //Used to read data from server
	Socket serverSocket = null; //Socket that connects to webserver
	Socket clientSocket = null; //Socket that connects to client
	int BUFFER_SIZE = 8192; //Size of download buffer, in bytes
	
	/* This initializes the thread with the pre-made/specified socket and port number */
	ProxyThread(Socket socket, int port) {
		clientSocket = socket;
		thisPort = port;
	}
	
	/* Actually runs the thread */
	public void run() {
		InetAddress address = null; //Will contain all the info about the webserver IP
		String clientString = "", clientLine = "", clientGET = "", clientURL = "";
		boolean host = false; //This is just used to see if the Host: line had to be used to extract the server address
		
		/* Get request from client */
		System.out.println("Getting user request.");
		try{
			/* The following sets up the necessary client data streams */
			toClient = new DataOutputStream(clientSocket.getOutputStream());
			fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			
			int i = 0;
			String[] lineArray = null, hyphenArray = null; //Used to parse the request header lines
			String temp = "";
			
			/* This method reads the request from the client line-by-line, extracts
			 * the address which is to be accessed, and saves the request as a whole
			 * into a String.
			 */
			while ((clientLine = fromClient.readLine()) != null) {
				System.err.println(clientLine);
				temp = "";
				int index = clientLine.indexOf(" ");
				lineArray = clientLine.split(" "); //Splits up the given request line into a string array
				
				/* If the http method ends in a colon, then this statement makes sure
				 * that the method is formatted correctly.
				 * AKA, makes it into camel-case with the casing happening
				 * in the beginning and after each hyphen.
				 */
				if(index != -1 && lineArray[0].endsWith(":") == true) {
					hyphenArray = lineArray[0].split("-");
					for(int j = 0; j < hyphenArray.length; j++){
						hyphenArray[j] = hyphenArray[j].substring(0, 1).toUpperCase() + hyphenArray[j].substring(1).toLowerCase();
						
						if(j+1 == hyphenArray.length) {
							temp += hyphenArray[j];
						}
						else {
							temp += hyphenArray[j] + "-";
						}
					}
					for (int j = 1; j < lineArray.length; j++) {
						temp += " " + lineArray[j];
					}
					
					clientLine = temp;
				} else if (index != -1) {
					/* Otherwise it assumes the http method is a GET, HEAD, etc. methods,
					 * so this statement just goes through and capitalizes it.
					 */
					lineArray[0] = lineArray[0].toUpperCase();
					for (int j = 0; j < lineArray.length; j++) {
						if(j+1 == lineArray.length) {
							temp += lineArray[j];
						}
						else {
							temp += lineArray[j] + " ";
						}
					}
					clientLine = temp;
				}
				if(clientLine.length() == 0) { //breaks out of the loop is the length of the line in 0
					break;
				}
				
				/* The following statements go in and extract the address from
				 * either the GET/HEAD/etc line or from the Host: line.
				 */
				if(i == 0 && clientLine.contains("http://")) {
					clientGET = clientLine;
					i++;
				} else if (clientGET.length() == 0 && clientLine.contains("Host:")){
					clientGET = clientLine;
					host = true;
				}
				clientString += clientLine + "\r\n";
			}
			toClient.flush();
			String[] getIndex = clientGET.split(" ");
			
			/* The following makes sure that a URL exists, if not then quit the thread */
			if (getIndex.length >= 2) {
				clientURL = getIndex[1];
			} else {
				System.err.println("No URL detected.");
				throw new Exception();
			}
			
			/* The following checks to see if a port number is in the url, if so
			 * extract it and set it to portNumber.
			 */
			if (clientURL.substring(5).contains(":") == true) {
				String tempURL = clientURL.substring(clientURL.indexOf(":") + 1);
				tempURL = tempURL.substring(tempURL.indexOf(":"));
				tempURL = tempURL.substring(1, tempURL.indexOf("/"));
				portNumber = Integer.parseInt(tempURL);
			}
			if (host) {
				clientURL = "http://" + clientURL + "/";
			}
			/*
			if(clientURL.contains("www.") == false) {
				clientURL = "http://www." + clientURL.substring(7);
			}
			*/
			address = InetAddress.getByName(new URL(clientURL).getHost());
			if(address.getHostAddress().equals("127.0.0.1") && portNumber == thisPort) {
				System.err.println("Infinite loop detected. Closing connections.");
				throw new Exception();
			}
		}
		catch(Exception e){
			closeConnections();
			return;
		}
		/* End get request from client */
		
		/* Send request to server */
		System.out.println("Sending request to server at: " + address.getHostAddress());
		try {
			serverSocket = new Socket(address, portNumber); //Sets up the socket to the server
			toServer = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream(), "US-ASCII"));
			fromServer = serverSocket.getInputStream();
			
			toServer.write(clientString + "\r\n\r\n"); //Writes the whole request from the client to the server
			toServer.flush();
		}
		catch(Exception e){
			System.err.println(address.getHostAddress() + ":" + portNumber + " refused the connection.");
			closeConnections();
			return;
		}
		/* End send request to server */
		
		/* Get data from server */
		System.out.println("Retrieving server data from: " + address.getHostAddress());
		try {
			byte buf[] = new byte[BUFFER_SIZE]; //Sets up the buffer to read data from the server
			int index = fromServer.read(buf, 0, BUFFER_SIZE);
			/* This loop just reads data from the server in increments of BUFFER_SIZE */
			while ( index != -1 ) {
				/*
				String s = "";
				for(int j = 0; j < buf.length; j++){
					s += (char)buf[j];
				}
				System.err.println(s);
				*/
				toClient.write(buf, 0, index);
				index = fromServer.read(buf, 0, BUFFER_SIZE);
				toClient.flush();
			}
		}
		catch(Exception e){
			System.err.println("Bad things happened with the server at: " + address.getHostAddress());
			closeConnections();
			return;
		}
		/* End get data from server */
		
		/* Close connections */
		System.out.println("Closing server connections to: " + address.getHostAddress());
		closeConnections();
		/* End close connections */
	}
	
	/* This goes through and closes any possible connections that have been established */
	public void closeConnections() {
		try {
            if(toClient != null){
           	 toClient.close();
            }
		} catch (Exception e) {}
		try {
            if(toServer != null){
           	 toServer.close();
            }
		} catch (Exception e) {}
		try {
            if(fromServer != null){
           	 fromServer.close();
            }
		} catch (Exception e) {}
		try {
            if(fromClient != null){
           	 fromClient.close();
            }
		} catch (Exception e) {}
		try {
            if(serverSocket != null){
           	 serverSocket.close();
            }
		} catch (Exception e) {}
	}
}