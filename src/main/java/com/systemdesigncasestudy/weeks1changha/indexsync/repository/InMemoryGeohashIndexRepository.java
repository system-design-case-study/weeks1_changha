package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryGeohashIndexRepository implements GeohashIndexRepository {

    private final ConcurrentHashMap<String, Set<Long>> geohashToBusinessIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> businessIdToGeohash = new ConcurrentHashMap<>();

    @Override
    public synchronized void upsert(String geohash, long businessId) {
        String existing = businessIdToGeohash.put(businessId, geohash);
        if (existing != null && !existing.equals(geohash)) {
            Set<Long> oldSet = geohashToBusinessIds.get(existing);
            if (oldSet != null) {
                oldSet.remove(businessId);
                if (oldSet.isEmpty()) {
                    geohashToBusinessIds.remove(existing);
                }
            }
        }

        geohashToBusinessIds.computeIfAbsent(geohash, key -> ConcurrentHashMap.newKeySet()).add(businessId);
    }

    @Override
    public synchronized void deleteByBusinessId(long businessId) {
        String geohash = businessIdToGeohash.remove(businessId);
        if (geohash == null) {
            return;
        }

        Set<Long> set = geohashToBusinessIds.get(geohash);
        if (set == null) {
            return;
        }

        set.remove(businessId);
        if (set.isEmpty()) {
            geohashToBusinessIds.remove(geohash);
        }
    }

    @Override
    public Set<Long> findBusinessIdsByPrefix(String geohashPrefix) {
        Set<Long> result = new HashSet<>();
        geohashToBusinessIds.forEach((geohash, ids) -> {
            if (geohash.startsWith(geohashPrefix)) {
                result.addAll(ids);
            }
        });
        return result;
    }
}
