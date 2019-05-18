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
	private static int serverPort;
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
		 * server address and port #.  Sends the initial WRQ, then runs a loop to
		 * transfer the data from the server.
		 */
		
		if(verbose) {
			System.out.println("Reading from server file");
		}
		// Send first Ack Package
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
				System.out.println("Recieved data packet from client");
			}
			// Check Packet for correctness
		    
			try {
				int length = receivePacket.getLength();
				System.out.println("The receive packet has length: "+length);
				System.out.println("And looks like this: "+receivePacket.getData()[0]+", "+receivePacket.getData()[1]+", "+receivePacket.getData()[2]+", "+receivePacket.getData()[3]+", "+receivePacket.getData()[4]);
				dataPacket = new TFTPPacket.DATA(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				System.out.println("Not a data response to ack! :((((");
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
			if(verbose) {
				System.out.println("Data block #"+blockNum+" has "+len+" bytes!");
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
			sendPacket = new DatagramPacket(ackPacket.toBytes(), ackPacket.toBytes().length, serverAddress, serverPort);
			
			if(verbose) {
				System.out.println("Ack Packet Successfully Assembled");
			}
			
			// Send ack packet to server on serverPort
			if(verbose) {
				System.out.println("Sending ack Packet");
			}
			
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			if(verbose && moreToWrite) {
				System.out.println("Waiting for Next Data Block:");
			}
		}
		// All data received and writes performed and last ack sent
		if(verbose) {
			System.out.println("File transfer complete!");
		}
		// Close socket, quit
		sendReceiveSocket.close();
		if (verbose) {
			System.out.println("Closing Write Handler");
		}
		
	}
	
	public static void write()
	{
		/**
		 * Method to write a file to the server.  The Client must already have the server
		 * address and port #.  Sends the initial WRQ, then runs a loop to transfer the 
		 * data to the server.
		 */
		if(verbose) {
			System.out.println("Writing to server file");
		}
	    
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(verbose) {
			System.out.println("File Successfully Opened! filename: "+filename);
		}
		
		TFTPPacket.DATA dataPacket;
	    DatagramPacket sendPacket;
	    DatagramPacket receivePacket;
	    byte[] data = new byte[512];
	    int len = 69999;
		int blockNum = 0;
	    
		boolean moreToRead = true;
		while (moreToRead) {
			// Read data from file into data packet
		    blockNum++;
		    blockNum = blockNum & 0xFFFF;
		    if(verbose) {
				System.out.println("Reading Block Number #"+blockNum);
			}
		    try {
		    	if ((len=fis.read(data,0,512)) < 512) {
		    		moreToRead = false;
					fis.close();
		    	}
		    	// Shrink wrap size based on len
		    	data = Arrays.copyOf(data, len);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(0);
			}
		    
		    if(verbose) {
				System.out.println("Block #"+blockNum+" has "+len+" data bytes!");
			}
		    
			// Assemble data packet
		    dataPacket = new TFTPPacket.DATA(blockNum, data);
		    sendPacket = new DatagramPacket(dataPacket.toBytes(), dataPacket.toBytes().length, serverAddress, serverPort);
		    
		    if(verbose) {
				System.out.println("Data Packet Successfully Assembled");
			}
		    
		    // Send data packet to client on Client TID
		    if(verbose) {
				System.out.println("Sending Data Packet");
			}
		    
		    try {
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
			// Wait for ACK
		    if(verbose) {
				System.out.println("Waiting for ack packet");
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
		    
		    if(verbose) {
				System.out.println("Recieved packet from client");
			}
		    
		    // Parse ACK for correctness
		    TFTPPacket.ACK ackPacket = null;
	    	try {
				ackPacket = new TFTPPacket.ACK(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				System.out.println("Not a ack ackPacket to data! :((((");
				e.printStackTrace();
				System.exit(0);
			}
	    	if (ackPacket.getBlockNum() == blockNum ) {
				// Correct acks
				if(verbose) {
					System.out.println("Recieved ack for block #"+((TFTPPacket.ACK) ackPacket).getBlockNum());
				}
			} else {
				// Incorrect ack
				System.out.println("Wrong ACK response. Incorrect block number");
	    		throw new IllegalArgumentException();
			}
	    	
			// If more data, or exactly 0 send more packets
			// Wait for ACK
			// ...
			
			if(verbose && moreToRead) {
				System.out.println("Sending Next Data Block: ");
			}
			
		}
		// All data is sent and last ACK received,
		// Close socket, quit
		if(verbose) {
			System.out.println("Write request complete!");
		}
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
		
		/*
		 * Checking which file (source or dest) is on the server to determine the type of
		 * request.
		 * Also recording the IP address of the server from the path to the server file and
		 * building the packet bytes
		 */
		if(source.contains(":")) {		//Create and send a read request
			int index = source.indexOf(":");
			String addressString = source.substring(0, index);
			String filepath = source.substring(index);
			try {
				serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			// Read Request
			TFTPPacket.RRQ readPacket = new TFTPPacket.RRQ(filepath, TFTPPacket.TFTPMode.NETASCII);
			sendPacket = new DatagramPacket(readPacket.toBytes(), readPacket.size(), serverAddress, serverPort);
			
			System.out.println("Sending request.");
			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("Packet Sent.  Waiting for response from server...");
			
			read();
			
			
		} else if(dest.contains(":")) {		//Create and send a write request
			int index = dest.indexOf(":");
			String addressString = dest.substring(0, index);
			String filepath = dest.substring(index);
			try {
				serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			// Write request
			TFTPPacket.WRQ writePacket = new TFTPPacket.WRQ(filepath, TFTPPacket.TFTPMode.NETASCII);
			sendPacket = new DatagramPacket(writePacket.toBytes(), writePacket.size(), serverAddress, serverPort);
			
			System.out.println("Sending request.");
			try {
				sendReceiveSocket.send(sendPacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			System.out.println("Packet Sent.  Waiting for response from server...");
			
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
		    }
	    } catch( ParseException exp ) {
	    	System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
		    System.exit(1);
	    }
	    
	    //Not sure if this works or not
		String[] split = line.getArgs();
		source = split[0];
		dest = split[1];
		
		//Sends a prompt to provide filenames if none were given
		String command = null;
		Scanner in = new Scanner(System.in);
		if(split[0] == null || split[1] == null) {
			System.out.println("Enter a command in the format: sourceFilePath destinationFilePath");
			System.out.println("For RRQ, sourceFilePath should be the server file.  For WRQ, destinationFilePath should be the server file.");
			System.out.println("Enter 'shutdown' to close client.");
			command = in.nextLine();
			split = command.split("\\s+");
			
			source = split[0];
			dest = split[1];
			
			if(split[0].toLowerCase().equals("shutdown")) {
				if(verbose) {
					System.out.println("Closing socket and scanner, and shutting down server.");
				}
				sendReceiveSocket.close();
				in.close();
				System.exit(0);
			}
		}
		/* 
		 * Prompts user for the port number and verbose option if they were not specified
		 * in the command line arguments.  "Shutdown" can also be entered in response to these
		 * prompts.
		 */
		if(serverPort == -1) {
	    	System.out.println("Enter the server port number");
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
	    	serverPort = Integer.valueOf(split[0]);
	    }
		if(verbose == false) {
			System.out.println("Verbose?");
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
			if(split[0].toLowerCase().equals("y") || command.toLowerCase().equals("yes")) {
				verbose = true;
			}
		}
		
		/*
		 * Calls the buildRequest function
		 */
		buildRequest(source, dest);
		
		System.out.println("Client Idle.");
		
		/*
		 * Loops until the client is shut down.  Prompts the user for filepaths, server port
		 * changes, and the verbose option when the previous transfer has finished, then calls
		 * buildRequest() to send the request.
		 */
		//Currently there is no way to set finished to true, but this might be used in the future.
		while(!finished) {
			verbose = false;
			System.out.println("Enter a command in the format: requestType sourceFilePath destinationFilePath");
			System.out.println("For RRQ, sourceFilePath should be the server file.  For WRQ, destinationFilePath "
									+ "should be the server file.");
			System.out.println("Enter 'shutdown' to close client.");
			command = in.nextLine();
			split = command.split("\\s+");
			
			if(split[0].toLowerCase().equals("shutdown")) {
				if(verbose) {
					System.out.println("Closing socket and scanner, and shutting down server.");
				}
				sendReceiveSocket.close();
				in.close();
				System.exit(0);
				break;
			}
			
			source = split[0];
			dest = split[1];
			
			if(verbose == false) {
				System.out.println("Verbose?");
				command = in.nextLine();
				if(command.toLowerCase().equals("y") || command.toLowerCase().equals("yes")) {
					verbose = true;
				}
			}
			
			buildRequest(source, dest);
		}
		
		//Closes the socket once the client has been shut down
		if(verbose) {
			System.out.println("Closing socket and scanner, and shutting down server.");
		}
		sendReceiveSocket.close();
		in.close();
	}
}
