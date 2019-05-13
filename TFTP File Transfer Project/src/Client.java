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
		byte data[] = new byte[516];
		byte sendData[] = new byte[516];
		DatagramPacket receivePacket = new DatagramPacket(data, data.length);
		boolean transfer = false;
		
		Scanner in = new Scanner(System.in);  //Scanner for inputting commands
		String command;
		String[] split;
		
		while(!transfer) {  //While not transferring, scan for and handle commands
			System.out.println("Enter a command in the format: requestType sourceFile destinationFile");
			System.out.println("Enter 'shutdown' to close client.");
			command = in.nextLine();
			split = command.split("\\s+");
			
			if(split[0].toLowerCase().equals("shutdown")) {
				this.interrupt();
				System.out.println("Waiting for client to stop...");
				while(this.isAlive()) {}
				System.out.println("Shutting down.");
				System.exit(0);
			}
			
		}
		
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
		
		
	}
	
	public static void main(String[] args) {
		
		System.out.println("Client running");
		
	}
}
