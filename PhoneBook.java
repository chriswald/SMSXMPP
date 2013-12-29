import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Properties;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;


public class PhoneBook {

	/*
	 * Exception class used to indicate that a command has not been
	 * properly formatted.
	 */
	public class CommandFormatException extends Exception{
		public CommandFormatException(String message) {
			super(message);
		}

		private static final long serialVersionUID = -2548007931998778510L;
	}

	class CommandBook
	{
		private ArrayList<CommandSet> sets = new ArrayList<CommandSet>();
		private final String FILENAME = USER_DIR + "/.member28";

		public void addSet(String tok, String prog)
		{
			CommandSet set = new CommandSet(tok, prog);
			this.sets.add(set);
		}

		public void addCommand(String input, String output)
		{
			String tok = input.split(" ")[0];
			String prog = output.split(" ")[0];

			int index = -1;
			for (int i = 0; i < this.sets.size(); i ++)
			{
				if (tok.equals(this.sets.get(i).getToken()))
					index = i;
			}

			if (index == -1)
			{
				CommandSet set = new CommandSet(tok, prog);
				this.sets.add(set);
				index = this.sets.size() - 1;
			}

			input = input.substring(tok.length()).trim();
			output = output.substring(prog.length()).trim();

			this.sets.get(index).add(input, output);
		}

		public String generate(String input)
		{
			String tok = input.split(" ")[0];
			for (CommandSet set : this.sets)
			{
				if (tok.equalsIgnoreCase(set.getToken()))
				{
					input = input.substring(tok.length()).trim();
					return set.generate(input);
				}
			}

			return null;
		}

		public void writeToFile()
		{
			try {
				File file = new File(this.FILENAME);
				FileWriter fw = new FileWriter(file);
				BufferedWriter writer = new BufferedWriter(fw);
				writer.write(this.toString());
				writer.close();

			} catch (Exception ex) {
				System.out.println(">>> IN WRITETOFILE:");
				PhoneBook.this.LogException(ex);
			}
		}

		public void readFromFile()
		{
			try {
				File file = new File(this.FILENAME);
				FileReader fr = new FileReader(file);
				BufferedReader reader = new BufferedReader(fr);

				String line = reader.readLine();
				while (line != null)
				{
					String mapFrom = line.split("=>")[0].trim();
					String mapTo = line.split("=>")[1].trim();

					this.addCommand(mapFrom, mapTo);
					line = reader.readLine();
				}

				reader.close();

			} catch (Exception ex) {
				System.out.println(">>> IN READFROMFILE:");
				PhoneBook.this.LogException(ex);
			}
		}

		public void removeCommand(String command)
		{
			String[] tokens = command.split(" ");

			if (tokens.length == 1)
			{
				String tok = tokens[0];
				for (int i = 0; i < this.sets.size(); i ++)
				{
					if (tok.equals(this.sets.get(i).getToken()))
					{
						this.sets.remove(i);
						break;
					}
				}
			}
			else
			{
				String tok = tokens[0];
				for (int i = 0; i < this.sets.size(); i ++)
				{
					if (tok.equals(this.sets.get(i).getToken()))
					{
						this.sets.get(i).removeCommand(tokens[1]);
						break;
					}
				}
			}
		}

		@Override
		public String toString()
		{
			String output = "";
			for (CommandSet set : this.sets)
				output += set.toString();

			return output;
		}
	}

	class CommandSet
	{
		private String token;
		private String repToken;
		private ArrayList<Command> commands = new ArrayList<Command>();

		public CommandSet(String tok, String prog)
		{
			this.token = tok;
			this.repToken = prog;
		}

		public void add(String input, String output)
		{
			if (input.startsWith(this.token))
				input = input.substring(this.token.length()).trim();

			int num_args = 0;
			int index = output.indexOf("${");
			while (index != -1)
			{
				num_args ++;
				index = output.indexOf("${", index+1);
			}

			Command com = new Command(input, num_args, output);

			if (!com.in(this.commands))
				this.commands.add(com);
		}

		public String getToken()
		{
			return this.token;
		}

		public String generate(String input)
		{
			String retString = this.repToken + " ";
			ArrayList<String> split = PhoneBook.this.removeQuotes(input);

			for (int i = 0; i < split.size(); i ++)
			{
				String s = split.get(i);
				boolean converted = false;
				for (Command com : this.commands)
				{
					if (s.equals(com.getInputCommand()))
					{
						String send = s;
						int num = com.getNumArgs();
						for (int index = 0; index < num; index ++, i ++)
							send += " \"" + split.get(i+1) + "\"";
						retString += " " + com.generate(send);
						converted = true;
						break;
					}
				}

				if (!converted)
					retString += " \"" + s + "\"";
			}
			return retString;
		}

		public void removeCommand(String command)
		{
			for (int i = 0; i < this.commands.size(); i ++)
			{
				if (command.equals(this.commands.get(i).getInputCommand()))
				{
					this.commands.remove(i);
					break;
				}
			}
		}

		@Override
		public String toString()
		{
			String output = "";
			for (Command com : this.commands)
				output += this.token + " " + com.getInputCommand() + " => " + this.repToken + " " + com.getOutputCommand() + "\n";
			return output;
		}
	}

	class Command
	{
		public Command(String input, int num_args, String output)
		{
			this.input_command = input;
			this.output_command = output;
			this.num_args = num_args;
		}

		private String input_command;
		private String output_command;
		private int num_args;

		public boolean in(ArrayList<Command> commands)
		{
			for (Command c : commands)
			{
				if (this.equals(c))
					return true;
			}
			return false;
		}

		@Override
		public boolean equals(Object obj)
		{
			if (obj instanceof Command)
			{
				Command c = (Command) obj;
				if (this.input_command.equals(c.input_command) &&
						this.output_command.equals(c.output_command) &&
						this.num_args == c.num_args)
					return true;
			}

			return false;
		}

		public String generate(String input)
		{
			ArrayList<String> split = PhoneBook.this.removeQuotes(input);

			if (split.size() != this.num_args + 1)
				return "";

			String return_text = this.output_command;
			for (int i = 0; i < this.num_args; i ++)
			{
				return_text = PhoneBook.this.replaceAll(return_text, "${" + i + "}", split.get(i+1));
			}

			return return_text;
		}

		public String getInputCommand()
		{
			return this.input_command;
		}

		public String getOutputCommand()
		{
			return this.output_command;
		}

		public int getNumArgs()
		{
			return this.num_args;
		}
	}

	// Vars set from command line.
	private String username       = null;
	private String password       = null;
	private String folder_name    = "Inbox";

	private static final String USER_DIR = System.getProperty("user.home");

	// Indicates to rest() whether or not to print the output message.
	// If the program hasn't taken any action since the last time rest()
	// was called (still_resting == true) don't print the message.
	private boolean still_resting = false;

	// Timing vars used by rest().
	private static final int SECOND = 1000; //ms
	private static final int MINUTE = 60 * SECOND; //sec

	// Tokens found at the beginning of supported messages. These
	// indicate which part of the program should parse and handle the
	// command.
	private static final String COMMAND_TOKEN = "fb";
	private static final String PROGRAM_TOKEN = "ph";
	private static final String TEXTHER_TOKEN = "th";

	private CommandBook book = new CommandBook();

	// Vars relating to a single message.
	private String message_contents = null;
	private String attachment_file_name = null;

	// Vars relating to status information used by some commands.
	private String last_exception = "";
	private long start_time;
	private int programs_executed = 0;
	private int other_executed = 0;

	// Indicates the maximum length of a message. The actual maximum
	// length of an SMS message is 160 7-byte chars, but this allows
	// a certain fudge factor for added text.
	private static final int MAX_MSG_LENGTH = 150;

	//Vars relating to the mail server.
	private Store store = null;
	private Folder folder = null;
	private static final String SERVER = "imap.gmail.com";

	/*
	 * Try to avoid doing really anything here. Just start a new instance
	 * of PhoneBook.
	 */
	public static void main(String[] args) {
		new PhoneBook().run(args);
	}

	/*
	 * This is where the magic happens.
	 */
	public void run(String[] args)
	{
		// Log the program's start time for status later.
		this.start_time = System.currentTimeMillis();

		this.setUpCommands();

		// Parse the command line arguments for username, password, and
		// maybe folder name.
		this.parseArgs(args);

		// Exit with a code of 1 if the user has not supplied a username
		// and password for their Gmail account.
		if (this.username == null || this.password == null)
		{
			System.err.println(">>> Must specify USERNAME and PASSWORD");
			System.err.println(">>> Usage:");
			System.err.println("  phonebook -U username -P password [-f folder]");
			System.exit(1);
		}

		try {
			// Get the store for the Gmail account specified with the
			// parameters specified.
			this.getStore();

			// If the store could not be retrieved, rest for 30 seconds
			// and try again. Also tracks retries.
			int retries = 0;
			while (this.store == null || retries > 30)
			{
				retries ++;
				Thread.sleep(30 * SECOND);
				this.getStore();
			}

			// Exits the program if the store could not be set up.
			// This section should only be pertinent if the retry
			// limit was exceeded.
			if (this.store == null)
			{
				System.err.println(">>> ERROR CONNECTING TO: " + SERVER);
				System.err.println(">>> RETRY LIMIT EXCEEDED");
				System.exit(2);
			}

			// Print the full address of the store.
			System.out.println(this.store);
			System.out.println(">>> IN FOLDER: " + this.folder_name);

			// Move to the main portion of the program now that the
			// setup is complete.
			this.goLooping();

		} catch (Exception e) {
			System.err.println(">>> IN RUN:");
			this.LogException(e);
		}
	}

	private void setUpCommands()
	{
		this.setUpFaceBashCommands();
		this.setUpPhoneBookCommands();
		this.setUpTextHerCommands();
		this.book.readFromFile();
		this.book.writeToFile();
	}

	private void setUpFaceBashCommands()
	{
		this.book.addSet(COMMAND_TOKEN, "facebash");
		this.book.addCommand("fb @a",   "facebash -a --plain");

		this.book.addCommand("fb @c",   "facebash -c --force-yes --val \"${0}\"");
		this.book.addCommand("fb @cn",  "facebash -c --force-yes --num ${0} --val \"${1}\"");
		this.book.addCommand("fb @cw",  "facebash -c --force-yes --who \"${0}\" --val \"${1}\"");
		this.book.addCommand("fb @cnw", "facebash -c --force-yes --num ${0} --who \"${1}\" --val \"${2}\"");

		this.book.addCommand("fb @k",   "facebash -k --force-yes");
		this.book.addCommand("fb @kn",  "facebash -k --force-yes --num ${0}");
		this.book.addCommand("fb @kw",  "facebash -k --force-yes --who \"${0}\"");
		this.book.addCommand("fb @knw", "facebash -k --force-yes --num ${0} --who \"${1}\"");

		this.book.addCommand("fb @l",   "facebash -l --grant --user ${0} --pass ${1}");

		this.book.addCommand("fb @n",   "facebash -n --plain --num 1");
		this.book.addCommand("fb @nn",  "facebash -n --plain --num ${0}");
		this.book.addCommand("fb @nw",  "facebash -n --plain --num 1 --who \"${0}\"");
		this.book.addCommand("fb @nnw", "facebash -n --plain --num ${0} --who \"${1}\"");

		this.book.addCommand("fb @s",   "facebash -s --val \"${0}\"");
		this.book.addCommand("fb @sw",  "facebash -s --who \"${0}\" --val \"${1}\"");

		this.book.addCommand("fb @u",   "facebash -u --img \"#{file}\"");
		this.book.addCommand("fb @ua",  "facebash -u --img \"#{file}\" --album \"${0}\"");
		this.book.addCommand("fb @ul",  "facebash -u --list");
	}

	private void setUpPhoneBookCommands()
	{
		this.book.addSet(PROGRAM_TOKEN, "phonebook");
		this.book.addCommand("ph stat", "phonebook stat");
		this.book.addCommand("ph register", "phonebook register \"${0}\" \"${1}\"");
		this.book.addCommand("ph deregister", "phonebook deregister \"${0}\"");
	}

	private void setUpTextHerCommands()
	{
		this.book.addSet(TEXTHER_TOKEN, "java");
		this.book.addCommand("th send", "java -jar " + USER_DIR + "/PhoneBook/Text.jar ${0} ${1}");
	}

	/*
	 * Retrieve the requested folder from the store. This needs to be
	 * done to check for new messages.
	 */
	private void getFolder()
	{
		// Reopen the folder every iteration to check for new mail.
		// Open it with read-write permissions so the messages will
		// be marked as read and not re-retrieved next loop.
		try {
			// Make sure the store is still connected.
			while (!this.store.isConnected())
				this.getStore();
			// If the folder exists make sure that it's closed.
			if (this.folder != null && this.folder.isOpen())
				this.folder.close(false);
			this.folder = this.store.getFolder(this.folder_name);
			this.folder.open(Folder.READ_WRITE);
		} catch (MessagingException e) {
			// If the folder can't be opened with READ_WRITE try opening
			// it READ_ONLY before failing.
			try {
				this.folder.open(Folder.READ_ONLY);
			} catch (MessagingException ex) {
				System.err.println(">>> IN GETFOLDER:");
				this.LogException(ex);
			}
		}
	}

	// Now that the setup from run() is done this function does all of
	// the heavy lifting.
	private void goLooping()
	{
		// Loop forever...
		while(true)
		{
			// Get the new messages.
			Message[] messages = null;
			this.getFolder();
			messages = this.getMessages();

			if (messages != null)
			{
				// If messages were retrieved go through the process of
				// executing the commands contained in each.
				for (Message message : messages)
				{
					try
					{
						// Retrieve the message contents and download any
						// attachments.
						this.handleMessageContents(message);

						String output = "";
						String input = this.book.generate(this.message_contents);

						if (!(input == null || input == ""))
						{
							if (input.contains("#{file}"))
								input = this.replaceAll(input, "#{file}", this.attachment_file_name);

							if (input.startsWith("phonebook"))
							{
								this.other_executed ++;
								output = this.ProgramControl(input);
							}
							else
							{
								this.programs_executed ++;
								output = this.ExecuteCommand(input);
							}
						}
						else
						{
							System.out.println(">>> UNKNOWN COMMAND");
							System.out.println(this.message_contents);
						}

						// Send the output captured from the commands back
						// to the caller (texter).
						this.replyToSender(output, message);

						// Delete any downloaded attachments.
						this.deleteAttachment();

						// Reset the command variables
						this.attachment_file_name = null;
						this.message_contents = null;

						// Delete the message once it's been handled to
						// avoid junk on the server.
						message.setFlag(Flags.Flag.DELETED, true);
					}
					catch (Exception e)
					{
						System.err.println(">>> IN GOLOOPING (1):");
						this.LogException(e);
					}
				}
			}

			// Close the folder for next time.
			try {
				if (this.folder != null && this.folder.isOpen())
					this.folder.close(true);
			} catch (Exception e) {
				System.err.println(">>> IN GOLOOPING (2):");
				this.LogException(e);
			}

			// Take a break before checking for new messages to reduce
			// network and CPU load.
			this.rest();
		}
	}

	/*
	 * Begins the process of unpackaging the message contents.
	 */
	private void handleMessageContents(Message message) throws IOException, MessagingException
	{
		Object msg_contents = message.getContent();
		if (msg_contents instanceof Multipart)
			this.handleMultipart((Multipart) msg_contents);
		else
			this.handlePart(message);

		this.message_contents = this.message_contents.trim();
	}

	private String replaceAll(String s, String oldS, String newS)
	{
		if (!s.contains(oldS))
			return s;

		ArrayList<Integer> indices = new ArrayList<Integer>(1);
		int index = s.indexOf(oldS);
		while (index != -1)
		{
			indices.add(index);
			index = s.indexOf(oldS, index+1);
		}

		String tmp = s;
		for (int i = indices.size()-1; i >= 0; i --)
		{
			int j = indices.get(i);
			String begin = tmp.substring(0,j);
			String end = tmp.substring(j);
			end = end.replace(oldS, newS);
			tmp = begin + end;
		}

		s = tmp;
		return s;
	}

	/*
	 * Conditionally sends the output back to the message's sender.
	 */
	private void replyToSender(String output, Message message)
	{
		// Send the output from the command back to the
		// caller at whatever address was specified
		// in the REPLY-TO field.
		if (output == null || output.equals(""))
			this.mailBack("Executed command", message);
		else
			this.mailBack(output, message);
	}

	/*
	 * Deletes the attachment if any exists.
	 */
	private void deleteAttachment()
	{
		if (this.attachment_file_name != null)
		{
			File f = new File(this.attachment_file_name);
			if (f.exists())
				f.delete();
		}
	}

	/*
	 * Calls handlePart() on each part of a multipart message.
	 */
	private void handleMultipart(Multipart multipart) throws MessagingException, IOException
	{
		for (int i = 0; i < multipart.getCount(); i ++)
			this.handlePart(multipart.getBodyPart(i));
	}

	/*
	 * Handles one part of a message based on content type.
	 */
	private void handlePart(Part part) throws MessagingException, IOException
	{
		String disposition = part.getDisposition();
		String content_type = part.getContentType().toLowerCase();

		if (disposition == null)
		{
			if (content_type.contains("text/plain"))
			{
				this.message_contents = (String) part.getContent();
			}
			else if (content_type.contains("image/"))
			{
				this.saveFile(part.getFileName(), part.getInputStream());
			}
		}
		else if (disposition.equalsIgnoreCase(Part.ATTACHMENT))
		{
			this.saveFile(part.getFileName(), part.getInputStream());
		}
		else if (disposition.equalsIgnoreCase(Part.INLINE))
		{
			this.saveFile(part.getFileName(), part.getInputStream());
		}
		else
		{
			if (content_type.contains("text/plain"))
			{
				this.message_contents = (String) part.getContent();
			}
		}
	}

	/*
	 * Handles saving the attachment file.
	 */
	private void saveFile(String filename, InputStream input) throws IOException
	{
		this.deleteAttachment();

		// Creates a new temporary file if no name is given.
		if (filename == null)
		{
			filename = File.createTempFile("xx", ".out").getName();
		}
		// Do no overwrite existing file
		File file = new File(filename);
		for (int i=0; file.exists(); i++)
		{
			file = new File(filename+i);
		}

		this.attachment_file_name = file.getAbsolutePath();

		// Save the file.
		FileOutputStream fos = new FileOutputStream(file);
		BufferedOutputStream bos = new BufferedOutputStream(fos);

		BufferedInputStream bis = new BufferedInputStream(input);
		int aByte;
		while ((aByte = bis.read()) != -1)
		{
			bos.write(aByte);
		}
		bos.flush();
		bos.close();
		bis.close();
	}

	/*
	 * Gets all unread messages from the folder.
	 */
	private Message[] getMessages()
	{
		// Create a new flag that represents all unseen/unread messages.
		FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
		try {
			// Return all messages in the folder that match that flag.
			if (this.folder.isOpen())
				return this.folder.search(ft);
			else
				return null;
		} catch (Exception e) {
			System.err.println(">>> IN GETMESSAGES:");
			this.LogException(e);
			return null;
		}
	}

	/*
	 * Sleeps for 1 minute, conditionally printing a status message.
	 */
	private void rest()
	{
		try
		{
			// Display a status message if a command has been executed
			// between periods of rest.
			if (!this.still_resting)
			{
				System.out.println(">>> Resting");
				this.still_resting = true;
			}
			Thread.sleep(MINUTE);
		}
		catch (Exception e) {
			System.err.println(">>> IN REST:");
			this.LogException(e);
		}
	}

	/*
	 * Parses the program's command line arguments for username,
	 * password, and possibly folder name.
	 */
	private void parseArgs(String[] args)
	{
		// Walk through arguments looking for tokens denoting username,
		// password, and folder name.
		for (int i = 0; i < args.length; i ++)
		{
			// Indicate username. This command is required.
			if      (args[i].equals("-U"))
				this.username    = args[++i];

			// Indicate password. This command is required.
			else if (args[i].equals("-P"))
				this.password    = args[++i];

			// Indicate mail folder. This command is optional.
			else if (args[i].equals("-f"))
				this.folder_name = args[++i];

			// Any other command. Why should I bother failing on bad
			// input? Just ignore it and continue.
			else
			{
				System.err.println(">>> UNRECOGNIZED ARGUMENT");
				System.err.println(">>> " + args[i]);
			}
		}
	}

	/*
	 * Logs in to the Gmail mail server.
	 */
	private void getStore()
	{
		try
		{
			// Get the store variable for the specified user's Gmail account.
			Properties props = System.getProperties();
			Session session = Session.getDefaultInstance(props, null);
			this.store = session.getStore("imaps");

			if (!this.store.isConnected())
				this.store.connect(SERVER, this.username, this.password);
		}
		catch (Exception e) {
			System.err.println(">>> IN GETSTORE:");
			this.LogException(e);
		}
	}

	/*
	 * Run a F.aceBash command.
	 */
	private String ExecuteCommand(String input)
	{
		// Signal that something other than resting is being done.
		this.still_resting = false;

		String[] cmd_ary = this.formatCommand(input);

		// Print out status information
		System.out.println(">>> EXECUTING");
		this.printCommand(cmd_ary);

		// Set up variable for storing the output of the command.
		String process_output = "";

		Process proc = null;
		BufferedReader reader = null;

		// Run the command.
		try
		{
			proc = Runtime.getRuntime().exec(cmd_ary);
		}
		catch (Exception e) {
			System.err.println(">>> IN EXECUTE (1):");
			this.LogException(e);
			return e.toString();
		}

		// Store all of the command's output in the variable mentioned earlier.
		try
		{
			reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));

			String line = reader.readLine();
			while (line != null)
			{
				process_output += line + "\n";
				line = reader.readLine();
			}

			if (process_output.equals(""))
			{
				reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

				line = reader.readLine();
				while (line != null)
				{
					process_output += line + "\n";
					line = reader.readLine();
				}
			}
		}
		catch (Exception e) {
			System.err.println(">>> IN EXECUTE (2):");
			this.LogException(e);
			return e.toString();
		}

		return process_output;
	}

	/*
	 * Run commands pertaining to PhoneBook itself.
	 */
	private String ProgramControl(String content)
	{
		this.still_resting = false;
		String process_output = "";

		String command = content.substring(content.split(" ")[0].length()).trim();

		System.out.println(">>> CONTROL");
		System.out.println("phonebook " + command);

		// Generate status information
		if      (command.toLowerCase().startsWith("stat"))
		{
			process_output = this.programStats();
		}
		else if (command.toLowerCase().startsWith("register"))
		{
			process_output = this.registerCommand(command.substring(command.split(" ")[0].length()));
		}
		else if (command.toLowerCase().startsWith("deregister"))
		{
			process_output = this.deregisterCommand(command.substring(command.split(" ")[0].length()));
		}

		return process_output;
	}

	private String programStats()
	{
		String retString = "";
		long time_now = System.currentTimeMillis();
		long uptime = time_now - this.start_time;
		long uptime_sec = uptime / 1000;
		long uptime_minutes = uptime_sec / 60;
		uptime_sec = uptime_sec % 60;
		long uptime_hours = uptime_minutes / 60;
		uptime_minutes = uptime_minutes % 60;
		long uptime_days = uptime_hours / 24;
		uptime_hours = uptime_hours % 24;

		retString += "Uptime:\n";
		retString += (uptime_days    > 0 ? (uptime_days    + "d ") : "");
		retString += (uptime_hours   > 0 ? (uptime_hours   + "h ") : "");
		retString += (uptime_minutes > 0 ? (uptime_minutes + "m ") : "");
		retString += (uptime_sec     > 0 ? (uptime_sec     + "s ") : "");

		retString += "\n";
		retString += "Program commands: " + this.programs_executed + "\n";
		retString += "Other commands: " + this.other_executed + "\n";
		retString += "Total: " + (this.programs_executed + this.other_executed);

		if (!this.last_exception.equals(""))
		{
			retString += "\n";
			retString += "Last Exception:\n";
			retString += this.last_exception;
		}

		return retString;
	}

	private String registerCommand(String content)
	{
		ArrayList<String> args = this.removeQuotes(content);
		String mapFrom = args.get(0);
		String mapTo   = args.get(1);
		String mapString = mapFrom + " => " + mapTo;
		this.book.addCommand(mapFrom, mapTo);
		this.book.writeToFile();
		System.out.println(">>> ADDED COMMAND:");
		System.out.println(mapString);

		return "Added Command:\n " + mapString;
	}

	private String deregisterCommand(String content)
	{
		content = this.removeQuotes(content.trim()).get(0);
		this.book.removeCommand(content);
		this.book.writeToFile();

		System.out.println(">>> REMOVED COMMAND:");
		System.out.println(content);

		return "Removed Command:\n" + content;
	}

	private void mailBack(String content, Message msg)
	{
		// Build, format, and send a message containing some content to all
		// addresses listed in the REPLY-TO field of the message currently
		// being worked with.

		String host = "smtp.gmail.com";
		String protocol = "smtp";
		int    port = 587;
		//int    port = 465;  Maybe this port number will be useful again
		//                    sometime. It was the original that stopped
		//                    working when I got to school.

		try
		{
			System.out.println(">>> SENDING REPLY");
			InternetAddress[] to_addr = (InternetAddress[]) msg.getReplyTo();

			Properties props = new Properties();
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.transport.protocol", protocol);
			props.put("mail.smtps.host", host);
			Session mailSession = Session.getInstance(props);
			Transport transport = mailSession.getTransport(protocol);
			transport.connect(host, port, this.username, this.password);

			ArrayList<String> messages = this.setLineWidth(content, MAX_MSG_LENGTH);
			int index = 0;
			for (String m : messages)
			{
				if (messages.size() > 1)
					m = "(" + (++index) + "/" + messages.size() + ") " + m;
				MimeMessage message = new MimeMessage(mailSession);
				message.setContent(m, "text/plain");

				message.addRecipients(Message.RecipientType.TO, to_addr);

				transport.sendMessage(message,
						message.getRecipients(Message.RecipientType.TO));

				try {
					Thread.sleep(SECOND);
				} catch (Exception e) {
					System.err.println(">>> IN MAILBACK (1):");
					this.LogException(e);
				}
			}

			transport.close();
		}
		catch (Exception e) {
			System.err.println(">>> IN MAILBACK (2):");
			this.LogException(e);
		}
	}

	private String[] formatCommand(String command)
	{
		ArrayList<String> cmds = this.removeQuotes(command);

		String[] ret = new String[cmds.size()];
		for (int i = 0; i < cmds.size(); i ++)
		{
			ret[i] = cmds.get(i);
		}

		return ret;
	}

	private ArrayList<String> removeQuotes(String string)
	{
		// Split the command into tokens by space characters
		String[] split = string.split(" ");

		// Is the token part of a multi-word argument?
		// Multi word arguments are denoted by quotations
		boolean multi = false;

		String temp = "";
		ArrayList<String> cmds = new ArrayList<String>();


		for (String s : split)
		{
			if (s.startsWith("\"") && s.endsWith("\""))
			{
				s = s.substring(1, s.length()-1);
			}

			if (s.startsWith("\"") ^ s.endsWith("\""))
			{
				multi = !multi;
				s = this.replaceAll(s, "\"", "");
			}

			temp += s;

			if (multi)
			{
				temp += " ";
			}
			else
			{
				temp = temp.trim();
				if (temp.length() > 0)
				{
					cmds.add(temp);
					temp = "";
				}
			}
		}

		return cmds;
	}

	private void printCommand(String[] cmds)
	{
		for (String s : cmds)
		{
			if (s.contains(" "))
				System.out.print("\"" + s + "\" ");
			else
				System.out.print(s + " ");
		}

		System.out.println();
	}

	private ArrayList<String> setLineWidth(String message, int width)
	{
		String remainder = message;

		ArrayList<String> final_lines = new ArrayList<String>();

		while (remainder.length() > width)
		{
			int space_index = remainder.substring(0, width).lastIndexOf(" ");
			if (space_index == -1)
			{
				String line = remainder.substring(0, width).trim();
				remainder = remainder.substring(width);
				final_lines.add(line);
			}
			else
			{
				String line = remainder.substring(0, space_index).trim();
				remainder = remainder.substring(space_index);
				final_lines.add(line);
			}
		}
		final_lines.add(remainder);

		return final_lines;
	}

	private void LogException(Exception e)
	{
		this.still_resting = false;
		e.printStackTrace();
		this.last_exception = e.toString();
	}
}
