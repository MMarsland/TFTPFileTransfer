import java.util.Map;
import java.util.Scanner;

/**
 * A simple command line interface which parses commands and calls functions.
 * 
 * A console is initialized using a map of strings to functions. When the
 * console receives a line which begins with a string which is in the map it
 * will call the associated callback function.
 * 
 * @author Samuel Dewan
 */
public class Console implements Runnable {
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
	 * The prompt used by this console.
	 */
	private String prompt;
	
	/**
	 * Create a Console with a given Map of commands
	 * 
	 * @param commands The Map of command and their names for this console
	 */
	public Console (Map<String, CommandCallback> commands)
	{
		this.commands = commands;
		this.prompt = ">> ";
	}
	
	/**
	 * Print a message to the console's output stream.
	 * 
	 * @param str The message to be printed
	 */
	public void print(String str)
	{
		System.out.print(str);
	}
	
	/**
	 * Print a message to the console's output stream, followed by a newline.
	 * 
	 * @param str The message to be printed
	 */
	public void println(String str)
	{
		System.out.println(str);
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
		System.err.println(str);
	}

	@Override
	public void run()
	{
		try (Scanner scan = new Scanner(System.in)) {
			for (;;) {
				// Print prompt
				this.print(this.prompt);
				
				// Get the user's response
				String line = scan.nextLine();
				// Split the response on spaces
				String[] split = line.split(" ");
				
				// Get the specified command callback
				CommandCallback command = commands.get(split[0]);

				if (command != null) {
					// Valid command, run callback
					command.runCommand(this, split);
				} else {
					// Unknown command, print angry message
					this.println(String.format("Unkown command \"%s\"\n",
							split[0]));
				}
			}
		}
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
