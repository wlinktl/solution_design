import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class AzuriteExample {
    private static final String CONNECTION_STRING = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;";

    public static void main(String[] args) {
        // Create a BlobServiceClient object
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(CONNECTION_STRING)
                .buildClient();

        // Create a BlobContainerClient object
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient("mycontainer");

        // Create the container if it does not exist
        containerClient.createIfNotExists();

        // Upload a file
        String blobContent = "Hello, World!";
        BlockBlobClient blobClient = containerClient.getBlobClient("hello.txt").getBlockBlobClient();
        blobClient.upload(new ByteArrayInputStream(blobContent.getBytes(StandardCharsets.UTF_8)), blobContent.length(),
                true);

        System.out.println("Blob uploaded successfully");

        // Optionally, upload more files
        uploadFile(containerClient, "example1.txt", "This is example 1.");
        uploadFile(containerClient, "example2.txt", "This is example 2.");
    }

    private static void uploadFile(BlobContainerClient containerClient, String blobName, String content) {
        BlockBlobClient blobClient = containerClient.getBlobClient(blobName).getBlockBlobClient();
        blobClient.upload(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), content.length(), true);
        System.out.println("Uploaded " + blobName);
    }
}
