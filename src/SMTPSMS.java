import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.util.*;

/**
 * Author: Chris Wald
 * Date: 8/13/13
 * Time: 10:17 AM
 */
public class SMTPSMS {
    private String username, password;
    private String reply_to;

    private Store store;
    private Folder folder;

    private LinkedList<Message> messageQueue = new LinkedList<Message>();

    private static final String SERVER = "imap.gmail.com";

    private Session session;
    private Transport transport;

    private static final int MAX_SMS_LENGTH = 160;

    public SMTPSMS(String username, String password) {
        this.username = username;
        this.password = password;
    }

    private boolean connectOutbound() {
        String host = "smtp.gmail.com";
        String protocol = "smtps";
        int port = 465;

        Properties properties = new Properties();
        properties.put("mail.smtp.starttls.emable", "true");
        properties.put("mail.transport.protocol", protocol);
        properties.put("mail.smtps.host", host);
        properties.put("mail.smtp.port", Integer.toString(port));
        this.session = Session.getInstance(properties);

        try {
            this.transport = this.session.getTransport(protocol);
            this.transport.connect(host, port, this.username, this.password);
        } catch (NoSuchProviderException e) {
            return false;
        } catch (MessagingException e) {
            return false;
        }

        return true;
    }

    public boolean IsConnected() {
        return this.transport != null && this.transport.isConnected();
    }

    public void SetRecipient(String recipient) {
        this.reply_to = recipient;
    }

    public String GetRecipient() {
        return this.reply_to;
    }

    public boolean Open() {
        return this.GetStore() && this.GetFolder();
    }

    public boolean Close() {
        try {
            this.store.close();
            this.folder.close(true);
            this.transport.close();
        } catch (MessagingException e) {
            return false;
        }
        return true;
    }

    public boolean Write(String message) {
        if (!this.IsConnected())
            this.connectOutbound();

        try {
            InternetAddress[] to_address = {new InternetAddress(this.reply_to)};

            // Send only 160 characters at a time.
            while (message.length() > 0) {
                MimeMessage mimeMessage = new MimeMessage(this.session);

                String chunk;
                chunk = message.substring(0, MIN(message.length(), MAX_SMS_LENGTH));
                if (chunk.lastIndexOf(" ") != -1 && chunk.length() == MAX_SMS_LENGTH) {
                    chunk = chunk.substring(0, chunk.lastIndexOf(" ")).trim();
                }

                mimeMessage.setContent(chunk, "text/plain");
                mimeMessage.addRecipients(Message.RecipientType.TO, to_address);
                this.transport.sendMessage(mimeMessage, to_address);
                message = message.substring(chunk.length()).trim();
            }
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
            return false;
        } catch (AddressException e) {
            System.out.println(e.getMessage());
            return false;
        } catch (NoSuchProviderException e) {
            System.out.println(e.getMessage());
            return false;
        } catch (MessagingException e) {
            System.out.println(e.getMessage());
            return false;
        }

        return true;
    }

    public String Read() {
        Message[] messages = this.GetMessages();
        if (messages.length == 0)
            return null;

        Collections.addAll(messageQueue, messages);

        try {
            Message message = messageQueue.remove();
            SetRecipient(message.getFrom()[0].toString());
            Object msg_contents = message.getContent();
            if (!(msg_contents instanceof Multipart)) {
                return getMessageContents(message);
            }
        } catch (SocketTimeoutException e) {
            return null;
        } catch (MessagingException e) {
            return null;
        } catch (IOException e) {
            return null;
        }

        return null;
    }

    private String getMessageContents(Part part) throws IOException, MessagingException {
        String disposition = part.getDisposition();
        String content_type = part.getContentType().toLowerCase();

        if (disposition == null) {
            if (content_type.contains("text/plain")) {
                return (String) part.getContent();
            }
        }

        return "";
    }

    private boolean GetStore() {
        try {
            Properties properties = System.getProperties();
            Session session = Session.getDefaultInstance(properties, null);
            this.store = session.getStore("imaps");

            if (!this.store.isConnected())
                this.store.connect(SERVER, this.username, this.password);
        } catch (NoSuchProviderException e) {
            return false;
        } catch (MessagingException e) {
            return false;
        }
        return true;
    }

    private boolean GetFolder() {
        try {
            String folder_name = "Inbox";
            this.folder = this.store.getFolder(folder_name);
            this.folder.open(Folder.READ_WRITE);
        } catch (MessagingException e) {
            return false;
        }
        return true;
    }

    private Message[] GetMessages() {
        FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        try {
            if (!this.store.isConnected())
                this.GetStore();
            if (!this.folder.isOpen())
                this.GetFolder();
            return this.folder.search(ft);
        } catch (MessagingException e) {
            return new Message[0];
        }
    }

    private int MIN(int a, int b) {
        return a < b ? a : b;
    }
}
