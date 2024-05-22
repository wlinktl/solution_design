package com.example.sftppgpservice.service;

import com.jcraft.jsch.*;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

@Service
public class SFTPPGPService {

    private static final Logger logger = LoggerFactory.getLogger(SFTPPGPService.class);

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.port}")
    private int sftpPort;

    @Value("${sftp.username}")
    private String sftpUsername;

    @Value("${sftp.privateKey}")
    private String sftpPrivateKey;

    @Value("${sftp.remoteDir}")
    private String sftpRemoteDir;

    @Value("${sftp.localDir}")
    private String sftpLocalDir;

    @Value("${pgp.privateKey}")
    private String pgpPrivateKeyPath;

    @Value("${pgp.publicKey}")
    private String pgpPublicKeyPath;

    @Value("${pgp.passphrase}")
    private String pgpPassphrase;

    @Scheduled(cron = "0 */5 * * * ?")
    public void executeSFTPJob() {
        logger.info("Starting SFTP job...");

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(sftpPrivateKey);

            Session session = jsch.getSession(sftpUsername, sftpHost, sftpPort);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            logger.info("Connected to SFTP server.");

            Vector<ChannelSftp.LsEntry> files = sftpChannel.ls(sftpRemoteDir);

            for (ChannelSftp.LsEntry file : files) {
                if (!file.getAttrs().isDir()) {
                    String remoteFilePath = sftpRemoteDir + "/" + file.getFilename();
                    String localFilePath = sftpLocalDir + "/" + file.getFilename();

                    sftpChannel.get(remoteFilePath, localFilePath);
                    logger.info("Downloaded file: {}", file.getFilename());

                    // Decrypt the file
                    decryptFile(localFilePath, localFilePath + ".decrypted");
                }
            }

            sftpChannel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            logger.error("Error during SFTP job", e);
        }
    }

    private void decryptFile(String inputFilePath, String outputFilePath) throws IOException, PGPException {
        logger.info("Decrypting file: {}", inputFilePath);

        // Load PGP keys
        InputStream keyIn = new FileInputStream(pgpPrivateKeyPath);
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyIn), new JcaKeyFingerprintCalculator());

        PGPSecretKey secretKey = null;
        Iterator<PGPSecretKeyRing> keyRingIter = pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPSecretKeyRing keyRing = keyRingIter.next();
            Iterator<PGPSecretKey> keyIter = keyRing.getSecretKeys();
            while (keyIter.hasNext()) {
                secretKey = keyIter.next();
                if (secretKey.isSigningKey()) {
                    break;
                }
            }
        }
        keyIn.close();

        if (secretKey == null) {
            throw new IllegalArgumentException("No private key found in the key ring.");
        }

        // Decrypt the file using the PGPCryptoExample methods (adjust accordingly if necessary)
        byte[] encryptedData = readFile(inputFilePath);
        PGPPrivateKey privateKey = secretKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(pgpPassphrase.toCharArray()));
        byte[] decryptedData = PGPCryptoExample.decryptMessage(encryptedData, privateKey);

        writeFile(outputFilePath, decryptedData);
        logger.info("Decrypted file saved to: {}", outputFilePath);
    }

    private byte[] readFile(String filePath) throws IOException {
        try (InputStream is = new FileInputStream(filePath);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
