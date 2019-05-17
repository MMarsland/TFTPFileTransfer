/**
 * ErrorSim is the intermediate host between the TFTP server and client.
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import org.apache.commons.cli.*;

/**
 * ErrorSimListener class handles communication between the client and server.
 * It forwards packets from the client to the server an vice versa.
 */
class ErrorSimListener implements Runnable {
	private DatagramSocket sendReceiveSocket, receiveSocket;
	private int serverPort;
	private InetAddress serverAddress;
	private boolean verbose;
	
	/**
	 * Constructor for the ErrorSimListener class.
	 * @param listenerPort The port that will listen to requests from the client.
	 * @param server The IP address of the server.
	 * @param serverPort The port number on the server to send requests to.
	 * @param verbose true enables verbose mode to output debug info, false disables verbose
	 * mode so less information is output.
	 */
	public ErrorSimListener(int listenerPort, InetAddress server, int serverPort, boolean verbose) {
		this.serverPort = serverPort;
		this.verbose = verbose;
		serverAddress = server;
		
		try { //Set up the socket that will be used to send/receive packets to/from server
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		try { //Set up the socket that will be used to receive packets from client
			receiveSocket = new DatagramSocket(listenerPort);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	}
	
	/**
	 * Checks if a DatagramPacket contains a read request or a write request
	 * @param packet The packet to check
	 * @return true if the packet is a read or write request, false otherwise
	 */
	private boolean isRequestRW(DatagramPacket packet) {
		byte[] packetData = new byte[packet.getLength()];
    	System.arraycopy(packet.getData(), 0, packetData, 0, packet.getLength());
    	TFTPPacket parsedPacket = TFTPPacket.parse(packetData);
    	
    	return (parsedPacket instanceof TFTPPacket.WRQ || parsedPacket instanceof TFTPPacket.RRQ);
	}
	
	/**
	 * Enable/disables verbose mode on the listener thread
	 * @param mode true enables verbose mode, false disables it
	 */
	public void setVerbose(boolean mode) {
		verbose = mode;
	}
	
	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		byte data[] = new byte[516];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);
	    DatagramPacket sendPacket;
	    InetAddress clientAddress;
	    int clientTID;
	    int serverTID = serverPort;
	    DatagramSocket clientSocket = receiveSocket;
	    
	    while(!Thread.interrupted()) {
	    	if(verbose) {
	    		System.out.println("Waiting for client...");
	    	}
	    
	    	try { //Wait for a packet to come in from the client.
	    		clientSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			System.exit(0);
	    		}
	    		e.printStackTrace();
    			System.exit(1);
	    	}
	    
	    	//Keep the client address and port number for the response later
	    	clientAddress = receivePacket.getAddress();
	    	clientTID = receivePacket.getPort();
	    
	    	if(verbose) {
	    		System.out.println("Forwarding data to server...");
	    	}
	    	
	    	if(isRequestRW(receivePacket)){
	    		//This is the start of communication, use the servers known port
	    		sendPacket = new DatagramPacket(data, receivePacket.getLength(), serverAddress, serverPort);
	    		clientSocket = sendReceiveSocket; //listening to client on different port now that transaction has started
	    	}
	    	else{
	    		//Not the start of communication, use the port number for the server thread that handles this transaction
	    		sendPacket = new DatagramPacket(data, receivePacket.getLength(), serverAddress, serverTID);
	    		
	    		if(receivePacket.getLength() < 516) {
	    			//This packet is the end of a transaction, go back to listening to client on known port
	    			clientSocket = receiveSocket;
	    		}
	    	}
	    	
	    	try { //Send the packet to the server
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			System.exit(0);
	    		}
	    		e.printStackTrace();
    			System.exit(1);
	    	}
	    
	    	if(verbose) {
	    		System.out.println("Waiting for server...");
	    	}
	    
	    	try { //Wait for a packet to come in from the Server
	    		sendReceiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			System.exit(0);
	    		}
	    		e.printStackTrace();
    			System.exit(1);
	    	}
	    	
	    	//This is the port number for the servers transaction handling thread
	    	serverTID = receivePacket.getPort();
	    
	    	sendPacket = new DatagramPacket(data, receivePacket.getLength(), clientAddress, clientTID);
	    
	    	if(verbose) {
	    		System.out.println("Forwarding data to client...");
	    	}
	    
	    	try { //Send the packet to the client
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			System.exit(0);
	    		}
	    		e.printStackTrace();
    			System.exit(1);
	    	}
	    }
	}
	
	/**
	 * Closes the sockets used by the listener to clean up resources
	 * and also cause the listener thread to exit
	 */
	public void close()
	{
		sendReceiveSocket.close();
	    receiveSocket.close();
	}
}

/**
 * ErrorSim class handles the setup of the error simulator and acts as the UI thread.
 */
public class ErrorSim {

	/**
	 * main function for the error simulator
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {
		
		//Initialize settings to default values
		Boolean verbose = false;
		int serverPort = 69;
		int clientPort = 23;
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		//Setup command line parser
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );
		
		Option serverPortOption = Option.builder("sp").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();
		
		Option serverAddressOption = Option.builder("sa").argName("server address")
                .hasArg()
                .desc("the IP address of the server")
                .type(String.class)
                .build();
		
		Option clientPortOption = Option.builder("cp").argName("client port")
                .hasArg()
                .desc("the port number to listen to client requests on")
                .type(Integer.TYPE)
                .build();
		
		Options options = new Options();

		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		options.addOption(serverAddressOption);
		options.addOption(clientPortOption);
		
		CommandLineParser parser = new DefaultParser();
	    try {
	        // parse the command line arguments
	        CommandLine line = parser.parse( options, args );
	        
	        if( line.hasOption("verbose")) {
		        verbose = true;
		    }
	        
	        if( line.hasOption("sp")) {
		        serverPort = Integer.parseInt(line.getOptionValue("sp"));
		    }
	        
	        if( line.hasOption("sa")) {
		        try {
					serverAddress = InetAddress.getByName((String)line.getParsedOptionValue("sa"));
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
		    }
	        
	        if( line.hasOption("cp")) {
	        	clientPort = Integer.parseInt(line.getOptionValue("cp"));
		    } 
	    }
	    catch( ParseException exp ) {
	        System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
	        System.exit(1);
	    }
		
		System.out.println("Error Simulator Running");
		
		if(verbose) {
			System.out.println("Listening to client on port " + clientPort);
			System.out.println("Server address: " + serverAddress + " port " + serverPort);
		}
		
		//Create the listener thread
		ErrorSimListener listener = new ErrorSimListener(clientPort, serverAddress, serverPort, verbose);
		Thread listenerThread = new Thread(listener);
		listenerThread.start();
		
		Scanner in = new Scanner(System.in);
		String command;
		String[] split;
		
		//This is the UI thread. It handles commands from the user
		while(true) {
			command = in.nextLine();
			split = command.split("\\s+");
			
			//Handle the shutdown command
			if(split[0].toLowerCase().equals("shutdown")) {
				if(split.length > 1) {
					System.out.println("Error: Too many parameters.");
				}
				else {
					listener.close();
					try {
						listenerThread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					in.close();
					System.exit(0);
				}
			}
			//Handle the verbose command
			else if(split[0].toLowerCase().equals("verbose")) {
				if(split.length > 1) {
					System.out.println("Error: Too many parameters.");
				}
				else {
					verbose = true;
					listener.setVerbose(true);
				}
			}
			//Handle the quiet command
			else if(split[0].toLowerCase().equals("quiet")) {
				if(split.length > 1) {
					System.out.println("Error: Too many parameters.");
				}
				else {
					verbose = false;
					listener.setVerbose(false);
				}
			}
			//Handle the clientport command
			else if(split[0].toLowerCase().equals("clientport")) {
				if(split.length > 2) {
					System.out.println("Error: Too many parameters.");
				}
				else if(split.length == 1) {
					System.out.println("Client port: " + clientPort);
				}
				else if(split.length == 2) {
					int port = Integer.parseInt(split[1]);
					if(port > 0 && port < 65536) {
						clientPort = port;
					}
					else {
						System.out.println("Invalid argument");
					}
				}
			}
			//Handle the serverport command
			else if(split[0].toLowerCase().equals("serverport")) {
				if(split.length > 2) {
					System.out.println("Error: Too many parameters.");
				}
				else if(split.length == 1) {
					System.out.println("Server port: " + serverPort);
				}
				else if(split.length == 2) {
					int port = Integer.parseInt(split[1]);
					if(port > 0 && port < 65536) {
						serverPort = port;
					}
					else {
						System.out.println("Invalid argument");
					}
				}
			}
			//Handle the serverip command
			else if(split[0].toLowerCase().equals("serverip")) {
				if(split.length > 2) {
					System.out.println("Error: Too many parameters.");
				}
				else if(split.length == 1) {
					System.out.println("Server ip " + serverAddress);
				}
				else if(split.length == 2) {
					try {
						serverAddress = InetAddress.getByName(split[1]);
					} catch (UnknownHostException e) {
						System.out.println("Invalid argument");
					}
				}
			}
			//Handle the help command
			else if(split[0].toLowerCase().equals("help")) {
				System.out.println("The following is a list of commands and thier usage:");
				System.out.println("shutdown - Closes the error simulator program.");
				System.out.println("verbose - Makes the error simulator output more detailed information.");
				System.out.println("quiet - Makes the error simulator output only basic information.");
				System.out.println("clientport [x] - Outputs the port currently being used to listen to requests "
						+ "from the client if x is not provided. If parameter x is provided, then the port "
						+ "is changed to x.");
				System.out.println("serverport [x] - Outputs the port currently being used to forward requests "
						+ "to the server if x is not provided. If parameter x is provided, then the port "
						+ "is changed to x.");
				System.out.println("serverip [x] - Outputs the IP address currently being used to communicate with "
						+ "the server if x is not provided. If parameter x is provided, then the IP address "
						+ "is changed to x.");
				System.out.println("help - Shows help information.");
			}
			//Handle commands that do not exist
			else {
				System.out.println("Invalid command.");
			}
		}
	}
}