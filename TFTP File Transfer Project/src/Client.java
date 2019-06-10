import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * A TFTP Client program for a client-server project
 *
 * Follows TFTP standards, but cannot handle all errors.
 * Currently handles: incorrect packet types, delayed/duplicated packets, lost packets
 *
 * @author Scott Malonda
 *
 */

public class Client {
	/**
	 * This class is a single-threaded implementation of a TFTP client.
	 * Command line arguments for the first data transfer are accepted
	 */
	private int serverPort;
	private static Logger log = new Logger();

	private InetAddress serverAddress;

	public void setServerAddress(InetAddress serverAddress) {
		this.serverAddress = serverAddress;
	}


	public Client(int serverPort, LogLevel verboseLevel, String logFilePath)
	{
		this.serverPort = serverPort;

		log.setVerboseLevel(verboseLevel, true);

		log.setLogFile(logFilePath, true);

		log.log(LogLevel.INFO,"Setting up send/receive socket.");

		try {
			serverAddress = InetAddress.getLocalHost();
		} catch(UnknownHostException e) {
			log.log(LogLevel.QUIET, "Unable to initialize server address to local host.  Creating client anyways");
		}
	}


	/**
	 * Checks the specified filepaths (source and dest) to see which one is on the server.
	 * If source is the server file, calls getCmd().  If dest is on the server, calls putCmd().
	 * The IP of the server is taken from the filepath to the server file.
	 * @param source Filepath to the file that data will be taken from
	 * @param dest Filepath to the file that data will be copied to
	 * @param c Console object that will be passed to putCmd or getCmd
	 */
	public void buildRequest(String source, String dest, Console c)
	{

		/*
		 * Checking which file (source or dest) is on the server to determine the type of
		 * request.
		 * Also recording the IP address of the server from the path to the server file and
		 * building the packet bytes
		 */
		if(source.contains(":")) {		//Create and send a read request
			String split[] = source.split(":");
			String addressString = split[0];
			String filepath = split[1];

			try {
				this.serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				c.printerr(String.format("Unknown host \"%s\"", source));
				return;
			}

			String args[] = {"getCmd", filepath, dest};
			getCmd(c, args);

		} else if(dest.contains(":")) {		//Create and send a write request
			String split[] = dest.split(":");
			String addressString = split[0];
			String filepath = split[1];

			try {
				this.serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				c.printerr(String.format("Unknown host \"%s\"", source));
				return;
			}

			// Calls putCmd with the arguments determined from above
			String args[] = {"putCmd", filepath, source};
			putCmd(c, args);

		}

		else {	//If neither file is on the server, print an warning message and returns.
			log.log(LogLevel.WARN,"Neither file is on the server.  Starting interactive mode.");
		}
		return;
	}

	private void shutdown (Console c, String[] args) {
		try {
			c.close();
		} catch (IOException e) {
			c.printerr("Error closing console thread.");
			System.exit(1);
		}

		log.log(LogLevel.QUIET, "Shutting Down Client...");
		log.endLog();
		System.exit(0);
	}

	private void setVerboseCmd (Console c, String[] args) {
		c.println("Running in verbose mode.");
		log.setVerboseLevel(LogLevel.INFO, false);
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
		log.setLogFile(args[1], false);
	}

	private void setQuietCmd (Console c, String[] args) {
		c.println("Running in quiet mode.");
		log.setVerboseLevel(LogLevel.WARN, false);
	}

	private void putCmd (Console c, String[] args) {
		if (args.length < 2) {
			// Not enough arguments
			c.println("Error: Too few arguments.  Solution: returning to interactive mode.");
			return;
		} else if (args.length > 3) {
			// Too many arguments
			c.println("Too many arguments.");
			return;
		}

		if (this.serverAddress == null) {
			c.println("No server specified. Use the connect command to choose a server.");
			return;
		}

		String remoteFile = "";
		if (args.length == 2) {
			// Remote name is the same as the local name
			String[] parts = args[1].split("/");
			remoteFile = parts[parts.length - 1];
		} else {
			// Remote name is specified explicitly
			remoteFile = args[2];
		}

		// Aborts write request if the local file doesn't exist.
		File clientFile = new File(args[1]);
		if(!clientFile.isFile()) {
			c.println("Local file doesn't exist.  Aborting write request.");
			return;
		} else if (!clientFile.canRead()) {
			c.println("Local file could not be read.  Aborting write request.");
			return;
		}

		// Create socket for request
		DatagramSocket sendReceiveSocket = null;
		try {
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_TIMEOUT);
		} catch(SocketException se) {
			c.printerr("Could not create socket for transaction");
			return;
		}

		try (TFTPTransaction transaction =
				new TFTPTransaction.TFTPSendTransaction(sendReceiveSocket,
						serverAddress, serverPort, args[1], true,
						Client.log);) {
			// Do write request
			TFTPPacket.WRQ writePacket = new TFTPPacket.WRQ(remoteFile,
					TFTPPacket.TFTPMode.NETASCII);
			DatagramPacket request = new DatagramPacket(writePacket.toBytes(),
					writePacket.size(), serverAddress, serverPort);

			log.logPacket(LogLevel.INFO, request, writePacket, false, "server");

			try {
				sendReceiveSocket.send(request);
			} catch(IOException e) {
				c.printerr("Failed to send request to server.");
				return;
			}

			transaction.run();

			// Print success message if transfer is complete or error message if
			// transfer has failed.

			switch (transaction.getState()) {
			case BLOCK_ZERO_TIMEOUT:
				c.println("File transfer failed. Server did not respond to request.");
				break;
			case COMPLETE:
				c.println("File transfer complete.");
				break;
			case FILE_IO_ERROR:
				c.println("File transfer failed. File IO error.");
				break;
			case FILE_TOO_LARGE:
				c.println("File transfer failed. File too large.");
				break;
			case LAST_BLOCK_ACK_TIMEOUT:
				c.println("File transfer may have failed. Timed out waiting for server to acknowledge last block.");
				break;
			case RECEIVED_BAD_PACKET:
				c.println("File transfer failed. Received invalid packet.");
				break;
			case SOCKET_IO_ERROR:
				c.println("File transfer failed. Socket IO error.");
				break;
			case TIMEOUT:
				c.println("File transfer failed. Timed out waiting for server.");
				break;
			case PEER_ACCESS_VIOLATION:
				c.println("File transfer failed. Server access violation.");
				break;
			case PEER_BAD_PACKET:
				c.println("File transfer failed. Server received bad packet.");
				break;
			case PEER_DISK_FULL:
				c.println("File transfer failed. Server disk full.");
				break;
			case PEER_ERROR:
				c.println("File transfer failed. Error on server.");
				break;
			case PEER_FILE_EXISTS:
				c.println("File transfer failed. File exists on server.");
				break;
			default:
				c.println(String.format(
						"File transfer failed. Unknown error occurred: \"%s\"",
						transaction.getState().toString()));
				break;
			}
		} catch (FileNotFoundException e) {
			c.println(String.format("Error: Could not access file: \"%s\".  Free space on disk partition: %d", args[1], clientFile.getFreeSpace()));
			c.println("Returning to interactive mode.");
		} catch (IOException e1) {
			c.println("Error: Failed to close file after transaction completed.  Returning to interactive mode.");
		}

		sendReceiveSocket.close();
	}

	private void getCmd (Console c, String[] args) {
		if (args.length < 2) {
			// Not enough arguments
			c.println("Too few arguments.");
			return;
		} else if (args.length > 3) {
			// Too many arguments
			c.println("Too many arguments.");
			return;
		}

		if (this.serverAddress == null) {
			c.println("No server specified. Use the connect command to choose a server.");
			return;
		}

		String localFile = "";
		if (args.length == 2) {
			// Local name is the same as the remote name
			String[] parts = args[1].split("/");
			localFile = parts[parts.length - 1];
		} else {
			// Local name is specified explicitly
			localFile = args[2];
		}
		File clientFile = new File(localFile);
		if(clientFile.isFile()) {
			if(!clientFile.canWrite()) {
				c.println("Local file exists but cannot be written to.  Aborting write request.");
			}
		}
		// Create socket for request
		DatagramSocket sendReceiveSocket = null;
		try {
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_TIMEOUT);
		} catch(SocketException se) {
			c.printerr("Error: Could not create socket for transaction.  Returning to interactive mode.");
			return;
		}

		try (TFTPTransaction transaction =
				new TFTPTransaction.TFTPReceiveTransaction(
						sendReceiveSocket, serverAddress, serverPort,
						localFile, false, true, Client.log);) {
			// Do read Request
			TFTPPacket.RRQ readPacket = new TFTPPacket.RRQ(args[1],
					TFTPPacket.TFTPMode.NETASCII);
			DatagramPacket request = new DatagramPacket(readPacket.toBytes(),
					readPacket.size(), serverAddress, serverPort);

			log.logPacket(LogLevel.INFO, request, readPacket, false, "server");

			try {
				sendReceiveSocket.send(request);
			} catch(IOException e) {
				c.printerr("Error: Failed to send request to server.  Returning to interactive mode.");
				return;
			}

			transaction.run();

			// Print success message if transfer is complete or error message if
			// transfer has failed.

			switch (transaction.getState()) {
			case BLOCK_ZERO_TIMEOUT:
				c.println("File transfer failed. Server did not respond to request.");
				break;
			case COMPLETE:
				c.println("File transfer complete.");
				break;
			case FILE_IO_ERROR:
				c.println("File transfer failed. File IO error.");
				break;
			case FILE_TOO_LARGE:
				c.println("File transfer failed. File too large.");
				break;
			case LAST_BLOCK_ACK_TIMEOUT:
				c.println("File transfer may have failed. Timed out waiting for server to acknowledge last block.");
				break;
			case RECEIVED_BAD_PACKET:
				c.println("File transfer failed. Received invalid packet.");
				break;
			case SOCKET_IO_ERROR:
				c.println("File transfer failed. Socket IO error.");
				break;
			case TIMEOUT:
				c.println("File transfer failed. Timed out waiting for server.");
				break;
			case PEER_ACCESS_VIOLATION:
				c.println("File transfer failed. Server access violation.");
				break;
			case PEER_BAD_PACKET:
				c.println("File transfer failed. Server received bad packet.");
				break;
			case PEER_ERROR:
				c.println("File transfer failed. Error occurred on server.");
				break;
			case PEER_FILE_NOT_FOUND:
				c.println("File transfer failed. File not found on server.");
				break;
			default:
				c.println(String.format(
						"File transfer failed. Unknown error occurred: \"%s\"",
						transaction.getState().toString()));
				break;
			}
		} catch (FileNotFoundException e) {
			c.println(String.format("Error: File could not be accessed: \"%s\".  Free space on disk partition: %d", args[1], clientFile.getFreeSpace()));
			c.println("Returning to interactive mode.");
		} catch (IOException e1) {
			c.println("Error: Failed to close file after transaction completed.  Returning to interactive mode");
		}

		sendReceiveSocket.close();
	}

	private void connectCmd (Console c, String[] args) {
		if (args.length < 2) {
			// Not enough arguments
			c.println("Too few arguments.");
			return;
		} else if (args.length > 3) {
			// Too many arguments
			c.println("Too many arguments.");
			return;
		}

		try {
			InetAddress address = InetAddress.getByName(args[1]);
			this.setServerAddress(address);
			if (args.length == 3) {
				// Parse port
				try {
					int port = Integer.parseInt(args[2]);
					this.serverPort = port;
					c.println("Connected to "+address+" on port "+port);
				} catch (NumberFormatException e) {
					c.println("Invalid port: \"" + args[2] + "\"");
				}
			} else {
				c.println("Connected to "+address+" on port 69");
			}
		} catch (UnknownHostException e) {
			c.println("Error: Invalid server: \"" + args[1] + "\".  Returning to interactive mode");
		}
	}

	private void setNormalCmd (Console c, String[] args) {
		c.println("Running in normal mode (Sending to port 69)");
		serverPort = 69;
	}

	private void setTestCmd (Console c, String[] args) {
		c.println("Running in test mode (Sending to port 23)");
		serverPort = 23;
	}

	private void helpCmd (Console c, String[] args) {
		c.println("Avalable Client Commands:");
		c.println("connect [ip] <port> - Select a server, if port is not specified port 69 will be used.");
		c.println("put [local filename] <remote filename> - Send a file to the server.");
		c.println("get [remote filename] <local filename - Get a file from the server.");
		c.println("logfile [file path] - Set the log file.");
		c.println("verbose - Enable more detailed console output.");
		c.println("quiet - Limit console output to essential and convenient information.");
		c.println("test - Sets Client to send to port 23 (Error simulator port).");
		c.println("normal - Sets Client to send to port 69 (Server port)");
		c.println("shutdown - Shutdown client.");

	}

	public static void main(String[] args) {
		log.log(LogLevel.QUIET,"Starting Client...");

		int serverPort = 69;
		LogLevel verboseLevel = LogLevel.QUIET;
		String logFilePath = "";

		//Setting up the parsing options
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );

		Option serverPortOption = Option.builder("p").argName("server port")
                .hasArg()
                .desc("the port number of the server's listener")
                .type(Integer.TYPE)
                .build();

		Option logFilePathOption = Option.builder("l").argName("log file path")
                .hasArg()
                .desc("The log file the client writes to")
                .type(String.class)
                .build();

		Options options = new Options();
		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		options.addOption(logFilePathOption);

		CommandLine line = null;

		CommandLineParser parser = new DefaultParser();
	    try {
	        // parse the command line arguments
	        line = parser.parse( options, args );

	        if( line.hasOption("verbose")) {
	        	verboseLevel = LogLevel.INFO;
		    }

	        if( line.hasOption("p")) {
		        serverPort = Integer.parseInt(line.getOptionValue("p"));
		    }

	        if( line.hasOption("l")) {
	        	logFilePath = line.getOptionValue("l");
	        }
	    } catch( ParseException exp ) {
	    	log.log(LogLevel.FATAL, "Fatal Error: Command line argument parsing failed.  Reason: " + exp.getMessage() );
	    	log.log(LogLevel.QUIET, "Shutting Down Client...");
			log.endLog();
		    System.exit(1);
	    }
	    // Creating a client and initializing the server address to the local host address
	    Client client = new Client(serverPort,verboseLevel,logFilePath);

	    // Create console UI
	    Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", client::shutdown),
				Map.entry("verbose", client::setVerboseCmd),
				Map.entry("quiet", client::setQuietCmd),
				Map.entry("normal", client::setNormalCmd),
				Map.entry("test", client::setTestCmd),
				Map.entry("logfile", client::setLogfileCmd),
				Map.entry("put", client::putCmd),
				Map.entry("get", client::getCmd),
				Map.entry("connect", client::connectCmd),
				Map.entry("help", client::helpCmd)
				);

		Console console = new Console(commands);

	    // Get the positional arguments and perform a transaction if one is specified
		String[] positionalArgs = line.getArgs();
		if (positionalArgs.length == 1) {
			// Assume that the argument is the server address
			try {
				client.setServerAddress(InetAddress.getByName(positionalArgs[0]));
			} catch (UnknownHostException e) {
				log.log(LogLevel.QUIET, "Invalid server: \"" + positionalArgs[0] + "\"");
			}
		} else if (positionalArgs.length == 2) {
			// Source and destination files specified
			client.buildRequest(positionalArgs[0], positionalArgs[1], console);
		} else if (positionalArgs.length > 2) {
			// Too many arguments
			log.log(LogLevel.QUIET,"Too many files specified, entering interactive mode.");
		}

		// Start console UI thread
		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}
