import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class HostKeyManager {

    private static final String SSHD_CONFIG_PATH = "/etc/ssh/sshd_config";
    private static final String KNOWN_HOSTS_PATH = System.getProperty("user.home") + "/.ssh/known_hosts";
    private static final String[] HOST_KEY_PATHS = {
        "/etc/ssh/ssh_host_rsa_key",
        "/etc/ssh/ssh_host_ecdsa_key",
        "/etc/ssh/ssh_host_ed25519_key"
    };

    public static void main(String[] args) {
        try {
            if (!isRoot()) {
                System.out.println("Please run this program as the root user.");
                return;
            }

            generateHostKeys();
            updateSSHDConfig();
            updateKnownHosts("host-server");  // Replace "host-server" with the actual hostname or IP
            restartSSHService();
            System.out.println("Host keys have been added, known_hosts updated, and the SSH service has been restarted.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static boolean isRoot() {
        return "0".equals(System.getProperty("user.name"));
    }

    private static void generateHostKeys() throws IOException, InterruptedException {
        String[] keyTypes = {"rsa", "ecdsa", "ed25519"};
        for (int i = 0; i < HOST_KEY_PATHS.length; i++) {
            Path keyPath = Paths.get(HOST_KEY_PATHS[i]);
            if (Files.notExists(keyPath)) {
                ProcessBuilder pb = new ProcessBuilder(
                    "ssh-keygen",
                    "-t", keyTypes[i],
                    "-f", HOST_KEY_PATHS[i],
                    "-N", ""
                );
                pb.inheritIO();
                Process process = pb.start();
                process.waitFor();
            }
        }
    }

    private static void updateSSHDConfig() throws IOException {
        Path configPath = Paths.get(SSHD_CONFIG_PATH);
        List<String> configLines = Files.readAllLines(configPath);
        List<String> updatedLines = configLines.stream()
            .filter(line -> !line.trim().startsWith("HostKey"))
            .collect(Collectors.toList());

        for (String hostKeyPath : HOST_KEY_PATHS) {
            updatedLines.add("HostKey " + hostKeyPath);
        }

        Files.write(configPath, updatedLines);
    }

    private static void updateKnownHosts(String hostname) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("ssh-keyscan", "-H", hostname);
        pb.redirectOutput(new File(KNOWN_HOSTS_PATH));
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
    }

    private static void restartSSHService() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("sudo", "systemctl", "restart", "ssh");
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
    }
}
