import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeduplicationProcessor extends AbstractProcessor<String, String> {

    private CacheManager cacheManager;
    private Cache<String, String> cache;
    private DataSource dataSource;

    public DeduplicationProcessor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void init(ProcessorContext context) {
        super.init(context);

        // Initialize Ehcache
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("dedupCache",
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class, ResourcePoolsBuilder.heap(1000))
                ).build(true);
        cache = cacheManager.getCache("dedupCache", String.class, String.class);
    }

    @Override
    public void process(String key, String value) {
        // Check in Ehcache
        if (cache.get(key) == null) {
            // Check in MySQL
            if (!isInMySQL(key)) {
                // New record
                cache.put(key, "processed");
                saveToMySQL(key);
                context().forward(key, value);
            }
        } else {
            // Duplicate detected, do not forward
        }
    }

    private boolean isInMySQL(String key) {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM deduplication WHERE message_key = ?");
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void saveToMySQL(String key) {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO deduplication (message_key) VALUES (?)");
            stmt.setString(1, key);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (cacheManager != null) {
            cacheManager.close();
        }
    }
}
