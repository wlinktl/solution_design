import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.SftpPersistentAcceptOnceFileListFilter;
import org.springframework.integration.file.remote.synchronizer.InboundFileSynchronizer;
import org.springframework.integration.file.remote.synchronizer.SftpInboundFileSynchronizer;
import org.springframework.integration.file.remote.synchronizer.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.http.dsl.Http;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.ErrorHandlingSerializer;
import org.springframework.kafka.support.serializer.StringDeserializer;
import org.springframework.kafka.support.serializer.StringSerializer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableIntegration
public class IntegrationConfig {

    @Value("${sftp.host}")
    private String sftpHost;

    @Value("${sftp.port}")
    private int sftpPort;

    @Value("${sftp.user}")
    private String sftpUser;

    @Value("${sftp.private-key}")
    private String sftpPrivateKey;

    @Value("${sftp.private-key-passphrase}")
    private String sftpPrivateKeyPassphrase;

    @Value("${sftp.remote.directory}")
    private String remoteDirectory;

    @Value("${local.directory}")
    private String localDirectory;

    @Value("${azure.storage.connection-string}")
    private String azureConnectionString;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.input.topic}")
    private String inputTopic;

    @Value("${kafka.output.topic}")
    private String outputTopic;

    @Value("${rest.endpoint.url}")
    private String restEndpointUrl;

    @Bean
    public DefaultSftpSessionFactory sftpSessionFactory() {
        DefaultSftpSessionFactory factory = new DefaultSftpSessionFactory();
        factory.setHost(sftpHost);
        factory.setPort(sftpPort);
        factory.setUser(sftpUser);
        factory.setPrivateKey(sftpPrivateKey);
        factory.setPrivateKeyPassphrase(sftpPrivateKeyPassphrase);
        factory.setAllowUnknownKeys(true);
        return factory;
    }

    @Bean
    public SftpInboundFileSynchronizer sftpInboundFileSynchronizer() {
        SftpInboundFileSynchronizer synchronizer = new SftpInboundFileSynchronizer(sftpSessionFactory());
        synchronizer.setDeleteRemoteFiles(false);
        synchronizer.setRemoteDirectory(remoteDirectory);
        synchronizer.setFilter(new SftpPersistentAcceptOnceFileListFilter(new SimpleMetadataStore(), "sftp"));
        return synchronizer;
    }

    @Bean
    public SftpInboundFileSynchronizingMessageSource sftpInboundFileSynchronizingMessageSource() {
        SftpInboundFileSynchronizingMessageSource source = new SftpInboundFileSynchronizingMessageSource(sftpInboundFileSynchronizer());
        source.setLocalDirectory(new File(localDirectory));
        source.setAutoCreateLocalDirectory(true);
        source.setLocalFilter(new CompositeFileListFilter<>());
        return source;
    }

    @Bean
    public BlobContainerClient blobContainerClient() {
        return new BlobContainerClientBuilder()
                .connectionString(azureConnectionString)
                .containerName(containerName)
                .buildClient();
    }

    @Bean
    public BlobClientBuilder blobClientBuilder() {
        return new BlobClientBuilder()
                .connectionString(azureConnectionString);
    }

    @Bean
    public IntegrationFlow sftpToAzureFlow() {
        return IntegrationFlows.from(sftpInboundFileSynchronizingMessageSource(), config -> config.poller(p -> p.fixedDelay(60000)))
                .handle(Files.toStringTransformer())
                .handle(m -> {
                    String data = (String) m.getPayload();
                    byte[] bytes = data.getBytes();
                    blobClientBuilder().blobName("input-file").buildClient().upload(new ByteArrayInputStream(bytes), bytes.length, true);
                })
                .get();
    }

    @Bean
    public IntegrationFlow azureToKafkaFlow() {
        return IntegrationFlows.fromSupplier(() -> {
            BlobContainerClient containerClient = blobContainerClient();
            String data = new String(containerClient.getBlobClient("input-file").downloadContent().toBytes());
            return data;
        }, config -> config.poller(p -> p.fixedDelay(60000)))
                .handle(kafkaMessageHandler())
                .get();
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public MessageChannel inputChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow kafkaListenerFlow() {
        return IntegrationFlows.from(Kafka.messageDrivenChannelAdapter(consumerFactory(), inputTopic))
                .channel(inputChannel())
                .get();
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "group_id");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public IntegrationFlow processKafkaMessageFlow() {
        return IntegrationFlows.from(inputChannel())
                .handle(Http.out
