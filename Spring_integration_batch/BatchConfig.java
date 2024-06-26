import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.batch.item.kafka.KafkaItemReader;
import org.springframework.batch.item.kafka.builder.KafkaItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.ErrorHandlingSerializer;
import org.springframework.kafka.support.serializer.StringDeserializer;
import org.springframework.kafka.support.serializer.StringSerializer;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

@Configuration
@EnableBatchProcessing
public class BatchConfig {

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

    @Autowired
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    private StepBuilderFactory stepBuilderFactory;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Bean
    public Job job() {
        return jobBuilderFactory.get("job")
                .incrementer(new RunIdIncrementer())
                .start(step1())
                .next(step2())
                .next(step3())
                .build();
    }

    @Bean
    public Step step1() {
        return stepBuilderFactory.get("step1")
                .<String, String>chunk(10)
                .reader(fileReader())
                .processor(asyncItemProcessor1())
                .writer(asyncItemWriter1())
                .build();
    }

    @Bean
    public Step step2() {
        return stepBuilderFactory.get("step2")
                .<String, String>chunk(10)
                .reader(azureItemReader())
                .processor(asyncItemProcessor2())
                .writer(asyncItemWriter2())
                .build();
    }

    @Bean
    public Step step3() {
        return stepBuilderFactory.get("step3")
                .<String, String>chunk(10)
                .reader(kafkaItemReader())
                .processor(asyncItemProcessor3())
                .writer(asyncItemWriter3())
                .build();
    }

    @Bean
    public FlatFileItemReader<String> fileReader() {
        FlatFileItemReader<String> reader = new FlatFileItemReader<>();
        reader.setResource(new FileSystemResource(localDirectory));
        reader.setLineMapper(new PassThroughLineMapper());
        return reader;
    }

    @Bean
    public ItemReader<String> azureItemReader() {
        return () -> {
            BlobContainerClient containerClient = blobContainerClient();
            return new String(containerClient.getBlobClient("input-file").downloadContent().toBytes());
        };
    }

    @Bean
    public KafkaItemReader<String, String> kafkaItemReader() {
        return new KafkaItemReaderBuilder<String, String>()
                .consumerFactory(consumerFactory)
                .partitions(0)
                .pollTimeout(3000)
                .saveState(true)
                .name("kafkaItemReader")
                .topic(inputTopic)
                .build();
    }

    @Bean
    public AsyncItemProcessor<String, String> asyncItemProcessor1() {
        AsyncItemProcessor<String, String> asyncItemProcessor = new AsyncItemProcessor<>();
        asyncItemProcessor.setDelegate(processor1());
        asyncItemProcessor.setTaskExecutor(taskExecutor());
        return asyncItemProcessor;
    }

    @Bean
    public AsyncItemWriter<String> asyncItemWriter1() {
        AsyncItemWriter<String> asyncItemWriter = new AsyncItemWriter<>();
        asyncItemWriter.setDelegate(azureItemWriter());
        return asyncItemWriter;
    }

    @Bean
    public AsyncItemProcessor<String, String> asyncItemProcessor2() {
        AsyncItemProcessor<String, String> asyncItemProcessor = new AsyncItemProcessor<>();
        asyncItemProcessor.setDelegate(processor2());
        asyncItemProcessor.setTaskExecutor(taskExecutor());
        return asyncItemProcessor;
    }

    @Bean
    public AsyncItemWriter<String> asyncItemWriter2() {
        AsyncItemWriter<String> asyncItemWriter = new AsyncItemWriter<>();
        asyncItemWriter.setDelegate(kafkaItemWriter());
        return asyncItemWriter;
    }

    @Bean
    public AsyncItemProcessor<String, String> asyncItemProcessor3() {
        AsyncItemProcessor<String, String> asyncItemProcessor = new AsyncItemProcessor<>();
        asyncItemProcessor.setDelegate(processor3());
        asyncItemProcessor.setTaskExecutor(taskExecutor());
        return asyncItemProcessor;
    }

    @Bean
    public AsyncItemWriter<String> asyncItemWriter3() {
        AsyncItemWriter<String> asyncItemWriter = new AsyncItemWriter<>();
        asyncItemWriter.setDelegate(kafkaItemWriter());
        return asyncItemWriter;
    }

    @Bean
    public ItemProcessor<String, String> processor1() {
        return item -> {
            byte[] bytes = item.getBytes(StandardCharsets.UTF_8);
            blobClientBuilder().blobName("input-file").buildClient().upload(new ByteArrayInputStream(bytes), bytes.length, true);
            return item;
        };
    }

    @Bean
    public ItemProcessor<String, String> processor2() {
        return item -> item; // No processing needed for this step
    }

    @Bean
    public ItemProcessor<String, String> processor3() {
        return item -> {
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.exchange(restEndpointUrl, HttpMethod.POST, null, String.class, item).getBody();
            return response;
        };
    }

    @Bean
    public ItemWriter<String> azureItemWriter() {
        return items -> {
            for (String item : items) {
                byte[] bytes = item.getBytes(StandardCharsets.UTF_8);
                blobClientBuilder().blobName("input-file").buildClient().upload(new ByteArrayInputStream(bytes), bytes.length, true);
            }
        };
    }

    @Bean
    public ItemWriter<String> kafkaItemWriter() {
        return items -> items.forEach(item -> kafkaTemplate.send(outputTopic, item));
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
    public TaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("batch_processor");
        executor.setConcurrencyLimit(10);
        return executor;
    }
}
