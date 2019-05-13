import java.net.*;
import java.util.Scanner;
import java.io.IOException;

public class Client extends Thread {

	private DatagramSocket sendReceiveSocket;
	private InetAddress serverAddress;
	private int serverPort;
	private boolean verbose;
	
	public Client(boolean verbose, InetAddress server, int port)
	{
		this.serverAddress = server;
		this.serverPort = port;
		this.verbose = verbose;
		
		if(verbose) System.out.println("Setting up send/receive socket.");
		try {	//Setting up the socket that will send/receive packets
			sendReceiveSocket = new DatagramSocket();
		} catch(SocketException se) { //If the socket can't be created
			se.printStackTrace();
			System.exit(1);
		}
	}
	
	public void run()
	{
		byte sendData[] = new byte[516];
		byte data[] = new byte[516];
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		boolean transfer = false;
		
		String readTo, writeFrom;  //The client-side file location that we'll transfer data to/from
		
		Scanner in = new Scanner(System.in);  //Scanner for inputting commands
		String command, msg;
		String[] split;
		
		while(!transfer) {  //While not transferring, scan for and handle commands
			System.out.println("Enter a command in the format: requestType sourceFilePath destinationFilePath");
			System.out.println("Enter 'shutdown' to close client.");
			command = in.nextLine();
			split = command.split("\\s+");
			
			if(split[0].toLowerCase().equals("shutdown")) {
				this.interrupt();
				System.out.println("Waiting for client to stop...");
				while(this.isAlive()) {}
				System.out.println("Shutting down.");
				System.exit(0);
			} else if(split[0].toLowerCase().equals("read") || split[0].toLowerCase().equals("r")) {
				sendData[0] = (byte)0x0;
				sendData[1] = (byte)0x1;
				readTo = split[1];
			} else if(split[0].toLowerCase().equals("write") || split[0].toLowerCase().equals("w")) {
				sendData[0] = (byte)0x0;
				sendData[1] = (byte)0x2;
				writeFrom = split[1];
			}
			
		}
		
		
		//sendpacket for later in the code
		//DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
		
	}

	public static void main(String[] args) {
		
		System.out.println("Client running");
		
	}
}
