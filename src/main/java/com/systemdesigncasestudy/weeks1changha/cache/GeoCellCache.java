package com.systemdesigncasestudy.weeks1changha.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeoCellCache {

    private final ExpiringCache<String, List<Long>> cache = new ExpiringCache<>();
    private final Duration ttl;

    public GeoCellCache(@Value("${app.cache.geo-ttl-seconds:300}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<List<Long>> get(String key) {
        return cache.get(key);
    }

    public void put(String key, List<Long> businessIds) {
        cache.put(key, businessIds, ttl);
    }

    public void clear() {
        cache.clear();
    }
}
