import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.Charset;
import java.util.Arrays;

public class Logger {
	LogLevel VerboseLevel;
	FileOutputStream file;
	boolean fileopen;
	public Logger() {
		VerboseLevel = LogLevel.INFO;
		fileopen = false;
	}
	
	public void setLogFile(String file) {
		try {
			this.file = new FileOutputStream(file);
		} catch (FileNotFoundException e) {
			if(file == "") {
				System.out.println("No Log File Specified");
			} else {
				System.out.println("Failed to open log file");
			}
		}
	}

	public void setVerboseLevel(LogLevel verboseLevel) {
		VerboseLevel = verboseLevel;
		this.log(LogLevel.INFO, "VerboseLevel set to " + verboseLevel);
	}

	public void log(LogLevel VerboseLevel, String Content) {
		if(this.VerboseLevel.shouldLog(VerboseLevel)) {
			if(VerboseLevel == LogLevel.FATAL || VerboseLevel == LogLevel.ERROR) System.err.println(Content);
			else System.out.println(Content);
		}
		if(fileopen) {
			try {
				file.write(Content.getBytes(Charset.forName("UTF-8")));
			} catch (IOException e) {
				System.out.println("Failed to write to log file");
				e.printStackTrace();
			}
		}	
	}
	
	/**
	 * Log a datagram/TFTPPacket.
	 * 
	 * @param level The log level at which the packet information should be
	 * 				logged
	 * @param datagram The datagram packet to be logged
	 * @param packet The TFTPPacket parsed from the datagram packet, if null a
	 * 				 TFTPPacket will be created from datagram
	 * @param received True is this packet was received, false if this packet
	 * 				   was sent
	 * @param hostFriendlyName A human friendly name to referring to the host
	 * 						   to which this packet was sent or from which it
	 * 						   was received
	 */
	public void logPacket(LogLevel level, DatagramPacket datagram, TFTPPacket packet,
			boolean received, String hostFriendlyName) {
		
		if (packet == null) {
			try {
				packet = TFTPPacket.parse(Arrays.copyOf(datagram.getData(),
						datagram.getLength()));
			} catch (IllegalArgumentException e) {
				// ignore, packet remains null
			}
		}
		
		StringBuilder str = new StringBuilder();
		
		str.append(String.format("Packet %s %s:\n", 
				((received) ? "received from" : "sent to"), hostFriendlyName));
		str.append(String.format("\t%s: %s:%d\n", ((received) ? "From" : "To"),
				datagram.getAddress(), datagram.getPort()));
		str.append(String.format("\tLength: %d\n", datagram.getLength()));
		if (packet != null) {
			str.append(String.format("\tPacket: %s\n", packet.toString()));
		} else {
			str.append("\tNot a valid TFTP packet.");
		}
		
		this.log(level, str.toString());
	}
	
	public void endLog() {
		if(!this.fileopen) {
			return;
		}
		try {
			this.file.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.fileopen = false;
	}
	
}
