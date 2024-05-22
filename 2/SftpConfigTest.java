package com.example.sftp.config;

import com.jcraft.jsch.ChannelSftp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class SftpConfigTest {

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.port}")
    private int sftpPort;

    @Value("${sftp.user}")
    private String sftpUser;

    @Value("${sftp.privateKey}")
    private String sftpPrivateKey;

    @Test
    public void testSftpChannelCreation() throws Exception {
        SftpConfig sftpConfig = new SftpConfig();
        ChannelSftp channelSftp = sftpConfig.sftpChannel();
        assertNotNull(channelSftp);
    }
}
