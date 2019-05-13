import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public abstract class TFTPPacket {
	
	/**
	 * Marshal the packet to the format to be transmitted on a network.
	 * 
	 * @return A byte array containing the marshaled packet
	 */
	public abstract byte[] toBytes();
	
	/**
	 * Get a string representation of a packet.
	 */
	public abstract String toString();
	
	
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
		} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) == 1) {
			return new TFTPPacket.RRQ(bytes);
		} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) == 2) {
			return new TFTPPacket.WRQ(bytes);
		} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) == 3) {
			return new TFTPPacket.DATA(bytes);
		} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) == 4) {
			return new TFTPPacket.ACK(bytes);
		} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) == 5) {
			return new TFTPPacket.ERROR(bytes);
		} else {
			throw new IllegalArgumentException(String.format("Unkown Opcode."));
		}
	}
	
	
	public static enum TFTPMode {
		NETASCII, OCTET, MAIL;
		
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
				throw new IllegalArgumentException("Unkown mode \"" + str + "\"");
			}
		}
		
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
	
	public static enum TFTPError {
		ERROR(0), FILE_NOT_FOUND(1), ACCESS_VIOLATION(2), DISK_FULL(3),
		ILLEGAL_OPERATION(4), UNKOWN_TRANSFER_ID(5), FILE_ALREADY_EXISTS(6),
		NO_SUCH_USER(7);
		
		int code;
		
		private TFTPError (int code)
		{
			this.code = code;
		}
		
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
	
	
	public static class RRQ extends TFTPPacket {
		/*
		 *  2 bytes     string    1 byte     string   1 byte
         *  ------------------------------------------------
         * | Opcode |  Filename  |   0  |    Mode    |   0  |
         *  ------------------------------------------------ 
		 */
		
		private String filename;
		private TFTPMode mode;
		
		public RRQ (String filename, TFTPMode mode)
		{
			this.filename = filename;
			this.mode = mode;
		}
		
		public RRQ (byte[] bytes) throws IllegalArgumentException
		{
			if (bytes.length < 4) {
				throw new IllegalArgumentException("Read request is too short");
			} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) != 1) {
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
		
		public String getFilename()
		{
			return filename;
		}


		public TFTPMode getMode()
		{
			return mode;
		}
		
		/**
		 * Marshal the packet to the format to be transmitted on a network.
		 * 
		 * @return A byte array containing the marshaled packet
		 */
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[filename.length() + mode.toString().length()
			                       	+ 4];

			data[0] = 0;
			data[1] = 1;

			byte filenameBytes[] = filename.getBytes(StandardCharsets.UTF_8);
			System.arraycopy(filenameBytes, 0, data, 2, filenameBytes.length);
			data[filenameBytes.length + 2] = 0;

			byte modeBytes[] = mode.toString().getBytes(StandardCharsets.UTF_8);
			System.arraycopy(modeBytes, 0, data, modeBytes.length + 3,
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
		
	}
	
	public static class WRQ extends TFTPPacket {
		private String filename;
		private TFTPMode mode;
		
		public WRQ (String filename, TFTPMode mode)
		{
			this.filename = filename;
			this.mode = mode;
		}
		
		public WRQ (byte[] bytes) throws IllegalArgumentException
		{
			if (bytes.length < 4) {
				throw new IllegalArgumentException(
						"Write request is too short");
			} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) != 2) {
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
		
		public String getFilename()
		{
			return filename;
		}


		public TFTPMode getMode()
		{
			return mode;
		}
		
		/**
		 * Marshal the packet to the format to be transmitted on a network.
		 * 
		 * @return A byte array containing the marshaled packet
		 */
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.filename.length() + 
			                       	this.mode.toString().length() + 4];

			data[0] = 0;
			data[1] = 2;

			byte filenameBytes[] = this.filename.getBytes(
					StandardCharsets.UTF_8);
			System.arraycopy(filenameBytes, 0, data, 2, filenameBytes.length);
			data[filenameBytes.length + 2] = 0;

			byte modeBytes[] = this.mode.toString().getBytes(
					StandardCharsets.UTF_8);
			System.arraycopy(modeBytes, 0, data, modeBytes.length + 3,
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
	}
	
	public static class DATA extends TFTPPacket {
		/*
		 *  2 bytes     2 bytes      n bytes
         *  ----------------------------------
         * | Opcode |   Block #  |   Data     |
         *  ----------------------------------
		 */
		
		private int blockNum;
		private byte[] data;
		
		public DATA (int blockNum, byte[] data)
		{
			this.blockNum = blockNum & 0xFFFF;
			this.data = data;
		}
		
		public DATA (byte[] bytes)
		{
			if (bytes.length < 4) {
				throw new IllegalArgumentException("Data packet is too short");
			} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) != 3) {
				throw new IllegalArgumentException(
						"Incorrect opcode for data packet");
			}
			
			this.blockNum = ((int)bytes[2]) | (((int)bytes[3]) << 8);
			
			if (bytes.length > 4) {
				this.data = Arrays.copyOfRange(bytes, 4, bytes.length - 1);
			} else {
				this.data = new byte[] {};
			}
		}
		
		public int getBlockNum() {
			return blockNum;
		}

		public byte[] getData() {
			return data;
		}

		/**
		 * Marshal the packet to the format to be transmitted on a network.
		 * 
		 * @return A byte array containing the marshaled packet
		 */
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.data.length + 4];

			data[0] = 0;
			data[1] = 3;
			
			data[2] = (byte) (this.blockNum & 0xFF);
			data[3] = (byte) ((this.blockNum >> 8) & 0xFF);

			System.arraycopy(this.data, 0, data, 4, this.data.length);
			
			return data;
		}
		
		@Override
		public String toString()
		{
			return String.format("Data <block number: %d, num bytes: %d>",
					this.blockNum, this.data.length);
		}
	}
	
	public static class ACK extends TFTPPacket {
		/*
		 *  2 bytes     2 bytes
         *  ---------------------
         * | Opcode |   Block #  |
         *  ---------------------
		 */
		
		private int blockNum;
		
		public ACK (int blockNum)
		{
			this.blockNum = blockNum & 0xFFFF;
		}
		
		public ACK (byte[] bytes)
		{
			if (bytes.length != 4) {
				throw new IllegalArgumentException(
						"Invalid length for ACK packet");
			} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) != 4) {
				throw new IllegalArgumentException(
						"Incorrect opcode for ACK packet");
			}
			
			this.blockNum = ((int)bytes[2]) | (((int)bytes[3]) << 8);
		}

		public int getBlockNum() {
			return blockNum;
		}

		/**
		 * Marshal the packet to the format to be transmitted on a network.
		 * 
		 * @return A byte array containing the marshaled packet
		 */
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[4];

			data[0] = 0;
			data[1] = 4;
			
			data[2] = (byte) (this.blockNum & 0xFF);
			data[3] = (byte) ((this.blockNum >> 8) & 0xFF);
			
			return data;
		}
		
		@Override
		public String toString()
		{
			return String.format("Acknowledgment <block number: %d>",
					this.blockNum);
		}
	}
	
	public static class ERROR extends TFTPPacket {
		/*  2 bytes     2 bytes      string    1 byte
		 *  -----------------------------------------
         * | Opcode |  ErrorCode |   ErrMsg   |   0  |
         *  -----------------------------------------
		 */
		
		private TFTPError error;
		private String description;
		
		public ERROR  (TFTPError error, String description)
		{
			this.error = error;
			this.description = description;
		}
		
		public ERROR (byte[] bytes)
		{
			if (bytes.length < 5) {
				throw new IllegalArgumentException("Error packet is too short");
			} else if ((((int)bytes[0]) | (((int)bytes[1]) << 8)) != 5) {
				throw new IllegalArgumentException(
						"Incorrect opcode for error packet");
			}
			
			int code = ((int)bytes[2]) | (((int)bytes[3]) << 8);
			this.error = TFTPError.fromCode(code);

			// Find end of string
			int end = 2;
			for (; (end < bytes.length) && (bytes[end] != 0); end++) {}

			if (end == bytes.length) {
				throw new IllegalArgumentException(
						"Invalid error format");
			}

			this.description = new String(Arrays.copyOfRange(bytes, 4, end),
					StandardCharsets.UTF_8);
		}

		public TFTPError getError() {
			return error;
		}

		public String getDescription() {
			return description;
		}

		/**
		 * Marshal the packet to the format to be transmitted on a network.
		 * 
		 * @return A byte array containing the marshaled packet
		 */
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.description.length() + 4];

			data[0] = 0;
			data[1] = 5;
			
			data[2] = (byte) (this.error.code & 0xFF);
			data[3] = (byte) ((this.error.code >> 8) & 0xFF);
			
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
	}
}
