/**
 * ErrorSim is the intermediate host between the TFTP server and client.
 */
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.cli.*;

/**
 * Errors class stores all errors currently pending creation
 */
class Errors {
	private LinkedList<ErrorInstruction> errors = new LinkedList<ErrorInstruction>();
	private LinkedList<ErrorInstruction> errorsBackup = new LinkedList<ErrorInstruction>();

	/**
	 * Adds a new ErrorInstruction to the error simulators already pending errors
	 * @param error the new error to add
	 * @return true if the error was added, false if it already exists
	 */
	public synchronized boolean add(ErrorInstruction error) {

		//Don't allow adding duplicate commands
		for(ErrorInstruction ei : errors) {
			if(ei.equals(error)) {
				return false;
			}
		}

		errors.add(error);
		return true;
	}

	/**
	 * Removes an error from the pending errors
	 * @param error the error to remove
	 * @return true if the error was removed, false if it was not found
	 */
	public synchronized boolean remove(ErrorInstruction error) {
		return errors.remove(error);
	}

	/**
	 * Removes an error from the pending errors
	 * @param error the error number to remove
	 * @return true if the error was removed, false if it was not found
	 */
	public synchronized boolean remove(int errornum) {
		if(errornum <= errors.size() && errornum >= 0) {
			return errors.remove(errornum-1) != null;
		}
		return false;
	}

	/**
	 * Checks a packet to see if any of the pending errors are applicable to it
	 * @param packet the packet to check
	 * @return null if no errors apply to the packet, or the ErrorInstruction that does
	 */
	public synchronized ErrorInstruction checkPacket(DatagramPacket packet) {
		if(errors.size() == 0) {
			return null;
		}

		TFTPPacket parsedPacket = TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength()));

		int i;
		for(i = 0; i < errors.size(); i++) {
			ErrorInstruction ei = errors.get(i);

			if(ei.packetType == ErrorInstruction.packetTypes.RRQ && parsedPacket instanceof TFTPPacket.RRQ) {
				if(ei.skipped == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.WRQ && parsedPacket instanceof TFTPPacket.WRQ) {
				if(ei.skipped == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.DATA && parsedPacket instanceof TFTPPacket.DATA) {
				TFTPPacket.DATA data = (TFTPPacket.DATA)parsedPacket;
				if(data.getBlockNum() == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.ACK && parsedPacket instanceof TFTPPacket.ACK) {
				TFTPPacket.ACK ack = (TFTPPacket.ACK)parsedPacket;
				if(ack.getBlockNum() == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
			else if(ei.packetType == ErrorInstruction.packetTypes.ERROR && parsedPacket instanceof TFTPPacket.ERROR) {
				if(ei.skipped == ei.packetNumber) {
					ei.occurances++;
					if(ei.occurances == ei.timesToPerform) {
						errors.remove(i);
					}else {
						ei.skipped = 0;
					}
					return ei;
				}
				ei.skipped++;
			}
		}
		return null;
	}

	/**
	 * Creates a backup copy of the error list that can be restored at a later time
	 */
	public synchronized void backupErrors() {
		errorsBackup.clear();
		int i;
		for(i=0; i<errors.size(); i++) {
			errorsBackup.add(errors.get(i).clone());
		}
	}

	/**
	 * Restores the backup error list
	 */
	public synchronized void restoreErrors() {
		errors.clear();
		int i;
		for(i=0; i<errorsBackup.size(); i++) {
			errors.add(errorsBackup.get(i).clone());
		}
	}

	/**
	 * Provides a list of all pending errors
	 */
	public String toString() {

		if(errors.size() == 0) {
			return "No errors pending creation.";
		}
		String desc = "";

		desc += "The following errors will created:\n";

		int i = 1;
		for(ErrorInstruction ei : errors) {
			desc += i + ". " + ei.toString() + "\n";
			i++;
		}
		return desc;
	}
}

/**
 * ErrorInstruction class represents an network error that the error simulator should simulate
 */
class ErrorInstruction {

	//All possible packet types that an error can be applied to
	enum packetTypes{
		RRQ, WRQ, DATA, ACK, ERROR
	}

	//All possible error types that can be created
	enum errorTypes{
		DROP, DUPLICATE, DELAY, INVALIDATE_TID, INVALIDATE_OPCODE, INVALIDATE_MODE, INVALIDATE_SHRINK,
		INVALIDATE_RMZ, INVALIDATE_APPEND, INVALIDATE_BLOCKNUM, INVALIDATE_ERRORNUM
	}

	packetTypes packetType;
	errorTypes errorType;
	int packetNumber;
	int param1;
	int timesToPerform;
	int occurances;
	int skipped = 0;

	/**
	 * Constructor for ErrorInstruction
	 * @param packetType the type of packet this error applies to
	 * @param errorType the type of error that should occur
	 * @param packetNumber the block number of the packet (for DATA and ACK) or the number of packets to skip before causing error (RRQ, WRQ, ERROR)
	 * @param param1 first command parameter that is an int
	 * @param timesToPerform how many times to create this error (negative is infinite)
	 */
	ErrorInstruction(packetTypes packetType, errorTypes errorType, int packetNumber, int param1, int timesToPerform)
	{
		if(packetNumber < 0) {
			throw new IllegalArgumentException("packet number can't be less than 0");
		}
		if(timesToPerform == 0 ) {
			throw new IllegalArgumentException("cant perform error 0 times");
		}
		if(packetType == packetTypes.DATA && packetNumber == 0) {
			throw new IllegalArgumentException("DATA packet can not have block number 0");
		}
		if(errorType == errorTypes.INVALIDATE_RMZ) {
			if(packetType == packetTypes.DATA || packetType == packetTypes.ACK) {
				throw new IllegalArgumentException("command not applicable to packet type");
			}
			if(packetType == packetTypes.ERROR && param1 != 0) {
				throw new IllegalArgumentException("invalid parameter for Error packet type");
			}
			if(param1 <= 0 && (packetType == packetTypes.RRQ || packetType == packetTypes.WRQ)) {
				throw new IllegalArgumentException("invalid parameter for '0' to remove");
			}
			if(param1 >= 3 && (packetType == packetTypes.RRQ || packetType == packetTypes.WRQ)) {
				throw new IllegalArgumentException("invalid parameter for '0' to remove");
			}
		}
		if(errorType == errorTypes.INVALIDATE_APPEND && param1 == 0) {
			throw new IllegalArgumentException("must append at least one byte");
		}
		if(errorType == errorTypes.INVALIDATE_BLOCKNUM && !(packetType == packetTypes.DATA || packetType == packetTypes.ACK)) {
			throw new IllegalArgumentException("command not applicable to packet type");
		}
		if(errorType == errorTypes.INVALIDATE_ERRORNUM && packetType != packetTypes.ERROR) {
			throw new IllegalArgumentException("command not applicable to packet type");
		}
		if(param1 <= 0 && (errorType == errorTypes.INVALIDATE_BLOCKNUM || errorType == errorTypes.INVALIDATE_ERRORNUM)) {
			throw new IllegalArgumentException("new number must be greater than 0");
		}
		if(param1 >= 65536 && (errorType == errorTypes.INVALIDATE_BLOCKNUM || errorType == errorTypes.INVALIDATE_ERRORNUM)) {
			throw new IllegalArgumentException("new number must be less than 65536");
		}
		if(errorType == errorTypes.INVALIDATE_MODE && !(packetType == packetTypes.RRQ || packetType == packetTypes.WRQ)) {
			throw new IllegalArgumentException("command not applicable to packet type");
		}

		this.packetType = packetType;
		this.errorType = errorType;
		this.packetNumber = packetNumber;
		this.param1 = param1;
		this.timesToPerform = timesToPerform;
	}

	/**
	 * Checks if two ErrorInstructions are equivalent
	 */
	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}

		if(!(o instanceof ErrorInstruction)){
			return false;
		}

		ErrorInstruction error = (ErrorInstruction)o;

		if(this.packetType != error.packetType ||
		   this.errorType != error.errorType) {
			return false;
		}
		return this.packetNumber == error.packetNumber &&
				this.param1 == error.param1 &&
				this.timesToPerform == error.timesToPerform &&
				this.occurances == error.occurances;
	}

	/**
	 * Provides a string representation of an ErrorInstruction
	 */
	public String toString() {
		String desc = "";

		switch(errorType) {
		case DROP:
			desc = "Drop " + getPacketName(packetType) + " " + packetNumber + ". ";
			break;
		case DUPLICATE:
			desc = "Duplicate " + getPacketName(packetType) + " " + packetNumber + " with " + param1 + " ms between packets. ";
			break;
		case DELAY:
			desc = "Delay " + getPacketName(packetType) + " " + packetNumber + " by " + param1 + " ms. ";
			break;
		case INVALIDATE_TID:
			desc = "Invalidate the TID of " + getPacketName(packetType) + " " + packetNumber + ". ";
			break;
		case INVALIDATE_OPCODE:
			desc = "Invalidate the opcode of " + getPacketName(packetType) + " " + packetNumber + ". ";
			break;
		case INVALIDATE_MODE:
			desc = "Invalidate the mode of " + getPacketName(packetType) + " " + packetNumber + ". ";
			break;
		case INVALIDATE_SHRINK:
			desc = "Shrink " + getPacketName(packetType) + " " + packetNumber + " to less than its minimum size. ";
			break;
		case INVALIDATE_RMZ:
			if(param1 == 0) {
				desc = "Remove the '0' byte in " + getPacketName(packetType) + " " + packetNumber + ". ";
			}
			else if(param1 == 1) {
				desc = "Remove the 1st '0' byte in " + getPacketName(packetType) + " " + packetNumber + ". ";
			}
			else if(param1 == 2) {
				desc = "Remove the 2nd '0' byte in " + getPacketName(packetType) + " " + packetNumber + ". ";
			}
			break;
		case INVALIDATE_APPEND:
			desc = "Append " + param1 + " bytes of random data to " + getPacketName(packetType) + " " + packetNumber + ". ";
			break;
		case INVALIDATE_BLOCKNUM:
			desc = "Replace the block number of " + getPacketName(packetType) + " " + packetNumber + " with " + param1 + ". ";
			break;
		case INVALIDATE_ERRORNUM:
			desc = "Replace the error number of " + getPacketName(packetType) + " " + packetNumber + " with " + param1 + ". ";
			break;
		}

		if(timesToPerform < 0) {
			desc += "Repeat forever.";
		}
		else {
			desc += "Perform " + timesToPerform + " time(s), " + (timesToPerform - occurances) + " remaining.";
		}
		return desc;
	}

	public ErrorInstruction clone() {
		ErrorInstruction ei = new ErrorInstruction(this.packetType, this.errorType, this.packetNumber, this.param1, this.timesToPerform);
		ei.occurances = this.occurances;
		ei.skipped = this.skipped;
		return ei;
	}

	/**
	 * Modifies the data in a packet according to the error
	 * @param packet The packet whose data should be modified
	 * @return the modified data
	 */
	public byte[] modifyPacket(DatagramPacket packet) {

		if(this.errorType == errorTypes.INVALIDATE_APPEND) {
			byte[] temp = Arrays.copyOf(packet.getData(), packet.getLength() + this.param1);

			int i;
			for(i = packet.getLength(); i < temp.length; i++) {
				temp[i] = (byte)((Math.random() * 254) + 1);
			}
			return temp;
		}
		else if(this.errorType == errorTypes.INVALIDATE_BLOCKNUM) {
			//Replace the block number with the one provided by the user
			byte[] temp = Arrays.copyOf(packet.getData(), packet.getLength());
			if(packet.getLength() > 3) {
				temp[2] = (byte)(param1>>8);
				temp[3] = (byte)(param1);
				return temp;
			}
		}
		else if(this.errorType == errorTypes.INVALIDATE_ERRORNUM) {
			//Replace the error number with the one provided by the user
			byte[] temp = Arrays.copyOf(packet.getData(), packet.getLength());
			if(packet.getLength() > 3) {
				temp[2] = (byte)(param1>>8);
				temp[3] = (byte)(param1);
				return temp;
			}
		}
		else if(this.errorType == errorTypes.INVALIDATE_MODE) {
			//Remove the mode string to invalidate it
			byte[] temp = Arrays.copyOf(packet.getData(), packet.getLength());
			if(temp.length > 6) {
				int start;
				//Find the start of the mode string
				for(start=2; (start < packet.getLength()) && temp[start] != 0; start++) {}

				//Copy everything except the mode string into a new array
				byte[] toReturn = new byte[start + 2];
				System.arraycopy(temp, 0, toReturn, 0, start);
				toReturn[toReturn.length-1] = 0;
				return toReturn;
			}
		}
		else if(this.errorType == errorTypes.INVALIDATE_OPCODE) {
			//Replace the first byte of the opcode with a random number
			byte[] temp = Arrays.copyOf(packet.getData(), packet.getLength());
			if(packet.getLength() > 0) {
				temp[0] = (byte)((Math.random() * 254) + 1);
				return temp;
			}
		}
		else if(this.errorType == errorTypes.INVALIDATE_RMZ) {
			//Either removing the last 0 in an ERROR packet, or the last 0 in a WRQ/RRQ packet
			if(this.param1 == 0 || this.param1 == 2) {
				return Arrays.copyOf(packet.getData(), packet.getLength()-1);
			}
			else if(packet.getLength() > 4){
				//Remove the first 0 in a WRQ/RRQ
				byte[] temp = Arrays.copyOf(packet.getData(), packet.getLength());
				int pos;

				//Find the first 0 byte
				for(pos=2; (pos < temp.length) && temp[pos] != 0; pos++) {}

				//Copy everything except the first 0 byte into a new array
				byte[] toReturn = new byte[packet.getLength()-1];
				System.arraycopy(temp, 0, toReturn, 0, pos);
				System.arraycopy(temp, pos+1, toReturn, pos, packet.getLength()-pos-1);
				return toReturn;
			}
		}
		else if(this.errorType == errorTypes.INVALIDATE_SHRINK) {
			if(this.packetType == packetTypes.ERROR) {
				//Shrink error packets to 4 bytes
				return Arrays.copyOf(packet.getData(), 4);
			}
			else {
				//Shrink all other packets to 3 bytes
				return Arrays.copyOf(packet.getData(), 3);
			}
		}
		return packet.getData(); //Return the original data if the packet does not need to be modified
	}

	/**
	 * Converts a string into a PacketType
	 * @param typeString a string representing a packet type
	 * @return the packetType enum corresponding to the string
	 */
	public static packetTypes getPacketType(String typeString) {
		switch(typeString.toLowerCase()) {
		case "rrq":
			return packetTypes.RRQ;
		case "wrq":
			return packetTypes.WRQ;
		case "data":
			return packetTypes.DATA;
		case "ack":
			return packetTypes.ACK;
		case "error":
			return packetTypes.ERROR;
		default:
			return null;
		}
	}

	/**
	 * Converts a packetType into a string
	 * @param type a packetType enum to be converted into a string
	 * @return the String corresponding to the packetType
	 */
	public static String getPacketName(packetTypes type) {
		switch(type) {
		case RRQ:
			return "RRQ";
		case WRQ:
			return "WRQ";
		case DATA:
			return "DATA";
		case ACK:
			return "ACK";
		case ERROR:
			return "ERROR";
		default:
			return null;
		}
	}
}


/**
 * Handles all communications to and from the client
 */
class ErrorSimClientListener{
	private ErrorSim errorSim;
	private DatagramSocket knownSocket;
	private DatagramSocket TIDSocket;
	private InetAddress clientAddress;
    private int clientPort;
    private Timer sendTimer;
    private Timer invalidTIDSendTimer;
    private SocketListener knownPortListener;
    private SocketListener TIDPortListener;
    private Thread knownPortListenerThread;
    private Thread TIDPortListenerThread;
    boolean verbose;


    /**
     * Constructor for ErrorSimClientListener
     * @param port the port to listen to client on
     * @param verbose true means debug info will be shown, false is basic output
     * @param errorSim the instance of errorSim using this class
     */
	public ErrorSimClientListener(int port, boolean verbose, ErrorSim errorSim) {
		this.clientPort = port;
		this.verbose = verbose;
		this.errorSim = errorSim;

		try { //Set up the socket that will be used to receive packets from client on known port
			knownSocket = new DatagramSocket(port);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }

		try { //Set up the socket that will be used to communicate with TID port
			TIDSocket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }

		sendTimer = new Timer();
		invalidTIDSendTimer = new Timer();
		knownPortListener = new SocketListener(knownSocket);
		TIDPortListener = new SocketListener(TIDSocket);
		knownPortListenerThread = new Thread(knownPortListener);
		TIDPortListenerThread = new Thread(TIDPortListener);
	}

	/**
	 * Sends a packet to the client and applies any applicable pending errors to it
	 * @param packet the packet to send
	 */
	public synchronized void sendToClient(DatagramPacket packet) {
		ErrorInstruction ei = errorSim.errors.checkPacket(packet);
		if(ei != null) {
			System.out.println("Applying the following error before sending packet to client:");
			System.out.println(ei);
			System.out.print("\n");

			if(ei.errorType == ErrorInstruction.errorTypes.DUPLICATE) {
				//Send the packet now, and its duplicate later
				sendTimer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), 0);
				sendTimer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), ei.param1);
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.DELAY) {
				//Send the packet later
				sendTimer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), ei.param1);
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.DROP) {
				//Do nothing, packet does not need to be sent
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.INVALIDATE_TID) {
				//Send out of a different port
				invalidTIDSendTimer.schedule(new InvalidTIDSendToClient(packet.getData(), packet.getLength()), 0);
			}
			else {
				//Modify the packet according to the error and send it
				byte[] temp = ei.modifyPacket(packet);
				sendTimer.schedule(new DelayedSendToClient(temp, temp.length), 0);
			}
		}
		else {
			//Send the packet to the client without introducing any errors
			sendTimer.schedule(new DelayedSendToClient(packet.getData(), packet.getLength()), 0);
		}
	}

	/**
	 * Makes ErrorSimClientListener start listening to the client
	 */
	public void start() {
		knownPortListenerThread.start();
		TIDPortListenerThread.start();
	}

	/**
	 * Gets the port that this listens to for new requests from the client
	 * @return the port number
	 */
	public int getClientKnownPort() {
		return clientPort;
	}

	/**
	 * sets the known port that this listens to for new requests from the client
	 * @param port the port number
	 */
	public synchronized void setClientKnownPort(int port) {
		knownSocket.close();
		clientPort = port;
		try { //Set up the socket that will be used to receive packets from client on known port
			knownSocket = new DatagramSocket(port);
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }

		knownPortListener = new SocketListener(knownSocket);
		knownPortListenerThread = new Thread(knownPortListener);
		knownPortListenerThread.start();
	}

	/**
	 * stops the ErrorSimClientListener
	 */
	public void close()
	{
	    cancelDelayedSend();
	    knownSocket.close();
	    TIDSocket.close();
	}

	/**
	 * Cancels the sending of all delayed packets
	 */
	public void cancelDelayedSend() {
		synchronized(sendTimer) {
			sendTimer.cancel(); //Remove all scheduled tasks
			sendTimer = new Timer(); //Restart the timer
		}
		synchronized(invalidTIDSendTimer) {
			invalidTIDSendTimer.cancel(); //Remove all scheduled tasks
			invalidTIDSendTimer = new Timer(); //Restart the timer
		}
	}

	/**
	 * Sets the TID port
	 * @param port the port number
	 */
	private synchronized void setClientPort(int port){
		clientPort = port;
	}

	/**
	 * Sets the IP address of the client
	 * @param address the IP address
	 */
	private synchronized void setClientAddress(InetAddress address){
		clientAddress = address;
	}

	/**
	 * Waits to receive a packet from the client
	 * @param socket the socket to listen to
	 * @return a packet if one was received or null if the socket was closed
	 */
	private DatagramPacket receiveFromClient(DatagramSocket socket) {

		byte data[] = new byte[TFTPPacket.MAX_SIZE];
	    DatagramPacket packet = new DatagramPacket(data, data.length);
	    TFTPPacket TFTPpacket;

    	try { //Wait for a packet to come in from the client.
    		socket.receive(packet);
    	} catch(IOException e) {
    		if(e.getMessage().toLowerCase().equals("socket closed")){
    			return null;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}

    	try {
    	    TFTPpacket = TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength()));

    	    //Check if this is the start of a new transaction. If it is, cancel all the pending delayed packets
    	    if(TFTPpacket instanceof TFTPPacket.WRQ || TFTPpacket instanceof TFTPPacket.RRQ) {
    	    	errorSim.clientListener.cancelDelayedSend();
    	    	errorSim.serverListener.cancelDelayedSend();
    	    	errorSim.errors.backupErrors();
    	    }
    	}
    	catch(IllegalArgumentException e) {

    	}

    	if(verbose) {
    		System.out.println("Received packet from client.");
    	    System.out.println("From address: " + packet.getAddress());
    	    System.out.println("From port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    try {
    	    	TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
	    	}
	    	catch(IllegalArgumentException e) {
	    		System.out.println("Invalid packet.");
	    	}
    	    System.out.print("\n");
    	}

    	return packet;
	}

	/**
	 * Listens to a socket on a new thread so the ErrorSimClientListener can listen to multiple sockets at the same time
	 */
	private class SocketListener implements Runnable{
		private DatagramSocket socket;

		/**
		 * Constructor for SocketListener
		 * @param socket the socket to listen to
		 */
		public SocketListener(DatagramSocket socket) {
			this.socket = socket;
		}

		/**
		 * The overridden run method for this thread
		 */
		public void run() {
			DatagramPacket receivePacket;

			while(true) {
				receivePacket = receiveFromClient(socket);

				if(receivePacket == null) {
					return;
				}
				setClientAddress(receivePacket.getAddress());
				setClientPort(receivePacket.getPort());
				errorSim.serverListener.sendToServer(receivePacket);
			}
		}
	}

	/**
	 * DelayedSendToClient class allows a packet to be sent at some time in the future
	 */
	private class DelayedSendToClient extends TimerTask{
		byte data[];
		int length;

		/**
		 * Creates a new task that will send a packet
		 * @param data the data to send
		 * @param length the length of the data
		 */
		public DelayedSendToClient(byte data[], int length) {
			this.data = data;
			this.length = length;
		}

		/**
		 * The overridden run method for this thread
		 */
		public synchronized void run() {

			DatagramPacket packet = new DatagramPacket(data, length, clientAddress, clientPort);

			if(verbose) {
	    		System.out.println("Sending packet to client.");
	    	    System.out.println("To address: " + packet.getAddress());
	    	    System.out.println("To port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    try {
	    	    	TFTPPacket.parse(Arrays.copyOf(data, length)).print();
		    	}
		    	catch(IllegalArgumentException e) {
		    		System.out.println("Invalid packet.");
		    	}
	    	    System.out.print("\n");
	    	}

			try { //Send the packet to the client
	    		TIDSocket.send(packet);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}
			this.cancel();
		}
	}

	/**
	 * InvalidTIDSendToClient class allows a packet to be sent to the client with the wrong TID
	 */
	private class InvalidTIDSendToClient extends TimerTask{
		byte data[];
		int length;

		/**
		 * Creates a new task that will send a packet
		 * @param data the data to send
		 * @param length the length of the data
		 */
		public InvalidTIDSendToClient(byte data[], int length) {
			this.data = data;
			this.length = length;
		}

		/**
		 * The overridden run method for this thread
		 */
		public void run() {
			DatagramSocket UnknownTIDSocket = null;

			try { //Set up the socket that will be used to send from an unknown TID
				UnknownTIDSocket = new DatagramSocket();
			} catch (SocketException se) { // Can't create the socket.
				se.printStackTrace();
				System.exit(1);
		    }

			DatagramPacket packet = new DatagramPacket(data, length, clientAddress, clientPort);

			System.out.println("Sending packet to client with invalid TID.");

			if(verbose) {
	    	    System.out.println("To address: " + packet.getAddress());
	    	    System.out.println("To port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    try {
	    	    	TFTPPacket.parse(Arrays.copyOf(data, length)).print();
		    	}
		    	catch(IllegalArgumentException e) {
		    		System.out.println("Invalid packet.");
		    	}
	    	    System.out.print("\n");
	    	}

			try { //Send the packet to the client
	    		UnknownTIDSocket.send(packet);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}

			try { //Wait for a packet to come in from the client.
				UnknownTIDSocket.receive(packet);
	    	} catch(IOException e) {
	    		if(e.getMessage().toLowerCase().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}

			System.out.println("Received packet from client in response to invalid TID.");

	    	if(verbose) {
	    	    System.out.println("From address: " + packet.getAddress());
	    	    System.out.println("From port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    try {
	    	    	TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
		    	}
		    	catch(IllegalArgumentException e) {
		    		System.out.println("Invalid packet.");
		    	}
	    	    System.out.print("\n");
	    	}
			this.cancel();
		}
	}
}

/**
 * Handles all communication to and from the server
 */
class ErrorSimServerListener implements Runnable {
	private ErrorSim errorSim;
	private DatagramSocket socket;
	private InetAddress serverAddress;
    private int serverPort;
    private int serverTID;
    private Timer sendTimer;
    private Timer invalidTIDSendTimer;
    boolean verbose;

    /**
     * Constructor for ErrorSimServerListener
     * @param port the servers known port
     * @param address the servers IP address
     * @param verbose true prints extra debug info, false prints only basic info
     * @param errorSim the instance of ErrorSim using this class
     */
	public ErrorSimServerListener(int port, InetAddress address, boolean verbose, ErrorSim errorSim) {
		serverPort = port;
		serverAddress = address;
		this.verbose = verbose;
		this.errorSim = errorSim;

		try { //Set up the socket that will be used to communicate with the server
			socket = new DatagramSocket();
		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
	    }

		sendTimer = new Timer();
		invalidTIDSendTimer = new Timer();
	}

	/**
	 * Sends a packet to the server
	 * @param packet the packet to send
	 */
	public synchronized void sendToServer(DatagramPacket packet) {
		ErrorInstruction ei = errorSim.errors.checkPacket(packet);
		if(ei != null) {
			System.out.println("Applying the following error before sending packet to server:");
			System.out.println(ei);
			System.out.print("\n");

			if(ei.errorType == ErrorInstruction.errorTypes.DUPLICATE) {
				//Send the packet now, and its duplicate later
				sendTimer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), 0);
				sendTimer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), ei.param1);
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.DELAY) {
				//Send the packet later
				sendTimer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), ei.param1);
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.DROP) {
				//Do nothing, packet does not need to be sent
			}
			else if(ei.errorType == ErrorInstruction.errorTypes.INVALIDATE_TID) {
				//Send out of a different port
				invalidTIDSendTimer.schedule(new InvalidTIDSendToServer(packet.getData(), packet.getLength()), 0);
			}
			else {
				//Modify the packet according to the error and send it
				byte[] temp = ei.modifyPacket(packet);
				sendTimer.schedule(new DelayedSendToServer(temp, temp.length), 0);
			}
		}
		else {
			//Send the packet to the client without introducing any errors
			sendTimer.schedule(new DelayedSendToServer(packet.getData(), packet.getLength()), 0);
		}
	}

	/**
	 * Gets the servers known port
	 * @return the port number
	 */
	public int getServerKnownPort() {
		return serverPort;
	}

	/**
	 * Gets the servers IP address
	 * @return the IP address
	 */
	public InetAddress getServerAddress() {
		return serverAddress;
	}

	/**
	 * Closes the ErrorSimServerListener
	 */
	public void close()
	{
		cancelDelayedSend();
		socket.close();
	}

	/**
	 * Cancels the sending of all delayed packets
	 */
	public void cancelDelayedSend() {
		synchronized(sendTimer) {
			sendTimer.cancel(); //Remove all scheduled tasks
			sendTimer = new Timer(); //Restart the timer
		}
		synchronized(invalidTIDSendTimer) {
			invalidTIDSendTimer.cancel(); //Remove all scheduled tasks
			invalidTIDSendTimer = new Timer(); //Restart the timer
		}
	}

	/**
	 * The overridden run method for this thread
	 */
	public void run() {
	    DatagramPacket receivePacket;

	    while(true){
	    	receivePacket = receiveFromServer();

	    	if(receivePacket == null) {
	    		return;
	    	}
	    	else {
	    		setServerTID(receivePacket.getPort());
	    	}
	    	errorSim.clientListener.sendToClient(receivePacket);
	    }
	}

	/**
	 * sets the TID used to communicate with the server
	 * @param port the port number
	 */
	private synchronized void setServerTID(int port){
		serverTID = port;
	}

	/**
	 * Waits to receive a packet from the server
	 * @return the packet if one was received, or null if the socket was closed
	 */
	private DatagramPacket receiveFromServer() {

		byte data[] = new byte[TFTPPacket.MAX_SIZE];
	    DatagramPacket packet = new DatagramPacket(data, data.length);

    	try { //Wait for a packet to come in from the client.
    		socket.receive(packet);
    	} catch(IOException e) {
    		if(e.getMessage().toLowerCase().equals("socket closed")){
    			return null;
    		}
    		e.printStackTrace();
			System.exit(1);
    	}

    	if(verbose) {
    		System.out.println("Received packet from server.");
    	    System.out.println("From address: " + packet.getAddress());
    	    System.out.println("From port: " + packet.getPort());
    	    System.out.println("Length: " + packet.getLength());
    	    try {
    	    	TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
	    	}
	    	catch(IllegalArgumentException e) {
	    		System.out.println("Invalid packet.");
	    	}
    	    System.out.print("\n");
    	}

    	return packet;
	}

	/**
	 * DelayedSendToServer class allows a packet to be sent at some time in the future
	 */
	private class DelayedSendToServer extends TimerTask{
		byte data[];
		int length;

		/**
		 * Constructor for DelayedSendToServer
		 * @param data the data to send
		 * @param length the length of the data
		 */
		public DelayedSendToServer(byte data[], int length) {
			this.data = data;
			this.length = length;
		}

		/**
		 * The overridden run method
		 */
		public synchronized void run() {
			DatagramPacket packet;

			if(length > 1 && (data[1] == 1 || data[1] == 2)) {
				packet = new DatagramPacket(data, length, serverAddress, serverPort);
			}
			else {
				packet = new DatagramPacket(data, length, serverAddress, serverTID);
			}

			if(verbose) {
	    		System.out.println("Sending packet to server.");
	    	    System.out.println("To address: " + packet.getAddress());
	    	    System.out.println("To port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    try {
	    	    TFTPPacket.parse(Arrays.copyOf(data, length)).print();
	    	    }
	    	    catch(IllegalArgumentException e) {
	    	    	System.out.println("Invalid packet.");
	    	    }
	    	    System.out.print("\n");
	    	}

			try { //Send the packet to the client
	    		socket.send(packet);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}
			this.cancel();
		}
	}

	/**
	 * InvalidTIDSendToServer class allows a packet to be sent to the client with the wrong TID
	 */
	private class InvalidTIDSendToServer extends TimerTask{
		byte data[];
		int length;

		/**
		 * Creates a new task that will send a packet
		 * @param data the data to send
		 * @param length the length of the data
		 */
		public InvalidTIDSendToServer(byte data[], int length) {
			this.data = data;
			this.length = length;
		}

		/**
		 * The overridden run method for this thread
		 */
		public void run() {
			DatagramSocket UnknownTIDSocket = null;
			DatagramPacket packet;

			try { //Set up the socket that will be used to send from an unknown TID
				UnknownTIDSocket = new DatagramSocket();
			} catch (SocketException se) { // Can't create the socket.
				se.printStackTrace();
				System.exit(1);
		    }

			if(length > 1 && (data[1] == 1 || data[1] == 2)) {
				packet = new DatagramPacket(data, length, serverAddress, serverPort);
			}
			else {
				packet = new DatagramPacket(data, length, serverAddress, serverTID);
			}

			if(verbose) {
	    		System.out.println("Sending packet to server with invalid TID.");
	    	    System.out.println("To address: " + packet.getAddress());
	    	    System.out.println("To port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    try {
		    	    TFTPPacket.parse(Arrays.copyOf(data, length)).print();
		    	}
		    	catch(IllegalArgumentException e) {
		    		System.out.println("Invalid packet.");
		    	}
	    	    System.out.print("\n");
	    	}

			try { //Send the packet to the server
	    		UnknownTIDSocket.send(packet);
	    	} catch (IOException e) {
	    		if(e.getMessage().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}

			try { //Wait for a packet to come in from the server.
				UnknownTIDSocket.receive(packet);
	    	} catch(IOException e) {
	    		if(e.getMessage().toLowerCase().equals("socket closed")){
	    			return;
	    		}
	    		e.printStackTrace();
				System.exit(1);
	    	}

	    	if(verbose) {
	    		System.out.println("Received packet from server in response to invalid TID.");
	    	    System.out.println("From address: " + packet.getAddress());
	    	    System.out.println("From port: " + packet.getPort());
	    	    System.out.println("Length: " + packet.getLength());
	    	    try {
	    	    	TFTPPacket.parse(Arrays.copyOf(packet.getData(), packet.getLength())).print();
		    	}
		    	catch(IllegalArgumentException e) {
		    		System.out.println("Invalid packet.");
		    	}
	    	    System.out.print("\n");
	    	}
			this.cancel();
		}
	}
}

/**
 * ErrorSim class handles the setup of the error simulator and acts as the UI thread.
 */
public class ErrorSim {
	public ErrorSimClientListener clientListener;
	public ErrorSimServerListener serverListener;
	private Thread serverListenerThread;
	public Errors errors;

	/**
	 * Constructor for the error sim class
	 * @param clientPort the port to listen to client requests on
	 * @param serverPort the port to send requests to the server on
	 * @param serverAddress the IP address of the server
	 * @param verbose true prints extra debug info, false prints only basic info
	 */
	public ErrorSim (int clientPort, int serverPort, InetAddress serverAddress, boolean verbose) {
		//Create the errors instance
		errors = new Errors();

		//Create the listeners
		clientListener = new ErrorSimClientListener(clientPort, verbose, this);
		serverListener = new ErrorSimServerListener(serverPort, serverAddress, verbose, this);
		serverListenerThread = new Thread(serverListener);
	}

	/**
	 * Starts the error simulator listener threads
	 */
	public void start () {
		clientListener.start();
		serverListenerThread.start();
	}

	//Handles the shutdown command
	private void shutdown (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.close();
			serverListener.close();
			try {
				serverListenerThread.join();
			} catch (InterruptedException e) {
				c.printerr("Error closing server listener thread.");
				System.exit(1);
			}
			try {
				c.close();
			} catch (IOException e) {
				c.printerr("Error closing console thread.");
				System.exit(1);
			}
			c.println("Shutting down Error Simulator...");
			System.exit(0);
		}
	}

	//Handles the verbose command
	private void setVerboseCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.verbose = true;
			serverListener.verbose = true;
			c.println("Running in verbose mode.");
		}
	}

	//Handles the quiet command
	private void setQuietCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		} else {
			clientListener.verbose = false;
			serverListener.verbose = false;
			c.println("Running in quiet mode.");
		}
	}

	//Handles the clientport command
	private void setClientPortCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Client port: " + this.clientListener.getClientKnownPort());
		} else if(args.length == 2) {
			int port = Integer.parseInt(args[1]);

			if(port > 0 && port < 65536) {
				clientListener.setClientKnownPort(port);
				c.println("Client port set to: " + port);
			}
			else {
				c.println("Invalid argument");
			}
		}
	}

	//Handles the serverport command
	private void setServerPortCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Server port: " + this.serverListener.getServerKnownPort());
		} else if(args.length == 2) {
			int port = Integer.parseInt(args[1]);

			if(port > 0 && port < 65536) {
				InetAddress serverAddress = this.serverListener.getServerAddress();
				boolean verbose = this.clientListener.verbose;

				serverListener.close();
				try {
					serverListenerThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//Restart the listener
				serverListener = new ErrorSimServerListener(port, serverAddress, verbose, this);
				serverListenerThread = new Thread(serverListener);
				serverListenerThread.start();
				c.println("Server port set to: " + port);
			}
			else {
				c.println("Invalid argument");
			}
		}
	}

	//Handles the serverip command
	private void setServerIPCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		} else if(args.length == 1) {
			c.println("Server ip: " + this.serverListener.getServerAddress());
		} else if(args.length == 2) {
			try {
				int serverPort = this.serverListener.getServerKnownPort();
				boolean verbose = this.serverListener.verbose;

				InetAddress serverAddress = InetAddress.getByName(args[1]);

				serverListener.close();
				serverListenerThread.join();

				//Restart the listener
				serverListener = new ErrorSimServerListener(serverPort, serverAddress, verbose, this);
				serverListenerThread = new Thread(serverListener);
				serverListenerThread.start();
				c.println("Server ip set to: " + this.serverListener.getServerAddress());
			} catch (UnknownHostException e) {
				c.println("Invalid argument");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	//Handles the drop command
	private void dropCmd (Console c, String[] args) {
		if(args.length > 4) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 4){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.DROP, 	//Error type
						Integer.parseInt(args[2]),			//Packet number
						0,									//Time delay - not used here
						Integer.parseInt(args[3])));		//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the delay command
	private void delayCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.DELAY, 	//Error type
						Integer.parseInt(args[2]),			//Packet number
						Integer.parseInt(args[3]),			//Time delay
						Integer.parseInt(args[4])));		//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the duplicate command
	private void duplicateCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.DUPLICATE, 	//Error type
						Integer.parseInt(args[2]),				//Packet number
						Integer.parseInt(args[3]),				//Time delay
						Integer.parseInt(args[4])));			//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the invd_tid command
	private void invdTIDCmd (Console c, String[] args) {
		if(args.length > 4) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 4){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.INVALIDATE_TID, //Error type
						Integer.parseInt(args[2]),					//Packet Number
						0,											//param1 - not used here
						Integer.parseInt(args[3])));				//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the invd_opcode command
	private void invdOpcodeCmd (Console c, String[] args) {
		if(args.length > 4) {
			c.println("Error: Too many parameters.");
			return;
		}
		else if(args.length < 4){
			c.println("Error: Not enough parameters.");
			return;
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.INVALIDATE_OPCODE,	//Error type
						Integer.parseInt(args[2]),						//Packet Number
						0,												//param1 - not used here
						Integer.parseInt(args[3])));					//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the invd_mode command
	private void invdModeCmd (Console c, String[] args) {
		if(args.length > 4) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 4){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.INVALIDATE_MODE,	//Error type
						Integer.parseInt(args[2]),						//Packet Number
						0,												//param1 - not used here
						Integer.parseInt(args[3])));					//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the invd_shrink command
	private void invdShrinkCmd (Console c, String[] args) {
		if(args.length > 4) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 4){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.INVALIDATE_SHRINK,	//Error type
						Integer.parseInt(args[2]),						//Packet Number
						0,												//param1 - not used here
						Integer.parseInt(args[3])));					//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the invd_rmz command
	private void invdRmzCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 4 || (args.length == 4 && (args[1].toLowerCase().equals("wrq") || args[2].toLowerCase().equals("rrq")))){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}
			if(args.length == 5) {
				try {
					errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
							ErrorInstruction.errorTypes.INVALIDATE_RMZ,	//Error type
							Integer.parseInt(args[2]),					//Packet Number
							Integer.parseInt(args[3]),					//param1 - new which zero byte to remove (1 or 2)
							Integer.parseInt(args[4])));				//How many times to perform
					if (clientListener.verbose) {
						c.println(errors.toString());
					}
				}
				catch(IllegalArgumentException e) {
					c.println("Could not create error: " + e.getMessage());
				}
			}
			else {
				try {
					errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
							ErrorInstruction.errorTypes.INVALIDATE_RMZ,	//Error type
							Integer.parseInt(args[2]),					//Packet Number
							0,											//param1 - not used here
							Integer.parseInt(args[3])));				//How many times to perform
				}
				catch(IllegalArgumentException e) {
					c.println("Could not create error: " + e.getMessage());
				}
			}
		}
	}

	//Handles the invd_append command
	private void invdAppendCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.INVALIDATE_APPEND,	//Error type
						Integer.parseInt(args[2]),						//Packet Number
						Integer.parseInt(args[3]),						//param1 - how many bytes to append
						Integer.parseInt(args[4])));					//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the invd_blocknum command
	private void invdBlocknumCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.INVALIDATE_BLOCKNUM,	//Error type
						Integer.parseInt(args[2]),							//Packet Number
						Integer.parseInt(args[3]),							//param1 - new block number
						Integer.parseInt(args[4])));						//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the invd_errornum command
	private void invdErrornumCmd (Console c, String[] args) {
		if(args.length > 5) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 5){
			c.println("Error: Not enough parameters.");
		}
		else {
			if(ErrorInstruction.getPacketType(args[1]) == null) {
				c.println("Error: Invalid packet type");
				return;
			}

			try {
				errors.add(new ErrorInstruction(ErrorInstruction.getPacketType(args[1]),
						ErrorInstruction.errorTypes.INVALIDATE_ERRORNUM,	//Error type
						Integer.parseInt(args[2]),							//Packet Number
						Integer.parseInt(args[3]),							//param1 - new error number
						Integer.parseInt(args[4])));						//How many times to perform
				if (clientListener.verbose) {
					c.println(errors.toString());
				}
			}
			catch(IllegalArgumentException e) {
				c.println("Could not create error: " + e.getMessage());
			}
		}
	}

	//Handles the errors command
	private void errorsCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			c.println(errors.toString());
		}
	}

	//Handles the remove command
	private void removeCmd (Console c, String[] args) {
		if(args.length > 2) {
			c.println("Error: Too many parameters.");
		}
		else if(args.length < 2){
			c.println("Error: Not enough parameters.");
		}
		else {
			int errornum = Integer.parseInt(args[1]);

			if(errornum < 1) {
				c.println("Error: Invalid argument.");
			}
			else {
				if(errors.remove(errornum)) {
					c.println("Error " + errornum + " removed.");
					if (clientListener.verbose) {
						c.println(errors.toString());
					}
				}
				else {
					c.println("Could not remove error " + errornum + ".");
				}
			}
		}
	}

	//Handles the recall command
	private void recallCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			errors.restoreErrors();
			c.println("Errors recalled to previous state.");
		}
	}

	//Handles the reset command
	private void resetCmd (Console c, String[] args) {
		if(args.length > 1) {
			c.println("Error: Too many parameters.");
		}
		else {
			clientListener.cancelDelayedSend();
			serverListener.cancelDelayedSend();
			c.println("Cleared all remaining errors.");
		}
	}

	//Handles the help command
	private void helpCmd (Console c, String[] args) {
		c.println("The following is a list of commands and their usage:");
		c.println("");
		c.println("shutdown - Closes the error simulator program.");
		c.println("");
		c.println("verbose - Makes the error simulator output more detailed information.");
		c.println("");
		c.println("quiet - Makes the error simulator output only basic information.");
		c.println("");
		c.println("clientport <port> - Outputs the port used to listen to requests from the client.");
		c.println("                If a port is provided, then the port is changed to that port.");
		c.println("");
		c.println("serverport <port> - Outputs the port on the server the ErrorSim communicates with.");
		c.println("                If a port is provided, then the port is changed to that port.");
		c.println("");
		c.println("serverip <ip> - Outputs the IP address for the server.");
		c.println("              If an ip is provided, then the address is changed to that ip.");
		c.println("");
		c.println("errors - Shows a list of all pending errors.");
		c.println("");
		c.println("rm [x] - Removes pending error [x].");
		c.println("");
		c.println("recall - Restores the errorlist to its state before the last TFTP transaction.");
		c.println("");
		c.println("reset - Cancels the sending of all delayed packets.");
		c.println("");
		c.println("drop [packet type][packet number][# of times] - drops a packet.");
		c.println("    [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("    [packet number] - the packets block number, or the nth packet seen.");
		c.println("    [# of times] - how many times this error should be created.");
		c.println("");
		c.println("delay [packet type][packet number][delay][# of times] - delays a packet.");
		c.println("     [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("     [packet number] - the packets block number, or the nth packet seen.");
		c.println("     [delay] - time in ms between duplicated packets.");
		c.println("     [# of times] - how many times this error should be created.");
		c.println("");
		c.println("duplicate [packet type][packet number][delay][# of times] - duplicates a packet.");
		c.println("         [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("     	[packet number] - the packets block number, or the nth packet seen.");
		c.println("     	[delay] - time in ms between duplicated packets.");
		c.println("     	[# of times] - how many times this error should be created.");
		c.println("");
		c.println("tid [packet type][packet number][# of times] - invalidates TID for a packet.");
		c.println("   [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("   [packet number] - the packets block number, or the nth packet seen.");
		c.println("   [# of times] - how many times this error should be created.");
		c.println("");
		c.println("opcode [packet type][packet number][# of times] - invalidates opcode for a packet");
		c.println("      [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("      [packet number] - the packets block number, or the nth packet seen.");
		c.println("      [# of times] - how many times this error should be created.");
		c.println("");
		c.println("mode [packet type][packet number][# of times] - invalidates mode for a packet.");
		c.println("    [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("    [packet number] - the packets block number, or the nth packet seen.");
		c.println("    [# of times] - how many times this error should be created.");
		c.println("    NOTE: This command is only applicable to RRQ and WRQ packets.");
		c.println("");
		c.println("shrink [packet type][packet number][# of times] - shrinks packet below min size.");
		c.println("      [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("      [packet number] - the packets block number, or the nth packet seen.");
		c.println("      [# of times] - how many times this error should be created.");
		c.println("");
		c.println("rmz [packet type][packet number][pos][# of times] - removes '0' byte from packet.");
		c.println("   [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("   [packet number] - the packets block number, or the nth packet seen.");
		c.println("   [pos] - which '0' byte to remove. 1 for 1st, 2 for 2nd.");
		c.println("   [# of times] - how many times this error should be created.");
		c.println("   NOTE: This command is only applicable to RRQ, WRQ and ERROR packets. Do not");
		c.println("         specify [pos] for an error packet as there is only one '0' byte.");
		c.println("");
		c.println("append [packet type][packet number][# of bytes][# of times] - append random data.");
		c.println("      [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("      [packet number] - the packets block number, or the nth packet seen.");
		c.println("      [# of bytes] - how many bytes to append to the packet.");
		c.println("      [# of times] - how many times this error should be created.");
		c.println("");
		c.println("blocknum [packet type][packet number][new][# of times] - changes block number.");
		c.println("        [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("        [packet number] - the packets block number, or the nth packet seen.");
		c.println("        [new] - the new block number.");
		c.println("        [# of times] - how many times this error should be created.");
		c.println("");
		c.println("errornum [packet type][packet number][new][# of times] - changes error number.");
		c.println("        [packet type] - the type of packet. (RRQ, WRQ, DATA, ACK, ERROR)");
		c.println("        [packet number] - the packets block number, or the nth packet seen.");
		c.println("        [new] - the new error number.");
		c.println("        [# of times] - how many times this error should be created.");
		c.println("");
		c.println("help - Displays this message.");
	}

	/**
	 * main function for the error simulator
	 * @param args Command line arguments
	 */
	public static void main(String[] args) {

		//Initialize settings to default values
		Boolean verbose = false;
		int serverPort = 69;
		int clientPort = 23;
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		//Setup command line parser
		Option verboseOption = new Option( "v", "verbose", false, "print extra debug info" );

		Option serverPortOption = Option.builder("sp").argName("server port")
                .hasArg()
                .desc("the port number of the servers listener")
                .type(Integer.TYPE)
                .build();

		Option serverAddressOption = Option.builder("sa").argName("server address")
                .hasArg()
                .desc("the IP address of the server")
                .type(String.class)
                .build();

		Option clientPortOption = Option.builder("cp").argName("client port")
                .hasArg()
                .desc("the port number to listen to client requests on")
                .type(Integer.TYPE)
                .build();

		Options options = new Options();

		options.addOption(verboseOption);
		options.addOption(serverPortOption);
		options.addOption(serverAddressOption);
		options.addOption(clientPortOption);

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

	        if( line.hasOption("sa")) {
		        try {
					serverAddress = InetAddress.getByName((String)line.getParsedOptionValue("sa"));
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
		    }

	        if( line.hasOption("cp")) {
	        	clientPort = Integer.parseInt(line.getOptionValue("cp"));
		    }
	    }
	    catch( ParseException exp ) {
	        System.err.println( "Command line argument parsing failed.  Reason: " + exp.getMessage() );
	        System.exit(1);
	    }

		System.out.println("Starting Error Simulator...");

		if(verbose) {
			System.out.println("Listening to client on port " + clientPort);
			System.out.println("Server address: " + serverAddress + " port " + serverPort + "\n");
		}

		// Create and start ErrorSim instance
		ErrorSim errorSim = new ErrorSim (clientPort, serverPort, serverAddress, verbose);
		errorSim.start();

		// Create and start console UI thread
		Map<String, Console.CommandCallback> commands = Map.ofEntries(
				Map.entry("shutdown", errorSim::shutdown),
				Map.entry("verbose", errorSim::setVerboseCmd),
				Map.entry("quiet", errorSim::setQuietCmd),
				Map.entry("clientport", errorSim::setClientPortCmd),
				Map.entry("serverport", errorSim::setServerPortCmd),
				Map.entry("serverip", errorSim::setServerIPCmd),
				Map.entry("drop", errorSim::dropCmd),
				Map.entry("delay", errorSim::delayCmd),
				Map.entry("duplicate", errorSim::duplicateCmd),
				Map.entry("tid", errorSim::invdTIDCmd),
				Map.entry("opcode", errorSim::invdOpcodeCmd),
				Map.entry("mode", errorSim::invdModeCmd),
				Map.entry("shrink", errorSim::invdShrinkCmd),
				Map.entry("rmz", errorSim::invdRmzCmd),
				Map.entry("append", errorSim::invdAppendCmd),
				Map.entry("blocknum", errorSim::invdBlocknumCmd),
				Map.entry("errornum", errorSim::invdErrornumCmd),
				Map.entry("errors", errorSim::errorsCmd),
				Map.entry("rm", errorSim::removeCmd),
				Map.entry("recall", errorSim::recallCmd),
				Map.entry("reset", errorSim::resetCmd),
				Map.entry("help", errorSim::helpCmd)
				);

		Console console = new Console(commands);

		Thread consoleThread = new Thread(console);
		consoleThread.start();
	}
}
