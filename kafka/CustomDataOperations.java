import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;

public class CustomDataOperations {

    private final KafkaStreams streams;

    public CustomDataOperations(KafkaStreams streams) {
        this.streams = streams;
    }

    public void putData(String key, String value) {
        KeyValueStore<String, String> store = (KeyValueStore<String, String>) streams.store("custom-store", QueryableStoreTypes.keyValueStore());
        store.put(key, value);
    }

    public String getData(String key) {
        ReadOnlyKeyValueStore<String, String> store = streams.store("custom-store", QueryableStoreTypes.keyValueStore());
        return store.get(key);
    }
}
