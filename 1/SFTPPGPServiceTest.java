package com.example.sftppgpservice.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.bouncycastle.openpgp.PGPException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.Vector;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SpringBootTest
public class SFTPPGPServiceTest {

    @InjectMocks
    private SFTPPGPService sftpPGPService;

    @Mock
    private JSch jsch;

    @Mock
    private Session session;

    @Mock
    private ChannelSftp sftpChannel;

    @Mock
    private PGPSecretKeyRingCollection pgpSec;

    @Value("${sftp.localDir}")
    private String localDir;

    @BeforeEach
    public void setup() throws Exception {
        ReflectionTestUtils.setField(sftpPGPService, "sftpHost", "sftp.example.com");
        ReflectionTestUtils.setField(sftpPGPService, "sftpPort", 22);
        ReflectionTestUtils.setField(sftpPGPService, "sftpUsername", "sftpuser");
        ReflectionTestUtils.setField(sftpPGPService, "sftpPrivateKey", "/path/to/private/key");
        ReflectionTestUtils.setField(sftpPGPService, "sftpRemoteDir", "/remote/dir");
        ReflectionTestUtils.setField(sftpPGPService, "sftpLocalDir", localDir);
        ReflectionTestUtils.setField(sftpPGPService, "pgpPrivateKeyPath", "/path/to/pgp/privatekey.asc");
        ReflectionTestUtils.setField(sftpPGPService, "pgpPassphrase", "your_passphrase");

        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testExecuteSFTPJob() throws Exception {
        when(jsch.getSession(anyString(), anyString(), anyInt())).thenReturn(session);
        when(session.openChannel("sftp")).thenReturn(sftpChannel);
        when(sftpChannel.ls(anyString())).thenReturn(mock(Vector.class));

        doNothing().when(session).connect();
        doNothing().when(sftpChannel).connect();
        doNothing().when(sftpChannel).get(anyString(), anyString());
        doNothing().when(sftpChannel).disconnect();
        doNothing().when(session).disconnect();

        sftpPGPService.executeSFTPJob();

        verify(sftpChannel, times(1)).ls(anyString());
        verify(sftpChannel, times(1)).get(anyString(), anyString());
        verify(sftpChannel, times(1)).disconnect();
        verify(session, times(1)).disconnect();
    }

    @Test
    public void testDecryptFile() throws Exception {
        InputStream mockInputStream = mock(FileInputStream.class);
        when(mockInputStream.read(any(byte[].class), anyInt(), anyInt())).thenReturn(-1);

        doReturn(mockInputStream).when(sftpPGPService).readFile(anyString());

        byte[] encryptedData = new byte[]{};
        byte[] decryptedData = new byte[]{};

        try (MockedStatic<PGPCryptoExample> pgpCryptoMock = mockStatic(PGPCryptoExample.class)) {
            pgpCryptoMock.when(() -> PGPCryptoExample.decryptMessage(eq(encryptedData), any()))
                    .thenReturn(decryptedData);

            sftpPGPService.decryptFile("/path/to/encrypted/file", "/path/to/decrypted/file");

            verify(mockInputStream, times(1)).close();
        }
    }
}
