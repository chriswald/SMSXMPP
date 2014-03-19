import org.jivesoftware.smack.Connection;

public interface JabberLogin {
    public boolean    Login(String username, String password);
    public boolean    Logout();
    public Connection getConnection();
    public String     getServiceName();
}
