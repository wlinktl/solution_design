import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPool;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Service
public class DeduplicationServiceRedis {

    @Autowired
    private JedisPool jedisPool;

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/kafka";
    private static final String JDBC_USER = "user";
    private static final String JDBC_PASSWORD = "password";

    public static void main(String[] args) throws Exception {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "deduplication-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("input-topic"));

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);

        Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
        Statement statement = connection.createStatement();
        statement.execute("CREATE TABLE IF NOT EXISTS processed_messages (message_id VARCHAR PRIMARY KEY)");

        DeduplicationService service = new DeduplicationService();
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            List<ConsumerRecord<String, String>> recordList = records.records("input-topic");
            service.processRecords(recordList, producer, connection);
        }
    }

    public void processRecords(List<ConsumerRecord<String, String>> records, KafkaProducer<String, String> producer, Connection connection) throws Exception {
        // Filter records using Redis cache
        List<ConsumerRecord<String, String>> uniqueRecords = records.stream()
                .filter(record -> !isMessageProcessed(record.key()))
                .collect(Collectors.toList());

        // Insert unique records into the database in batch
        if (!uniqueRecords.isEmpty()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO processed_messages (message_id) VALUES (?)")) {
                for (ConsumerRecord<String, String> record : uniqueRecords) {
                    preparedStatement.setString(1, record.key());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            }

            // Send unique records to the output topic
            for (ConsumerRecord<String, String> record : uniqueRecords) {
                producer.send(new ProducerRecord<>("output-topic", record.key(), record.value()));
            }

            // Update Redis cache
            try (var jedis = jedisPool.getResource()) {
                for (ConsumerRecord<String, String> record : uniqueRecords) {
                    jedis.set(record.key(), "processed");
                }
            }
        }
    }

    private boolean isMessageProcessed(String messageId) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.exists(messageId);
        }
    }
}
