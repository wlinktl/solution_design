import com.jcraft.jsch.*;
import java.util.Base64;
import java.util.Properties;

public class ConnectToTibcoMailbox {

    public static void main(String[] args) {
        try {
            // Server details
            String hostname = "your-tibco-mailbox-server-ip-or-hostname";
            String username = "your-username";
            int port = 22;  // Default SSH port

            // Public key of the server in Base64 format
            String serverHostKeyBase64 = "AAAAB3NzaC1yc2EAAAABIwAAAQEArb9O4v1Hj...";

            // Parse the Base64 encoded host key
            byte[] serverHostKey = Base64.getDecoder().decode(serverHostKeyBase64);

            // Initialize JSch
            JSch jsch = new JSch();

            // Create a HostKey instance
            HostKey hostKey = new HostKey(hostname, serverHostKey);

            // Add the server's host key to the host key repository
            jsch.getHostKeyRepository().add(hostKey, null);

            // Create a session
            Session session = jsch.getSession(username, hostname, port);

            // Use public key authentication
            String privateKeyPath = System.getProperty("user.home") + "/.ssh/id_rsa";  // Path to user's private key
            jsch.addIdentity(privateKeyPath);

            // Configure session
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");  // Enable in production with proper key management
            session.setConfig(config);

            // Connect to the server
            session.connect();

            System.out.println("Connected to the TIBCO Mailbox server!");

            // Perform SFTP operations (example: list files in the root directory)
            Channel channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftpChannel = (ChannelSftp) channel;
            sftpChannel.ls("/").forEach(System.out::println);

            // Disconnect
            sftpChannel.disconnect();
            session.disconnect();

        } catch (JSchException | SftpException e) {
            e.printStackTrace();
        }
    }
}
