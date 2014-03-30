import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;

import java.util.ArrayList;

public class FBMessageListener implements MessageListener {
    private SMTPSMS smtpsms;
    private ArrayList<Roster> rosters = new ArrayList<>();
    private boolean send = true;
    private String last_recipient;

    public FBMessageListener(SMTPSMS smtpsms, ArrayList<Roster> rosters) {
        this.smtpsms = smtpsms;
        this.rosters = rosters;
    }

    public void Start() {
        send = true;
    }

    public void Stop() {
        send = false;
    }

    public String getLastRecipient() {
        return last_recipient;
    }

    @Override
    public void processMessage(Chat chat, Message message) {
        String name = getParticipantName(chat.getParticipant());
        this.last_recipient = chat.getParticipant();
        String body = message.getBody();

        if (body == null)
            return;

        if (this.send && name != null) {
            boolean success = smtpsms.Write(name + ">>\r\n" + body);
            if (!success) {
                System.out.println("Relay Message... [FAIL]");
            }
        } else {
            System.out.println("Relay Message... [BLOCKED]");
        }
    }

    private String getParticipantName(String participant) {
        for (Roster roster : rosters) {
            RosterEntry entry = roster.getEntry(participant);
            if (entry != null)
                return entry.getName();
        }
        return null;
    }
}
