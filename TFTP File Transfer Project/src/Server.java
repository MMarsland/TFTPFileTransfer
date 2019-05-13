import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

class ServerListener implements Runnable {
	private DatagramSocket  receiveSocket;
	private int listenerPort;
	private boolean verbose;
	
	public ServerListener(int listenerPort, boolean verbose) {
		this.listenerPort = listenerPort;
		this.verbose = verbose;
		
		if(verbose) {
			System.out.println("Setting up receive socket on port " + listenerPort);
		}
		
		// Set up the socket that will be used to receive packets from clients (or error simulators)
		try { 
			receiveSocket = new DatagramSocket(listenerPort);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	}
	
	public void run(){
		byte data[] = new byte[516];
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);
	    DatagramPacket sendPacket;
	    InetAddress clientAddress;
	    int clientPort;
	    
	    while(!Thread.interrupted()) {
	    	if(verbose) {
	    		System.out.println("Waiting for packet from client on port 69...");
	    	}
	    	
	    	// Wait for a packet to come in from the client.
	    	try { 	    		
	    		receiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		if(e.getMessage().equals("socket closed")) {
	    			System.exit(0);
	    		}
	    		e.printStackTrace();
    			System.exit(1);
	    	}
	    
	    	// Keep the client address and port number for the response later
	    	clientAddress = receivePacket.getAddress();
	    	clientPort = receivePacket.getPort();
	    
	    	
	    	// Parse Packet type to determine what type of thread to make.. (or if to make one)
	    	if(verbose) {
	    		System.out.println("Creating New thread for this connection");
	    	}
	    	// New Thread with new TID port
	    	// Create a connection thread
	    	// Return to listening for new requests
	    }
	}
	
	public void kill()
	{
	    receiveSocket.close();
	}
}


public class Server {
	
	public static void main(String[] args) throws InterruptedException {
		
		System.out.println("Starting Server..."); 
		
		InetAddress localHost = null;
		try {
			localHost = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		ServerListener listener = new ServerListener(69, true);
		Thread listenerThread = new Thread(listener);
		listenerThread.start();
		
		Scanner in = new Scanner(System.in);
		String command;
		String[] split;
		
		// This is the main UI thread. It handles commands from the user
		while(true) {
			System.out.print(">> ");
			command = in.nextLine();
			split = command.split("\\s+");
			
			// Handle the shutdown command
			if(split[0].toLowerCase().equals("shutdown")) {
				if(split.length > 1) {
					System.out.println("Error: Too many parameters.");
				}
				else {
					listener.kill();
					/*try {
						listenerThread.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}*/
					System.out.println("Server Shutting Down...");
					in.close();
					System.exit(0);
				}
			}
			//Handle commands that do not exist
			else {
				System.out.println("Invalid command.");
			}
		}
	}
}
