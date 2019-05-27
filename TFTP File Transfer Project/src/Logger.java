import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

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
}
