import java.net.*;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;

public class Client {

	private static DatagramSocket sendReceiveSocket;
	private static InetAddress serverAddress;
	private static int serverPort;
	private static boolean verbose;
	
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
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		
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
	    } catch( ParseException exp ) {
	    	System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
		    System.exit(1);
		}
	    
		Scanner in = new Scanner(System.in);  //Scanner for inputting commands
		String command;
		String[] split;
		
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
		sendData[0] = (byte)0x0;
		sendData[1] = (byte)0x1;
		source = split[1];
		dest = split[2];
		
		//DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
	}
}
