import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.channels.Channels;
import java.util.Map;

/**
 * A simple command line interface which parses commands and calls functions.
 * 
 * A console is initialized using a map of strings to functions. When the
 * console receives a line which begins with a string which is in the map it
 * will call the associated callback function.
 * 
 * @author Samuel Dewan
 */
public class Console implements Runnable, Closeable {
	/**
	 * Represents a callback for a console command
	 * 
	 * @author Samuel Dewan
	 */
	public interface CommandCallback {
	    void runCommand(Console console, String[] args);
	}
	
	/**
	 * Map of available commands and their names.
	 */
	private Map<String, CommandCallback> commands; 
	
	/**
	 * FileInputStream on which input is received
	 */
	private FileInputStream inputStream;
	
	/**
	 * The reader used to read data from stdin.
	 */
	private BufferedReader reader;
	
	/**
	 * Writer used for normal output.
	 */
	private PrintWriter outputWriter;
	
	/**
	 * Writer used for errors.
	 */
	private PrintWriter errorWriter;
	
	/**
	 * The prompt used by this console.
	 */
	private String prompt;
	
	/**
	 * Create a Console with a given Map of commands using stdin and stdout.
	 * 
	 * @param commands The Map of command and their names for this console
	 * @param input The input stream for this console
	 * @param output The output steam for this console
	 * @param error The error stream for this console
	 * @throws IOException 
	 */
	public Console(Map<String, CommandCallback> commands, FileDescriptor input, 
			OutputStream output, OutputStream error)
	{
		this.commands = commands;
		this.prompt = ">> ";
		
		// Output is wrapped in a FileInputStream so that the input stream can
		// be closed from another thread, causing the reader's readLine method
		// to throw an exception.
		this.inputStream = new FileInputStream(input);
		this.reader = new BufferedReader(new InputStreamReader(
				Channels.newInputStream(this.inputStream.getChannel())));
		
		this.outputWriter = new PrintWriter(output, true);
		this.errorWriter = new PrintWriter(error, true);
	}
	
	/**
	 * Create a Console with a given Map of commands using stdin and stdout.
	 * 
	 * @param commands The Map of command and their names for this console
	 * @throws IOException 
	 */
	public Console(Map<String, CommandCallback> commands)
	{
		this(commands, FileDescriptor.in, System.out, System.err);
	}
	
	/**
	 * Print a message to the console's output stream, followed by a newline.
	 * 
	 * @param str The message to be printed
	 */
	public void println(String str)
	{
		this.outputWriter.println(str);
	}
	
	/**
	 * Print a message to the console's error stream, or its output stream if no
	 * dedicated error stream is available. The message will be followed by a
	 * newline.
	 * 
	 * @param str The message to be printed
	 */
	public void printerr(String str)
	{
		this.errorWriter.println(str);
	}

	@Override
	public void run()
	{
		for (;;) {
			try {
				// Print prompt
				this.outputWriter.print(this.prompt);
				this.outputWriter.flush();
				
				// Get the user's response
				String line = this.reader.readLine();
				
				// Split the response on spaces
				String[] split = line.split(" ");

				// Get the specified command callback
				CommandCallback command = commands.get(split[0]);

				if (command != null) {
					// Valid command, run callback
					command.runCommand(this, split);
				} else if (!line.isBlank()) {
					// Unknown command, print angry message
					this.println("Unkown command \"" + split[0] + "\"");
				}
			} catch (IOException e) {
				// Console closed
				break;
			} catch (NullPointerException e) {
				// Program killed
				break;
			}
		}
	}
	
	@Override
	public void close() throws IOException {
		this.inputStream.close();
		this.outputWriter.close();
		this.errorWriter.close();
	}

	/**
	 * Get the prompt used by the console.
	 * 
	 * @return The prompt used by the console
	 */
	public String getPrompt()
	{
		return prompt;
	}

	/**
	 * Set the prompt used by a console.
	 * 
	 * @param prompt The new prompt for the console
	 */
	public void setPrompt(String prompt)
	{
		this.prompt = prompt;
	}
}
