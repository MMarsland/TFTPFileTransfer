/**
 * The Server for the TFTP client-server project
 */

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * ErrorSimListener class handles incoming communications from the client 
 * and creates the appropriate handler threads to handle the requests.
 */
class ServerListener implements Runnable {
	private DatagramSocket  receiveSocket;
	private int listenerPort;
	private boolean verbose;
	
	/**
	 * Constructor for the SeverListener class.
	 * @param listenerPort The port that will listen to requests from the client.
	 * @param verbose true enables verbose mode to output debug info, false disables verbose
	 * mode so less information is output.
	 */
	public ServerListener(int listenerPort, boolean verbose) {
		this.listenerPort = listenerPort;
		this.verbose = verbose;
		
		// Set up the socket that will be used to receive packets from clients (or error simulators)
		try { 
			receiveSocket = new DatagramSocket(listenerPort);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	}
	
	/**
	 * Enable/disables verbose mode on the listener thread
	 * @param mode true enables verbose mode, false disables it
	 */
	public void setVerbose(boolean mode) {
		verbose = mode;
	}
	
	/**
	 * Determine if listener thread is in verbose mode
	 * @return Whether the listener thread is in verbose mode
	 */
	public boolean getVerbose() {
		return verbose;
	}
	
	/**
	 * Get the port that the listener is listening on
	 * @return The port that the listener is listening on
	 */
	public int getPort() {
		return listenerPort;
	}
	
	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		byte data[] = new byte[TFTPPacket.MAX_SIZE];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);
	    
	    while(!Thread.interrupted()) {
	    	if(verbose) {
	    		System.out.println("Listening for packets on port "+this.listenerPort+"...");
	    	}
	    	
	    	// Wait for a packet to come in from the client.
	    	try { 	    		
	    		receiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		if(e.getMessage().equals("socket closed")) {
	    			System.exit(0);
	    		}
	    		e.printStackTrace();
    			System.exit(1);
	    	}
	    
	    	if(verbose) {
	    		System.out.println("New Request Received:");
	    	}
	    	
	    	// Parse the packet to determine the type of handler required
	    	TFTPPacket request = null;
	    	try {
				request = TFTPPacket.parse(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				System.exit(0);
			}
	    	// Create a handler thread
			if (request instanceof TFTPPacket.RRQ) {
				if (!verbose) {
					System.out.println("Received a read request.");
				} else {
					System.out.println("Creating a read handler for this request.");
				}
				ReadHandler handler = new ReadHandler(receivePacket, (TFTPPacket.RRQ) request, verbose);
				Thread handlerThread = new Thread(handler);
				handlerThread.start();
	    	} else if (request instanceof TFTPPacket.WRQ) {
	    		if (!verbose) {
					System.out.println("Received a write request.");
				} else {
					System.out.println("Creating a write handler for this request.");
				}
	    		WriteHandler handler = new WriteHandler(receivePacket, (TFTPPacket.WRQ) request, verbose);
				Thread handlerThread = new Thread(handler);
				handlerThread.start();
	    	} else {
	    		// Not the right first request type..
	    		System.err.println("Unexpected first request packet. Reason: Not Read or Write");
	    		throw new IllegalArgumentException();
	    	}
			
	    	// Return to listening for new requests
	    }
	}
	
	/**
	 * Closes the sockets used by the listener to clean up resources
	 * and also cause the listener thread to exit
	 */
	public void close()
	{
	    receiveSocket.close();
	}
}


/**
 * Abstract Class for Request Handlers. Allows inheritance for ReadHandler 
 * and Write Handler for code simplification and cleaner structure
 */
abstract class RequestHandler implements Runnable {
	protected DatagramSocket sendReceiveSocket;
	protected DatagramPacket receivePacket;
	protected boolean verbose;
	protected int clientTID;
	protected InetAddress clientAddress;
	protected String filename;
	
	
	public abstract void run();
	
}

/**
 * Read Handler Class for handling Read Requests
 */
class ReadHandler extends RequestHandler implements Runnable {

	protected TFTPPacket.RRQ request;
	protected int blockNum = 0;
	
	/**
	 * Constructor for the ReadHandler class.
	 * @param receivePacket The DatagramPacket received for this request
	 * @param request The formed TFTPPacket for the read request
	 * @param verbose true enables verbose mode to output debug info, false disables verbose
	 * mode so less information is output.
	 */
	public ReadHandler(DatagramPacket receivePacket, TFTPPacket.RRQ request, boolean verbose) {
		if(verbose) {
			System.out.println("Setting up read handler.");
		}
		this.receivePacket = receivePacket;
		this.request = request;
		this.verbose = verbose;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();
		
		//Set up the socket that will be used to send/receive packets to/from client
		try { 
			this.sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	
	}

	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		if(this.verbose) {
			System.out.println("Handling read request.");
		}
	    
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(this.filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(this.verbose) {
			System.out.println("Successfully opened: "+this.filename);
		}
		
		TFTPPacket.DATA dataPacket;
	    DatagramPacket sendPacket;
	    byte[] data = new byte[512];
	    int len = 69999; 
	    
		boolean moreToRead = true;
		while (moreToRead) {
			// Read data from file into data packet
		    this.blockNum++;
		    this.blockNum = this.blockNum & 0xFFFF;
		    try {
		    	if ((len=fis.read(data,0,512)) < 512) {
		    		moreToRead = false;
		    		if (len == -1) {
		    			// End of file reached exactly. Send 0 bytes of data.
		    			len = 0;
		    		}
					fis.close();
		    	}
		    	// Shrink wrap size based on the # of bytes read from the file
		    	data = Arrays.copyOf(data, len);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(0);
			}
		    
			// Assemble data packet
		    dataPacket = new TFTPPacket.DATA(this.blockNum, data);
		    sendPacket = new DatagramPacket(dataPacket.toBytes(), dataPacket.toBytes().length, clientAddress, clientTID);
		    
		    // Send data packet to client on Client TID
		    if(this.verbose) {
				System.out.println("Sending Packet:");
				System.out.println("Packet Type: RRQ");
				System.out.println("Filename: "+this.filename);
				// Mode not Applicable
				System.out.println("Block Number: "+this.blockNum);
				System.out.println("# of Bytes: "+len);
			}
		    
		    try {
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
			// Wait for ACK
		    if(this.verbose) {
				System.out.println("Waiting for ack packet...");
			}
		    
		    // New Receive total bytes
		    data = new byte[TFTPPacket.MAX_SIZE];
		    DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		    try {
	    		sendReceiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
		    		    
		    // Parse ACK for correctness
		    TFTPPacket.ACK ackPacket = null;
	    	try {
				ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				System.err.println("Wrong Packet Recieved. Reason: Not an ackPacket");
				e.printStackTrace();
				System.exit(0);
			}
	    	if (ackPacket.getBlockNum() == this.blockNum ) {
				// Correct acks
			} else {
				// Incorrect ack
				System.err.println("Wrong ACK response. Reason: Incorrect block number");
	    		throw new IllegalArgumentException();
			}
	    	
	    	if(this.verbose) {
				System.out.println("Received Packet:");
				System.out.println("Packet Type: ACK");
				// N/A System.out.println("Filename: "+this.filename);
				// N/A System.out.println("Mode: "+this.Mode);
				System.out.println("Block Number: "+this.blockNum);
				// N/A System.out.println("# of Bytes: "+len);
			}
	    	
	    	//Continue...
		}
		// All data is sent and last ACK received,
		// Close socket, quit
		if(this.verbose) {
			System.out.println("Data Transefer complete! Closing socket.");
		} else {
			System.out.println("Successfully completed a read request.");
		}
		sendReceiveSocket.close();
		if (this.verbose) {
			System.out.println("Closing Read Handler");
		}
	}
}

/**
 * Write Handler Class for handling Write Requests
 */
class WriteHandler extends RequestHandler implements Runnable {

	protected TFTPPacket.WRQ request;
	protected TFTPPacket.TFTPMode mode;
	
	/**
	 * Constructor for the WriteHandler class.
	 * @param receivePacket The DatagramPacket received for this request
	 * @param request The formed TFTPPacket for the write request
	 * @param verbose true enables verbose mode to output debug info, false disables verbose
	 * mode so less information is output.
	 */
	public WriteHandler(DatagramPacket receivePacket, TFTPPacket.WRQ request, boolean verbose) {
		if(verbose) {
			System.out.println("Setting up Write Handler");
		}
		this.receivePacket = receivePacket;
		this.request = request;
		this.verbose = verbose;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();
		this.mode = this.request.getMode();
		
		//Set up the socket that will be used to send/receive packets to/from client
		try { 
			this.sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		
	}

	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		if(this.verbose) {
			System.out.println("Handling Write Request");
		}
		// Send first Ack Package
	    TFTPPacket.ACK ackPacket = new TFTPPacket.ACK(0);
	    DatagramPacket sendPacket = new DatagramPacket(ackPacket.toBytes(), ackPacket.toBytes().length, clientAddress, clientTID);
	    // Send data packet to client on Client TID
	    try {
    		sendReceiveSocket.send(sendPacket);
    	} catch (IOException e) {
    		e.printStackTrace();
			System.exit(1);
    	}
	    
	    FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(this.filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(this.verbose) {
			System.out.println("File ready to be written! filename: "+this.filename);
		}
	    
		byte[] data = new byte[TFTPPacket.MAX_SIZE];
		DatagramPacket receivePacket;
		TFTPPacket.DATA dataPacket = null;
		int len = 699999;
		int blockNum = 699999;
	    // Receive data and send acks
		boolean moreToWrite = true;
		while (moreToWrite) {
			// Receive Data Packet
		    data = new byte[TFTPPacket.MAX_SIZE];
		    receivePacket = new DatagramPacket(data, data.length);
		    try {
	    		sendReceiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
		    
			// Check Packet for correctness
	    	try {
				dataPacket = new TFTPPacket.DATA(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				System.err.println("Incorrect Response. Reason: Expected Data.");
				e.printStackTrace();
				System.exit(0);
			}
	    	// Definitely data :)
			// Strip block number
		    blockNum = dataPacket.getBlockNum();
			// Check size? Less than 512 == done
		    len = dataPacket.getData().length;
		    if (len < 512) {
		    	moreToWrite = false;
		    }

		    if(this.verbose) {
				System.out.println("Received Packet:");
				System.out.println("Packet Type: DATA");
				System.out.println("Filename: "+this.filename);
				System.out.println("Mode: "+this.mode);
				System.out.println("Block Number: "+blockNum);
				System.out.println("# of Bytes: "+len);
			}
		    
			// Write into file
		    try {
				fos.write(dataPacket.getData(),0,dataPacket.getData().length);
			} catch (IOException e) {
				System.err.println("Failed to write data to file.");
				e.printStackTrace();
				System.exit(0);
			}
		    
			// Send Acknowledgement packet with block number
		    ackPacket = new TFTPPacket.ACK(blockNum);
		    sendPacket = new DatagramPacket(ackPacket.toBytes(), ackPacket.toBytes().length, clientAddress, clientTID);
		    
		    // Send ack packet to client on Client TID
		    if(this.verbose) {
				System.out.println("Sending Packet:");
				System.out.println("Packet Type: ACK");
				// N/A System.out.println("Filename: "+this.filename);
				// N/A System.out.println("Mode: "+this.Mode);
				System.out.println("Block Number: "+blockNum);
				// N/A System.out.println("# of Bytes: "+len);
			}
		    
		    try {
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
		}
		
		try {
			fos.flush();
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// All data received and writes performed and last ack sent
		if(this.verbose) {
			System.out.println("Write Request complete! Closing socket.");
		} else {
			System.out.println("Successfully completed a write request.");
		}
		// Close socket, quit
		sendReceiveSocket.close();
		if (this.verbose) {
			System.out.println("Closing Write Handler");
		}
	}
}

/**
 * Server class handles the setup of the Server and acts as the UI thread.
 */
public class Server {
	private ServerListener listener;
	private Thread listenerThread;
	
	public Server(int serverPort, boolean verbose) {
		this.listener = new ServerListener(serverPort, verbose);
		this.listenerThread = new Thread(listener);
	}
	
	public void start () {
		listenerThread.start();
	}
	
	private void shutdown (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			if (this.listener.getVerbose()) {
				c.println("Shutting down Server...");
			}
			this.listener.close();
			try {
				c.close();
			} catch (IOException e) {
				c.printerr("Error closing console thread.");
				System.exit(1);
			}
			c.println("Server shutdown.");
			System.exit(0);
		}
	}

	private void setVerboseCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			listener.setVerbose(true);
			c.println("Showing additional information.");
			
		}
	}
	
	private void setQuietCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			listener.setVerbose(false);
			c.println("Hiding extra information.");
		}
	}
	
	private void setServerPortCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length == 1) {
			c.println("Server port: " + this.listener.getPort());
		}
	}
	
	private void helpCmd (Console c, String[] args) {
		c.println("The following is a list of commands and thier usage:");
		c.println("shutdown - Closes the Server.");
		c.println("verbose - Makes the server output more detailed information.");
		c.println("quiet - Makes the server output only basic information.");
		c.println("serverport - Outputs the port currently being used to listen to requests");
		c.println("help - Shows help information.");
	}
	
	/**
	 * main function for the server
	 * @param args Command line arguments
	 */
	public static void main(String[] args) throws InterruptedException {
		
		System.out.println("Starting Server..."); 
		
		//Initialize settings to default values
		Boolean verbose = false;
		int serverPort = 69;
		
		//Setup command line parser
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );
		
		Option serverPortOption = Option.builder("p").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();
		
		Options options = new Options();

		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		
		CommandLineParser parser = new DefaultParser();
	    try {
	        // parse the command line arguments
	        CommandLine line = parser.parse( options, args );
	        
	        if( line.hasOption("verbose")) {
		        verbose = true;
		    }
	        
	        if( line.hasOption("p")) {
		        serverPort = Integer.parseInt(line.getOptionValue("p"));
		    }
	    }
	    catch( ParseException exp ) {
	        System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
	        System.exit(1);
	    }
		
		// Create server instance and start it
	    Server server = new Server(serverPort, verbose);
		
		if(verbose) {
			System.out.println("Listening to client on port " + serverPort);
		}


		// Create and start console UI thread
		Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", server::shutdown),
				Map.entry("verbose", server::setVerboseCmd),
				Map.entry("quiet", server::setQuietCmd),
				Map.entry("serverport", server::setServerPortCmd),
				Map.entry("help", server::helpCmd)
				);

		Console console = new Console(commands);

		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}
