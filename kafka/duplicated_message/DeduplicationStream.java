import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;

import java.time.Duration;
import java.util.Properties;

public class DeduplicationStream {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "deduplication-stream");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams");

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> inputStream = builder.stream("input-topic");

        // Configure state store supplier with retention policy
        KeyValueBytesStoreSupplier storeSupplier = Stores.persistentKeyValueStore("dedup-store");
        Materialized<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>> materialized = Materialized
                .<String, String>as(storeSupplier)
                .withRetention(Duration.ofDays(7)) // Retain entries for 7 days
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String());

        KStream<String, String> deduplicatedStream = inputStream
                .groupByKey()
                .reduce((aggValue, newValue) -> newValue, materialized)
                .toStream();

        deduplicatedStream.to("deduplicated-topic");

        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        streams.start();

        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
    }
}
