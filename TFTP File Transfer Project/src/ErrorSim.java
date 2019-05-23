/**
 * ErrorSim is the intermediate host between the TFTP server and client.
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;
import org.apache.commons.cli.*;

class Errors {
	private LinkedList<ErrorInstruction> errors = new LinkedList<ErrorInstruction>();
	
	public boolean add(ErrorInstruction error) {
		
		//Don't allow adding duplicate commands
		for(ErrorInstruction ei : errors) {
			if(ei.equals(error)) {
				return false;
			}
		}
		
		errors.add(error);
		return true;
	}
	
	public boolean remove(ErrorInstruction error) {
		return errors.remove(error);
	}
	
	public ErrorInstruction isPacketAffected(DatagramPacket packet) {
		return null;
	}
	
	public String toString() {
		
		if(errors.size() == 0) {
			return "No errors pending creation.";
		}
		String desc = "";
		
		desc += "The following errors will created:\n";
		
		int i = 1;
		for(ErrorInstruction ei : errors) {
			desc += i + ". " + ei.toString() + "\n";
			i++;
		}
		return desc;
	}
}

class ErrorInstruction {
	enum packetTypes{
		RRQ, WRQ, DATA, ACK, ERROR
	}
	
	enum errorTypes{
		DROP, DUPLICATE, DELAY
	}
	
	packetTypes packetType;
	errorTypes errorType;
	int packetNumber;
	int delay;
	int repeat;
	int repeatCount;
	
	ErrorInstruction(packetTypes packetType, errorTypes errorType, int packetNumber, int delay, int repeat)
	{
		if(packetNumber < 0) {
			throw new IllegalArgumentException("packet number can't be less than 0");
		}
		if(delay < 0) {
			throw new IllegalArgumentException("delay can't be less than 0");
		}
		if(repeat < -1 ) {
			throw new IllegalArgumentException("repeat can't be less than -1");
		}
		
		this.packetType = packetType;
		this.errorType = errorType;
		this.packetNumber = packetNumber;
		this.delay = delay;
		this.repeat = repeat;
	}
	
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		
		if(!(o instanceof ErrorInstruction)){
			return false;
		}
		
		ErrorInstruction error = (ErrorInstruction)o;
		
		if(this.packetType != error.packetType ||
		   this.errorType != error.errorType) {
			return false;
		}
		return this.packetNumber == error.packetNumber &&
				this.delay == error.delay &&
				this.repeat == error.repeat &&
				this.repeatCount == error.repeatCount;
	}
	
	public String toString() {
		String desc = "";
		
		switch(errorType) {
		case DROP:
			desc += "Drop ";
			break;
		case DUPLICATE:
			desc += "Duplicate ";
			break;
		case DELAY:
			desc += "Delay ";
			break;
		}
		
		switch(packetType) {
		case RRQ:
			desc += "RRQ packet ";
			break;
		case WRQ:
			desc += "WRQ packet ";
			break;
		case DATA:
			desc += "DATA packet ";
			break;
		case ACK:
			desc += "ACK packet ";
			break;
		case ERROR:
			desc += "ERROR packet ";
			break;
		}
		
		desc += packetNumber;
		
		if(errorType == errorTypes.DELAY) {
			desc += " by " + delay + "ms ";
		}
		else if(errorType == errorTypes.DUPLICATE) {
			desc += " with" + delay + "ms between packets ";
		}
		
		if(repeat < 0) {
			desc += ". Repeat indefinitley.";
		}
		else {
			desc += ". Repeat " + repeat + " times, " + (repeat - repeatCount) + " remaining.";
		}
		return desc;
	}
	
	public static errorTypes getErrorType(String typeString) {
		switch(typeString.toLowerCase()) {
		case "drop":
			return errorTypes.DROP;
		case "duplicate":
			return errorTypes.DUPLICATE;
		case "delay":
			return errorTypes.DELAY;
		default:
			return null;
		}
	}
	
	public static packetTypes getPacketType(String typeString) {
		switch(typeString.toLowerCase()) {
		case "rrq":
			return packetTypes.RRQ;
		case "wrq":
			return packetTypes.WRQ;
		case "data":
			return packetTypes.DATA;
		case "ack":
			return packetTypes.ACK;
		case "error":
			return packetTypes.ERROR;
		default:
			return null;
		}
	}
}

class ErrorSimClientListener implements Runnable {
	private ErrorSim errorSim;
	private DatagramSocket knownSocket;
	private DatagramSocket TIDSocket;
	private InetAddress clientAddress;
    private int clientPort;
    boolean verbose;
	
	public ErrorSimClientListener(int port, boolean verbose, ErrorSim errorSim) {
		this.verbose = verbose;
		this.errorSim = errorSim;
		
		try { //Set up the socket that will be used to receive packets from client on known port
			knownSocket = new DatagramSocket(port);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		try { //Set up the socket that will be used to communicate with TID port
			TIDSocket = new DatagramSocket();
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
	
	public synchronized int getClientPort() {
		return clientPort;
	}
	
	private synchronized void setClientPort(int port){
		clientPort = port;
	}
	
	private synchronized void setClientAddress(InetAddress address){
		clientAddress = address;
	}
	
	private DatagramPacket receiveFromClient(DatagramSocket socket) {

		byte data[] = new byte[TFTPPacket.MAX_SIZE];
	    DatagramPacket packet = new DatagramPacket(data, data.length);
	    
    	try { //Wait for a packet to come in from the client.
    		socket.receive(packet);
    	} catch(IOException e) {
    		if(e.getMessage().toLowerCase().equals("socket closed")){
    			return null;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}
    	
    	if(verbose) {
    		System.out.println("Received packet from client.");
    	    System.out.println("From address: " + packet.getAddress());
    	    System.out.println("From port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
    	    System.out.print("\n");
    	}
    	
    	return packet;
	}
	
	public synchronized void sendToClient(byte data[], int length) {
		
		DatagramPacket packet = new DatagramPacket(data, length, clientAddress, clientPort);
		
		if(verbose) {
    		System.out.println("Sending packet to client.");
    	    System.out.println("To address: " + packet.getAddress());
    	    System.out.println("To port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
    	    System.out.print("\n");
    	}
		
		try { //Send the packet to the client
    		TIDSocket.send(packet);
    	} catch (IOException e) {
    		if(e.getMessage().equals("socket closed")){
    			return;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}
	}
	
	public void run() {
		DatagramSocket clientSocket = knownSocket;
	    DatagramPacket receivePacket;
	    
		while(true) {	
			receivePacket = receiveFromClient(clientSocket);
    	
			if(receivePacket == null) {
				return;
			}
			else{
				//Keep the client address and port number for the response later
				setClientAddress(receivePacket.getAddress());
				setClientPort(receivePacket.getPort());
			}
    
			if(isRequestRW(receivePacket)){
				//This is the start of communication, use the servers known port
				//sendPacket = new DatagramPacket(data, receivePacket.getLength(), serverAddress, serverPort);
				clientSocket = TIDSocket; //listening to client on different port now that transaction has started
			}
			else{
				//Not the start of communication, use the port number for the server thread that handles this transaction
				//sendPacket = new DatagramPacket(data, receivePacket.getLength(), serverAddress, serverTID);
    		
				if(receivePacket.getLength() < 516) {
					//This packet is the end of a transaction, go back to listening to client on known port
					clientSocket = knownSocket;
				}
			}
    	
			//Send packet to the server
			this.errorSim.serverListener.sendToServer(receivePacket.getData(), receivePacket.getLength());
		}
	}
	
	/**
	 * Closes the sockets used by the listener to clean up resources
	 * and also cause the listener thread to exit
	 */
	public void close()
	{
		knownSocket.close();
	    TIDSocket.close();
	}
}

class ErrorSimServerListener implements Runnable {
	private ErrorSim errorSim;
	private DatagramSocket socket;
	private InetAddress serverAddress;
    private int serverPort;
    private int serverTID;
    boolean verbose;
	
	public ErrorSimServerListener(int port, InetAddress address, boolean verbose, ErrorSim errorSim) {
		serverPort = port;
		serverAddress = address;
		this.verbose = verbose;
		this.errorSim = errorSim;
		
		try { //Set up the socket that will be used to communicate with the server
			socket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	}
	
	public synchronized int getServerPort () {
		return this.serverPort;
	}
	
	private synchronized void setServerPort(int port){
		serverPort = port;
	}
	
	private synchronized void setServerTID(int port){
		serverTID = port;
	}
	
	public synchronized InetAddress getServerAddress () {
		return this.serverAddress;
	}
	
	private synchronized void setServerAddress(InetAddress address){
		serverAddress = address;
	}
	
	private DatagramPacket receiveFromServer() {

		byte data[] = new byte[TFTPPacket.MAX_SIZE];
	    DatagramPacket packet = new DatagramPacket(data, data.length);
	    
    	try { //Wait for a packet to come in from the client.
    		socket.receive(packet);
    	} catch(IOException e) {
    		if(e.getMessage().toLowerCase().equals("socket closed")){
    			return null;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}
    	
    	if(verbose) {
    		System.out.println("Received packet from server.");
    	    System.out.println("From address: " + packet.getAddress());
    	    System.out.println("From port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
    	    System.out.print("\n");
    	}
    	
    	return packet;
	}
	
	public synchronized void sendToServer(byte data[], int length) {
		
		DatagramPacket packet;
		TFTPPacket parsedPacket = TFTPPacket.parse(Arrays.copyOf(data, length));
		
		if(parsedPacket instanceof TFTPPacket.WRQ || parsedPacket instanceof TFTPPacket.RRQ) {
			packet = new DatagramPacket(data, length, serverAddress, serverPort);
		}
		else {
			packet = new DatagramPacket(data, length, serverAddress, serverTID);
		}
		
		if(verbose) {
    		System.out.println("Sending packet to server.");
    	    System.out.println("To address: " + packet.getAddress());
    	    System.out.println("To port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
    	    System.out.print("\n");
    	}
		
		try { //Send the packet to the client
    		socket.send(packet);
    	} catch (IOException e) {
    		if(e.getMessage().equals("socket closed")){
    			return;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}
	}
	
	public void run() {
	    DatagramPacket receivePacket;
		
	    while(true){
	    	receivePacket = receiveFromServer();
    	
	    	if(receivePacket == null) {
	    		return;
	    	}
	    	else {
	    		setServerTID(receivePacket.getPort());
	    	}
    	
	    	//Send packet to the client
	    	this.errorSim.clientListener.sendToClient(receivePacket.getData(), receivePacket.getLength());
	    }
	}
	
	/**
	 * Closes the sockets used by the listener to clean up resources
	 * and also cause the listener thread to exit
	 */
	public void close()
	{
		socket.close();
	}
}

/**
 * ErrorSim class handles the setup of the error simulator and acts as the UI thread.
 */
public class ErrorSim {
	public ErrorSimClientListener clientListener;
	public ErrorSimServerListener serverListener;
	private Errors errors;
	
	private Thread clientListenerThread;
	private Thread serverListenerThread;
	
	public ErrorSim (int clientPort, int serverPort, InetAddress serverAddress, boolean verbose) {
		//Create the errors instance
		this.errors = new Errors();
		
		//Create the listener thread
		this.clientListener = new ErrorSimClientListener(clientPort, verbose, this);
		this.serverListener = new ErrorSimServerListener(serverPort, serverAddress, verbose, this);
		this.clientListenerThread = new Thread(clientListener);
		this.serverListenerThread = new Thread(serverListener);
	}
	
	public void start () {
		this.clientListenerThread.start();
		this.serverListenerThread.start();
	}
	
	private void shutdown (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.close();
			try {
				clientListenerThread.join();
			} catch (InterruptedException e) {
				c.printerr("Error closing client listener thread.");
				System.exit(1);
			}
			serverListener.close();
			try {
				serverListenerThread.join();
			} catch (InterruptedException e) {
				c.printerr("Error closing server listener thread.");
				System.exit(1);
			}
			try {
				c.close();
			} catch (IOException e) {
				c.printerr("Error closing console thread.");
				System.exit(1);
			}
			System.exit(0);
		}
	}
	
	private void setVerboseCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.verbose = true;
			serverListener.verbose = true;
		}
	}
	
	private void setQuietCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.verbose = false;
			serverListener.verbose = false;
		}
	}
	
	private void setClientPortCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Client port: " + this.clientListener.getClientPort());
		} else if(args.length == 2) {
			int port = Integer.parseInt(args[1]);
			
			if(port > 0 && port < 65536) {
				boolean verbose = this.clientListener.verbose;
				
				this.clientListener.close();
				try {
					this.clientListenerThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//Restart the listener
				this.clientListener = new ErrorSimClientListener(port, verbose, this);
				this.clientListenerThread = new Thread(clientListener);
				this.clientListenerThread.start();
			}
			else {
				c.println("Invalid argument");
			}
		}
	}
	
	private void setServerPortCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Server port: " + this.serverListener.getServerPort());
		} else if(args.length == 2) {
			int port = Integer.parseInt(args[1]);
			
			if(port > 0 && port < 65536) {
				InetAddress serverAddress = this.serverListener.getServerAddress();
				boolean verbose = this.clientListener.verbose;
				
				serverListener.close();
				try {
					serverListenerThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//Restart the listener
				serverListener = new ErrorSimServerListener(port, serverAddress, verbose, this);
				serverListenerThread = new Thread(serverListener);
				serverListenerThread.start();
			}
			else {
				c.println("Invalid argument");
			}
		}
	}
	
	private void setServerIPCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Server ip " + this.serverListener.getServerAddress());
		} else if(args.length == 2) {
			try {
				int serverPort = this.serverListener.getServerPort();
				boolean verbose = this.serverListener.verbose;
				
				InetAddress serverAddress = InetAddress.getByName(args[1]);
				
				serverListener.close();
				serverListenerThread.join();
				
				//Restart the listener
				serverListener = new ErrorSimServerListener(serverPort, serverAddress, verbose, this);
				serverListenerThread = new Thread(serverListener);
				serverListenerThread.start();
			} catch (UnknownHostException e) {
				c.println("Invalid argument");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void dropCmd (Console c, String[] args) {
		if(args.length > 4) {
			c.println("Error: Too many parameters.");
		} else if(args.length < 4){
			c.println("Error: Not enough parameters.");
		} else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]), 
						ErrorInstruction.errorTypes.DROP, 	//Error type
						Integer.parseInt(args[2]),			//Affected packet type
						0,									//Time delay - not used here
						Integer.parseInt(args[3])));		//Repeat count
			}
			catch(IllegalArgumentException e) {
				c.println("Error: Invalid argument");
			}
		}
	}
	
	private void delayCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]), 
						ErrorInstruction.errorTypes.DELAY, 	//Error type
						Integer.parseInt(args[2]),			//Affected packet type
						Integer.parseInt(args[3]),			//Time delay
						Integer.parseInt(args[4])));		//Repeat count
			}
			catch(IllegalArgumentException e) {
				c.println("Error: Invalid argument");
			}
		}
	}
	
	private void duplicateCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]), 
						ErrorInstruction.errorTypes.DUPLICATE, 	//Error type
						Integer.parseInt(args[2]),			//Affected packet type
						Integer.parseInt(args[3]),			//Time delay
						Integer.parseInt(args[4])));		//Repeat count
			}
			catch(IllegalArgumentException e) {
				c.println("Error: Invalid argument");
			}
		}
	}
	
	private void errorsCmd (Console c, String[] args) {
		c.println(errors.toString());
	}
	
	private void helpCmd (Console c, String[] args) {
		c.println("The following is a list of commands and thier usage:");
		c.println("shutdown - Closes the error simulator program.");
		c.println("verbose - Makes the error simulator output more detailed information.");
		c.println("quiet - Makes the error simulator output only basic information.");
		c.println("clientport [x] - Outputs the port currently being used to listen to requests "
				+ "from the client if x is not provided. If parameter x is provided, then the port "
				+ "is changed to x.");
		c.println("serverport [x] - Outputs the port currently being used to forward requests "
				+ "to the server if x is not provided. If parameter x is provided, then the port "
				+ "is changed to x.");
		c.println("serverip [x] - Outputs the IP address currently being used to communicate with "
				+ "the server if x is not provided. If parameter x is provided, then the IP address "
				+ "is changed to x.");
		c.println("drop [A B C] - Drops a packet. A = packet type, B = packet number, C = repeat count.");
		c.println("	Valid packet types are RRQ, WRQ, DATA, ACK, ERROR. Reapeat count < 0 is infinite.");
		c.println("delay [A B C D] - Delays a packet. A = packet type, B = packet number, C = delay time (ms), D = repeat count.");
		c.println("	Valid packet types are RRQ, WRQ, DATA, ACK, ERROR. Reapeat count < 0 is infinite.");
		c.println("duplicate [A B C D] - Duplicates a packet. A = packet type, B = packet number, C = time between packets (ms), D = repeat count.");
		c.println("	Valid packet types are RRQ, WRQ, DATA, ACK, ERROR. Reapeat count < 0 is infinite.");
		c.println("help - Shows help information.");
	}
	

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
			System.out.println("Server address: " + serverAddress + " port " + serverPort + "\n");
		}
		
		// Create and start ErrorSim instance
		ErrorSim errorSim = new ErrorSim (clientPort, serverPort, serverAddress, verbose);
		errorSim.start();
		
		// Create and start console UI thread
		Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", errorSim::shutdown),
				Map.entry("verbose", errorSim::setVerboseCmd),
				Map.entry("quiet", errorSim::setQuietCmd),
				Map.entry("clientport", errorSim::setClientPortCmd),
				Map.entry("serverport", errorSim::setServerPortCmd),
				Map.entry("serverip", errorSim::setServerIPCmd),
				Map.entry("drop", errorSim::dropCmd),
				Map.entry("delay", errorSim::delayCmd),
				Map.entry("duplicate", errorSim::duplicateCmd),
				Map.entry("errors", errorSim::errorsCmd),
				Map.entry("help", errorSim::helpCmd)
				);
		
		Console console = new Console(commands);
		
		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}