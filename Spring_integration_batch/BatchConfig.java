import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.integration.launch.JobLaunchRequest;
import org.springframework.batch.integration.launch.JobLaunchingMessageHandler;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.SftpPersistentAcceptOnceFileListFilter;
import org.springframework.integration.file.remote.session.DefaultSftpSessionFactory;
import org.springframework.integration.file.remote.synchronizer.InboundFileSynchronizer;
import org.springframework.integration.file.remote.synchronizer.SftpInboundFileSynchronizer;
import org.springframework.integration.file.remote.synchronizer.SftpInboundFileSynchronizingMessageSource;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private JobLauncher jobLauncher;

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
    public Job processJob() {
        return jobBuilderFactory.get("processJob")
                .incrementer(new RunIdIncrementer())
                .start(step1())
                .next(step2())
                .next(step3())
                .next(step4())
                .build();
    }

    @Bean
    public Step step1() {
        return stepBuilderFactory.get("step1")
                .<String, String>chunk(10)
                .reader(fileReader())
                .processor(fileProcessor())
                .writer(azureWriter())
                .build();
    }

    @Bean
    public Step step2() {
        return stepBuilderFactory.get("step2")
                .<String, String>chunk(10)
                .reader(azureFileReader())
                .processor(kafkaMessageProcessor())
                .writer(kafkaMessageWriter())
                .build();
    }

    @Bean
    public Step step3() {
        return stepBuilderFactory.get("step3")
                .<String, String>chunk(10)
                .reader(kafkaMessageReader())
                .processor(restProcessor())
                .writer(restMessageWriter())
                .build();
    }

    @Bean
    public Step step4() {
        return stepBuilderFactory.get("step4")
                .<String, String>chunk(10)
                .reader(kafkaMessageReader())
                .processor(kafkaOutputProcessor())
                .writer(kafkaOutputWriter())
                .build();
    }

    @Bean
    public FlatFileItemReader<String> fileReader() {
        FlatFileItemReader<String> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource("local-directory/input-file.csv"));
        reader.setLineMapper(new PassThroughLineMapper());
        return reader;
    }

    @Bean
    public ItemProcessor<String, String> fileProcessor() {
        return item -> item; // No processing needed, just read and write
    }

    @Bean
    public ItemWriter<String> azureWriter() {
        return items -> {
            BlobClientBuilder blobClientBuilder = new BlobClientBuilder().connectionString(azureConnectionString);
            for (String item : items) {
                byte[] bytes = item.getBytes(StandardCharsets.UTF_8);
                blobClientBuilder.blobName("input-file").buildClient().upload(new ByteArrayInputStream(bytes), bytes.length, true);
            }
        };
    }

    @Bean
    public ItemReader<String> azureFileReader() {
        return () -> {
            BlobContainerClient containerClient = new BlobContainerClientBuilder()
                    .connectionString(azureConnectionString)
                    .containerName(containerName)
                    .buildClient();
            return new String(containerClient.getBlobClient("input-file").downloadContent().toBytes());
        };
    }

    @Bean
    public ItemProcessor<String, String> kafkaMessageProcessor() {
        return item -> item; // Process if needed
    }

    @Bean
    public ItemWriter<String> kafkaMessageWriter() {
        return items -> items.forEach(item -> {
            KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
            kafkaTemplate.send(inputTopic, item);
        });
    }

    @Bean
    public KafkaItemReader<String, String> kafkaMessageReader() {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "group_id");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new KafkaItemReaderBuilder<String, String>()
                .consumerFactory(new DefaultKafkaConsumerFactory<>(consumerProps))
                .partitions(0)
                .pollTimeout(3000)
                .saveState(true)
                .name("kafkaItemReader")
                .topic(inputTopic)
                .build();
    }

    @Bean
    public ItemProcessor<String, String> restProcessor() {
        return item -> {
            RestTemplate restTemplate = new RestTemplate();
            return restTemplate.postForObject(restEndpointUrl, item, String.class);
        };
    }

    @Bean
    public ItemWriter<String> restMessageWriter() {
        return items -> items.forEach(item -> {
            KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
            kafkaTemplate.send(outputTopic, item);
        });
    }

    @Bean
    public ItemProcessor<String, String> kafkaOutputProcessor() {
        return item -> item; // Process if needed
    }

    @Bean
    public ItemWriter<String> kafkaOutputWriter() {
        return items -> items.forEach(item -> {
            KafkaTemplate<String, String> kafkaTemplate = kafkaTemplate();
            kafkaTemplate.send(outputTopic, item);
        });
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        return new KafkaTemplate<>(producerFactory);
    }
}
