import org.jivesoftware.smack.*;

public class FacebookLogin implements JabberLogin {
    private Connection connection;

    @Override
    public boolean Login(String username, String password) {
        SASLAuthentication.registerSASLMechanism("DIGEST-MD5", FBSASL.class);
        ConnectionConfiguration config = new ConnectionConfiguration("chat.facebook.com", 5222);
        this.connection = new XMPPConnection(config);

        try {
            this.connection.connect();
            this.connection.login(username, password);
        } catch (XMPPException e) {
            return false;
        }

        return true;
    }

    @Override
    public boolean Logout() {
        if (this.connection == null)
            return true;
        this.connection.disconnect();
        return true;
    }

    @Override
    public Connection getConnection() {
        return this.connection;
    }

    @Override
    public String getServiceName() {
        return "Facebook Chat";
    }
}
