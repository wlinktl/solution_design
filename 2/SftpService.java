package com.example.sftp.service;

import com.jcraft.jsch.ChannelSftp;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class SftpService {

    private final ChannelSftp sftpChannel;

    @Value("${sftp.remote.directory}")
    private String remoteDirectory;

    @Value("${sftp.local.directory}")
    private String localDirectory;

    @Value("${pgp.privateKey}")
    private String pgpPrivateKey;

    @Value("${pgp.passphrase}")
    private String pgpPassphrase;

    public SftpService(ChannelSftp sftpChannel) {
        this.sftpChannel = sftpChannel;
    }

    @Scheduled(cron = "${sftp.scheduler.cron}")
    public void downloadAndDecryptFiles() throws Exception {
        sftpChannel.cd(remoteDirectory);
        sftpChannel.ls("*").forEach(file -> {
            ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) file;
            String filename = entry.getFilename();
            try (InputStream inputStream = sftpChannel.get(filename);
                 FileOutputStream outputStream = new FileOutputStream(localDirectory + "/" + filename)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                decryptFile(localDirectory + "/" + filename);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void decryptFile(String filePath) throws Exception {
        try (InputStream keyIn = new FileInputStream(pgpPrivateKey);
             InputStream in = new FileInputStream(filePath)) {
            PGPObjectFactory pgpF = new PGPObjectFactory(PGPUtil.getDecoderStream(in), new JcaKeyFingerprintCalculator());
            PGPEncryptedDataList enc;
            Object o = pgpF.nextObject();
            if (o instanceof PGPEncryptedDataList) {
                enc = (PGPEncryptedDataList) o;
            } else {
                enc = (PGPEncryptedDataList) pgpF.nextObject();
            }

            Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;
            while (sKey == null && it.hasNext()) {
                pbe = it.next();
                sKey = findSecretKey(keyIn, pbe.getKeyID(), pgpPassphrase.toCharArray());
            }

            if (sKey == null) {
                throw new IllegalArgumentException("Secret key for message not found.");
            }

            InputStream clear = pbe.getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(sKey));
            PGPObjectFactory plainFact = new PGPObjectFactory(clear, new JcaKeyFingerprintCalculator());
            Object message = plainFact.nextObject();

            if (message instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) message
