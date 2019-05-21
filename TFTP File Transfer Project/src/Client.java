import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


public class Client {
	/**
	 * This class is a single-threaded static implementation of a TFTP client.
	 * Command line arguments for the first data transfer are accepted
	 */
	private static DatagramSocket sendReceiveSocket;
	private static InetAddress serverAddress;
	private static int serverPort, replyPort;
	private static boolean verbose;
	private static String filename;
	
	/*
	 * Commented out for the moment because I don't think I need this.
	public Client(int request, InetAddress server, int port)
	{
		this.serverAddress = server;
		this.serverPort = port;
		
		if(verbose) System.out.println("Setting up send/receive socket.");
		try {	//Setting up the socket that will send/receive packets
			sendReceiveSocket = new DatagramSocket();
		} catch(SocketException se) { //If the socket can't be created
			se.printStackTrace();
			System.exit(1);
		}
	}
	*/
	
	public static void read()
	{
		/**
		 * Method to read a file from the server.  The Client must already have the
		 * server address and port #.  Runs a loop that waits for a data packet, then
		 * sends an ack packet back.
		 */
		
		if(verbose) {
			System.out.println("Reading from server file");
		}
		
		TFTPPacket.ACK ackPacket = new TFTPPacket.ACK(0);
		DatagramPacket sendPacket;
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(verbose) {
			System.out.println("File ready to be written! filename: "+filename);
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
			if(verbose) {
				System.out.println("Waiting for data packet");
			}
			
			data = new byte[TFTPPacket.MAX_SIZE];
			receivePacket = new DatagramPacket(data, data.length);
			try {
				sendReceiveSocket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if(verbose) {
				System.out.println("Recieved packet from server");
			}
			// Check Packet for correctness
		    
			try {
				dataPacket = new TFTPPacket.DATA(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				System.out.println("Not a DATA response to ack! :((((");
				e.printStackTrace();
				System.exit(0);
			}
			// Definitely data :)
			// Strip block number & port
			blockNum = dataPacket.getBlockNum();
			replyPort = receivePacket.getPort();
			// Check size? Less than 512 == done
			len = dataPacket.getData().length;
			if (len < 512) {
				moreToWrite = false;
			}
			if(verbose) {
				System.out.println("Received Packet:");
				System.out.println("Packet Type: DATA");
				System.out.println("Filename: "+filename);
				System.out.println("Block Number: "+blockNum);
				System.out.println("# of Bytes: "+len);
			}
			// Write into file
			try {
				fos.write(dataPacket.getData(),0,dataPacket.getData().length);
			} catch (IOException e) {
				System.out.println("Failed to write data to file!");
				e.printStackTrace();
				System.exit(0);
			}
			
			
			// Send Acknowledgement packet with block number
			ackPacket = new TFTPPacket.ACK(blockNum);
			sendPacket = new DatagramPacket(ackPacket.toBytes(), ackPacket.toBytes().length, serverAddress, replyPort);
			
			if(verbose) {
				System.out.println("ACK Packet Successfully Assembled");
			}
			
			// Send ack packet to server on serverPort
			if(verbose) {
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
			
			if(verbose && moreToWrite) {
				System.out.println("Waiting for Next DATA Block:");
			}
		}
		// All data received and writes performed and last ack sent
		if(verbose) {
			System.out.println("File transfer complete!");
		}
		
	}
	
	public static void write()
	{
		/**
		 * Method to write a file to the server.  The Client must already have the server
		 * address and port #.  Sends the initial WRQ, then runs a loop to transfer the 
		 * data to the server.
		 */
	    
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(verbose) {
			System.out.println("Successfully opened: "+filename);
		}
		
		TFTPPacket.DATA dataPacket;
	    DatagramPacket sendPacket, receivePacket;
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
	    
	    if(verbose) {
			System.out.println("Recieved packet from server.");
		}
	    
	    // Parse ACK for correctness
	    TFTPPacket.ACK ackPacket = null;
    	try {
			ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
		} catch (IllegalArgumentException e) {
			System.out.println("Not an ACK Packet! :((((");
			e.printStackTrace();
			System.exit(0);
		}
    	if (ackPacket.getBlockNum() == 0 ) {
			// Correct acks
			if(verbose) {
				System.out.println("Recieved ACK for block #0.  Starting data transfer...");
				replyPort = receivePacket.getPort();
			}
		} else {
			// Incorrect ack
			System.out.println("Wrong ACK response. Incorrect block number");
    		throw new IllegalArgumentException();
		}
    	
		replyPort = receivePacket.getPort();
		
		boolean moreToRead = true;
		while (moreToRead) {
			// Read data from file into data packet
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
		    
		    if(verbose) {
				System.out.println("Sending Packet:");
				System.out.println("Packet Type: DATA");
				System.out.println("Filename: "+filename);
				// Mode not Applicable
				System.out.println("Block Number: "+blockNum);
				System.out.println("# of Bytes: "+len);
			}
		    
		    try {
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
			// Wait for ACK
		    if(verbose) {
				System.out.println("Waiting for ACK packet...");
			}
		    
		    // New Receive total bytes
		    data = new byte[TFTPPacket.MAX_SIZE];
		    receivePacket = new DatagramPacket(data, data.length);
		    try {
	    		sendReceiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
		    
		    // Parse ACK for correctness
		    ackPacket = null;
	    	try {
				ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
				replyPort = receivePacket.getPort();
			} catch (IllegalArgumentException e) {
				System.err.println("Wrong Packet Recieved. Reason: Not an ackPacket");
				e.printStackTrace();
				System.exit(0);
			}
	    	if (ackPacket.getBlockNum() == blockNum ) {
				// Correct acks
			} else {
				// Incorrect ack
				System.err.println("Wrong ACK response. Reason: Incorrect block number");
	    		throw new IllegalArgumentException();
			}
	    	
	    	if(verbose) {
				System.out.println("Received Packet:");
				System.out.println("Packet Type: ACK");
				// N/A System.out.println("Filename: "+this.filename);
				// N/A System.out.println("Mode: "+this.Mode);
				System.out.println("Block Number: "+blockNum);
				// N/A System.out.println("# of Bytes: "+len);
			}
			
		}
		// All data is sent and last ACK received,
		// Close socket, quit
		System.out.println("File transfer complete!");
	}
	
	public static void buildRequest(String source, String dest)
	{
		/**
		 * Checks the specified filepaths (source and dest) to see which one is on the server.
		 * If source is the server file, creates a read request and calls read().  If dest is
		 * on the server, creates a write request and calls write().
		 * The IP of the server is take
		 */
		DatagramPacket sendPacket = null;
		replyPort = serverPort;
		
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
				serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			// Read Request
			TFTPPacket.RRQ readPacket = new TFTPPacket.RRQ(filepath, TFTPPacket.TFTPMode.NETASCII);
			sendPacket = new DatagramPacket(readPacket.toBytes(), readPacket.size(), serverAddress, serverPort);
			
			if(verbose) {
				System.out.println("Sending Packet");
				System.out.println("Packet Type: RRQ");
				System.out.println("Filename: "+filepath);
				System.out.println("Mode: "+readPacket.getMode().toString());
				// Block Number not Applicable
				System.out.println("# of Bytes: "+(sendPacket.getData().length-4));
			}
			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if(verbose ) {
				System.out.println("Request sent.  Waiting for response from server...");
			}
			
			filename = dest;
			
			read();
			
			
		} else if(dest.contains(":")) {		//Create and send a write request
			String split[] = dest.split(":");
			String addressString = split[0];
			String filepath = split[1];
			
			try {
				serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// Write request
			TFTPPacket.WRQ writePacket = new TFTPPacket.WRQ(filepath, TFTPPacket.TFTPMode.parseFromString("netascii"));
			sendPacket = new DatagramPacket(writePacket.toBytes(), writePacket.size(), serverAddress, serverPort);
			
			if(verbose) {
				System.out.println("Sending Packet");
				System.out.println("Packet Type: RRQ");
				System.out.println("Filename: "+filepath);
				System.out.println("Mode: "+writePacket.getMode().toString());
				// Block Number not Applicable
				System.out.println("# of Bytes: "+(sendPacket.getData().length-4));
			}
			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("Request sent.");
	    	
			filename = source;
			
			write();
		}
		
		else {	//If neither file is on the server, print an error message and quit.
			System.out.println("Error: neither file is on the server.  Please try another command.");
		}
	}

	public static void main(String[] args) {
		
		System.out.println("Setting up Client...");
		
		serverPort = -1;
		verbose = false;
		boolean finished = false;
		
		try {	//Setting up the socket that will send/receive packets
			sendReceiveSocket = new DatagramSocket();
		} catch(SocketException se) { //If the socket can't be created
			se.printStackTrace();
			System.exit(1);
		}
		
		// The file locations that we'll transfer data to/from.  The order of the filenames will determine
		// if this is a read or write request (Server file first = RRQ, server file last = WRQ).
		String source, dest;
		String[] split;
		String command = null;
		Scanner in = new Scanner(System.in);
		
		//Setting up the parsing options
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );
		
		Option serverPortOption = Option.builder("sp").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();
		
		Options options = new Options();
		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		
		CommandLine line = null;
		
		CommandLineParser parser = new DefaultParser();
	    try {
	        // parse the command line arguments
	        line = parser.parse( options, args );
	        
	        if( line.hasOption("verbose")) {
		        verbose = true;
		    }
	        
	        if( line.hasOption("sp")) {
		        serverPort = Integer.parseInt(line.getOptionValue("sp"));
		    } else {
		    	//Continually ask for a server port number until one is specified
		    	while(serverPort < 1) {
		    		System.out.println("Enter the well-known server port number for requests (Port number must be positive)");
		    		System.out.print(">> ");
		    		command = in.nextLine();
		    		split = command.split("\\s+");
		    		if(split[0].toLowerCase().equals("shutdown")) {
						if(verbose) {
							System.out.println("Closing socket and scanner, and shutting down server.");
						}
						sendReceiveSocket.close();
						in.close();
						System.exit(0);
					}
		    		try {
		    			serverPort = Integer.valueOf(split[0]);
		    		} catch(NumberFormatException e) {
		    			System.out.println("No integer values found.  Please enter an integer value.");
		    		}
		    	}
		    }
	    } catch( ParseException exp ) {
	    	System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
		    System.exit(1);
	    }
	    
	    //Gets anything remaining in the command line and initializes source and dest
		split = line.getArgs();
		source = null;
		dest = null;
		
		//Sends a prompt to provide filenames if none were given
		if(split.length < 2) {
			while(source == null || dest == null) {
				System.out.print(">> ");
				command = in.nextLine();
				split = command.split("\\s+");
				
				if(split[0].toLowerCase().equals("help")) {
					System.out.println("Enter a request in the format: sourceFilePath destinationFilePath");
					System.out.println("	For RRQ, sourceFilePath should be the server file.  For WRQ, destinationFilePath should be the server file.");
					System.out.println("	Ex. for a RRQ: 123.456.7.89:/Users/name/source.txt /Users/name/destination");
					System.out.println("Enter 'shutdown' to close client.");
					System.out.println("Enter 'verbose' to display more information while transferring");
					System.out.println("Enter 'quiet' to exit verbose mode");
				}
				if(split[0].toLowerCase().equals("verbose")) {
					System.out.println("Running in verbose mode.");
					verbose = true;
				}
				if(split[0].toLowerCase().equals("quiet")) {
					System.out.println("Running in quiet mode.");
					verbose = false;
				}
				if(split[0].toLowerCase().equals("shutdown")) {
					if(verbose) {
						System.out.println("Closing socket and scanner, and shutting down server.");
					}
					sendReceiveSocket.close();
					in.close();
					System.exit(0);
				}
				
				if(split.length == 2) {
					source = split[0];
					dest = split[1];
				}
			} 
		} else {
			source = split[0];
			dest = split[1];
		}
		
		
		/*
		 * Calls the buildRequest function
		 */
		buildRequest(source, dest);
		
		/*
		 * Loops until the client is shut down.  Prompts the user for filepaths, server port
		 * changes, and the verbose option when the previous transfer has finished, then calls
		 * buildRequest() to send the request.
		 */
		//Currently there is no way to set finished to true, but this might be used in the future.
		while(!finished) {
			source = null;
			dest = null;
			System.out.print(">> ");
			command = in.nextLine();
			split = command.split("\\s+");
			
			if(split[0].toLowerCase().equals("help")) {
				System.out.println("Enter a request in the format: sourceFilePath destinationFilePath");
				System.out.println("	For RRQ, sourceFilePath should be the server file.  For WRQ, destinationFilePath should be the server file.");
				System.out.println("	Ex. for a RRQ: 123.456.7.89:/Users/name/source.txt /Users/name/destination");
				System.out.println("Enter 'shutdown' to close client.");
				System.out.println("Enter 'verbose' to display more information while transferring");
				System.out.println("Enter 'quiet' to exit verbose mode");
			}
			if(split[0].toLowerCase().equals("verbose")) {
				System.out.println("Running in verbose mode.");
				verbose = true;
			}
			if(split[0].toLowerCase().equals("quiet")) {
				System.out.println("Running in quiet mode.");
				verbose = false;
			}
			if(split[0].toLowerCase().equals("shutdown")) {
				if(verbose) {
					System.out.println("Closing socket and scanner, and shutting down server.");
				}
				sendReceiveSocket.close();
				in.close();
				System.exit(0);
				break;
			}
			
			if(split.length == 2) {
				source = split[0];
				dest = split[1];
				buildRequest(source, dest);
			}
			
		}
		
		//Closes the socket once the client has been shut down
		if(verbose) {
			System.out.println("Closing socket and scanner, and shutting down server.");
		}
		sendReceiveSocket.close();
		in.close();
	}
}
