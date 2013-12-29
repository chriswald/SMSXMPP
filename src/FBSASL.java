import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;

import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.sasl.SASLMechanism;
import org.jivesoftware.smack.util.Base64;

/**
 * Author: Chris Wald
 * Date: 8/13/13
 * Time: 11:37 AM
 */
public class FBSASL extends SASLMechanism {

    public FBSASL(SASLAuthentication saslAuthentication) {
        super(saslAuthentication);
    }

    protected void authenticate() throws IOException, XMPPException {
        String[] mechanisms = { getName() };
        Map<String, String> props = new HashMap<String, String>();
        sc = Sasl.createSaslClient(mechanisms, null, "xmpp", hostname, props, this);

        super.authenticate();
    }

    public void authenticate(String username, String host, CallbackHandler cbh) throws IOException, XMPPException {
        String[] mechanisms = { getName() };
        Map<String,String> props = new HashMap<String,String>();
        sc = Sasl.createSaslClient(mechanisms, null, "xmpp", host, props, cbh);
        super.authenticate();
    }

    protected String getName() {
        return "DIGEST-MD5";
    }

    public void challengeReceived(String challenge) throws IOException {
        // Build the challenge response stanza encoding the response text
        StringBuilder stanza = new StringBuilder();

        byte response[];
        if (challenge != null) {
            response = sc.evaluateChallenge(Base64.decode(challenge));
        } else {
            response = sc.evaluateChallenge(null);
        }

        String authenticationText="";

        if (response != null) { // fix from 3.1.1
            authenticationText = Base64.encodeBytes(response, Base64.DONT_BREAK_LINES);
            if (authenticationText.equals("")) {
                authenticationText = "=";
            }
        }

        stanza.append("<response xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\">");
        stanza.append(authenticationText);
        stanza.append("</response>");

        final String r = stanza.toString();

        // Send the authentication to the server
        getSASLAuthentication().send(new Packet() {
            @Override
            public String toXML() {
                return r;
            }
        });
    }
}