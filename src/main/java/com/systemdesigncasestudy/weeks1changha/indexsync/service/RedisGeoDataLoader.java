package com.systemdesigncasestudy.weeks1changha.indexsync.service;

import com.systemdesigncasestudy.weeks1changha.indexsync.repository.RedisGeoIndexRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time bulk loader: reads all businesses from MySQL and populates Redis
 * GeoSet.
 * Runs at startup only when the Redis geo key is empty.
 */
@Profile("mysql")
@Component
public class RedisGeoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RedisGeoDataLoader.class);

    private final JdbcTemplate primaryJdbcTemplate;
    private final RedisGeoIndexRepository redisGeoIndexRepository;

    public RedisGeoDataLoader(
            @Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
            RedisGeoIndexRepository redisGeoIndexRepository) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.redisGeoIndexRepository = redisGeoIndexRepository;
    }

    @Override
    public void run(String... args) {
        if (redisGeoIndexRepository.hasData()) {
            log.info("Redis geo:businesses already has data. Skipping bulk load.");
            return;
        }

        log.info("Redis geo:businesses is empty. Starting bulk load from MySQL...");
        long start = System.currentTimeMillis();

        String sql = "SELECT id, latitude, longitude FROM business WHERE status = 'ACTIVE'";
        int[] count = { 0 };

        primaryJdbcTemplate.query(sql, rs -> {
            long id = rs.getLong("id");
            double lat = rs.getDouble("latitude");
            double lon = rs.getDouble("longitude");
            redisGeoIndexRepository.add(id, lat, lon);
            count[0]++;
            if (count[0] % 10000 == 0) {
                log.info("Loaded {} businesses into Redis...", count[0]);
            }
        });

        long elapsed = System.currentTimeMillis() - start;
        log.info("Bulk load complete: {} businesses loaded into Redis in {}ms", count[0], elapsed);
    }
}
