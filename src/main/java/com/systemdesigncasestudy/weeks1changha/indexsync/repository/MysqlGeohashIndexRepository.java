package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Profile("mysql")
@Repository
public class MysqlGeohashIndexRepository implements GeohashIndexRepository {

    private final JdbcTemplate jdbcTemplate;

    public MysqlGeohashIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void upsert(String geohash, long businessId) {
        jdbcTemplate.update(
            "DELETE FROM geohash_index WHERE business_id = ? AND geohash <> ?",
            businessId,
            geohash
        );
        jdbcTemplate.update(
            """
                INSERT INTO geohash_index (geohash, business_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE geohash = VALUES(geohash)
            """,
            geohash,
            businessId
        );
    }

    @Override
    public void deleteByBusinessId(long businessId) {
        jdbcTemplate.update("DELETE FROM geohash_index WHERE business_id = ?", businessId);
    }

    @Override
    public Set<Long> findBusinessIdsByPrefix(String geohashPrefix) {
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT business_id FROM geohash_index WHERE geohash LIKE CONCAT(?, '%')",
            Long.class,
            geohashPrefix
        );
        return new LinkedHashSet<>(ids);
    }
}
