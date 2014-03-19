import org.jivesoftware.smack.*;

public class GoogleTalkLogin implements JabberLogin {
    private Connection connection;

    @Override
    public boolean Login(String username, String password) {
        if (!username.endsWith("@gmail.com"))
            username += "@gmail.com";
        ConnectionConfiguration config = new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
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
        return "Google Talk";
    }
}
