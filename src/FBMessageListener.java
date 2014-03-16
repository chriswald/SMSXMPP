import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;

public class FBMessageListener implements MessageListener {
    private SMTPSMS smtpsms;
    private Roster roster;
    private boolean send = true;
    private String last_recipient;

    public FBMessageListener(SMTPSMS smtpsms, Roster roster) {
        this.smtpsms = smtpsms;
        this.roster = roster;
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
        String name = roster.getEntry(chat.getParticipant()).getName();
        this.last_recipient = chat.getParticipant();
        String body = message.getBody();

        if (body == null)
            return;

        if (this.send) {
            boolean success = smtpsms.Write(name + ">>\r\n" + body);
            if (!success) {
                System.out.println("Relay Message... [FAIL]");
            }
        } else {
            System.out.println("Relay Message... [BLOCKED]");
        }
    }
}
