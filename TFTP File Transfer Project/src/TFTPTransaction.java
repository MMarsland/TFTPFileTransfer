import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

/**
 * Encapsulates the logic of a TFTP file transfer
 * 
 * @author Samuel Dewan
 */
public abstract class TFTPTransaction implements Runnable {
	
	/**
	 * Represents the state of a TFTPTransaction
	 */
	public enum TFTPTransactionState {
		INITIALIZED, IN_PROGRESS, BLOCK_ZERO_TIMEOUT, TIMEOUT,
		LAST_BLOCK_ACK_TIMEOUT, FILE_TOO_LARGE, FILE_IO_ERROR, SOCKET_IO_ERROR,
		RECEIVED_BAD_PACKET, PEER_BAD_PACKET, PEER_FILE_NOT_FOUND,
		PEER_ACCESS_VIOLATION, PEER_DISK_FULL, PEER_FILE_EXISTS, PEER_ERROR,
		COMPLETE
	}
	
	/**
	 * The socket used to communicate with the peer
	 */
	private DatagramSocket socket;
	/**
	 * The address of the peer
	 */
	private InetAddress remoteHost;
	/**
	 * The TID of the peer
	 */
	private int remoteTID;
	
	/**
	 * The current state of the transaction
	 */
	private TFTPTransactionState state;
	
	/**
	 * Logger used to log details of send and received packets
	 */
	private Logger logger;
	
	/**
	 * Create a TFTPTransaction.
	 * 
	 * @param socket The socket used to communicate with the peer
	 * @param remoteHost The address of the peer
	 * @param remoteTID The TID of the peer
	 * @param logger The logger used to log details of packets
	 */
	private TFTPTransaction(DatagramSocket socket, InetAddress remoteHost,
			int remoteTID, Logger logger)
	{
		this.socket = socket;
		this.remoteHost = remoteHost;
		this.remoteTID = remoteTID;
		this.logger = logger;
		
		this.state = TFTPTransactionState.INITIALIZED;
	}
	
	/**
	 * Send a TFTPPacket to the peer
	 * 
	 * @param packet The packet to be sent
	 * @throws IOException
	 */
	private void sendToRemote(TFTPPacket packet) throws IOException
	{	
		synchronized (this.socket) {
			DatagramPacket outgoing = new DatagramPacket(packet.toBytes(),
					packet.size(), this.remoteHost, this.remoteTID);
				
			this.socket.send(outgoing);
			
			this.logger.logPacket(LogLevel.INFO, outgoing, packet, false, "peer");
		}
	}
	
	/**
	 * Receive a TFTPPacket from the remote
	 * 
	 * @param timeout Receive timeout in milliseconds
	 * @param updateTID Whether the peer's TID should be updated based on the 
	 * 					TID of the received packet
	 * @return The received TFTPPacket
	 * @throws SocketException
	 * @throws IOException
	 * @throws IllegalArgumentException
	 */
	private TFTPPacket receiveFromRemote(int timeout, boolean updateTID)
			throws SocketException, IOException, IllegalArgumentException
	{
		synchronized (this.socket) {
			// Calculate timeout
			long timeoutTime = System.currentTimeMillis() + timeout;
			int timeoutMillis = 0;
			
			// Continue trying to receive until the timeout runs out
			while ((timeoutMillis =
					(int)(timeoutTime - System.currentTimeMillis())) > 0) {
				// Receive packet
				this.socket.setSoTimeout(timeoutMillis);
				
				byte[] data = new byte[TFTPPacket.MAX_SIZE];
				DatagramPacket received = new DatagramPacket(data, data.length);
				
				this.socket.receive(received);
				
				TFTPPacket packet = TFTPPacket.parse(Arrays.copyOf(
						received.getData(), received.getLength()));
				
				// Got packet
				
				if (!received.getAddress().equals(this.remoteHost)) {
					// Packet from wrong host, ignore
					this.logger.log(LogLevel.WARN, String.format("Received " + 
							"packet from incorrect host (%s should be %s), " +
							"ignoring.", received.getAddress().toString(), 
							this.remoteHost.toString()));
					continue;
				} else if (updateTID) {
					// Update remote TID to match received packet
					this.remoteTID = received.getPort();
				} else if (received.getPort() != this.remoteTID) {
					// Got packet from wrong TID, send error to source host
					TFTPPacket error = new TFTPPacket.ERROR(
							TFTPPacket.TFTPError.UNKOWN_TRANSFER_ID,
							String.format("Unkown TID: %d",
							received.getPort()));
					
					DatagramPacket outgoing = new DatagramPacket(
							error.toBytes(), error.size(),
							received.getAddress(), received.getPort());
						
					this.socket.send(outgoing);
					
					this.logger.logPacket(LogLevel.INFO, outgoing, error, false,
							"peer");
					
					continue;
				}
				
				// Received packet from valid TID
				this.logger.logPacket(LogLevel.INFO, received, packet, true,
						"peer");
				
				return packet;
			}
			
			// Socket has timed out
			throw new SocketTimeoutException();
		}
	}
	
	/**
	 * Send an error packet to the peer
	 * 
	 * @param error The error type to be sent
	 * @param description Error description to be sent
	 */
	private void sendErrorPacket (TFTPPacket.TFTPError error,
			String description)
	{
		try {
			TFTPPacket.ERROR packet = new TFTPPacket.ERROR(error, description);
			this.sendToRemote(packet);
		} catch (IOException e) {
			// Ignore, we don't try to guaranty delivery of ERROR packets
		}
	}
	
	/**
	 * Set the transaction state based on a received error packet.
	 * 
	 * @param error The error packet received
	 */
	private void handleErrorPacket (TFTPPacket.ERROR error)
	{ 
		switch (error.getError()) {
		case ACCESS_VIOLATION:
			this.state = TFTPTransactionState.PEER_ACCESS_VIOLATION;
			break;
		case DISK_FULL:
			this.state = TFTPTransactionState.PEER_DISK_FULL;
			break;
		case FILE_ALREADY_EXISTS:
			this.state = TFTPTransactionState.PEER_FILE_EXISTS;
			break;
		case FILE_NOT_FOUND:
			this.state = TFTPTransactionState.PEER_FILE_NOT_FOUND;
			break;
		case ILLEGAL_OPERATION:
			this.state = TFTPTransactionState.PEER_BAD_PACKET;
			break;
		default:
			this.state = TFTPTransactionState.PEER_ERROR;
			break;
		}
		return;
	}
	
	/**
	 * Get the current state of the transaction.
	 * 
	 * @return The current state of the transaction
	 */
	public TFTPTransactionState getState ()
	{
		return this.state;
	}
	
	/**
	 * Performs a transaction where data is being sent to the remote host
	 */
	public static class TFTPSendTransaction extends TFTPTransaction
								implements Runnable {
		/**
		 * The file being sent
		 */
		private FileInputStream file;
		/**
		 * Whether we need to wait for ACK 0 before starting to send data
		 */
		private boolean waitAckZero;
		/**
		 * Buffer for data read from file
		 */
		private byte[] buffer;
		
		/**
		 * Create a TFTPSendTransaction
		 * 
		 * @param socket The socket to be used to communicate with the peer
		 * @param remoteHost The address of the peer
		 * @param remoteTID The TID of the peer
		 * @param sourceFile The file to be sent to the peer
		 * @param waitAckZero Whether we need to wait for ACK 0
		 * @param logger The logger used to print information on packets
		 * @throws FileNotFoundException
		 */
		public TFTPSendTransaction(DatagramSocket socket,
				InetAddress remoteHost, int remoteTID, String sourceFile,
				boolean waitAckZero, Logger logger) throws FileNotFoundException
		{
			super(socket, remoteHost, remoteTID, logger);
			this.waitAckZero = waitAckZero;
			
			this.file = new FileInputStream(sourceFile);
			this.buffer = new byte[TFTPPacket.BLOCK_SIZE];
		}
		
		/**
		 * Send a single data block.
		 * 
		 * @param blockNum The block number of the block to be sent
		 * @param resend Whether this is a repeat block, if false a new block
		 * 				 will be read from the file
		 * @return True if an error occurred
		 */
		private boolean sendDataBlock (int blockNum, boolean resend)
		{
			if (!resend) {
				// Read next block from file
				try {
					// Get up to a full buffer of data from the file
					int numBytes = file.read(this.buffer);
					// If no bytes where read from the file, buffer length
					// should be 0
					numBytes = (numBytes < 0) ? 0 : numBytes;
					// Trim buffer to size
					this.buffer = Arrays.copyOf(this.buffer, numBytes);
				} catch (IOException e) {
					// Could not read block from file
					super.state = TFTPTransactionState.FILE_IO_ERROR;
					return true;
				}
			}
			
			// Send block to client
			TFTPPacket.DATA data = new TFTPPacket.DATA(blockNum, this.buffer);
			try {
				super.sendToRemote(data);
			} catch (IOException e) {
				// Failed to send block
				super.state = TFTPTransactionState.SOCKET_IO_ERROR;
				return true;
			}
			
			return false;
		}
		
		/**
		 * Run transaction.
		 */
		public void run()
		{
			super.state = TFTPTransactionState.IN_PROGRESS;
						
			// Listen for ACK 0 if required
			if (this.waitAckZero) {
				// Receive a packet
				TFTPPacket ack;
				try {
					ack = super.receiveFromRemote(TFTPPacket.TFTP_TIMEOUT,
							true);
				} catch (SocketTimeoutException e) {
					// Receive has timed out, don't bother trying again
					super.state = TFTPTransactionState.BLOCK_ZERO_TIMEOUT;
					return;
				} catch (SocketException e) {
					super.state = TFTPTransactionState.SOCKET_IO_ERROR;
					return;
				} catch (IllegalArgumentException e) {
					super.sendErrorPacket(
							TFTPPacket.TFTPError.ILLEGAL_OPERATION,
							String.format("Not a valid packet. " + 
							"Expected ACK 0."));
					super.state =
							TFTPTransactionState.RECEIVED_BAD_PACKET;
					return;
				} catch (IOException e) {
					super.state = TFTPTransactionState.SOCKET_IO_ERROR;
					return;
				}
				// Validate packet
				if (!(ack instanceof TFTPPacket.ACK) ||
						(((TFTPPacket.ACK)ack).getBlockNum() != 0)) {
					// Got a bad packet, give up
					super.sendErrorPacket(
							TFTPPacket.TFTPError.ILLEGAL_OPERATION,
							String.format("Invalid packet. Expected ACK 0."));
					super.state = TFTPTransactionState.RECEIVED_BAD_PACKET;
					return;
				}
				// Successfully received ACK 0
			}
			
			// Find the total number of blocks to be sent
			long numBlocks = 0;
			try {
				numBlocks = (int)
						((file.getChannel().size() / TFTPPacket.BLOCK_SIZE)
								+ 1);
			} catch (IOException e) {
				// Didn't even manage to get the file size
				super.state = TFTPTransactionState.FILE_IO_ERROR;
				return;
			}
			// Check that file can be sent over TFTP
			if (numBlocks > TFTPPacket.MAX_BLOCK_NUM) {
				// Too many blocks
				super.state = TFTPTransactionState.FILE_TOO_LARGE;
				return;
			}
			
			
			// Send all the blocks
			TFTPPacket ack = null;
			
			for (int i = 1; i <= (int)numBlocks; i++) {
				int retries = 0;
				
				// Send data block i
				long retransmitTime = System.currentTimeMillis() +
						TFTPPacket.TFTP_DATA_TIMEOUT;
				boolean blockFailed = this.sendDataBlock(i, false);
				if (blockFailed) {
					return;
				}
				
				while (retries < TFTPPacket.TFTP_NUM_RETRIES) {
					// Wait for ACK i
					ack = null;
					
					int timeout = (int)
							(retransmitTime - System.currentTimeMillis());
					
					if (timeout > 0) {
						try {
							ack = super.receiveFromRemote(timeout, false);
						} catch (SocketTimeoutException e) {
							// Receive has timed out, previous DATA needs to be
							// resent
						} catch (SocketException e) {
							super.state = TFTPTransactionState.SOCKET_IO_ERROR;
							return;
						} catch (IllegalArgumentException e) {
							super.sendErrorPacket(
									TFTPPacket.TFTPError.ILLEGAL_OPERATION,
									String.format("Not a valid packet. " + 
										"Expected ACK %d.", i));
							super.state =
									TFTPTransactionState.RECEIVED_BAD_PACKET;
							return;
						} catch (IOException e) {
							super.state = TFTPTransactionState.SOCKET_IO_ERROR;
							return;
						}
					}
					
					// Check that received ACK is valid
					if (ack instanceof TFTPPacket.ACK) {
						if (((TFTPPacket.ACK)ack).getBlockNum() == i) {
							// Got ACK, ready to send next data block
							break;
						} else if (((TFTPPacket.ACK)ack).getBlockNum() < i) {
							// Probably a duplicated or delayed ACK, should be
							// ignored
							continue;
						} else if (((TFTPPacket.ACK)ack).getBlockNum() > i) {
							// Invalid packet
							super.sendErrorPacket(
									TFTPPacket.TFTPError.ILLEGAL_OPERATION,
									String.format("ACK has bad block number. " +
											"Expected ACK %d.", i));
							super.state =
									TFTPTransactionState.RECEIVED_BAD_PACKET;
							return;
						}
					} else if (ack instanceof TFTPPacket.ERROR) {
						// Got an error packet
						super.handleErrorPacket((TFTPPacket.ERROR)ack);
						return;
					} else if (ack != null) {
						// Received something that is not an ACK
						super.sendErrorPacket(
								TFTPPacket.TFTPError.ILLEGAL_OPERATION,
								String.format("Invalid packet. " +
								"Expected ACK %d.", i));
						super.state = TFTPTransactionState.RECEIVED_BAD_PACKET;
						return;
					}
					
					
					// Receive timed out, re-send data block i
					retries++;
					
					retransmitTime = System.currentTimeMillis() +
							TFTPPacket.TFTP_DATA_TIMEOUT;
					
					blockFailed = this.sendDataBlock(i, true);
					if (blockFailed) {
						return;
					}
				}
				
				if (retries == TFTPPacket.TFTP_NUM_RETRIES) {
					// Timed out waiting for ACK
					if (i == numBlocks) {
						super.state =
								TFTPTransactionState.LAST_BLOCK_ACK_TIMEOUT;
					} else {
						super.state = TFTPTransactionState.TIMEOUT;
					}
					return;
				}
			}
			
			super.state = TFTPTransactionState.COMPLETE;
		}
	}
	
	/**
	 * Encapsulates a transaction where DATA packets are received and ACK are
	 * sent.
	 */
	public static class TFTPReceiveTransaction extends TFTPTransaction
								implements Runnable {
		/**
		 * The file in which data should be saved.
		 */
		private FileOutputStream file;
		/**
		 * Whether an ACK 0 packet should be send before waiting for the first
		 * DATA.
		 */
		private boolean sendAckZero;
		/**
		 * Whether the TID should be updated based on the first DATA packet.
		 */
		private boolean updateTID;
		
		
		/**
		 * Create a TFTPReceiveTransaction.
		 * 
		 * @param socket The socket to be used in the transaction
		 * @param remoteHost The address of the peer
		 * @param remoteTID The TID of the peer
		 * @param destFile Path to where the received file should be stored
		 * @param sendAckZero Whether ACK 0 should be sent before waiting for
		 * 					  the first DATA
		 * @param updateTID Whether the TID should be updated based on the
		 * 					first DATA received
		 * @param logger The logger used to print packet information
		 * @throws FileNotFoundException
		 */
		public TFTPReceiveTransaction(DatagramSocket socket,
				InetAddress remoteHost, int remoteTID,  String destFile,
				boolean sendAckZero, boolean updateTID, Logger logger)
						throws FileNotFoundException
		{
			super(socket, remoteHost, remoteTID, logger);
			this.sendAckZero = sendAckZero;
			this.updateTID = updateTID;
			
			this.file = new FileOutputStream(destFile);
		}
		
		/**
		 * Send an ACK packet.
		 * 
		 * @param blockNum The block number to acknowledge
		 * @return
		 */
		private boolean sendAck (int blockNum)
		{
			try {
				super.sendToRemote(new TFTPPacket.ACK(blockNum));
			} catch (IllegalArgumentException e) {
				// This should never actually happen
				e.printStackTrace();
				System.exit(1);
			} catch (IOException e) {
				super.state = TFTPTransactionState.SOCKET_IO_ERROR;
				return true;
			}
			
			return false;
		}
		
		/**
		 * Run the transaction.
		 */
		public void run()
		{
			super.state = TFTPTransactionState.IN_PROGRESS;
			
			int blockNum = 1;
			
			// Send ACK 0 if required
			if (this.sendAckZero) {
				TFTPPacket.ACK ackZero = new TFTPPacket.ACK(0);
				try {
					super.sendToRemote(ackZero);
				} catch (IOException e) {
					super.state = TFTPTransactionState.SOCKET_IO_ERROR;
					return;
				}
			}
			
			// Loop through all blocks
			TFTPPacket data = null;
			long retransmitTime = System.currentTimeMillis() +
					TFTPPacket.TFTP_DATA_TIMEOUT;
			
			for (;;) {
				// Receive data and send ACK
				
				int retries = 0;
				
				while (retries < TFTPPacket.TFTP_NUM_RETRIES) {
					// Receive some data
					data = null;
					
					int timeout = (int)
							(retransmitTime - System.currentTimeMillis());
					
					if (timeout > 0) {
						try {
							data = super.receiveFromRemote(timeout,
									((blockNum == 1) && this.updateTID));
						} catch (SocketTimeoutException e) {
							// Receive has timed out, previous ACK needs to be
							// resent
						} catch (SocketException e) {
							super.state = TFTPTransactionState.SOCKET_IO_ERROR;
							return;
						} catch (IllegalArgumentException e) {
							super.sendErrorPacket(
									TFTPPacket.TFTPError.ILLEGAL_OPERATION,
									String.format("Not a valid packet. " +
									"Expected DATA %d.", blockNum));
							super.state =
									TFTPTransactionState.RECEIVED_BAD_PACKET;
							return;
						} catch (IOException e) {
							super.state = TFTPTransactionState.SOCKET_IO_ERROR;
							return;
						}
					}
					
					// Check that received data is valid
					if (data instanceof TFTPPacket.DATA) {
						TFTPPacket.DATA tftpData = ((TFTPPacket.DATA)data);
						
						if (tftpData.getBlockNum() == blockNum) {
							// Received the data that we expected, write to file
							try {
								this.file.write(tftpData.getData());
							} catch (IOException e) {
								super.state =
										TFTPTransactionState.FILE_IO_ERROR;
								e.printStackTrace();
							}
							// Send ACK
							retransmitTime = System.currentTimeMillis() +
									TFTPPacket.TFTP_DATA_TIMEOUT;
							
							boolean ackFailed = this.sendAck(blockNum);
							if (ackFailed) {
								return;
							}
							
							if (tftpData.getData().length <
									TFTPPacket.BLOCK_SIZE) {
								// Transaction complete
								try {
									this.file.flush();
									this.file.close();
								} catch (IOException e) {
									super.state =
											TFTPTransactionState.FILE_IO_ERROR;
									return;
								}
								
								super.state = TFTPTransactionState.COMPLETE;
								return;
							} else {
								// Continue to waiting for next block
								blockNum++;
								blockNum &= 0xFFFF;
								
								if (blockNum == 0) {
									// Block number has wrapped
									super.state =
											TFTPTransactionState.FILE_TOO_LARGE;
									return;
								}
							}
							break;
						} else if (tftpData.getBlockNum() < blockNum) {
							// Probably a duplicate or delayed data packet
							// Re-send ACK for packet
							try {
								super.sendToRemote(new TFTPPacket.ACK(
										tftpData.getBlockNum()));
							} catch (IllegalArgumentException e) {
								// This should never actually happen
								e.printStackTrace();
								System.exit(1);
							} catch (IOException e) {
								super.state =
										TFTPTransactionState.SOCKET_IO_ERROR;
								e.printStackTrace();
								return;
							}
						} else if (tftpData.getBlockNum() > blockNum) {
							// Block number is too high
							super.sendErrorPacket(
									TFTPPacket.TFTPError.ILLEGAL_OPERATION,
									String.format("Recevied bad DATA block. " +
									"Expected ACK %d.", blockNum));
							super.state =
									TFTPTransactionState.RECEIVED_BAD_PACKET;
							return;
						}
					} else if (data instanceof TFTPPacket.ERROR) {
						// Got an error packet
						super.handleErrorPacket((TFTPPacket.ERROR)data);
						return;
					} else if (data != null) {
						// Received something that is not data
						super.sendErrorPacket(
								TFTPPacket.TFTPError.ILLEGAL_OPERATION,
								String.format("Invalid packet. " +
								"Expected ACK %d.", blockNum));
						super.state = TFTPTransactionState.RECEIVED_BAD_PACKET;
						return;
					}
					
					// Received timed out
					if (blockNum == 1) {
						// If this is data 1, there is no previous ACK to 
						// retransmit (server does not retransmit ACK 0)
						super.state = TFTPTransactionState.BLOCK_ZERO_TIMEOUT;
						return;
					} else {
						// Re-send previous ACK if timeout has elapsed
						retries++;
						
						retransmitTime = System.currentTimeMillis() +
								TFTPPacket.TFTP_DATA_TIMEOUT;
						
						// Send ACK
						boolean ackFailed = this.sendAck(blockNum - 1);
						if (ackFailed) {
							return;
						}
					}
				}
				
				if (retries >= TFTPPacket.TFTP_NUM_RETRIES) {
					// Timed out waiting for data
					super.state = TFTPTransactionState.TIMEOUT;
					return;
				}
			}
		}
	}
}
