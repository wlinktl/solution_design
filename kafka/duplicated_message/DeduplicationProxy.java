import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class DeduplicationProxy {

    private static final String TEMP_INPUT_TOPIC = "temp-input-topic";
    private static final String DEDUPLICATED_TOPIC = "deduplicated-topic";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    public static void main(String[] args) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "deduplication-proxy-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(TEMP_INPUT_TOPIC));

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);

        JedisPool jedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);

        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records) {
                String messageId = record.key(); // Assuming the message ID is the key

                try (Jedis jedis = jedisPool.getResource()) {
                    if (!jedis.exists(messageId)) {
                        jedis.set(messageId, "processed");
                        producer.send(new ProducerRecord<>(DEDUPLICATED_TOPIC, record.key(), record.value()));
                    }
                }
            }
        }
    }
}
