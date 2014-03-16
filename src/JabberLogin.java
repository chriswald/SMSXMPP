import org.jivesoftware.smack.Connection;

/**
 * Created by chris on 3/15/14.
 */
public interface JabberLogin {
    public boolean    Login(String username, String password);
    public boolean    Logout();
    public Connection getConnection();
    public String     getServiceName();
}
