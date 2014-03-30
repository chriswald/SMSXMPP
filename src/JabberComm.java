import org.jivesoftware.smack.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class JabberComm {
    private String recipient;
    private Map<String, Chat> chats = new HashMap<>();
    private ArrayList<Roster> rosters = new ArrayList<>();
    private ArrayList<Connection> connections = new ArrayList<>();
    private ArrayList<JabberLogin> logins = new ArrayList<>();
    private MessageListener messageListener;

    public boolean Login(JabberLogin login, String username, String password) {
        boolean success = login.Login(username, password);
        if (!success)
            return false;

        Connection connection = login.getConnection();
        Roster roster = connection.getRoster();
        FBRosterListener rosterListener = new FBRosterListener();
        roster.addRosterListener(rosterListener);
        rosters.add(roster);
        logins.add(login);
        connections.add(connection);
        return true;
    }

    public boolean Logout() {
        for (JabberLogin login : logins)
            login.Logout();
        return true;
    }

    public boolean SendMessage(String s) {
        if (s.isEmpty())
            return true;

        if (this.recipient == null || this.recipient.isEmpty()) {
            return false;
        }
        try {
            Chat chat = chats.get(this.recipient);
            chat.sendMessage(s);
        } catch (XMPPException e) {
            return false;
        } catch (NullPointerException e) {
            return false;
        }
        return true;
    }

    public boolean SetRecipient(String recipient) {
        if (recipient.isEmpty())
            return false;

        String new_recip = null;
        for (Roster roster : rosters) {
            for (RosterEntry entry : roster.getEntries()) {
                if (entry.getName().toLowerCase().contains(recipient.toLowerCase()) ||
                        entry.getUser().equals(recipient)) {
                    new_recip = entry.getUser();
                    // If the user was found is is available on this service, switch to this user.
                    // Otherwise keep searching in case they're available on some other service.
                    if (roster.getPresence(new_recip).isAvailable()) {
                        this.recipient = new_recip;
                        return true;
                    }
                }
            }
        }

        // The user was found but is not available on any service. Switch to
        // them anyway.
        if (new_recip != null) {
            this.recipient = new_recip;
            return true;
        } else {
            return false;
        }
    }

    public String GetRecipient() {
        return this.recipient;
    }

    public String GetRecipientFullName() {
        for (Roster roster : rosters) {
            RosterEntry entry = roster.getEntry(this.recipient);
            if (entry != null)
                return entry.getName().trim();
        }
        return null;
    }

    public void SetMessageListener(MessageListener messageListener) {
        Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all);
        // Remove the old message listeners
        for (Object o : chats.entrySet()) {
            Map.Entry pairs = (Map.Entry) o;
            ((Chat) (pairs.getValue())).removeMessageListener(this.messageListener);
        }
        this.chats.clear();

        // Set up the new message listener.
        this.messageListener = messageListener;
        for (Connection connection : connections) {
            ChatManager chatManager = connection.getChatManager();
            for (Roster roster : rosters) {
                for (RosterEntry entry : roster.getEntries()) {
                    this.chats.put(entry.getUser(), chatManager.createChat(entry.getUser(), this.messageListener));
                }
            }
        }
    }

    public ArrayList<Roster> getRoster() {
        return rosters;
    }
}
