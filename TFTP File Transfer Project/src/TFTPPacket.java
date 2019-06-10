import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
	 * TFTP receive timeout before packet is assumed lost in milliseconds
	 */
	public static final int TFTP_TIMEOUT = 3000;
	
	/**
	 * TFTP timeout for re-sending DATA packets
	 */
	public static final int TFTP_DATA_TIMEOUT = 3500;
	
	/**
	 * Number of retries before partner is assumed to be missing
	 */
	public static final int TFTP_NUM_RETRIES = 5;
	
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
	 * Print the type, parameters and number of bytes for a TFTPPacket.
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
		}
		
		TFTPOpcode opcode = TFTPOpcode.fromInt(ByteBuffer.wrap(
				new byte[] {0, 0, bytes[0], bytes[1]}).getInt());
		
		switch (opcode) {
		case RRQ:
			return new TFTPPacket.RRQ(bytes);
		case WRQ:
			return new TFTPPacket.WRQ(bytes);
		case DATA:
			return new TFTPPacket.DATA(bytes);
		case ACK:
			return new TFTPPacket.ACK(bytes);
		case ERROR:
			return new TFTPPacket.ERROR(bytes);
		case OACK:
			return new TFTPPacket.OACK(bytes);
		default:
			throw new InvalidOpcodeException("Unkown Opcode");
		}
	}
	
	private static enum TFTPOpcode {
		RRQ(1), WRQ(2), DATA(3), ACK(4), ERROR(5), OACK(6);
		
		private int opcode;
		
		private TFTPOpcode(int opcode)
		{
			this.opcode = opcode;
		}
		
		public int getOpcode ()
		{
			return this.opcode;
		}
		
		public static TFTPOpcode fromInt (int opcode)
				throws IllegalArgumentException
		{
			if (opcode == 1) {
				return TFTPOpcode.RRQ;
			} else if (opcode == 2) {
				return TFTPOpcode.WRQ;
			} else if (opcode == 3) {
				return TFTPOpcode.DATA;
			} else if (opcode == 4) {
				return TFTPOpcode.ACK;
			} else if (opcode == 5) {
				return TFTPOpcode.ERROR;
			} else if (opcode == 6) {
				return TFTPOpcode.OACK;
			} else {
				throw new IllegalArgumentException(
						String.format("Unkown Opcode %d.", opcode));
			}
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
	 * Exception thrown when a packet has an invalid opcode
	 * 
	 * @author Samuel Dewan
	 */
	public static class InvalidOpcodeException extends IllegalArgumentException { 
		private static final long serialVersionUID = 1L;

		public InvalidOpcodeException(String errorMessage) {
	        super(errorMessage);
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
		NO_SUCH_USER(7), OPTION_NEGOTIATION_ERROR(8);
		
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
				case 8:
					return TFTPError.OPTION_NEGOTIATION_ERROR;
				default:
					return TFTPError.ERROR;
			}
		}
	}
	
	/**
	 * Represents a TFTP packets set of options (RFC 2347).
	 * 
	 * @author Samuel Dewan
	 */
	public static class OptionSet {
		private Map<String, String> options;
		
		/**
		 * Create a new empty options set.
		 */
		private OptionSet ()
		{
			options = new HashMap<String, String>();
		}
		
		/**
		 * Create a new options set with options parsed from a byte array.
		 * 
		 * @param bytes The options portion of a received packet
		 */
		private OptionSet (byte[] bytes) throws IllegalArgumentException
		{
			this();
			
			int position = 0;
			
			while (position != bytes.length) {
				// Find end of option name
				int string_end = position;
				for (; (string_end < bytes.length) && (bytes[string_end] != 0);
						string_end++) {}
				
				// Check if option name is cut off
				if (string_end == bytes.length) {
					throw new IllegalArgumentException("Invalid option format");
				}
				
				// Get string of option name
				String option = new String(Arrays.copyOfRange(bytes, position,
						string_end), StandardCharsets.UTF_8).toLowerCase();
				
				position += string_end;
				
				// Find end of option value
				string_end = position;
				for (; (string_end < bytes.length) && (bytes[string_end] != 0);
						string_end++) {}
				
				// Check if option value is cut off
				if (string_end == bytes.length) {
					throw new IllegalArgumentException("Invalid option format");
				}
				
				// Get string of option value
				String value = new String(Arrays.copyOfRange(bytes, position,
						string_end), StandardCharsets.UTF_8);
				
				position += string_end;
				
				// Add option to map
				options.put(option, value);
			}
		}
		
		/**
		 * Get the value of an option.
		 * 
		 * @param option The option for which the value should be found
		 * @return The value of the option or null if the option is not
		 * specified
		 */
		public String getOptionValue (String option)
		{
			return options.get(option.toLowerCase());
		}
		
		/**
		 * Get a set of all options in this set.
		 * 
		 * @return A set of all specified options
		 */
		public Set<String> getOptions () {
			return options.keySet();
		}
		
		/**
		 * Add a new option to this set.
		 * 
		 * @param option The option to add
		 * @param value The value for the new option
		 */
		public void addOption (String option, String value)
		{
			options.put(option.toLowerCase(), value);
		}
		
		/**
		 * Remove an option from this set.
		 * 
		 * @param option The option to be removed
		 * @return The value that the option had or null if the option was not
		 * specified
		 */
		public String removeOption (String option)
		{
			return options.remove(option.toLowerCase());
		}
		
		/**
		 * Marshal the options for this set.
		 * 
		 * @return Buffer of the marshaled options
		 */
		private byte[] getBytes ()
		{
			byte data[] = new byte[this.size()];
			
			int position = 0;
			
			// Get an iterator for all of the options in this packet
		    Iterator<Entry<String, String>> it =
		    		this.options.entrySet().iterator();
		    
		    while (it.hasNext()) {
		    	Map.Entry<String, String> pair =
		        		(Map.Entry<String, String>)it.next();
		    	
		    	// Copy option name to output buffer
				byte key[] = pair.getKey().getBytes(StandardCharsets.UTF_8);
				System.arraycopy(key, 0, data, position, key.length);
				position += key.length;
				data[position] = 0;
				position += 1;
				
				// Copy option value to output buffer
				byte value[] = pair.getValue().getBytes(StandardCharsets.UTF_8);
				System.arraycopy(value, 0, data, position, value.length);
				position += value.length;
				data[position] = 0;
				position += 1;
		    }
			
			return data;
		}
		
		/**
		 * Get the size of this option set when marshaled.
		 * 
		 * @return The length of the array that would be returned by getBytes
		 */
		public int size() {
			int size = 0;
			
			// Get an iterator for all of the options in this packet
		    Iterator<Entry<String, String>> it =
		    		this.options.entrySet().iterator();
		    
		    while (it.hasNext()) {
		    	Map.Entry<String, String> pair =
		        		(Map.Entry<String, String>)it.next();
		    	
		    	size += pair.getKey().length() + pair.getValue().length() + 2;
		    }
			
			return size;
		}
		
		/**
		 * Get a string representation of this option set.
		 */
		public String toString()
		{
			StringBuilder str = new StringBuilder();
			
			str.append("{");
			
			// Get an iterator for all of the options in this packet
		    Iterator<Entry<String, String>> it =
		    		this.options.entrySet().iterator();
		    
		    // Add the first option to the string without a preceding comma
		    if (it.hasNext()) {
		    	Map.Entry<String, String> pair =
		    			(Map.Entry<String, String>)it.next();
		    	
		    	str.append(String.format("%s: \"%s\"", pair.getKey(),
		    			pair.getValue()));
		    }
		    
		    // Add all subsequent options to the string with a preceding comma
		    while (it.hasNext()) {
		        Map.Entry<String, String> pair =
		        		(Map.Entry<String, String>)it.next();
		        
		        
		        str.append(String.format(", %s: \"%s\"", pair.getKey(),
		    			pair.getValue()));
		    }
			
		    str.append("}");
		    
			return str.toString();
		}
	}
	
	/**
	 * Represents a TFTP request.
	 * 
	 * @author Samuel Dewan
	 */
	private static abstract class RQ extends TFTPPacket {
		/**
		 * Name of file to be read or written
		 */
		private String filename;
		/**
		 * Transfer mode
		 */
		private TFTPMode mode;
		/**
		 * Options in this request
		 */
		private OptionSet options;
		
		/**
		 * Create a request.
		 * 
		 * @param filename Name of the file to be requested
		 * @param mode TFTP mode for the request
		 */
		public RQ (String filename, TFTPMode mode)
		{
			this.filename = filename;
			this.mode = mode;
			
			this.options = new OptionSet();
		}
		
		/**
		 * Create a request from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public RQ (byte[] bytes, TFTPOpcode opcode)
				throws IllegalArgumentException
		{	
			if (bytes.length < 4) {
				throw new IllegalArgumentException("Read request is too short");
			} else if (TFTPOpcode.fromInt(ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt()) !=
					opcode) {
				throw new InvalidOpcodeException(
						"Incorrect opcode for request");
			}
			
			// Find end of first string
			int filename_end = 2;
			for (; (filename_end < bytes.length) && (bytes[filename_end] != 0);
					filename_end++) {}
			
			if (filename_end == bytes.length) {
				throw new IllegalArgumentException(
						"Invalid request format");
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
			
			
			this.options = new OptionSet(Arrays.copyOfRange(bytes, mode_end + 1,
					bytes.length));
		}
		
		/**
		 * Get the filename for this request.
		 * 
		 * @return The filename for this request
		 */
		public String getFilename()
		{
			return filename;
		}

		/**
		 * Get the TFTP mode for this request.
		 * 
		 * @return The TFTP mode for this request
		 */
		public TFTPMode getMode()
		{
			return mode;
		}
		
		/**
		 * Get the set of options in this request.
		 * 
		 * @return The set of options in this request.
		 */
		public OptionSet getOptions ()
		{
			return this.options;
		}
		
		/**
		 * Marshal request packet.
		 * 
		 * @param opcode The opcode that should be used for this packet
		 * @return Buffer containing the marshaled packet
		 */
		private byte[] toBytes(TFTPOpcode opcode)
		{
			byte data[] = new byte[this.size()];

			data[0] = (byte) ((opcode.getOpcode() >> 8) & 0xFF);
			data[1] = (byte) (opcode.getOpcode() & 0xFF);

			// Copy file name to output
			byte filenameBytes[] = filename.getBytes(StandardCharsets.UTF_8);
			System.arraycopy(filenameBytes, 0, data, 2, filenameBytes.length);
			data[filenameBytes.length + 2] = 0;

			// Copy mode to output
			byte modeBytes[] = mode.toString().getBytes(StandardCharsets.UTF_8);
			System.arraycopy(modeBytes, 0, data, filenameBytes.length + 3,
					modeBytes.length);
			data[filenameBytes.length + modeBytes.length + 3] = 0;
			
			// Copy options to output
			byte options[] = this.options.getBytes();
			System.arraycopy(options, 0, data, filenameBytes.length +
					modeBytes.length + 4, options.length);
			
			return data;
		}
		
		@Override
		public int size() {
			return filename.length() + mode.toString().length() +
					this.options.size() + 4;
		}
	}
	
	/**
	 * Represents a TFTP read request
	 * 
	 * @author Samuel Dewan
	 */
	public static class RRQ extends RQ {
		/*
		 *  2 bytes     string    1 byte     string   1 byte
         *  ------------------------------------------------
         * | Opcode |  Filename  |   0  |    Mode    |   0  |
         *  ------------------------------------------------ 
		 */
		
		/**
		 * Create a read request.
		 * 
		 * @param filename Name of the file to be requested
		 * @param mode TFTP mode for the request
		 */
		public RRQ (String filename, TFTPMode mode)
		{
			super(filename, mode);
		}
		
		/**
		 * Create a read request from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public RRQ (byte[] bytes) throws IllegalArgumentException
		{
			super(bytes, TFTPOpcode.RRQ);
		}
		
		@Override
		public byte[] toBytes()
		{
			return super.toBytes(TFTPOpcode.RRQ);
		}

		@Override
		public String toString()
		{
			return String.format(
					"Read Request <filename: \"%s\", mode: %s, options: %s>",
					super.filename, super.mode.toString(),
					super.options.toString());
		}
	}
	
	/**
	 * Represents a TFTP write request.
	 * 
	 * @author Samuel Dewan
	 */
	public static class WRQ extends RQ {
		/**
		 *  2 bytes     string    1 byte     string   1 byte
         *  ------------------------------------------------
         * | Opcode |  Filename  |   0  |    Mode    |   0  |
         *  ------------------------------------------------
		 */
		
		/**
		 * Create a write request.
		 * @param filename The name of the file to be written
		 * @param mode The TFTP mode for the transfer
		 */
		public WRQ (String filename, TFTPMode mode)
		{
			super(filename, mode);
		}
		
		/**
		 * Create a write request from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public WRQ (byte[] bytes) throws IllegalArgumentException
		{
			super(bytes, TFTPOpcode.WRQ);
		}
		
		@Override
		public byte[] toBytes()
		{
			return super.toBytes(TFTPOpcode.WRQ);
		}
		
		@Override
		public String toString()
		{
			return String.format(
					"Write Request <filename: \"%s\", mode: %s, options: %s>",
					super.filename, super.mode.toString(),
					super.options.toString());
		}
	}
	
	/**
	 * Represents a TFTP Data Packet.
	 * 
	 * @author Samuel Dewan
	 */
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
			} else if (TFTPOpcode.fromInt(ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt()) !=
					TFTPOpcode.DATA) {
				throw new InvalidOpcodeException(
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

			data[0] = (byte) ((TFTPOpcode.DATA.getOpcode() >> 8) & 0xFF);
			data[1] = (byte) (TFTPOpcode.DATA.getOpcode() & 0xFF);
			
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
	
	/**
	 * Represents a TFTP ACK packet.
	 * 
	 * @author Samuel Dewan
	 */
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
			} else if (TFTPOpcode.fromInt(ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt()) !=
					TFTPOpcode.ACK) {
				throw new InvalidOpcodeException(
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

			data[0] = (byte) ((TFTPOpcode.ACK.getOpcode() >> 8) & 0xFF);
			data[1] = (byte) (TFTPOpcode.ACK.getOpcode() & 0xFF);
			
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
	
	/**
	 * Represents a TFTP error packet.
	 * 
	 * @author Samuel Dewan
	 */
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
			} else if (TFTPOpcode.fromInt(ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt()) !=
					TFTPOpcode.ERROR) {
				throw new InvalidOpcodeException(
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

			data[0] = (byte) ((TFTPOpcode.ERROR.getOpcode() >> 8) & 0xFF);
			data[1] = (byte) (TFTPOpcode.ERROR.getOpcode() & 0xFF);
			
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
			return this.description.length() + 5;
		}
	}
	
	/**
	 * Represents a TFTP options acknowledge packet.
	 * 
	 * @author Samuel Dewan
	 */
	public static class OACK extends TFTPPacket {
		/*  +-------+---~~---+---+---~~---+---+---~~---+---+---~~---+---+
      	 *  |  opc  |  opt1  | 0 | value1 | 0 |  optN  | 0 | valueN | 0 |
      	 *  +-------+---~~---+---+---~~---+---+---~~---+---+---~~---+---+
		 */
		
		/**
		 * Set of acknowledged options
		 */
		private OptionSet options;
		
		/**
		 * Create a new option acknowledge packet.
		 */
		public OACK ()
		{
			this.options = new OptionSet();
		}
	
		/**
		 * Create an option acknowledge packet from received data.
		 * 
		 * @param bytes The received packet
		 * @throws IllegalArgumentException
		 */
		public OACK (byte[] bytes) throws IllegalArgumentException 
		{	
			if (bytes.length < 2) {
				throw new IllegalArgumentException(
						"Option acknowledge packet is too short");
			} else if (TFTPOpcode.fromInt(ByteBuffer.wrap(
					new byte[] {0, 0, bytes[0], bytes[1]}).getInt()) !=
					TFTPOpcode.OACK) {
				throw new InvalidOpcodeException(
						"Incorrect opcode for option acknowledgment packet");
			}
			
			this.options = new OptionSet(Arrays.copyOfRange(bytes, 2,
					bytes.length));
		}
		
		/**
		 * Get the set of options in this acknowledge packet.
		 * 
		 * @return The set of options in this acknowledge packet.
		 */
		public OptionSet getOptions ()
		{
			return this.options;
		}
		
		@Override
		public byte[] toBytes()
		{
			byte data[] = new byte[this.size()];

			data[0] = (byte) ((TFTPOpcode.OACK.getOpcode() >> 8) & 0xFF);
			data[1] = (byte) (TFTPOpcode.OACK.getOpcode() & 0xFF);
			
			// Copy options to output
			byte options[] = this.options.getBytes();
			System.arraycopy(options, 0, data, 2, options.length);
			
			return data;
		}
		
		@Override
		public String toString()
		{
			return String.format("Options Acknowledgment <options: %s>",
					this.options.toString());
		}
		
		@Override
		public int size() {
			return this.options.size() + 4;
		}
	}
}
