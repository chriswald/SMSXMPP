import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.RosterEntry;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Author: Chris Wald
 * Date: 8/13/13
 * Time: 11:09 AM
 */
public class SMSXMPP {
    private static String FB_CHAT_USER;
    private static String FB_CHAT_PASS;
    private static String SMS_USER;
    private static String SMS_PASS;

    private SMTPSMS smtpsms;
    private FBMessageListener messageListener;

    private JabberComm comm = new JabberComm();
    private JabberLogin fblogin = new FacebookLogin();
    private JabberLogin googlelogin = new GoogleTalkLogin();
    private ArrayList<JabberLogin> services = new ArrayList<JabberLogin>();

    private static String serviceLoginInfoFile;
    private ArrayList<ServiceLoginInfo> loginInfo = new ArrayList<ServiceLoginInfo>();

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage:");
            System.out.println("  smsxmpp <filename> <phone username> <phone password>");
            return;
        }

        serviceLoginInfoFile = args[0];
        SMS_USER = args[1];
        SMS_PASS = args[2];

        new SMSXMPP().run();
    }

    private boolean parseFile(String filename) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            ServiceLoginInfo info = null;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    // Store the service name
                    info = new ServiceLoginInfo();
                    info.serviceName = line.substring(0, line.length()-1).substring(1).trim();

                    // Store the username
                    line = br.readLine();
                    if (line != null) {
                        info.username = line.trim();
                    } else {
                        System.out.println("Could not find username for " + info.serviceName);
                        return false;
                    }

                    // Store the password
                    line = br.readLine();
                    if (line != null) {
                        info.password = line.trim();
                    } else {
                        System.out.println("Could not find password for " + info.serviceName);
                        return false;
                    }

                    this.loginInfo.add(info);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Could not open file " + filename);
            return false;
        } catch (IOException e) {
            System.out.println("Could not read file " + filename);
            return false;
        }

        return true;
    }

    private void run() {
        Connection.DEBUG_ENABLED = false;
        boolean success = false;

        parseFile(serviceLoginInfoFile);
        services.add(this.fblogin);
        services.add(this.googlelogin);

        // Login to Chats.
        for (ServiceLoginInfo info : loginInfo) {
            for (JabberLogin login : services) {
                if (info.serviceName.equals(login.getServiceName())) {
                    if (ChatLogin(login, info.username, info.password))
                        success = true; // Got at least one service logged in.
                }
            }
        }
        if (!success) return;

        // Open a new communication channel to a phone.
        success = OpenPhoneComm();
        if (!success) return;

        // Test input channel.
        success = RegisterPhone();
        if (!success) return;

        // Test output channel.
        success = TestPhoneComm();
        if (!success) return;

        // Set up channels to listen for uninitiated chats.
        success = CreateChatListeners();
        if (!success) return;

        System.out.println("-- Configuration Done. --");

        while (true) {
            try {
                GetPhoneMessages();
                Wait();
            } catch (Exception e) {
                System.out.println(e.getMessage());
                break;
            }
        }
    }

    // Login to Chat
    private boolean ChatLogin(JabberLogin login, String user, String pass) {
        long start;
        start = System.currentTimeMillis();
        System.out.print("Logging into " + login.getServiceName() + " as " + user + "...");

        boolean success = comm.Login(login, user, pass);
        if (success) {
            System.out.print(" [Done ");
            System.out.println(TimeDiffString(start, System.currentTimeMillis()) + "]");
            return true;
        } else {
            System.out.println(" [FAIL]");
            return false;
        }
    }

    private boolean OpenPhoneComm() {
        long start;
        start = System.currentTimeMillis();
        System.out.print("Opening phone communication...");
        this.smtpsms = new SMTPSMS(SMS_USER, SMS_PASS);
        boolean success = this.smtpsms.Open();
        if (success) {
            System.out.print(" [Done ");
            System.out.println(TimeDiffString(start, System.currentTimeMillis()) + "]");
            return true;
        } else {
            System.out.println(" [FAIL]");
            return false;
        }
    }

    private boolean RegisterPhone() {
        System.out.println("Waiting for phone.");
        System.out.println("Please send a text message to " + SMS_USER + "@gmail.com");

        // Wait for a message to come in.
        while (this.smtpsms.Read() == null) {
            Wait();
        }
        System.out.println("Configured for " + this.smtpsms.GetRecipient());
        return true;
    }

    private boolean TestPhoneComm() {
        System.out.print("Testing communication to phone...");
        String setup_message = "Systems Functional\n";
        boolean success = this.smtpsms.Write(setup_message);
        if (success) {
            System.out.println(" [Done]");
            return true;
        } else {
            System.out.println(" [FAIL]");
            return false;
        }
    }

    private boolean CreateChatListeners() {
        this.messageListener = new FBMessageListener(smtpsms, comm.getRoster());
        comm.SetMessageListener(this.messageListener);
        return true;
    }

    private void GetPhoneMessages() {
        String contents = this.smtpsms.Read();
        if (contents != null) {
            if (contents.startsWith(">>")) {
                ProcessCommands(contents);
            } else if (contents.startsWith("!")) {
                ChangeRecipient(this.messageListener.getLastRecipient());
                contents = contents.substring("!".length()).trim();
                SendMessageToChat(contents);
            } else {
                SendMessageToChat(contents);
            }
        }
    }

    private void ProcessCommands(String command) {
        if (command.startsWith(">>")) {
            command = command.substring(2);
        }

        command = command.trim().toLowerCase();

        if (command.equals("start")) {
            // Start sending chat messages to the phone.
            SendStartCommand();
        } else if (command.equals("stop")) {
            // Stop sending chat messages to the phone.
            SendStopCommand();
        } else if (command.equals("terminate")) {
            // Close the program "normally".
            TerminateServer();
        } else {
            ChangeRecipientCommand(command);
        }
    }

    // Handles >>start
    private void SendStartCommand() {
        messageListener.Start();
        System.out.println("Send to phone [START]");
        boolean success = smtpsms.Write("You will now receive messages.");
        if (!success) {
            System.out.println("Notification delivery [FAIL]");
        }
    }

    // Handles >>stop
    private void SendStopCommand() {
        messageListener.Stop();
        System.out.println("Send to phone [STOP]");
        boolean success = smtpsms.Write("You will no longer receive messages.");
        if (!success) {
            System.out.println("Notification delivery [FAIL]");
        }
    }

    // Handles >>terminate
    private void TerminateServer() {
        System.out.print("System TERMINATION...");
        smtpsms.Write("Are you sure you wish to terminate the server?");
        String response;
        while ((response = smtpsms.Read()) == null) {
            Wait();
        }
        if (response.toLowerCase().trim().equals("yes")) {
            boolean success = smtpsms.Write("Server going down NOW.");
            if (!success) {
                System.out.println("Notification delivery [FAIL]");
            }
            System.out.println(" [Done]");
            //this.connection.disconnect();
            this.comm.Logout();
            this.smtpsms.Close();
            System.exit(0);
        } else {
            System.out.println(" [ABORT]");
        }
    }

    // Handles >>name
    private void ChangeRecipientCommand(String command) {
        String name = ChangeRecipient(command);
        if (name != null) {
            boolean success = this.smtpsms.Write("Changed to " + name);
            if (!success) {
                System.out.println("Notification delivery [FAIL]");
            }
        } else {
            boolean success = this.smtpsms.Write(command + " not found.");
            if (!success) {
                System.out.println("Notification delivery [FAIL]");
            }
        }
    }

    private String ChangeRecipient(String new_to) {
        if (new_to.isEmpty())
            return null;

        System.out.print("Changing recipient...");

        boolean success = this.comm.SetRecipient(new_to);
        if (success) {
            System.out.println(" [Done]");
            return this.comm.GetRecipientFullName();
        } else {
            System.out.println(" [FAIL]");
            return null;
        }
    }

    private boolean SendMessageToChat(String s) {
        boolean success = this.comm.SendMessage(s);
        if (!success) {
            if (!smtpsms.Write("Could not send message. Please try later."))
                System.out.println("Notification delivery [FAIL]");
        }

        return success;
    }

    private void Wait() {
        this.Wait(5000); // 5 sec
    }

    private void Wait(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Do nothing.
        }
    }

    private String TimeDiffString(long start, long end) {
        long diff = end - start;
        double secs = (double) diff / 1000d;
        return String.format("%.3g", secs);
    }
}
