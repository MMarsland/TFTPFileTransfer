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

public abstract class TFTPTransaction implements Runnable {
	public enum TFTPTransactionState {
		INITIALIZED, IN_PROGRESS, BLOCK_ZERO_TIMEOUT, TIMEOUT,
		LAST_BLOCK_ACK_TIMEOUT, FILE_TOO_LARGE, FILE_IO_ERROR, SOCKET_IO_ERROR,
		RECEIVED_BAD_PACKET, COMPLETE
	}
	
	
	private DatagramSocket socket;
	private InetAddress remoteHost;
	private int remoteTID;
	
	private TFTPTransactionState state;
	
	private Logger logger;
	
	private TFTPTransaction(DatagramSocket socket, InetAddress remoteHost,
			int remoteTID, Logger logger)
	{
		this.socket = socket;
		this.remoteHost = remoteHost;
		this.remoteTID = remoteTID;
		this.logger = logger;
		
		this.state = TFTPTransactionState.INITIALIZED;
	}
	
	private void sendToRemote(TFTPPacket packet) throws IOException
	{	
		synchronized (this.socket) {
			DatagramPacket outgoing = new DatagramPacket(packet.toBytes(),
					packet.size(), this.remoteHost, this.remoteTID);
				
			this.socket.send(outgoing);
			
			this.logger.logPacket(5, outgoing, packet, false, "remote");
		}
	}
	
	private TFTPPacket receiveFromRemote(int timeout, boolean updateTID)
			throws SocketException, IOException, IllegalArgumentException
	{
		synchronized (this.socket) {
			this.socket.setSoTimeout(timeout);
			
			byte[] data = new byte[TFTPPacket.MAX_SIZE];
			DatagramPacket received = new DatagramPacket(data, data.length);
			
			this.socket.receive(received);
			
			TFTPPacket packet = TFTPPacket.parse(Arrays.copyOf(received.getData(),
					received.getLength()));
			
			if (updateTID) {
				this.remoteTID = received.getPort();
			}
			
			this.logger.logPacket(5, received, packet, true, "remote");
			
			return packet;
		}
	}
	
	public TFTPTransactionState getState ()
	{
		return this.state;
	}
	
	
	public static class TFTPSendTransaction extends TFTPTransaction
								implements Runnable {
		private FileInputStream file;
		private boolean waitAckZero;
		private byte[] buffer;
		
		public TFTPSendTransaction(DatagramSocket socket,
				InetAddress remoteHost, int remoteTID, String sourceFile,
				boolean waitAckZero, Logger logger) throws FileNotFoundException
		{
			super(socket, remoteHost, remoteTID, logger);
			this.waitAckZero = waitAckZero;
			
			this.file = new FileInputStream(sourceFile);
			this.buffer = new byte[TFTPPacket.BLOCK_SIZE];
		}
		
		private boolean sendDataBlock (int blockNum, boolean resend)
		{
			if (!resend) {
				// Read next block from file
				try {
					int numBytes = file.read(this.buffer);
					numBytes = (numBytes < 0) ? 0 : numBytes;
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
							super.state =
									TFTPTransactionState.RECEIVED_BAD_PACKET;
							return;
						}
					} else if (ack != null) {
						// Received something that is not an ACK
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
	
	public static class TFTPReceiveTransaction extends TFTPTransaction
								implements Runnable {
		private FileOutputStream file;
		private boolean sendAckZero;
		private boolean updateTID;
		
		
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
				e.printStackTrace();
				return true;
			}
			
			return false;
		}
		
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
							super.state =
									TFTPTransactionState.RECEIVED_BAD_PACKET;
							return;
						}
					} else if (data != null) {
						// Received something that is not data
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