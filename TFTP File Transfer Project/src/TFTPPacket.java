import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a TFTP packet which has been received or will be sent
 * 
 * @author Samuel Dewan
 */
public abstract class TFTPPacket {
	
	/**
	 * Maximum TFTP block size
	 */
	public static final int BLOCK_SIZE = 512;
	
	/**
	 * Maximum size for a received TFTP packet
	 */
	public static final int MAX_SIZE = BLOCK_SIZE + 4;
	
	/**
	 * Maximum TFTP block number
	 */
	public static final int MAX_BLOCK_NUM = 0xFFFF;
	
	
	/**
	 * Marshal the packet to the format to be transmitted on a network.
	 * 
	 * @return A byte array containing the marshaled packet
	 */
	public abstract byte[] toBytes();
	
	/**
	 * Get the number of bytes in the marshaled representation of this
	 * packet.
	 * 
	 * @param The number of bytes in the marshaled representation of this
	 * packet.
	 */
	public abstract int size();
	
	/**
	 * Get a string representation of this packet.
	 * 
	 * @return A string representation of this packet
	 */
	public abstract String toString();
	
	/**
	 * Print the type, parameters and number of bytes for a TFTPPacket
	 */
	public void print()
	{
		System.out.printf("Packet: %s (%d bytes)\n", this.toString(),
				this.size());
	}
	
	
	/**
	 * Get a packet object from an array of bytes.
	 * 
	 * @note The array passed to this function should contain only the packet,
	 * 	     any trailing bytes should be removed before passing the array to
	 *       this function
	 * 
	 * @param bytes The bytes from which a packet should be parsed
	 * @return The packet parsed from the provided bytes
	 * @throws IllegalArgumentException
	 */
	public static TFTPPacket parse (byte[] bytes)
			throws IllegalArgumentException
	{
		if (bytes.length < 4) {
			throw new IllegalArgumentException("Packet is not long enough.");
		} else if ((((int)bytes[1]) | (((int)bytes[0]) << 8)) == 1) {
			return new TFTPPacket.RRQ(bytes);
		} else if ((((int)bytes[1]) | (((int)bytes[0]) << 8)) == 2) {
			return new TFTPPacket.WRQ(bytes);
		} else if ((((int)bytes[1]) | (((int)bytes[0]) << 8)) == 3) {
			return new TFTPPacket.DATA(bytes);
		} else if ((((int)bytes[1]) | (((int)bytes[0]) << 8)) == 4) {
			return new TFTPPacket.ACK(bytes);
		} else if ((((int)bytes[1]) | (((int)bytes[0]) << 8)) == 5) {
			return new TFTPPacket.ERROR(bytes);
		} else {
			throw new IllegalArgumentException(String.format("Unkown Opcode."));
		}
	}
	
	/**
	 * Represents the mode field of a TFTP read or write request.
	 * 
	 * @author Samuel Dewan
	 */
	public static enum TFTPMode {
		NETASCII, OCTET, MAIL;
		
		/**
		 * Parse a TFTPMode from a string
		 * @param str The string from which the TFTPMode should be parsed
		 * @return The TFTPMode which corresponds with the string
		 * @throws IllegalArgumentException
		 */
		public static TFTPMode parseFromString (String str) throws
								IllegalArgumentException
		{
			if (str.equalsIgnoreCase("NETASCII")) {
				return TFTPMode.NETASCII;
			} else if (str.equalsIgnoreCase("OCTET")) {
				return TFTPMode.OCTET;
			} else if (str.equalsIgnoreCase("MAIL")) {
				return TFTPMode.MAIL;
			} else {
				throw new IllegalArgumentException("Unkown mode \"" + str +
						"\"");
			}
		}
		
		/**
		 * Get a string representation of the mode as it should be formated in
		 * the packet to be transmitted.
		 */
		public String toString()
		{
			switch (this) {
			case NETASCII:
				return "NETASCII";
			case OCTET:
				return "OCTET";
			case MAIL:
				return "MAIL";
			default:
				// Shouldn't happen, fall back to netascii
				return "NETASCII";
			}
		}
	}
	
	/**
	 * Represents the error code from a TFTP error packet.
	 * 
	 * @author Samuel Dewan
	 */
	public static enum TFTPError {
		ERROR(0), FILE_NOT_FOUND(1), ACCESS_VIOLATION(2), DISK_FULL(3),
		ILLEGAL_OPERATION(4), UNKOWN_TRANSFER_ID(5), FILE_ALREADY_EXISTS(6),
		NO_SUCH_USER(7);
		
		/**
		 * The integer value of the error code
		 */
		private int code;
		
		private TFTPError (int code)
		{
			this.code = code;
		}
		
		/**
		 * Create a TFTPError to represent a given error code.
		 * 
		 * @param code The error code
		 * @return A TFTPError instance which represents the error code
		 */
		public static TFTPError fromCode(int code)
		{
			switch (code) {
				case 0:	
					return TFTPError.ERROR;
				case 1:	
					return TFTPError.FILE_NOT_FOUND;
				case 2:	
					return TFTPError.ACCESS_VIOLATION;
				case 3:	
					return TFTPError.DISK_FULL;
				case 4:	
					return TFTPError.ILLEGAL_OPERATION;
				case 5:	
					return TFTPError.UNKOWN_TRANSFER_ID;
				case 6:	
					return TFTPError.FILE_ALREADY_EXISTS;
				case 7:	
					return TFTPError.NO_SUCH_USER;
				default:
					return TFTPError.ERROR;
			}
		}
	}
	
	/**
	 * Represents a TFTP read request
	 * 
	 * @author Samuel Dewan
	 */
	public static class RRQ extends TFTPPacket {
		/*
		 *  2 bytes     string    1 byte     string   1 byte
         *  ------------------------------------------------
         * | Opcode |  Filename  |   0  |    Mode    |   0  |
         *  ------------------------------------------------ 
		 */
		
		/**
		 * Name of file to be read
		 */
		private String filename;
		/**
		 * Transfer mode
		 */
		private TFTPMode mode;
		
		/**
		 * Create a read request
		 * 
		 * @param filename Name of the file to be requested
		 * @param mode TFTP mode for the request
		 */
		public RRQ (String filename, TFTPMode mode)
		{
			this.filename = filename;
			this.mode = mode;
		}
		
		/**
		 * Create a read request from received data
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public RRQ (byte[] bytes) throws IllegalArgumentException
		{
			if (bytes.length < 4) {
				throw new IllegalArgumentException("Read request is too short");
			} else if (ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt() != 1) {
				throw new IllegalArgumentException(
						"Incorrect opcode for read request");
			}
			
			// Find end of first string
			int filename_end = 2;
			for (; (filename_end < bytes.length) && (bytes[filename_end] != 0);
					filename_end++) {}
			
			if (filename_end == bytes.length) {
				throw new IllegalArgumentException(
						"Invalid read request format");
			}
			
			this.filename = new String(Arrays.copyOfRange(bytes, 2,
					filename_end), StandardCharsets.UTF_8);
			
			// Find end of second string
			int mode_end = filename_end + 1;
			for (; (mode_end < bytes.length) && (bytes[mode_end] != 0);
					mode_end++) {}
			
			if (mode_end == bytes.length) {
				throw new IllegalArgumentException(
						"Invalid read request format");
			}
			
			String mode = new String(Arrays.copyOfRange(bytes,
					filename_end + 1, mode_end), StandardCharsets.UTF_8);
			this.mode = TFTPMode.parseFromString(mode);
		}
		
		/**
		 * Get the filename for this request
		 * 
		 * @return The filename for this request
		 */
		public String getFilename()
		{
			return filename;
		}

		/**
		 * Get the TFTP mode for this request
		 * 
		 * @return The TFTP mode for this request
		 */
		public TFTPMode getMode()
		{
			return mode;
		}
	
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.size()];

			data[0] = 0;
			data[1] = 1;

			byte filenameBytes[] = filename.getBytes(StandardCharsets.UTF_8);
			System.arraycopy(filenameBytes, 0, data, 2, filenameBytes.length);
			data[filenameBytes.length + 2] = 0;

			byte modeBytes[] = mode.toString().getBytes(StandardCharsets.UTF_8);
			System.arraycopy(modeBytes, 0, data, filenameBytes.length + 3,
					modeBytes.length);
			data[data.length - 1] = 0;
			
			return data;
		}

		@Override
		public String toString()
		{
			return String.format("Read Request <filename: \"%s\", mode: %s>",
					this.filename, this.mode.toString());
		}

		@Override
		public int size() {
			return filename.length() + mode.toString().length() + 4;
		}
		
	}
	
	public static class WRQ extends TFTPPacket {
		/**
		 * Name of file to be written
		 */
		private String filename;
		/**
		 * Transfer mode
		 */
		private TFTPMode mode;
		
		/**
		 * Create a write request.
		 * @param filename The name of the file to be written
		 * @param mode The TFTP mode for the transfer
		 */
		public WRQ (String filename, TFTPMode mode)
		{
			this.filename = filename;
			this.mode = mode;
		}
		
		/**
		 * Create a write request from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public WRQ (byte[] bytes) throws IllegalArgumentException
		{
			if (bytes.length < 4) {
				throw new IllegalArgumentException(
						"Write request is too short");
			} else if (ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt() != 2) {
				throw new IllegalArgumentException(
						"Incorrect opcode for write request");
			}
			
			// Find end of first string
			int filename_end = 2;
			for (; (filename_end < bytes.length) && (bytes[filename_end] != 0);
					filename_end++) {}
			
			if (filename_end == bytes.length) {
				throw new IllegalArgumentException(
						"Invalid write request format");
			}
			
			this.filename = new String(Arrays.copyOfRange(bytes, 2,
					filename_end), StandardCharsets.UTF_8);
			
			// Find end of second string
			int mode_end = filename_end + 1;
			for (; (mode_end < bytes.length) && (bytes[mode_end] != 0);
					mode_end++) {}
			
			if (mode_end == bytes.length) {
				throw new IllegalArgumentException(
						"Invalid write request format");
			}
			
			String mode = new String(Arrays.copyOfRange(bytes,
					filename_end + 1, mode_end), StandardCharsets.UTF_8);
			this.mode = TFTPMode.parseFromString(mode);
		}
		
		/**
		 * Get the filename for this request
		 * 
		 * @return The filename for this request
		 */
		public String getFilename()
		{
			return filename;
		}

		/**
		 * Get the TFTP mode for this request
		 * 
		 * @return The TFTP mode for this request
		 */
		public TFTPMode getMode()
		{
			return mode;
		}
		
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.size()];

			data[0] = 0;
			data[1] = 2;

			byte filenameBytes[] = this.filename.getBytes(
					StandardCharsets.UTF_8);
			System.arraycopy(filenameBytes, 0, data, 2, filenameBytes.length);
			data[filenameBytes.length + 2] = 0;

			byte modeBytes[] = this.mode.toString().getBytes(
					StandardCharsets.UTF_8);
			System.arraycopy(modeBytes, 0, data, filenameBytes.length + 3,
					modeBytes.length);
			data[data.length - 1] = 0;
			
			return data;
		}
		
		@Override
		public String toString()
		{
			return String.format("Write Request <filename: \"%s\", mode: %s>",
					this.filename, this.mode.toString());
		}
		
		@Override
		public int size() {
			return this.filename.length() + this.mode.toString().length() + 4;
		}
	}
	
	public static class DATA extends TFTPPacket {
		/*
		 *  2 bytes     2 bytes      n bytes
         *  ----------------------------------
         * | Opcode |   Block #  |   Data     |
         *  ----------------------------------
		 */
		
		/**
		 * Block number for this data packet
		 */
		private int blockNum;
		/**
		 * Payload of data packet
		 */
		private byte[] data;
	
		/**
		 * Create a data packet.
		 * @param blockNum The block number for this data
		 * @param data The data to be sent
		 * @throws IllegalArgumentException
		 */
		public DATA (int blockNum, byte[] data) throws IllegalArgumentException
		{
			if (blockNum > TFTPPacket.MAX_BLOCK_NUM) {
				throw new IllegalArgumentException("Block number is too high.");
			} else if (data.length > TFTPPacket.BLOCK_SIZE) {
				throw new IllegalArgumentException("Data block is too large.");
			}
			
			this.blockNum = blockNum;
			this.data = data;
		}
		
		/**
		 * Create a data packet from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public DATA (byte[] bytes) throws IllegalArgumentException 
		{
			if (bytes.length < 4) {
				throw new IllegalArgumentException("Data packet is too short");
			} else if (ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt() != 3) {
				throw new IllegalArgumentException(
						"Incorrect opcode for data packet");
			}
			
			this.blockNum = ByteBuffer.wrap(
					new byte[] {0, 0, bytes[2], bytes[3]}).getInt();
			
			if (bytes.length > 4) {
				this.data = Arrays.copyOfRange(bytes, 4, bytes.length);
			} else {
				this.data = new byte[] {};
			}
		}
		
		/**
		 * Get the block number for this data packet.
		 * 
		 * @return The block number of the data packet
		 */
		public int getBlockNum() {
			return blockNum;
		}

		/**
		 * Get the data from this data packet.
		 * 
		 * @return The data contained in the data packet
		 */
		public byte[] getData() {
			return data;
		}
		
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.size()];

			data[0] = 0;
			data[1] = 3;
			
			data[2] = (byte) ((this.blockNum >> 8) & 0xFF);
			data[3] = (byte) (this.blockNum & 0xFF);

			System.arraycopy(this.data, 0, data, 4, this.data.length);
			
			return data;
		}
		
		@Override
		public String toString()
		{
			return String.format("Data <block number: %d, num bytes: %d>",
					this.blockNum, this.data.length);
		}
		
		@Override
		public int size() {
			return this.data.length + 4;
		}
	}
	
	public static class ACK extends TFTPPacket {
		/*
		 *  2 bytes     2 bytes
         *  ---------------------
         * | Opcode |   Block #  |
         *  ---------------------
		 */
		
		/**
		 * Block number for this ACK
		 */
		private int blockNum;
		
		/**
		 * Create an ACK packet
		 * 
		 * @param blockNum The block number to be acknowledged 
		 * @throws IllegalArgumentException
		 */
		public ACK (int blockNum) throws IllegalArgumentException
		{
			if (blockNum > TFTPPacket.MAX_BLOCK_NUM) {
				throw new IllegalArgumentException("Block number is too high.");
			}
			
			this.blockNum = blockNum & 0xFFFF;
		}
		
		/**
		 * Create an ACK packet from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public ACK (byte[] bytes) throws IllegalArgumentException 
		{	
			if (bytes.length != 4) {
				throw new IllegalArgumentException(
						"Invalid length for ACK packet");
			} else if (ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt() != 4) {
				throw new IllegalArgumentException(
						"Incorrect opcode for ACK packet");
			}
			
			this.blockNum = ByteBuffer.wrap(
					new byte[] {0, 0, bytes[2], bytes[3]}).getInt();
		}

		/**
		 * Get the block number for this ACK.
		 * 
		 * @return The block number of the ACK packet
		 */
		public int getBlockNum() {
			return blockNum;
		}

		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.size()];

			data[0] = 0;
			data[1] = 4;
			
			data[2] = (byte) ((this.blockNum >> 8) & 0xFF);
			data[3] = (byte) (this.blockNum & 0xFF);
			
			return data;
		}
		
		@Override
		public String toString()
		{
			return String.format("Acknowledgment <block number: %d>",
					this.blockNum);
		}
		
		@Override
		public int size() {
			return 4;
		}
	}
	
	public static class ERROR extends TFTPPacket {
		/*  2 bytes     2 bytes      string    1 byte
		 *  -----------------------------------------
         * | Opcode |  ErrorCode |   ErrMsg   |   0  |
         *  -----------------------------------------
		 */
		
		/**
		 * Error type
		 */
		private TFTPError error;
		/**
		 * Error description
		 */
		private String description;
		
		/**
		 * Create and error packet.
		 * 
		 * @param error The error code for this packet
		 * @param description A description of the error
		 */
		public ERROR  (TFTPError error, String description)
		{
			this.error = error;
			this.description = description;
		}
		
		/**
		 * Create an error packet from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public ERROR (byte[] bytes) throws IllegalArgumentException 
		{	
			if (bytes.length < 5) {
				throw new IllegalArgumentException("Error packet is too short");
			} else if (ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt() != 5) {
				throw new IllegalArgumentException(
						"Incorrect opcode for error packet");
			}
			
			int code = ByteBuffer.wrap(
					new byte[] {0, 0, bytes[2], bytes[3]}).getInt();
			this.error = TFTPError.fromCode(code);

			// Find end of string
			int end = 4;
			for (; (end < bytes.length) && (bytes[end] != 0); end++) {}

			if (end == bytes.length) {
				throw new IllegalArgumentException(
						"Invalid error format");
			}

			this.description = new String(Arrays.copyOfRange(bytes, 4, end),
					StandardCharsets.UTF_8);
		}

		/**
		 * Get the error code for this packet.
		 * 
		 * @return The error code
		 */
		public TFTPError getError() {
			return error;
		}

		/**
		 * Get the human readable description for this error.
		 * 
		 * @return The description of this error
		 */
		public String getDescription() {
			return description;
		}

		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.size()];

			data[0] = 0;
			data[1] = 5;
			
			data[2] = (byte) ((this.error.code >> 8) & 0xFF);
			data[3] = (byte) (this.error.code & 0xFF);
			
			byte descBytes[] = this.description.getBytes(
					StandardCharsets.UTF_8);
			System.arraycopy(descBytes, 0, data, 4, descBytes.length);
			data[data.length - 1] = 0;
			
			return data;
		}
		
		@Override
		public String toString()
		{
			return String.format("Error <code: %d, description: \"%s\">",
					this.error.code, this.description);
		}
		
		@Override
		public int size() {
			return this.description.length() + 4;
		}
	}
}
