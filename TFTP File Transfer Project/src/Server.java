/**
 * The Server for the TFTP client-server project
 */

import java.io.File;
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

		logger.setVerboseLevel(verboseLevel, true);
		logger.setLogFile(logFilePath, true);

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
			logger.endLog();
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
			logger.setVerboseLevel(LogLevel.INFO, false);
		}
	}

	private void setQuietCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			c.println("Running in quiet mode.");
			logger.setVerboseLevel(LogLevel.QUIET, false);
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
		logger.setLogFile(args[1], false);
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
		c.println("The following is a list of commands and their usage:");
		c.println("shutdown - Closes the Server.");
		c.println("verbose - Makes the server output more detailed information.");
		c.println("quiet - Makes the server output only basic information.");
		c.println("logfile <filename> - Makes the server write displayed information to a log file on shutdown.");
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
		LogLevel verboseLevel = LogLevel.QUIET;
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
			logger.log(LogLevel.FATAL, "Error: SocketException. Reason: Could not create listener socket. Solution: Shutting down Server.");
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
	    		if(!e.getMessage().equals("socket closed")) {
	    			// An IOException occurred listening for packages. (Probably won't be able to listen again, we'd better die.)
	    			logger.log(LogLevel.FATAL, "Error: SocketException. Reason: Listener Socket Failed to Recieve. Solution: Shutting down Server.");
		    		e.printStackTrace();
	    			System.exit(1);
	    		} else {
	    			// The socket was closed to shutdown the listener thread.
	    			return;
	    		}
	    	}

	        logger.log(LogLevel.INFO, "New Request Received:");

	    	// Parse the packet to determine the type of handler required
	    	try {
				TFTPPacket request = TFTPPacket.parse(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));

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
		    		logger.log(LogLevel.ERROR, "Error: Unexpected DATA packet as first request. Reason: Not a read or write request. Solution: Print angry message and continue.");
		    	} else if (request instanceof TFTPPacket.ACK) {
		    		logger.log(LogLevel.ERROR, "Error: Unexpected ACK packet as first request. Reason: Not a read or write request. Solution: Print angry message and continue.");
		    	} else if (request instanceof TFTPPacket.ERROR) {
		    		logger.log(LogLevel.ERROR, "Error: Unexpected ERROR packet as first request. Reason: Not a read or write request. Solution: Print angry message and continue.");
		    	}
			} catch (IllegalArgumentException e) {
				// Unknown Packet Type... (Incorrect OP Code)
	    		logger.log(LogLevel.ERROR, "Error: Unknown Packet Type. Reason: Not a valid TFTP Packet. Solution: Send Error Packet in return and continue.");
	    		try {
	    			DatagramSocket sendSocket = new DatagramSocket();
	    			TFTPPacket.ERROR errorPacket = new TFTPPacket.ERROR(TFTPPacket.TFTPError.ILLEGAL_OPERATION, "The first request received by server must be a valid Read or Write Request. (OPCode: 01 or 02)");
	    			DatagramPacket sendPacket = new DatagramPacket(errorPacket.toBytes(), errorPacket.size(), receivePacket.getAddress(), receivePacket.getPort());
	    			sendSocket.send(sendPacket);
	    			sendSocket.close();
	    		} catch (SocketException se) { // Can't create the socket.
	    			se.printStackTrace();
	    			logger.log(LogLevel.ERROR, "Error: SocketException. Reason: Could not create socket. Solution: Return to Listening.");
	    	    } catch (IOException ioe) { // Can't send the packet.
	    	    	ioe.printStackTrace();
	    	    	logger.log(LogLevel.ERROR, "Error: Socket IO Error. Reason: Could not send packet. Solution: Return to Listening.");
	    	    }
			} catch (SocketException se) {
				se.printStackTrace();
				logger.log(LogLevel.ERROR, "Error: SocketException. Reason: Could not create the handler's socket. Solution: Return to Listening.");
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
	public void sendErrorPacket(TFTPPacket.TFTPError error, String description) {
		try {
			DatagramSocket sendSocket = new DatagramSocket();
			TFTPPacket.ERROR errorPacket = new TFTPPacket.ERROR(error, description);
			DatagramPacket sendPacket = new DatagramPacket(errorPacket.toBytes(), errorPacket.size(), clientAddress, clientTID);
			sendSocket.send(sendPacket);
			sendSocket.close();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			logger.log(LogLevel.ERROR, "Error: SocketException. Reason: Could not create socket. Solution: Ending this transaction.");
	    } catch (IOException ioe) { // Can't send the packet.
	    	ioe.printStackTrace();
	    	logger.log(LogLevel.ERROR, "Error: Socket IO Error. Reason: Could not send packet. Solution: Ending this transaction.");
	    }
	}
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
	 * @throws SocketException
	 */
	public ReadHandler(DatagramPacket receivePacket, TFTPPacket.RRQ request, Logger logger) throws SocketException {
		logger.log(LogLevel.INFO, "Setting up read handler.");
		this.logger = logger;
		this.receivePacket = receivePacket;
		this.request = request;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();

		//Set up the socket that will be used to send/receive packets to/from client
		this.sendReceiveSocket = new DatagramSocket();
		// Set Timeout for the socket!
		sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_TIMEOUT);
	}

	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		logger.log(LogLevel.INFO,"Handling read request.");

		// Set up and run the TFTP Transaction
		try (TFTPTransaction transaction =
				new TFTPTransaction.TFTPSendTransaction(sendReceiveSocket,
						clientAddress, clientTID, filename, false, logger)) {

			transaction.run();

			// Print success message if transfer is complete or error message if transfer has failed.
			switch (transaction.getState()) {
			case COMPLETE:
				logger.log(LogLevel.INFO, "File transfer complete.");
				break;
			case FILE_IO_ERROR:
				logger.log(LogLevel.FATAL, "File transfer failed. File IO error.");
				break;
			case FILE_TOO_LARGE:
				logger.log(LogLevel.FATAL, "File transfer failed. File too large.");
				break;
			case LAST_BLOCK_ACK_TIMEOUT:
				logger.log(LogLevel.ERROR, "File transfer may have failed. Timed out waiting for client to acknowledge last block.");
				break;
			case PEER_BAD_PACKET:
				logger.log(LogLevel.ERROR, "File transfer failed. Client received a bad packet. Error packet response received.");
				break;
			case PEER_DISK_FULL:
				logger.log(LogLevel.FATAL, "File transfer failed. Client disk full.");
				break;
			case PEER_ERROR:
				logger.log(LogLevel.ERROR, "File transfer failed. Error Packet Received from client.");
				break;
			case RECEIVED_BAD_PACKET:
				logger.log(LogLevel.ERROR, "File transfer failed. Received a bad packet. Error packet sent in response.");
				break;
			case SOCKET_IO_ERROR:
				logger.log(LogLevel.ERROR, "File transfer failed. Socket IO error.");
				break;
			case TIMEOUT:
				logger.log(LogLevel.ERROR, "File transfer failed. Timed out waiting for client.");
				break;
			default:
				logger.log(LogLevel.FATAL, String.format(
						"File transfer failed. Unkown error occured: \"%s\"",
						transaction.getState().toString()));
				sendErrorPacket(TFTPPacket.TFTPError.ERROR, "The file transfer failed for an unknown reason. Terminating Transfer.");
				break;

			}
		} catch (FileNotFoundException e) {
			// If the file does not exist,is a directory rather than a regular file,or for some other reason cannot be opened for reading.
		    File fileToTest = new File(filename);
		    if (fileToTest.exists() && fileToTest.isFile())
		    {
		        // The file exists and is a file.. Must be an access violation (or some other error)
		    	if(!fileToTest.canRead()) {
		    		logger.log(LogLevel.ERROR, String.format("The file: "+filename+" could not be opened for reading due to access privileges."));
		    		sendErrorPacket(TFTPPacket.TFTPError.ACCESS_VIOLATION, "The file \""+filename+"\" could not be opened for reading due to access privileges.");
		    	} else {
		    		// Some other unknown error.
		    		logger.log(LogLevel.ERROR, String.format("File IOError trying to open the file for reading for an unknown reason."));
		    		sendErrorPacket(TFTPPacket.TFTPError.ERROR, "The file \""+filename+"\" could not be opened for reading for an unknown reason.");
		    	}
		    } else {
		    	// The file does not exist or is already a directory!
		    	logger.log(LogLevel.ERROR, String.format("File not found on Server: \"%s\".", filename));
		    	sendErrorPacket(TFTPPacket.TFTPError.FILE_NOT_FOUND, "The file \""+filename+"\" could not be found on the Server. (May be a directory)");
		    }
		} catch (IOException e) {
			logger.log(LogLevel.ERROR, "Error: File Closure. Reason: Failed to close file when terminating transaction. Solution: Ending Transaction without closing file.");
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
	 * @throws SocketException
	 */
	public WriteHandler(DatagramPacket receivePacket, TFTPPacket.WRQ request, Logger logger) throws SocketException {
		logger.log(LogLevel.INFO, "Setting up Write Handler");
		this.receivePacket = receivePacket;
		this.request = request;
		this.logger = logger;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();
		this.mode = this.request.getMode();

		//Set up the socket that will be used to send/receive packets to/from client
		this.sendReceiveSocket = new DatagramSocket();
		// Set Timeout for the socket!
		sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_TIMEOUT);
	}

	/**
	 * The run method required to implement Runnable.
	 */
	public void run(){
		logger.log(LogLevel.INFO, "Handling Write Request");
		
		// Parse the file name for validity
		File fileToTest = new File(filename);
		File dirToTest = fileToTest.getParentFile();
		if (dirToTest.isDirectory()) {
			// The directory exists! We can try to write!
			//Check if there's enough space!
			if (dirToTest.getFreeSpace() > 0) {
				// There is enough space! Proceed with the transaction!
			} else {
				// There is not enough space.
				logger.log(LogLevel.ERROR, String.format("The file could not be written because there is not enough space. \""+filename+"\""));
	    		sendErrorPacket(TFTPPacket.TFTPError.DISK_FULL, "The file could not be written because there is not enough space. \""+filename+"\"");
	    		return;
			}
		} else {
			// The directory does not exists.
			logger.log(LogLevel.ERROR, String.format("The file could not be written because its directory does not exist. \""+filename+"\""));
    		sendErrorPacket(TFTPPacket.TFTPError.FILE_NOT_FOUND, "The file could not be written because its directory does not exist. \""+filename+"\"");
    		return;
		}
		
		// Set up and run the TFTP Transaction
		try (TFTPTransaction transaction =
				new TFTPTransaction.TFTPReceiveTransaction(sendReceiveSocket,
						clientAddress, clientTID, filename, true, false, logger)) {

			transaction.run();

			// Print success message if transfer is complete or error message if transfer has failed.
			switch (transaction.getState()) {
				case BLOCK_ZERO_TIMEOUT:
					logger.log(LogLevel.FATAL, "File transfer failed. Timed out waiting for first data packet.");
					break;
				case COMPLETE:
					logger.log(LogLevel.INFO, "File transfer complete.");
					break;
				case FILE_IO_ERROR:
					logger.log(LogLevel.FATAL, "File transfer failed. File IO error.");
					break;
				case FILE_TOO_LARGE:
					logger.log(LogLevel.FATAL, "File transfer failed. File too large.");
					break;
				case PEER_BAD_PACKET:
					logger.log(LogLevel.ERROR, "File transfer failed. Client received a bad packet. Error packet response received.");
					break;
				case PEER_ERROR:
					logger.log(LogLevel.ERROR, "File transfer failed. Error Packet Received from client.");
					break;
				case RECEIVED_BAD_PACKET:
					logger.log(LogLevel.ERROR, "File transfer failed. Received a bad packet. Error packet sent in response.");
					break;
				case SOCKET_IO_ERROR:
					logger.log(LogLevel.ERROR, "File transfer failed. Socket IO error.");
					break;
				case TIMEOUT:
					logger.log(LogLevel.ERROR, "File transfer failed. Timed out waiting for client.");
					break;
				default:
					logger.log(LogLevel.FATAL, String.format(
							"File transfer failed. Unknown error occurred: \"%s\"",
							transaction.getState().toString()));
					sendErrorPacket(TFTPPacket.TFTPError.ERROR, "The file transfer failed for an unknown reason. Terminating Transfer.");
					break;
			}
		} catch (FileNotFoundException e) {
			// If the file does not exist,is a directory rather than a regular file,or for some other reason cannot be opened for writing.
		    fileToTest = new File(filename);
		    if (fileToTest.exists() && fileToTest.isFile())
		    {
		        // The file exists and is a file.. Must be an access violation (or some other error)
		    	if(!fileToTest.canWrite()) {
		    		if(fileToTest.canRead()) {
		    			// Read-Only
		    			logger.log(LogLevel.ERROR, String.format("The file: "+filename+" could not be opened for writing because it is read-only."));
			    		sendErrorPacket(TFTPPacket.TFTPError.ACCESS_VIOLATION, "The file \""+filename+"\" could not be opened for writing because it is read-only.");
		    		} else {
		    			// Some other reason it can't be written
		    			logger.log(LogLevel.ERROR, String.format("The file: "+filename+" could not be opened for writing."));
			    		sendErrorPacket(TFTPPacket.TFTPError.ACCESS_VIOLATION, "The file \""+filename+"\" could not be opened for writing due to an access violation.");
		    		}
		    	} else {
		    		// Some other unknown error.
		    		logger.log(LogLevel.ERROR, String.format("The file \""+filename+"\" could not be opened for writing due to an unknown file IOError"));
		    		sendErrorPacket(TFTPPacket.TFTPError.ERROR, "The file \""+filename+"\" could not be opened for writing due to an unknown file IOError");
		    	}
		    } else {
		    	// The file does not exist or is already a directory!
		    	logger.log(LogLevel.ERROR, String.format("The file \""+filename+"\" could not be written. May already be a directory."));
		    	sendErrorPacket(TFTPPacket.TFTPError.ACCESS_VIOLATION, "The file \""+filename+"\" could not be written. May already be a directory.");
		    }
		} catch (IOException e) {
			logger.log(LogLevel.ERROR, "Error: File Closure. Reason: Failed to close file when terminating transaction. Solution: Ending Transaction without closing file.");
		}
	}
}
