import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

class Listener implements Runnable {
	private DatagramSocket sendReceiveSocket, receiveSocket;
	private int listenerPort, serverPort;
	private InetAddress serverAddress;
	private boolean verbose;
	
	public Listener(int listenerPort, InetAddress server, int serverPort, boolean verbose) {
		this.listenerPort = listenerPort;
		this.serverPort = serverPort;
		this.verbose = verbose;
		serverAddress = server;
		
		if(verbose) {
			System.out.println("Setting up send/receive socket");
		}
		
		try { //Set up the socket that will be used to send/receive packets to/from server
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		if(verbose) {
			System.out.println("Setting up receive socket on port" + listenerPort);
		}
		
		try { //Set up the socket that will be used to receive packets from client
			receiveSocket = new DatagramSocket(listenerPort);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	}
	
	public void run(){
		byte data[] = new byte[100]; //<--- THIS SIZE SHOULD BE CHANGED, NEED TO DECIDE WHAT IT SHOULD BE
	    DatagramPacket receivePacket = new DatagramPacket(data, data.length);
	    DatagramPacket sendPacket;
	    InetAddress clientAddress;
	    int clientPort;
	    
	    while(!Thread.interrupted()) {
	    	if(verbose) {
	    		System.out.println("Waiting for client...");
	    	}
	    
	    	try { //Wait for a packet to come in from the client.
	    		receiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		e.printStackTrace();
	    		System.exit(1);
	    	}
	    
	    	//Keep the client address and port number for the response later
	    	clientAddress = receivePacket.getAddress();
	    	clientPort = receivePacket.getPort();
	    
	    	sendPacket = new DatagramPacket(data, receivePacket.getLength(), serverAddress, serverPort);
	    
	    	if(verbose) {
	    		System.out.println("Forwarding data to server...");
	    	}
	    
	    	try { //Send the packet to the server
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		e.printStackTrace();
	    		System.exit(1);
	    	}
	    
	    	if(verbose) {
	    		System.out.println("Waiting for server...");
	    	}
	    
	    	try { //Wait for a packet to come in from the Server
	    		sendReceiveSocket.receive(receivePacket);
	    	} catch(IOException e) {
	    		e.printStackTrace();
	    		System.exit(1);
	    	}
	    
	    	sendPacket = new DatagramPacket(data, receivePacket.getLength(), clientAddress, clientPort);
	    
	    	if(verbose) {
	    		System.out.println("Forwarding data to client...");
	    	}
	    
	    	try { //Send the packet to the client
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		e.printStackTrace();
	    		System.exit(1);
	    	}
	    }
	    
	    System.out.println("Listener thread stopping");
	    sendReceiveSocket.close();
	    receiveSocket.close();
	    return;
	}	
}

public class ErrorSim {

	public static void main(String[] args) {
		
		System.out.println("Error Simulator Running"); 
		
		InetAddress localHost = null;
		try {
			localHost = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		Thread listener = new Thread(new Listener(23, localHost, 69, true));
		listener.start();
		
		Scanner in = new Scanner(System.in);
		String command;
		String[] split;
		
		//This is the UI thread. It handles commands from the user
		while(true) {
			command = in.nextLine();
			split = command.split("\\s+");
			
			//Handle the shutdown command
			if(split[0].toLowerCase().equals("shutdown")) {
				if(split.length > 1) {
					System.out.println("Error: Too many parameters.");
				}
				else {
					listener.interrupt();
					System.out.println("Waiting for listener to stop...");
					while(listener.isAlive()) {}
					System.out.println("Shutting down.");
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