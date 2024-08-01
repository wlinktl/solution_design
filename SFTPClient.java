import com.jcraft.jsch.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Properties;

public class SFTPClient {
    private static final String DB_URL = "jdbc:your_database_url";
    private static final String DB_USER = "your_db_user";
    private static final String DB_PASSWORD = "your_db_password";
    private static final int MAX_RETRIES = 3;

    public static void main(String[] args) {
        while (true) {
            processPendingDownloads();
            try {
                Thread.sleep(60000); // Sleep for 1 minute
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void processPendingDownloads() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT * FROM file_downloads WHERE status = 'PENDING' OR (status = 'FAILED' AND retry_count < ? AND next_attempt <= ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, MAX_RETRIES);
                stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String fileName = rs.getString("file_name");
                        downloadFile(conn, id, fileName);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void downloadFile(Connection conn, int id, String fileName) {
        String sftpHost = "your_sftp_host";
        String sftpUser = "your_sftp_user";
        String sftpPassword = "your_sftp_password";
        String sftpDirectory = "your_sftp_directory";
        String localDirectory = "your_local_directory";

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channelSftp = null;

        try {
            session = jsch.getSession(sftpUser, sftpHost, 22);
            session.setPassword(sftpPassword);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            channelSftp.cd(sftpDirectory);
            channelSftp.get(fileName, localDirectory + "/" + fileName);

            updateDownloadStatus(conn, id, "SUCCESS", null);

        } catch (JSchException | SftpException e) {
            handleDownloadFailure(conn, id, e.getMessage());
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private static void updateDownloadStatus(Connection conn, int id, String status, String errorMessage) {
        String updateQuery = "UPDATE file_downloads SET status = ?, retry_count = retry_count + 1, last_attempt = ?, next_attempt = ?, error_message = ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            stmt.setString(1, status);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setTimestamp(3, status.equals("SUCCESS") ? null : Timestamp.valueOf(LocalDateTime.now().plusMinutes(5))); // Retry after 5 minutes if failed
            stmt.setString(4, errorMessage);
            stmt.setInt(5, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void handleDownloadFailure(Connection conn, int id, String errorMessage) {
        updateDownloadStatus(conn, id, "FAILED", errorMessage);
    }
}
