package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisGeoIndexRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisGeoIndexRepository.class);
    private static final String GEO_KEY = "geo:businesses";

    private final StringRedisTemplate redisTemplate;

    public RedisGeoIndexRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * GEOADD geo:businesses longitude latitude businessId
     */
    public void add(long businessId, double latitude, double longitude) {
        redisTemplate.opsForGeo().add(GEO_KEY,
                new Point(longitude, latitude),
                String.valueOf(businessId));
    }

    /**
     * ZREM geo:businesses businessId
     */
    public void remove(long businessId) {
        redisTemplate.opsForZSet().remove(GEO_KEY, String.valueOf(businessId));
    }

    /**
     * GEORADIUS geo:businesses longitude latitude radius m ASC COUNT 5000
     * Returns business IDs within the given radius, sorted by distance ascending.
     */
    public List<Long> findByRadius(double latitude, double longitude, double radiusMeters) {
        Distance distance = new Distance(radiusMeters, RedisGeoCommands.DistanceUnit.METERS);
        Circle circle = new Circle(new Point(longitude, latitude), distance);

        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().radius(GEO_KEY, circle,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(5000));

        List<Long> ids = new ArrayList<>();
        if (geoResults != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : geoResults) {
                try {
                    ids.add(Long.parseLong(result.getContent().getName()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid business ID in Redis geo set: {}",
                            result.getContent().getName());
                }
            }
        }
        return ids;
    }

    /**
     * Check if the geo set has any data (for fallback decision).
     */
    public boolean hasData() {
        Long size = redisTemplate.opsForZSet().size(GEO_KEY);
        return size != null && size > 0;
    }
}
