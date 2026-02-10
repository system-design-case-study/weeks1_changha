package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import java.util.Set;

public interface GeohashIndexRepository {

    void upsert(String geohash, long businessId);

    /**
     * Upsert geohash index with coordinates for Redis geo sync.
     */
    void upsertWithCoordinates(String geohash, long businessId, double latitude, double longitude);

    void deleteByBusinessId(long businessId);

    Set<Long> findBusinessIdsByPrefix(String geohashPrefix);
}
