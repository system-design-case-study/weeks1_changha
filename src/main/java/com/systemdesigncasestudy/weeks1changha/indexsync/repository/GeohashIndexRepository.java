package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import java.util.Set;

public interface GeohashIndexRepository {

    void upsert(String geohash, long businessId);

    void deleteByBusinessId(long businessId);

    Set<Long> findBusinessIdsByPrefix(String geohashPrefix);
}
