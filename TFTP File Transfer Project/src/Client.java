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
	

	public Client(int serverPort, LogLevel verboseLevel, String logFilePath)
	{
		this.serverPort = serverPort;
		
		log.setVerboseLevel(verboseLevel);
		
		log.setLogFile(logFilePath);
		
		log.log(LogLevel.INFO,"Setting up send/receive socket.");
		
		try {	//Setting up the socket that will send/receive packets
			sendReceiveSocket = new DatagramSocket();
			sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_TIMEOUT);
		} catch(SocketException se) { //If the socket can't be created
			se.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * Method to read a file from the server.  The Client must already have the server
	 * address and port #.  This has been replaced with TFTPTransaction methods and is
	 * not being used for transfers.
	 * 
	 * @param filename Filepath to the client-side file that will be written to
	 */
	public void read(String filename)
	{
		
		log.log(LogLevel.INFO,"Reading from server file");
		
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			log.log(LogLevel.FATAL, "Invalid file name: file not found.  Please re-enter a valid file name");
			return;
		}
		
		TFTPPacket.ACK ackPacket = new TFTPPacket.ACK(0);
		DatagramPacket sendPacket = null;
		byte[] data = new byte[TFTPPacket.MAX_SIZE];
		DatagramPacket receivePacket;
		TFTPPacket.DATA dataPacket = null;
		int len = 0;
		int blockNum = 0;
		int lastBlock = 0;

		log.log(LogLevel.INFO,"File ready to be written! filename: "+filename);
		
		// Receive data and send acks
		boolean moreToWrite = true;
		boolean duplicateData = false;
		boolean acknowledged = false;
		int resendCount = 0;
		while (moreToWrite) {
			// Receive Data Packet
			log.log(LogLevel.INFO,"Waiting for data packet");
			
			acknowledged = false;
			
			receivePacket = new DatagramPacket(data, data.length);
			
			while(!acknowledged) {
				try {
					sendReceiveSocket.receive(receivePacket);
					acknowledged = true;
				} catch(IOException e1) {
					// Checks if the socket timed out
					if(e1 instanceof SocketTimeoutException) {
						//If no data blocks have been received yet, send Client back to console to re-send request
						if(blockNum == 0) {
							log.log(LogLevel.FATAL,  "Timeout while waiting for first DATA packet.  Client returning to console.");
							try {
								fos.flush();
								fos.close();
							} catch (IOException e) {
								e.printStackTrace();
								System.exit(1);
							}
							return;
						} else {
							if(resendCount > 4) {
						    	log.log(LogLevel.FATAL,"ACK has been re-sent 5 times.  Aborting file transfer.");
						    	try {
						    		fos.flush();
									fos.close();
								} catch (IOException e) {
									e.printStackTrace();
									System.exit(1);
								}
						    	return;
							}
							// Re-send the last ACK packet
							
							log.logPacket(LogLevel.INFO, sendPacket, ackPacket, false, "server");
							
							try {
								sendReceiveSocket.send(sendPacket);
							} catch(IOException e2) {
								e2.printStackTrace();
								System.exit(1);
							}
						}
					} else {
						e1.printStackTrace();
						System.exit(1);
					}
				}
			}
			
			log.log(LogLevel.INFO,"Recieved packet from server");
			// Check Packet for correctness
		    
			try {
				dataPacket = new TFTPPacket.DATA(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				log.log(LogLevel.FATAL,"Not a DATA response to ack! :((((");
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
			
			log.logPacket(LogLevel.INFO, receivePacket, dataPacket, true, "server");
			
			// Write into file
			if(!duplicateData) {
				try {
					fos.write(dataPacket.getData(),0,dataPacket.getData().length);
				} catch (IOException e) {
				    log.log(LogLevel.FATAL,"Failed to write data to file!");
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
			
			log.log(LogLevel.INFO,"ACK Packet Successfully Assembled");
			log.log(LogLevel.INFO,"Last block number updated");
			
			// Send ack packet to server on serverPort
			log.logPacket(LogLevel.INFO, sendPacket, ackPacket, false, "server");
			
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			duplicateData = false;
			if(moreToWrite) {
				log.log(LogLevel.INFO,"Waiting for Next DATA Block:");
			}
		}
		
		log.log(LogLevel.INFO,"File transfer complete!");
		
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
	 * address and port #.  This has been replaced with TFTPTransaction methods and is
	 * not being used for transfers.
	 * 
	 * @param filename Filepath to the client-side file that will be read from
	 */
	public void write(String filename)
	{
		
		//Tries to open the file.  If the filename is invalid, cancels the request.
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filename);
		} catch (FileNotFoundException e) {
			log.log(LogLevel.WARN, "Invalid file name: file not found.  Please re-enter command with a valid file name");
			return;
		} 
		log.log(LogLevel.INFO,"Successfully opened: "+filename);
    	
    	long timeout = 0;
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
	    } catch(IOException e1) {
	    	if(e1 instanceof SocketTimeoutException) {
	    		log.log(LogLevel.ERROR,  "Socket timeout while waiting for first ACK packet.  Returning to console.");
	    		try {
	    			fis.close();
	    		} catch(IOException e2) {
	    			e2.printStackTrace();
	    			System.exit(1);
	    		}
	    		return;
	    	} else {
	    	e1.printStackTrace();
	    	System.exit(1);
	    	}
	    }
	    
		log.log(LogLevel.INFO,"Recieved packet from server.");
	    
	    // Parse ACK for correctness
	    TFTPPacket.ACK ackPacket = null;
    	try {
			ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
		} catch (IllegalArgumentException e) {
			log.log(LogLevel.FATAL,"Not an ACK Packet! :((((");
			e.printStackTrace();
			System.exit(0);
		}
    	
    	log.logPacket(LogLevel.INFO, receivePacket, ackPacket, true, "server");
    	
    	if (ackPacket.getBlockNum() == 0 ) {
			// Correct acks
			log.log(LogLevel.INFO,"Recieved ACK for block #0.  Starting data transfer...");
		} else {
			// Incorrect ack
			log.log(LogLevel.FATAL,"Wrong ACK response. Incorrect block number.");
			try {
    			fis.close();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    		throw new IllegalArgumentException();
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
		    	
		    	log.logPacket(LogLevel.INFO, sendPacket, dataPacket, false, "server");
		    	
		    	try {
		    		sendReceiveSocket.send(sendPacket);
		    		sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_DATA_TIMEOUT);
		    	} catch (IOException e) {
		    		e.printStackTrace();
		    		System.exit(1);
		    	}
		    	
		    	timeout = System.currentTimeMillis() + TFTPPacket.TFTP_DATA_TIMEOUT;
		    	duplicateAck = false;
		    }
		    
		    // Wait for ACK
			log.log(LogLevel.INFO,"Waiting for ACK packet...");
		    
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
		    			log.log(LogLevel.FATAL,"Socket timeout while waiting for ACK packet.");
		    			if(resendCount > 4) {
				    		log.log(LogLevel.FATAL,"Data has been re-sent 5 times.  Aborting file transfer.");
				    		return;
				    	}
		    			log.log(LogLevel.INFO,"Re-sending last DATA packet.");
		    			
		    			log.logPacket(LogLevel.INFO, sendPacket, ackPacket, false, "server");
		    			try {
				    		sendReceiveSocket.send(sendPacket);
				    		sendReceiveSocket.setSoTimeout(TFTPPacket.TFTP_DATA_TIMEOUT);
				    	} catch (IOException e2) {
				    		e2.printStackTrace();
				    		System.exit(1);
				    	}
		    			timeout = System.currentTimeMillis() + TFTPPacket.TFTP_DATA_TIMEOUT;
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
				log.log(LogLevel.FATAL,"Wrong Packet Recieved. Reason: Not an ackPacket");
				e.printStackTrace();
				System.exit(0);
			}
	    	log.logPacket(LogLevel.INFO, receivePacket, ackPacket, true, "server");
	    	if (ackPacket.getBlockNum() == blockNum ) {
				// Correct ack
	    		duplicateAck = false;
			} else if(ackPacket.getBlockNum() < blockNum) {
				// Duplicate ack
				log.log(LogLevel.FATAL,"Wrong ACK response. Reason: Incorrect block number.  Ignoring ACK and waiting for another packet.");
				int timeLeft = (int)(timeout - System.currentTimeMillis());
				if(timeLeft > 0) {
					try {
						sendReceiveSocket.setSoTimeout(timeLeft);
					} catch(SocketException se) {
						se.printStackTrace();
						System.exit(1);
					}
				} else {
					try {
						sendReceiveSocket.setSoTimeout(10);
					} catch(SocketException se) {
						se.printStackTrace();
						System.exit(1);
					}
				}
				duplicateAck = true;
			} else {
				log.log(LogLevel.FATAL,"Error: Block number higher than current block number.  Aborting transfer.");
				System.exit(1);
			}
	    	
			log.log(LogLevel.INFO,"Received Packet:"
				+"Packet Type: ACK"
				+"Block Number: "+blockNum);
		}
		// All data is sent and last ACK received,
		// Close socket, quit
		log.log(LogLevel.INFO,"File transfer complete!");
	}
	
	/**
	 * Checks the specified filepaths (source and dest) to see which one is on the server.
	 * If source is the server file, calls getCmd().  If dest is on the server, calls putCmd().
	 * The IP of the server is taken from the filepath to the server file.
	 * 
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
				e.printStackTrace();
				System.exit(1);
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
				e.printStackTrace();
				System.exit(1);
			}
			
			// Calls putCmd with the arguments determined from above
			String args[] = {"putCmd", filepath, source};
			putCmd(c, args);
			
		}
		
		else {	//If neither file is on the server, print an warning message and returns.
			log.log(LogLevel.WARN,"Neither file is on the server.  Please try another command.");
		}
		return;
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
		
		log.endLog();
		System.exit(0);
	}
	
	private void setVerboseCmd (Console c, String[] args) {
		c.println("Running in verbose mode.");
		log.setVerboseLevel(LogLevel.INFO);
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
		log.setVerboseLevel(LogLevel.WARN);
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
		
		log.logPacket(LogLevel.INFO, request, writePacket, false, "server");
		
		try {
			sendReceiveSocket.send(request);
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
    	
		try {
			TFTPTransaction transaction =
					new TFTPTransaction.TFTPSendTransaction(sendReceiveSocket,
							serverAddress, serverPort, args[1], true,
							Client.log);
			
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
			default:
				c.println(String.format(
						"File transfer failed. Unkown error occured: \"%s\"", 
						transaction.getState().toString()));
				break;
			
			}
		} catch (FileNotFoundException e) {
			c.println(String.format("File not found: \"%s\".", args[1]));
		}
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
		
		log.logPacket(LogLevel.INFO, request, readPacket, false, "server");
		
		try {
			sendReceiveSocket.send(request);
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		try {
			TFTPTransaction transaction =
					new TFTPTransaction.TFTPReceiveTransaction(
							sendReceiveSocket, serverAddress, serverPort,
							localFile, false, true, Client.log);

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
			case RECEIVED_BAD_PACKET:
				c.println("File transfer failed. Received invalid packet.");
				break;
			case SOCKET_IO_ERROR:
				c.println("File transfer failed. Socket IO error.");
				break;
			case TIMEOUT:
				c.println("File transfer failed. Timed out waiting for server.");
				break;
			default:
				c.println(String.format(
						"File transfer failed. Unkown error occured: \"%s\"", 
						transaction.getState().toString()));
				break;

			}
		} catch (FileNotFoundException e) {
			c.println(String.format("File not found: \"%s\".", args[1]));
		}
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
		log.log(LogLevel.INFO,"Setting up Client...");
		
		int serverPort = 69;
		LogLevel verboseLevel = LogLevel.WARN;
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
	    	log.log(LogLevel.FATAL, "Command line argument parsing failed.  Reason: " + exp.getMessage() );
		    System.exit(1);
	    }
	    
	    Client client = new Client(serverPort,verboseLevel,logFilePath);
	    
	    // Create console UI
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
	    
	    // Get the positional arguments and perform a transaction if one is specified
		String[] positionalArgs = line.getArgs();
		if (positionalArgs.length == 1) {
			// Assume that the argument is the server address
			try {
				client.setServerAddress(InetAddress.getByName(positionalArgs[0]));
			} catch (UnknownHostException e) {
				log.log(LogLevel.WARN, "Invalid server: \"" + positionalArgs[0] + "\"");
			}
		} else if (positionalArgs.length == 2) {
			// Source and destination files specified
			client.buildRequest(positionalArgs[0], positionalArgs[1], console);
		} else if (positionalArgs.length > 2) {
			// Too many arguments
			log.log(LogLevel.WARN,"Too many files specified, entering interactive mode.");
		}

		// Start console UI thread
		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}
