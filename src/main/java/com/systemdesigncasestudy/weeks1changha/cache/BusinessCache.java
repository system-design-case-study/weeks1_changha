package com.systemdesigncasestudy.weeks1changha.cache;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BusinessCache {

    private final ExpiringCache<Long, Business> cache = new ExpiringCache<>();
    private final Duration ttl;

    public BusinessCache(@Value("${app.cache.business-ttl-seconds:3600}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<Business> get(long businessId) {
        return cache.get(businessId);
    }

    public void put(Business business) {
        cache.put(business.id(), business, ttl);
    }

    public void evict(long businessId) {
        cache.invalidate(businessId);
    }
}
