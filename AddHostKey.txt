/*
The process involves creating an instance of JSch, parsing the host key, and then adding it to the HostKeyRepository.
*/

import com.jcraft.jsch.*;
import java.util.Base64;

public class AddHostKey {

    public static void main(String[] args) {
        try {
            // Hostname of the TIBCO Mailbox server
            String hostname = "your-tibco-mailbox-server-ip-or-hostname";

            // Host key in Base64 format (you should replace this with the actual key)
            String keyString = "AAAAB3NzaC1yc2EAAAABIwAAAQEArb9O4v1Hj...";

            // Parse the Base64 encoded host key
            byte[] key = Base64.getDecoder().decode(keyString);

            // Create a new JSch instance
            JSch jsch = new JSch();

            // Create a HostKey instance
            HostKey hostKey = new HostKey(hostname, key);

            // Add the host key to the HostKeyRepository
            jsch.getHostKeyRepository().add(hostKey, null);

            System.out.println("Host key added successfully!");

            // Example usage: Connecting to the server (Optional)
            Session session = jsch.getSession("your-username", hostname, 22);
            session.setPassword("your-password");

            // Disable StrictHostKeyChecking to automatically add new host keys
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();
            System.out.println("Connected to the server!");

            // Disconnect the session
            session.disconnect();

        } catch (JSchException e) {
            e.printStackTrace();
        }
    }
}
