package com.example.sftp.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpStatVFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@EnableScheduling
public class SftpServiceTest {

    private ChannelSftp sftpChannel;
    private SftpService sftpService;

    @Value("${sftp.local.directory}")
    private String localDirectory;

    @BeforeEach
    public void setUp() throws Exception {
        sftpChannel = Mockito.mock(ChannelSftp.class);
        sftpService = new SftpService(sftpChannel);
    }

    @Test
    public void testDownloadAndDecryptFiles() throws Exception {
        // Mock the file list on the SFTP server
        Vector<ChannelSftp.LsEntry> fileList = new Vector<>();
        ChannelSftp.LsEntry entry = Mockito.mock(ChannelSftp.LsEntry.class);
        SftpATTRS attrs = Mockito.mock(SftpATTRS.class);
        when(entry.getFilename()).thenReturn("testfile.txt.gpg");
        when(entry.getAttrs()).thenReturn(attrs);
        fileList.add(entry);
        when(sftpChannel.ls(anyString())).thenReturn(fileList);

        // Mock the file download
        String fileContent = "encrypted file content";
        InputStream inputStream = new ByteArrayInputStream(fileContent.getBytes());
        when(sftpChannel.get(anyString())).thenReturn(inputStream);

        // Mock the decryption process
        sftpService.downloadAndDecryptFiles();

        // Verify that the file was downloaded and decrypted
        String downloadedFilePath = localDirectory + "/testfile.txt.gpg";
        File downloadedFile = new File(downloadedFilePath);
        assertTrue(downloadedFile.exists());

        // Clean up
        Files.deleteIfExists(Paths.get(downloadedFilePath));
    }
}
