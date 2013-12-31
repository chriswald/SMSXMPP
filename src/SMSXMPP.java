import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Presence;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * Author: Chris Wald
 * Date: 8/13/13
 * Time: 11:09 AM
 */
public class SMSXMPP {
    private String recipient = "";
    private static String FB_CHAT_USER;
    private static String FB_CHAT_PASS;
    private static String SMS_USER;
    private static String SMS_PASS;

    private Roster roster;
    private SMTPSMS smtpsms;
    private Connection connection;
    private FBMessageListener messageListener;
    private FBRosterListener rosterListener;

    private Map<String, Chat> chats = new HashMap<String, Chat>();

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage:");
            System.out.println("  smsxmpp <chat username> <chat password> <gmail username> <gmail password>");
            return;
        }

        FB_CHAT_USER = args[0];
        FB_CHAT_PASS = args[1];
        SMS_USER     = args[2];
        SMS_PASS     = args[3];

        new SMSXMPP().run();
    }

    private void run() {
        // Login to Facebook Chat.
        FacebookLogin(FB_CHAT_USER, FB_CHAT_PASS);

        // Open a new communication channel to a phone.
        OpenPhoneComm();

        // Test input channel.
        RegisterPhone();

        // Test output channel.
        TestPhoneComm();

        // Set up channels to listen for uninitiated chats.
        CreateChatListeners();

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

    /*private void PrintRoster() {
        for (RosterEntry entry : this.roster.getEntries()) {
            System.out.println(entry.getName() + " " + entry.getUser());
        }
    }*/

    private void OpenPhoneComm() {
        System.out.print("Opening phone communication...");
        this.smtpsms = new SMTPSMS(SMS_USER, SMS_PASS);
        boolean success = this.smtpsms.Open();
        if (!success) {
            System.out.println(" [FAIL]");
            System.exit(-2);
        } else {
            System.out.println(" [Done]");
        }
    }

    private void TestPhoneComm() {
        System.out.print("Testing communication to phone...");
        String setup_message = "Systems Functional\n";
        boolean success = this.smtpsms.Write(setup_message);
        if (success) {
            System.out.println(" [Done]");
        } else {
            System.out.println(" [FAIL]");
            System.exit(-3);
        }
    }

    private void CreateChatListeners() {
        Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all);
        messageListener = new FBMessageListener(smtpsms, roster);
        ChatManager chatManager = connection.getChatManager();
        for (RosterEntry entry : roster.getEntries()) {
            this.chats.put(entry.getUser(), chatManager.createChat(entry.getUser(), messageListener));
        }
    }

    private void GetPhoneMessages() {
        String contents = this.smtpsms.Read();
        if (contents != null) {
            if (contents.startsWith(">>")) {
                ProcessCommands(contents);
            } else if (contents.startsWith("!")) {
                ChangeRecipient(messageListener.getLastRecipient());
                contents  = contents.substring("!".length()).trim();
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

    private void SendStartCommand() {
        messageListener.Start();
        System.out.println("Send to phone [START]");
        boolean success = smtpsms.Write("You will now receive messages.");
        if (!success) {
            System.out.println("Notification delivery [FAIL]");
        }
    }

    private void SendStopCommand() {
        messageListener.Stop();
        System.out.println("Send to phone [STOP]");
        boolean success = smtpsms.Write("You will no longer receive messages.");
        if (!success) {
            System.out.println("Notification delivery [FAIL]");
        }
    }

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
            this.connection.disconnect();
            this.smtpsms.Close();
            System.exit(0);
        } else {
            System.out.println(" [ABORT]");
        }
    }

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

        for (RosterEntry entry : roster.getEntries()) {
            if (entry.getName().toLowerCase().contains(new_to.toLowerCase()) ||
                entry.getUser().equals(new_to)) {
                this.recipient = entry.getUser();
                System.out.println(" [" + entry.getName() + " " + entry.getUser() + "]");
                return entry.getName();
            }
        }

        System.out.println(" [FAIL]");
        return null;
    }

    private boolean SendMessageToChat(String s) {
        if (s.isEmpty())
            return true;

        if (this.recipient == null || this.recipient.isEmpty()) {
            System.out.println("No recipient set.");
            boolean success = smtpsms.Write("Please set a recipient.");
            if (!success) {
                System.out.println("Notification delivery [FAIL]");
            }
            return false;
        }
        try {
            Chat chat = chats.get(this.recipient);
            chat.sendMessage(s);
        } catch (XMPPException e) {
            boolean success = smtpsms.Write("Could not send message. Please try later.");
            System.out.println("Message send [FAIL]");
            if (!success) {
                System.out.println("Notification delivery [FAIL]");
            }
            return false;
        }
        return true;
    }

    private void RegisterPhone() {
        System.out.println("Waiting for phone.");
        System.out.println("Please send a text message to " + SMS_USER + "@gmail.com");

        // Wait for a message to come in.
        while (this.smtpsms.Read() == null) {
            Wait();
        }
        System.out.println("Configured for " + this.smtpsms.GetRecipient());
    }

    // Login to Facebook Chat
    private void FacebookLogin(String user, String pass) {
        System.out.print("Logging into Facebook as " + FB_CHAT_USER + "...");

        SASLAuthentication.registerSASLMechanism("DIGEST-MD5", FBSASL.class);
        ConnectionConfiguration config = new ConnectionConfiguration("chat.facebook.com", 5222);
        this.connection = new XMPPConnection(config);

        try {
            this.connection.connect();
            this.connection.login(user, pass);
        } catch (XMPPException e) {
            System.out.println(" [FAIL]");
            System.exit(-1);
        }

        this.roster = this.connection.getRoster();
        this.rosterListener = new FBRosterListener();
        this.roster.addRosterListener(this.rosterListener);

        System.out.println(" [Done]");
    }

    private void Wait() {
        this.Wait(1000);
    }

    private void Wait(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Do nothing.
        }
    }
}
