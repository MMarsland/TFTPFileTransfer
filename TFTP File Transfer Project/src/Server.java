import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
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
		byte data[] = new byte[TFTPPacket.MAX_SIZE];
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
	    	if(verbose) {
	    		System.out.println("Creating a response handler for this connection");
	    	}
	    	
	    	// Parse the packet to determine the type of handler required
	    	TFTPPacket request = null;
	    	try {
				request = TFTPPacket.parse(Arrays.copyOf(receivePacket.getData(), receivePacket.getLength()));
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				System.exit(0);
			}
	    	// Create a handler thread
			if (request instanceof TFTPPacket.RRQ) {
				ReadHandler handler = new ReadHandler(receivePacket, request, verbose);
				Thread handlerThread = new Thread(handler);
				handlerThread.start();
	    	} else if (request instanceof TFTPPacket.WRQ) {
	    		WriteHandler handler = new WriteHandler(receivePacket, request, verbose);
				Thread handlerThread = new Thread(handler);
				handlerThread.start();
	    	} else {
	    		// Not the right first request type..
	    		System.out.println("Unexpected first request packet... Not Read or Write!");
	    		throw new IllegalArgumentException();
	    	}
			
	    	// Return to listening for new requests
	    }
	}
	
	public void kill()
	{
	    receiveSocket.close();
	}
}

abstract class RequestHandler implements Runnable {
	protected DatagramSocket sendReceiveSocket;
	protected DatagramPacket receivePacket;
	protected boolean verbose;
	protected int clientTID;
	protected InetAddress clientAddress;
	protected String filename;
	
	
	public abstract void run();
	
}

class ReadHandler extends RequestHandler implements Runnable {

	protected TFTPPacket.RRQ request;
	protected int blockNum = 0;
	
	public ReadHandler(DatagramPacket receivePacket, TFTPPacket request, boolean verbose) {
		if(verbose) {
			System.out.println("Setting up Read Handler");
		}
		this.receivePacket = receivePacket;
		this.request = (TFTPPacket.RRQ) request;
		this.verbose = verbose;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();
		
		//Set up the socket that will be used to send/receive packets to/from client
		try { 
			this.sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
	
	}

	public void run(){
		if(this.verbose) {
			System.out.println("Handling Read Request");
		}
		// Inspect read request
		
		TFTPPacket.DATA dataPacket;
	    DatagramPacket sendPacket;
		
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(this.filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		} 
		if(this.verbose) {
			System.out.println("File Successfully Opened");
		}
		boolean moreToRead = true;
		while (moreToRead) {
			// READ DATA FROM FILE into data bytes{
			byte[] data = new byte[512];
		    int len = 69999; 
		    this.blockNum++;
		    this.blockNum = this.blockNum % (2^16);
		    if(this.verbose) {
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
		    if(this.verbose) {
				System.out.println("Block #"+blockNum+"has "+len+" data data bytes!");
			}
			// Assemble data packet
		    dataPacket = new TFTPPacket.DATA(this.blockNum, data);
		    sendPacket = new DatagramPacket(dataPacket.toBytes(), dataPacket.toBytes().length, clientAddress, clientTID);
		    if(this.verbose) {
				System.out.println("Data Packet Successfully Assembled");
			}
		    // Send data packet to client on Client TID
		    if(this.verbose) {
				System.out.println("Sending Data Packet");
			}
		    try {
	    		sendReceiveSocket.send(sendPacket);
	    	} catch (IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
			// Wait for ACK
		    if(this.verbose) {
				System.out.println("Waiting for ack packet");
			}
		    data = new byte[TFTPPacket.MAX_SIZE];
		    DatagramPacket ackPacket = new DatagramPacket(data, data.length);
		    try {
	    		sendReceiveSocket.receive(ackPacket);
	    	} catch(IOException e) {
	    		e.printStackTrace();
    			System.exit(1);
	    	}
		    if(this.verbose) {
				System.out.println("Recieved packet from client");
			}
		    // Parse ACK for correctness
		    TFTPPacket response = null;
	    	try {
				response = TFTPPacket.parse(Arrays.copyOf(ackPacket.getData(), ackPacket.getLength()));
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
				System.exit(0);
			}
	    	// Create a handler thread
			if (response instanceof TFTPPacket.ACK) {
				// Check for matching block number
				if (((TFTPPacket.ACK) response).getBlockNum() == this.blockNum ) {
					// Correct ack
					if(this.verbose) {
						System.out.println("Recieved ack for block #"+((TFTPPacket.ACK) response).getBlockNum());
					}
				} else {
					// Incorrect ack
					System.out.println("Wrong ACK response. Incorrect block number");
		    		throw new IllegalArgumentException();
				}
	    	} else {
	    		// Not and ACK type..
	    		System.out.println("Not a ack response to data! :((((");
	    		throw new IllegalArgumentException();
	    	}
			// If more data, or exactly 0 send more packets
			// Wait for ACK
			// ...
			if(this.verbose && moreToRead) {
				System.out.println("Repeating data transfer");
			}
		}
		// All data is sent and last ACK received,
		// Close socket, quit
		if(this.verbose) {
			System.out.println("Data Transefer complete! closing socket.");
		}
		sendReceiveSocket.close();
		if (this.verbose) {
			System.out.println("Closing Read Handler");
		}
	}
}


class WriteHandler extends RequestHandler implements Runnable {

	protected TFTPPacket.WRQ request;
	protected TFTPPacket.TFTPMode mode;
	
	public WriteHandler(DatagramPacket receivePacket, TFTPPacket request, boolean verbose) {
		if(verbose) {
			System.out.println("Setting up Write Handler");
		}
		this.receivePacket = receivePacket;
		this.request = (TFTPPacket.WRQ) request;
		this.verbose = verbose;
		this.clientTID = this.receivePacket.getPort();
		this.clientAddress = this.receivePacket.getAddress();
		this.filename =  this.request.getFilename();
		this.mode = this.request.getMode();
		
		//Set up the socket that will be used to send/receive packets to/from client
		try { 
			this.sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }
		
		
	}

	public void run(){
		if(this.verbose) {
			System.out.println("Handling Write Request");
		}
		// Send first Ack Package
	    TFTPPacket.ACK firstAck = new TFTPPacket.ACK(0);
	    DatagramPacket sendPacket = new DatagramPacket(firstAck.toBytes(), firstAck.toBytes().length, clientAddress, clientTID);
	    // Send data packet to client on Client TID
	    try {
    		sendReceiveSocket.send(sendPacket);
    	} catch (IOException e) {
    		e.printStackTrace();
			System.exit(1);
    	}
	    
	    // Receive data and send acks
		boolean moreToWrite = true;
		while (moreToWrite) {
			// Receive Data Packet
			// Strip block number
			// Check size? Less than 512 == done
			// Write into file &
			// Send Acknowledgement packet with block number
		}
		// All writes performed and last ack sent
		// Close socket, quit
		sendReceiveSocket.close();
		if (this.verbose) {
			System.out.println("Closing Write Handler");
		}
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
