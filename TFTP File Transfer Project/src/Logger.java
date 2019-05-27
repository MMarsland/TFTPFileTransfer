import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.charset.Charset;
import java.util.Arrays;

//TODO Add logger close method to close the fileoutputstream
public class Logger {
	/*
	 * Log Levels
	 * 		0: Error that causes program to halt
	 * 		5: General updates on status
	 */
	int VerboseLevel;
	FileOutputStream file;
	boolean fileopen;
	public Logger() {
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

	public void setVerboseLevel(int verboseLevel) {
		VerboseLevel = verboseLevel;
		this.log(5, "VerboseLevel set to " + verboseLevel);
	}

	public void log(int VerboseLevel, String Content) {
		if(fileopen) {
			if(VerboseLevel <= this.VerboseLevel) {
				if(VerboseLevel == 0) System.err.println(Content);
				else System.out.println(Content);
			}
				try {
					file.write(Content.getBytes(Charset.forName("UTF-8")));
				} catch (IOException e) {
					System.out.println("Failed to write to log file");
					e.printStackTrace();
				}
		} else {
			if(VerboseLevel <= this.VerboseLevel) {
				if(VerboseLevel == 0) System.err.println(Content + " (Not Writing to Log file)");
				else System.out.println(Content + " (Not Writing to Log file)");
			}
		}
	}
	
	public void logPacket(int level, DatagramPacket datagram, TFTPPacket packet,
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
}
