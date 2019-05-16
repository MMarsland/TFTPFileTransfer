import java.io.IOException;
import java.net.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Client {

	private static DatagramSocket sendReceiveSocket;
	private static InetAddress serverAddress;
	private static int serverPort;
	private static boolean verbose;
	
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
	
	public void read()
	{
		
	}
	
	public void write()
	{
		
	}

	public static void main(String[] args) {
		
		System.out.println("Setting up Client...");
		
		byte sendData[] = new byte[516];
		byte data[] = new byte[516];
		byte mode[] = "netascii".getBytes();
		DatagramPacket sendPacket, receivePacket;
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
		
		/*
		 * Checking which file (source or dest) is on the server to determine the type of
		 * request.
		 * Also recording the IP address of the server from the path to the server file and
		 * building the packet bytes
		 */
		if(source.contains(":")) {
			sendData[0] = (byte)0x0;
			sendData[1] = (byte)0x1;
			int index = source.indexOf(":");
			String addressString = source.substring(0, index);
			String filepath = source.substring(index);
			try {
				serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			//Adding the server-side filename and mode bytes to the packet bytes
			byte filepathBytes[] = filepath.getBytes();
			int byteIndex = 2;
			for(int i = 0; i < filepathBytes.length; i++) {
				sendData[byteIndex] = filepathBytes[i];
				byteIndex++;
			}
			sendData[byteIndex] = 0;
			for(int i = 0; i < mode.length; i++) {
				sendData[byteIndex] = mode[i];
				byteIndex++;
			}
			sendData[byteIndex] = 0;
			
		} else if(dest.contains(":")) {
			sendData[0] = (byte)0x0;
			sendData[1] = (byte)0x2;
			int index = dest.indexOf(":");
			String addressString = dest.substring(0, index);
			String filepath = dest.substring(index);
			try {
				serverAddress = InetAddress.getByName(addressString);
			} catch(UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
			//Adding the server-side filename and mode bytes to the packet bytes
			byte filepathBytes[] = filepath.getBytes();
			int byteIndex = 2;
			for(int i = 0; i < filepathBytes.length; i++) {
				sendData[byteIndex] = filepathBytes[i];
				byteIndex++;
			}
			sendData[byteIndex] = 0;
			for(int i = 0; i < mode.length; i++) {
				sendData[byteIndex] = mode[i];
				byteIndex++;
			}
			sendData[byteIndex] = 0;
		}
		else {	//If neither file is on the server, print an error message and quit.
			System.out.println("Error: neither file is on the server.  Terminating process");
			System.exit(1);
		}
		
		System.out.println("Client running.");
		/*
		 * Commented out in case I need/want to add this stuff later
		System.out.println("Enter a command in the format: requestType sourceFilePath destinationFilePath");
		System.out.println("Enter 'shutdown' to close client.");
		command = in.nextLine();
		split = command.split("\\s+");
		
		if(split[0].toLowerCase().equals("shutdown")) {
			sendReceiveSocket.close();
			in.close();
			System.exit(0);
		}
		*/
		
		//Creating and sending the request packet
		sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
		System.out.println("Sending request.");
		try {
			sendReceiveSocket.send(sendPacket);
		} catch(IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("Packet Sent.  Waiting for response from server...");
		
		/*
		 * This loop will run until the client is terminated (hopefully when the transfer is done)
		 * It waits for a packet, parses it to determine how it should respond (switch statement),
		 * and sends the response.  It is assumed that no errors occur for this iteration, so a timeout
		 * has not been implemented and there are no checks for duplicate Ack packets.
		 */
		while(!finished) {
			DatagramPacket lastPacket = sendPacket;
			data = new byte[512];
			receivePacket = new DatagramPacket(data, data.length);
			try {
				//Blocks until it receives a packet.
				sendReceiveSocket.receive(receivePacket);
			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			//Determining the type of packet it is
			switch(data[1]) {
			case 3:	
				//Received a data packet, send back an Ack packet
				break;
			case 4:
				//Received an Ack packet, send back a Data packet
				break;
			case 5:
				//Received an Error packet, re-send last packet(?)
				break;
			default: 
				//Received something unexpected, throw an exception and quit
				System.out.println("Unexpected response from server.");
				
				//There will be some logic to switch finished to true once the server sends
				//a certain message.
			}
			
		}
		
		sendReceiveSocket.close();
	}
}
