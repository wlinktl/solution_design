import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlockBlobClient;

public class AzuriteExample {
    private static final String CONNECTION_STRING = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
            "AccountKey=Eby8vdM02xNOcqFeq2+bgb7M3gR2Jw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;";

    public static void main(String[] args) {
        // Create a BlobServiceClient object
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(CONNECTION_STRING)
                .buildClient();

        // Create a BlobContainerClient object
        BlobContainerClient containerClient = blobServiceClient.createBlobContainerIfNotExists("mycontainer");

        // Upload a file
        String blobContent = "Hello, World!";
        BlockBlobClient blobClient = containerClient.getBlobClient("hello.txt").getBlockBlobClient();
        blobClient.upload(BinaryData.fromString(blobContent));

        System.out.println("Blob uploaded successfully");

        // Optionally, upload more files
        uploadFile(containerClient, "example1.txt", "This is example 1.");
        uploadFile(containerClient, "example2.txt", "This is example 2.");
    }

    private static void uploadFile(BlobContainerClient containerClient, String blobName, String content) {
        BlockBlobClient blobClient = containerClient.getBlobClient(blobName).getBlockBlobClient();
        blobClient.upload(BinaryData.fromString(content));
        System.out.println("Uploaded " + blobName);
    }
}
