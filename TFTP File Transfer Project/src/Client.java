import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.net.SocketTimeoutException;

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
	private DatagramSocket sendReceiveSocket;
	private int serverPort;
	private static Logger log = new Logger();
	
	private InetAddress serverAddress;
	
	public void setServerAddress(InetAddress serverAddress) {
		this.serverAddress = serverAddress;
	}
	

	public Client(int serverPort, int verboseLevel, String logFilePath)
	{
		this.serverPort = serverPort;
		
		log.setVerboseLevel(verboseLevel);
		
		log.setLogFile(logFilePath);
		
		log.log(5,"Setting up send/receive socket.");
		
		try {	//Setting up the socket that will send/receive packets
			sendReceiveSocket = new DatagramSocket();
		} catch(SocketException se) { //If the socket can't be created
			se.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Method to read a file from the server.  The Client must already have the server
	 * address and port #.
	 * 
	 * @param filename Filepath to the client-side file that will be written to
	 */
	public void read(String filename)
	{
		
		log.log(5,"Reading from server file");
		
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			log.log(0, "Invalid file name: file not found.  Please re-enter a valid file name");
			return;
		}
		
		// Attempting to disable the socket timeout
		try {
			sendReceiveSocket.setSoTimeout(0);
		} catch(SocketException se) {
			log.log(0,"Timeout could not be disabled.  Continuing proccess.\nProcess may terminate due to SocketTimeoutExceptions may be encountered.");
		}
		
		TFTPPacket.ACK ackPacket = new TFTPPacket.ACK(0);
		DatagramPacket sendPacket;
		byte[] data = new byte[TFTPPacket.MAX_SIZE];
		DatagramPacket receivePacket;
		TFTPPacket.DATA dataPacket = null;
		int len = 0;
		int blockNum = 0;
		int lastBlock = 0;

		log.log(5,"File ready to be written! filename: "+filename);
		
		// Receive data and send acks
		boolean moreToWrite = true;
		boolean duplicateData = false;
		while (moreToWrite) {
			// Receive Data Packet
			log.log(5,"Waiting for data packet");
			
			data = new byte[TFTPPacket.MAX_SIZE];
			receivePacket = new DatagramPacket(data, data.length);
			
			try {
				sendReceiveSocket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			log.log(5,"Recieved packet from server");
			// Check Packet for correctness
		    
			try {
				dataPacket = new TFTPPacket.DATA(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				log.log(0,"Not a DATA response to ack! :((((");
				e.printStackTrace();
				System.exit(0);
			}
			// Definitely data :)
			// Strip block number & port
			blockNum = dataPacket.getBlockNum();
			int replyPort = receivePacket.getPort();
			// Check if the received block is either duplicated or delayed.  If so, send an ACK 
			// packet for it but don't write any of the data to the file.
			if(blockNum < (lastBlock + 1)) {
				duplicateData = true;
			}
			// Check size? Less than 512 == done
			len = dataPacket.getData().length;
			if (len < 512) {
				moreToWrite = false;
			}
			log.log(5,
				"Received Packet:\n"
				+ "Packet Type: DATA\n"
				+ "Filename: "+filename+"\n"
				+ "Block Number: "+blockNum+"\n"
				+ "# of Bytes: "+len);
			// Write into file
			if(!duplicateData) {
				try {
					fos.write(dataPacket.getData(),0,dataPacket.getData().length);
				} catch (IOException e) {
				    log.log(0,"Failed to write data to file!");
					e.printStackTrace();
					System.exit(0);
				}
			}
			
			// Send Acknowledgement packet with block number
			ackPacket = new TFTPPacket.ACK(blockNum);
			sendPacket = new DatagramPacket(ackPacket.toBytes(), ackPacket.toBytes().length, serverAddress, replyPort);
			if(!duplicateData) {
				lastBlock = blockNum;
			}
			
			log.log(5,"ACK Packet Successfully Assembled");
			log.log(5,"Last block number updated");
			
			// Send ack packet to server on serverPort
			log.log(5,"Sending Packet:\n"
				+ "Packet Type: ACK"
				+ "Block Number: "+blockNum);
			
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			duplicateData = false;
			if(moreToWrite) {
				log.log(5,"Waiting for Next DATA Block:");
			}
		}
		
		log.log(5,"File transfer complete!");
		
		try {
			fos.flush();
			fos.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Method to write a file to the server.  The Client must already have the server
	 * address and port #.
	 * 
	 * @param filename Filepath to the client-side file that will be read from
	 */
	public void write(String filename)
	{
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filename);
		} catch (FileNotFoundException e) {
			log.log(0, "Invalid file name: file not found.  Please re-enter command with a valid file name");
			return;
		} 
		log.log(5,"Successfully opened: "+filename);
		
		TFTPPacket.DATA dataPacket;
	    DatagramPacket receivePacket;
	    DatagramPacket sendPacket = null;
	    byte[] data = new byte[TFTPPacket.MAX_SIZE];
	    int len = 69999;
		int blockNum = 0;
	    
		//Receiving the first ACK packet and stripping the new port number
	    receivePacket = new DatagramPacket(data, data.length);
	    try {
    		sendReceiveSocket.receive(receivePacket);
    	} catch(IOException e) {
    		e.printStackTrace();
			System.exit(1);
    	}
	    
		log.log(5,"Recieved packet from server.");
	    
	    // Parse ACK for correctness
	    TFTPPacket.ACK ackPacket = null;
    	try {
			ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
		} catch (IllegalArgumentException e) {
			log.log(0,"Not an ACK Packet! :((((");
			e.printStackTrace();
			System.exit(0);
		}
    	
    	if (ackPacket.getBlockNum() == 0 ) {
			// Correct acks
			log.log(5,"Recieved ACK for block #0.  Starting data transfer...");
		} else {
			// Incorrect ack
			log.log(0,"Wrong ACK response. Incorrect block number");
			try {
    			fis.close();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		throw new IllegalArgumentException();
		}
    	
    	// Initializing the socket timeout.  If it cannot be set, prints an error message but
    	// continues running.
    	try {
			sendReceiveSocket.setSoTimeout(5000);
		} catch(SocketException se) {
			log.log(0,"Socket timeout could not be set.  Continuing transfer without socket timeout.");
		}
    	
		int replyPort = receivePacket.getPort();
		boolean moreToRead = true;
		boolean duplicateAck = false;
		while (moreToRead) {
			// Read data from file into data packet and send to server if the last ACK was correct
		    if(!duplicateAck) {
		    	blockNum++;
		    	blockNum = blockNum & 0xFFFF;
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
		    	dataPacket = new TFTPPacket.DATA(blockNum, data);
		    	sendPacket = new DatagramPacket(dataPacket.toBytes(), dataPacket.toBytes().length, serverAddress, replyPort);
		    	
			    log.log(5,"Sending Packet:"
			    	+ "Packet Type: DATA"
			    	+ "Filename: "+filename
			    	+ "Block Number: "+blockNum
			    	+ "# of Bytes: "+len);
		    	
		    	try {
		    		sendReceiveSocket.send(sendPacket);
		    	} catch (IOException e) {
		    		e.printStackTrace();
		    		System.exit(1);
		    	}
		    	
		    	duplicateAck = false;
		    }
		    
		    // Wait for ACK
			log.log(5,"Waiting for ACK packet...");
		    
		    // New Receive total bytes
		    data = new byte[TFTPPacket.MAX_SIZE];
		    receivePacket = new DatagramPacket(data, data.length);
		    boolean acknowledged = false;
		    int resendCount = 0;
		    // Loop to handle socket timeouts.  If the socket times out, the last data packet is sent again.
		    // Loops 5 times until the program gives up and stops the transfer.
		    while(!acknowledged) {
		    	try {
		    		sendReceiveSocket.receive(receivePacket);
		    		acknowledged = true;
		    	} catch(IOException e1) {
		    		if(e1 instanceof SocketTimeoutException) {
		    			log.log(0,"Socket timeout while waiting for ACK packet.");
		    			if(resendCount > 4) {
				    		log.log(0,"Data has been re-sent 5 times.  Aborting file transfer.");
				    		return;
				    	}
		    			log.log(5,"Re-sending last DATA packet.");
		    			try {
				    		sendReceiveSocket.send(sendPacket);
				    	} catch (IOException e2) {
				    		e2.printStackTrace();
				    		System.exit(1);
				    	}
		    			resendCount++;
		    		} else {
		    		e1.printStackTrace();
		    		System.exit(1);
		    		}
		    	}
		    }
		    
		    // Parse ACK for correctness
		    ackPacket = null;
	    	try {
				ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
				replyPort = receivePacket.getPort();
			} catch (IllegalArgumentException e) {
				log.log(0,"Wrong Packet Recieved. Reason: Not an ackPacket");
				e.printStackTrace();
				System.exit(0);
			}
	    	if (ackPacket.getBlockNum() == blockNum ) {
				// Correct acks
	    		duplicateAck = false;
			} else {
				// Incorrect ack
				log.log(0,"Wrong ACK response. Reason: Incorrect block number.  Ignoring ACK and waiting for another packet.");
				duplicateAck = true;
			}
	    	
			log.log(5,"Received Packet:"
				+"Packet Type: ACK"
				+"Block Number: "+blockNum);
		}
		// All data is sent and last ACK received,
		// Close socket, quit
		log.log(5,"File transfer complete!");
	}
		
	public void buildRequest(String source, String dest)
	{
		/**
		 * Checks the specified filepaths (source and dest) to see which one is on the server.
		 * If source is the server file, creates a read request and calls read().  If dest is
		 * on the server, creates a write request and calls write().
		 * The IP of the server is take
		 */
		DatagramPacket sendPacket = null;
		
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
				e.printStackTrace();
				System.exit(1);
			}
			
			// Read Request
			TFTPPacket.RRQ readPacket = new TFTPPacket.RRQ(filepath, TFTPPacket.TFTPMode.NETASCII);
			sendPacket = new DatagramPacket(readPacket.toBytes(), readPacket.size(), serverAddress, serverPort);
			
			log.log(5,"Sending Packet"
				+"Packet Type: RRQ"
				+"Filename: "+filepath
				+"Mode: "+readPacket.getMode().toString()
				+"# of Bytes: "+(sendPacket.getData().length-4));

			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			log.log(5,"Request sent.  Waiting for response from server...");
			
			read(dest);
			
			
		} else if(dest.contains(":")) {		//Create and send a write request
			String split[] = dest.split(":");
			String addressString = split[0];
			String filepath = split[1];
			
			try {
				this.serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// Write request
			TFTPPacket.WRQ writePacket = new TFTPPacket.WRQ(filepath, TFTPPacket.TFTPMode.parseFromString("netascii"));
			sendPacket = new DatagramPacket(writePacket.toBytes(), writePacket.size(), serverAddress, serverPort);
			
			log.log(5,"Sending Packet"
				+"Packet Type: RRQ"
				+"Filename: "+filepath
				+"Mode: "+writePacket.getMode().toString()
				+"# of Bytes: "+(sendPacket.getData().length-4));

			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			log.log(5,"Request sent.  Waiting for response from server...");
	    	
			write(source);
		}
		
		else {	//If neither file is on the server, print an error message and quit.
			log.log(0,"Error: neither file is on the server.  Please try another command.");
		}
	}
	
	private void shutdown (Console c, String[] args) {
		c.println("Closing socket and scanner, and shutting down server.");
		
		sendReceiveSocket.close();
		try {
			c.close();
		} catch (IOException e) {
			c.printerr("Error closing console thread.");
			System.exit(1);
		}
		
		System.exit(0);
	}
	
	private void setVerboseCmd (Console c, String[] args) {
		c.println("Running in verbose mode.");
		log.setVerboseLevel(5);
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
		log.setLogFile(args[1]);
	}

	private void setQuietCmd (Console c, String[] args) {
		c.println("Running in quiet mode.");
		log.setVerboseLevel(0);
	}
	
	private void putCmd (Console c, String[] args) {
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
		
		String remoteFile = "";
		if (args.length == 2) {
			// Remote name is the same as the local name
			String[] parts = args[1].split("/");
			remoteFile = parts[parts.length - 1];
		} else {
			// Remote name is specified explicitly
			remoteFile = args[2];
		}
		
		// Do write request
		TFTPPacket.WRQ writePacket = new TFTPPacket.WRQ(remoteFile, TFTPPacket.TFTPMode.NETASCII);
		DatagramPacket request = new DatagramPacket(writePacket.toBytes(), writePacket.size(), serverAddress, serverPort);
		
		log.log(5,"Sending Packet"
			+"Packet Type: RRQ"
			+"Filename: " + remoteFile
			+"Mode: " + writePacket.getMode().toString()
			+"# of Bytes: " + (request.getData().length - 4));
		
		try {
			sendReceiveSocket.send(request);
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		log.log(5,"Request sent.  Waiting for response from server...");
    	
		write(args[1]);
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
		
		// Do read Request
		TFTPPacket.RRQ readPacket = new TFTPPacket.RRQ(args[1], TFTPPacket.TFTPMode.NETASCII);
		DatagramPacket request = new DatagramPacket(readPacket.toBytes(), readPacket.size(), serverAddress, serverPort);
		
			log.log(5,"Sending Packet"
				+"Packet Type: RRQ"
				+"Filename: " + args[1]
				+"Mode: " + readPacket.getMode().toString()
				+"# of Bytes: " + (request.getData().length - 4));
		
		try {
			sendReceiveSocket.send(request);
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
			log.log(5,"Request sent.  Waiting for response from server...");
		
		read(localFile);
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
			this.setServerAddress(InetAddress.getByName(args[1]));
		} catch (UnknownHostException e) {
			c.println("Invalid server: \"" + args[1] + "\"");
		}
		
		if (args.length == 3) {
			// Parse port
			try {
				this.serverPort = Integer.parseInt(args[2]);
			} catch (NumberFormatException e) {
				c.println("Invalid port: \"" + args[2] + "\"");
			}
		} else {
			this.serverPort = 69;
		}
	}
	
	private void helpCmd (Console c, String[] args) {
		c.println("Avaliable Client Commands:");
		c.println("connect [server] <ip>\n\tSelect a server, if port is not specified port 69 will be used.");
		c.println("put [local file] <remote file>\n\tSend a file to the server.");
		c.println("get [remote file] <local file>\n\tGet a file from the server.");
		c.println("shutdown\n\tShutdown client.");
		c.println("verbose\n\tEnable debugging output.");
		c.println("logfile [file path]\n\tSet the log file.");
		c.println("quiet\n\tDisable debugging output.");
	}

	public static void main(String[] args) {
		log.log(5,"Setting up Client...");
		
		int serverPort = 69;
		int verboseLevel = 0;
		String logFilePath = "";
		
		//Setting up the parsing options
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );
		
		Option serverPortOption = Option.builder("p").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();

		Option logFilePathOption = Option.builder("l").argName("log file path")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
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
	        	verboseLevel = 5;
		    }
	        
	        if( line.hasOption("p")) {
		        serverPort = Integer.parseInt(line.getOptionValue("p"));
		    }
	        
	        if( line.hasOption("l")) {
	        	logFilePath = line.getOptionValue("l");
	        }
	    } catch( ParseException exp ) {
	    	log.log(0, "Command line argument parsing failed.  Reason: " + exp.getMessage() );
		    System.exit(1);
	    }
	    
	    Client client = new Client(serverPort,verboseLevel,logFilePath);
	    
	    
	    // Get the positional arguments and perform a transaction if one is specified
		String[] positionalArgs = line.getArgs();
		if (positionalArgs.length == 1) {
			// Assume that the argument is the server address
			try {
				client.setServerAddress(InetAddress.getByName(positionalArgs[0]));
			} catch (UnknownHostException e) {
				log.log(0, "Invalid server: \"" + positionalArgs[0] + "\"");
			}
		} else if (positionalArgs.length == 2) {
			// Source and destination files specified
			client.buildRequest(positionalArgs[0], positionalArgs[1]);
			System.exit(0);
		} else if (positionalArgs.length > 2) {
			// Too many arguments
			log.log(0,"Too many files specified, entering interactive mode.");
		}

		// Create and start console UI thread
		Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", client::shutdown),
				Map.entry("verbose", client::setVerboseCmd),
				Map.entry("quiet", client::setQuietCmd),
				Map.entry("logfile", client::setLogfileCmd),
				Map.entry("put", client::putCmd),
				Map.entry("get", client::getCmd),
				Map.entry("connect", client::connectCmd),
				Map.entry("help", client::helpCmd)
				);

		Console console = new Console(commands);

		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}
