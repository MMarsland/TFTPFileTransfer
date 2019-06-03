/**
 * The Server for the TFTP client-server project
 */

import java.io.FileNotFoundException;
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
 * Server class handles the setup of the Server and acts as the UI thread.
 */
public class Server {
	
	private ServerListener listener;
	private Thread listenerThread;
	private static Logger logger = new Logger();
	
	public Server(int serverPort, LogLevel verboseLevel, String logFilePath) {
		
		logger.setVerboseLevel(verboseLevel);
		logger.setLogFile(logFilePath);
		
		this.listener = new ServerListener(serverPort, logger);
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
			c.println("Shutting down Server...");
			this.listener.close();
			try {
				c.close();
			} catch (IOException e) {
				c.printerr("Error closing console thread.");
				System.exit(1);
			}
			c.println("Server shutdown.");
		}
	}

	private void setVerboseCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			c.println("Running in verbose mode.");
			logger.setVerboseLevel(LogLevel.INFO);
		}
	}
	
	private void setQuietCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			c.println("Running in quiet mode.");
			logger.setVerboseLevel(LogLevel.QUIET);
		}
	}
	
	private void setLogfileCmd (Console c, String[] args) {
		if (args.length < 2) {
			// Not enough arguments
			c.println("Too few arguments.");
			return;
		} else if (args.length > 3) {
			// Too many arguments
			c.println("Too many arguments.");
			return;
		}
		logger.setLogFile(args[1]);
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
		
		logger.log(LogLevel.QUIET, "Starting Server..."); 
		
		//Initialize settings to default values
		LogLevel verboseLevel = LogLevel.FATAL;
		int serverPort = 69;
		String logFilePath = "";
		
		//Setup command line parser
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );
		
		Option serverPortOption = Option.builder("p").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();
		
		Option logFilePathOption = Option.builder("l").argName("log file path")
                .hasArg()
                .desc("The log file the server writes to")
                .type(String.class)
                .build();
		
		Options options = new Options();

		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		options.addOption(logFilePathOption);
		
		CommandLineParser parser = new DefaultParser();
	    try {
	        // parse the command line arguments
	        CommandLine line = parser.parse( options, args );
	        
	        if( line.hasOption("verbose")) {
		        verboseLevel = LogLevel.INFO;
		    }
	        
	        if( line.hasOption("p")) {
		        serverPort = Integer.parseInt(line.getOptionValue("p"));
		    }
	        
	        if( line.hasOption("l")) {
	        	logFilePath = line.getOptionValue("l");
	        }
	    }
	    catch( ParseException exp ) {
	        logger.log(LogLevel.FATAL, "Command line argument parsing failed.  Reason: " + exp.getMessage() );
	        System.exit(1);
	    }
		
		// Create server instance and start it
	    Server server = new Server(serverPort, verboseLevel, logFilePath);
		server.start();
		
		// Create and start console UI thread
		Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", server::shutdown),
				Map.entry("verbose", server::setVerboseCmd),
				Map.entry("quiet", server::setQuietCmd),
				Map.entry("logfile", server::setLogfileCmd),
				Map.entry("serverport", server::setServerPortCmd),
				Map.entry("help", server::helpCmd)
				);

		Console console = new Console(commands);

		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}


/**
 * ErrorSimListener class handles incoming communications from the client 
 * and creates the appropriate handler threads to handle the requests.
 */
class ServerListener implements Runnable {
	private DatagramSocket  receiveSocket;
	private int listenerPort;
	private Logger logger;
	
	
	/**
	 * Constructor for the SeverListener class.
	 * @param listenerPort The port that will listen to requests from the client.
	 * @param verbose true enables verbose mode to output debug info, false disables verbose
	 * mode so less information is output.
	 */
	public ServerListener(int listenerPort, Logger logger) {
		this.listenerPort = listenerPort;
		this.logger = logger;
		
		// Set up the socket that will be used to receive packets from clients (or error simulators)
		try { 
			receiveSocket = new DatagramSocket(listenerPort);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
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
	        logger.log(LogLevel.INFO, "Listening for packets on port "+this.listenerPort+"...");
	    	
	    	// Wait for a packet to come in from the client.
	    	try { 	    		
	    		receiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		if(e.getMessage().equals("socket closed")) {
	    			System.exit(1);
	    		}
	    		e.printStackTrace();
    			System.exit(1);
	    	}
	    
	        logger.log(LogLevel.INFO, "New Request Received:");
	    
	    	
	    	// Parse the packet to determine the type of handler required
	    	TFTPPacket request = null;
	    	try {
				request = TFTPPacket.parse(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				// Unknown Packet Type... (Incorrect OP Code)
	    		logger.log(LogLevel.ERROR, "Error: Unknown Packet Type. Reason: Not a TFTP OPCode Solution: Send Error Packet in return and continue.");
	    		try { 
	    			DatagramSocket sendSocket = new DatagramSocket();
	    			TFTPPacket.ERROR errorPacket = new TFTPPacket.ERROR(TFTPPacket.TFTPError.ILLEGAL_OPERATION, "The first request received by server must be a Read or Write Request. (OPCode: 01 or 02)");
	    			DatagramPacket sendPacket = new DatagramPacket(errorPacket.toBytes(), errorPacket.size(), receivePacket.getAddress(), receivePacket.getPort());
	    			sendSocket.send(sendPacket);
	    			sendSocket.close();
	    		} catch (SocketException se) { // Can't create the socket.
	    			se.printStackTrace();
	    			System.exit(1);
	    	    } catch (IOException ioe) { // Can't send the packet.
	    	    	ioe.printStackTrace();
	    			System.exit(1);
	    	    }
	    		// Continue with request = null
			}
	    	// Create a handler thread
			if (request instanceof TFTPPacket.RRQ) {
				logger.log(LogLevel.QUIET, "Received a read request.");
				logger.log(LogLevel.INFO, "Creating a read handler for this request.");
						
				ReadHandler handler = new ReadHandler(receivePacket, (TFTPPacket.RRQ) request, logger);
				Thread handlerThread = new Thread(handler);
				handlerThread.start();
				
	    	} else if (request instanceof TFTPPacket.WRQ) {
				logger.log(LogLevel.QUIET, "Received a write request.");
				logger.log(LogLevel.INFO, "Creating a write handler for this request.");
	    		
	    		WriteHandler handler = new WriteHandler(receivePacket, (TFTPPacket.WRQ) request, logger);
				Thread handlerThread = new Thread(handler);
				handlerThread.start();
					
	    	} else if (request instanceof TFTPPacket.DATA) {
	    		logger.log(LogLevel.FATAL, "Error: Unexpected DATA packet as first request. Reason: Not a read or write request. Solution: Die");
	    		(new IllegalArgumentException()).printStackTrace();
	    		System.exit(1);
	    	} else if (request instanceof TFTPPacket.ACK) {
	    		logger.log(LogLevel.FATAL, "Error: Unexpected ACK packet as first request. Reason: Not a read or write request. Solution: Die");
	    		(new IllegalArgumentException()).printStackTrace();
	    		System.exit(1);
	    	} else if (request instanceof TFTPPacket.ERROR) {
	    		logger.log(LogLevel.FATAL, "Error: Unexpected ERROR packet as first request. Reason: Not a read or write request. Solution: Die");
	    		(new IllegalArgumentException()).printStackTrace();
	    		System.exit(1);
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






/** Abstract Class for Request Handlers. Allows inheritance for
 * ReadHandler and Write Handler for code simplification and cleaner
 * structure 
 */
 
abstract class RequestHandler implements Runnable {
	protected DatagramSocket sendReceiveSocket;
	protected DatagramPacket receivePacket;
	protected int clientTID;
	protected InetAddress clientAddress;
	protected String filename;
	protected Logger logger;
	
	public abstract void run();
}


/**
 * Read Handler Class for handling Read Requests
 */
class ReadHandler extends RequestHandler implements Runnable {

	protected TFTPPacket.RRQ request;
	
	/**
	 * Constructor for the ReadHandler class.
	 * @param receivePacket The DatagramPacket received for this request
	 * @param request The formed TFTPPacket for the read request
	 * @param verbose true enables verbose mode to output debug info, false disables verbose
	 * mode so less information is output.
	 */
	public ReadHandler(DatagramPacket receivePacket, TFTPPacket.RRQ request, Logger logger) {
		logger.log(LogLevel.INFO, "Setting up read handler.");
		this.logger = logger;
		this.receivePacket = receivePacket;
		this.request = request;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();
		
		//Set up the socket that will be used to send/receive packets to/from client
		try { 
			this.sendReceiveSocket = new DatagramSocket();
			// Set Timeout for the socket!
			sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_TIMEOUT);
			
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	
	}

	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		logger.log(LogLevel.INFO,"Handling read request.");
		
		// Set up and run the TFTP Transaction
		try {
			TFTPTransaction transaction =
					new TFTPTransaction.TFTPSendTransaction(sendReceiveSocket,
							clientAddress, clientTID, filename, false, logger);
			
			transaction.run();
			
			// Print success message if transfer is complete or error message if transfer has failed.
			switch (transaction.getState()) {
				case COMPLETE:
					logger.log(LogLevel.INFO, "File transfer complete.");
					break;
				case FILE_IO_ERROR:
					logger.log(LogLevel.FATAL, "File transfer failed. File IO error.");
					System.exit(1);
					break;
				case FILE_TOO_LARGE:
					logger.log(LogLevel.FATAL, "File transfer failed. File too large.");
					System.exit(1);
					break;
				case LAST_BLOCK_ACK_TIMEOUT:
					logger.log(LogLevel.FATAL, "File transfer may have failed. Timed out waiting for server to acknowledge last block.");
					System.exit(1);
					break;
				case RECEIVED_BAD_PACKET:
					logger.log(LogLevel.FATAL, "File transfer failed. Received invalid packet.");
					System.exit(1);
					break;
				case SOCKET_IO_ERROR:
					logger.log(LogLevel.FATAL, "File transfer failed. Socket IO error.");
					System.exit(1);
					break;
				case TIMEOUT:
					logger.log(LogLevel.FATAL, "File transfer failed. Timed out waiting for server.");
					System.exit(1);
					break;
				default:
					logger.log(LogLevel.FATAL, String.format(
							"File transfer failed. Unkown error occured: \"%s\"", 
							transaction.getState().toString()));
					System.exit(1);
					break;
			}
		} catch (FileNotFoundException e) {
			logger.log(LogLevel.FATAL, String.format("File not found: \"%s\".", filename));
			System.exit(1);
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
	public WriteHandler(DatagramPacket receivePacket, TFTPPacket.WRQ request, Logger logger) {
		logger.log(LogLevel.INFO, "Setting up Write Handler");
		this.receivePacket = receivePacket;
		this.request = request;
		this.logger = logger;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();
		this.mode = this.request.getMode();
		
		//Set up the socket that will be used to send/receive packets to/from client
		try { 
			this.sendReceiveSocket = new DatagramSocket();
			// Set Timeout for the socket!
			sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_TIMEOUT);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		
	}

	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		logger.log(LogLevel.INFO, "Handling Write Request");
		
		// Set up and run the TFTP Transaction
		try {
			TFTPTransaction transaction =
					new TFTPTransaction.TFTPReceiveTransaction(sendReceiveSocket,
							clientAddress, clientTID, filename, false, false, logger);
			
			transaction.run();
			
			// Print success message if transfer is complete or error message if transfer has failed.
			switch (transaction.getState()) {
				case COMPLETE:
					logger.log(LogLevel.INFO, "File transfer complete.");
					break;
				case FILE_IO_ERROR:
					logger.log(LogLevel.FATAL, "File transfer failed. File IO error.");
					System.exit(1);
					break;
				case FILE_TOO_LARGE:
					logger.log(LogLevel.FATAL, "File transfer failed. File too large.");
					System.exit(1);
					break;
				case LAST_BLOCK_ACK_TIMEOUT:
					logger.log(LogLevel.FATAL, "File transfer may have failed. Timed out waiting for server to acknowledge last block.");
					System.exit(1);
					break;
				case RECEIVED_BAD_PACKET:
					logger.log(LogLevel.FATAL, "File transfer failed. Received invalid packet.");
					System.exit(1);
					break;
				case SOCKET_IO_ERROR:
					logger.log(LogLevel.FATAL, "File transfer failed. Socket IO error.");
					System.exit(1);
					break;
				case TIMEOUT:
					logger.log(LogLevel.FATAL, "File transfer failed. Timed out waiting for server.");
					System.exit(1);
					break;
				default:
					logger.log(LogLevel.FATAL, String.format(
							"File transfer failed. Unkown error occured: \"%s\"", 
							transaction.getState().toString()));
					System.exit(1);
					break;
			}
		} catch (FileNotFoundException e) {
			logger.log(LogLevel.FATAL, String.format("File not found: \"%s\".", filename));
			System.exit(1);
		}
	}
}