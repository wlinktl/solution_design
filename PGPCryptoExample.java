import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;

public class PGPCryptoExample {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        // Load keys
        PGPPrivateKey privateKey = KeyLoader.loadPrivateKey("path/to/privatekey.asc", "passphrase".toCharArray());
        PGPPublicKey publicKey = KeyLoader.loadPublicKey("path/to/publickey.asc");

        // Original message
        String message = "Hello, this is a secret message!";

        // Sign the message
        byte[] signedData = signMessage(message.getBytes(), privateKey, publicKey);

        // Encrypt the signed message
        byte[] encryptedData = encryptMessage(signedData, publicKey);

        // Decrypt the message
        byte[] decryptedData = decryptMessage(encryptedData, privateKey);

        // Verify the signature
        boolean isVerified = verifyMessage(decryptedData, publicKey);

        System.out.println("Decrypted Message: " + new String(decryptedData));
        System.out.println("Signature Verified: " + isVerified);
    }

    public static byte[] signMessage(byte[] data, PGPPrivateKey privateKey, PGPPublicKey publicKey) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArmoredOutputStream armoredOut = new ArmoredOutputStream(out);

        PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
        OutputStream cos = comData.open(armoredOut);

        PGPSignatureGenerator sGen = new PGPSignatureGenerator(
                new JcaPGPContentSignerBuilder(publicKey.getAlgorithm(), PGPUtil.SHA256).setProvider("BC"));
        sGen.init(PGPSignature.BINARY_DOCUMENT, privateKey);

        BCPGOutputStream bOut = new BCPGOutputStream(cos);
        sGen.generateOnePassVersion(false).encode(bOut);

        PGPLiteralDataGenerator lGen = new PGPLiteralDataGenerator();
        OutputStream lOut = lGen.open(bOut, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, data.length, new Date());
        lOut.write(data);
        sGen.update(data);
        lGen.close();

        sGen.generate().encode(bOut);
        comData.close();
        armoredOut.close();

        return out.toByteArray();
    }

    public static byte[] encryptMessage(byte[] data, PGPPublicKey publicKey) throws Exception {
        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        ArmoredOutputStream armoredOut = new ArmoredOutputStream(encOut);

        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
                        .setProvider("BC"));
        encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(publicKey).setProvider("BC"));

        OutputStream encOutStream = encGen.open(armoredOut, data.length);
        encOutStream.write(data);
        encOutStream.close();
        armoredOut.close();

        return encOut.toByteArray();
    }

    public static byte[] decryptMessage(byte[] encryptedData, PGPPrivateKey privateKey) throws Exception {
        InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(encryptedData));
        PGPObjectFactory pgpF = new PGPObjectFactory(in, new JcaKeyFingerprintCalculator());
        PGPEncryptedDataList enc = null;

        Object o = pgpF.nextObject();
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) pgpF.nextObject();
        }

        Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
        PGPPublicKeyEncryptedData pbe = it.next();

        InputStream clear = pbe.getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(privateKey));
        PGPObjectFactory plainFact = new PGPObjectFactory(clear, new JcaKeyFingerprintCalculator());

        PGPCompressedData cData = (PGPCompressedData) plainFact.nextObject();
        PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream(), new JcaKeyFingerprintCalculator());

        PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();
        InputStream unc = ld.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ch;
        while ((ch = unc.read()) >= 0) {
            out.write(ch);
        }
        return out.toByteArray();
    }

    public static boolean verifyMessage(byte[] signedData, PGPPublicKey publicKey) throws Exception {
        InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(signedData));
        PGPObjectFactory pgpFact = new PGPObjectFactory(in, new JcaKeyFingerprintCalculator());

        PGPCompressedData c1 = (PGPCompressedData) pgpFact.nextObject();
        PGPObjectFactory pgpFact2 = new PGPObjectFactory(c1.getDataStream(), new JcaKeyFingerprintCalculator());

        PGPOnePassSignatureList p1 = (PGPOnePassSignatureList) pgpFact2.nextObject();
        PGPOnePassSignature ops = p1.get(0);

        PGPLiteralData p2 = (PGPLiteralData) pgpFact2.nextObject();
        InputStream dIn = p2.getInputStream();

        ops.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), publicKey);

        int ch;
        while ((ch = dIn.read()) >= 0) {
            ops.update((byte) ch);
        }

        PGPSignatureList p3 = (PGPSignatureList) pgpFact2.nextObject();
        return ops.verify(p3.get(0));
    }
}
