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
    private final com.systemdesigncasestudy.weeks1changha.indexsync.config.HotZoneConfigService hotZoneConfigService;

    public MysqlGeohashIndexRepository(JdbcTemplate jdbcTemplate,
            com.systemdesigncasestudy.weeks1changha.indexsync.config.HotZoneConfigService hotZoneConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.hotZoneConfigService = hotZoneConfigService;
    }

    @Override
    @Transactional
    public void upsert(String geohash, long businessId) {
        String tableName = getTableForGeohash(geohash);

        // Ensure we delete from both to avoid stale data if business moved zones
        // (Optional: optimization would be check if zone changed, but simple is safe)
        jdbcTemplate.update("DELETE FROM geohash_index WHERE business_id = ? AND geohash <> ?", businessId, geohash);
        jdbcTemplate.update("DELETE FROM geohash_index_hot WHERE business_id = ? AND geohash <> ?", businessId,
                geohash);

        jdbcTemplate.update(
                "INSERT INTO " + tableName
                        + " (geohash, business_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE geohash = VALUES(geohash)",
                geohash,
                businessId);
    }

    @Override
    public void deleteByBusinessId(long businessId) {
        jdbcTemplate.update("DELETE FROM geohash_index WHERE business_id = ?", businessId);
        jdbcTemplate.update("DELETE FROM geohash_index_hot WHERE business_id = ?", businessId);
    }

    @Override
    public Set<Long> findBusinessIdsByPrefix(String geohashPrefix) {
        String tableName = getTableForGeohash(geohashPrefix);
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT business_id FROM " + tableName + " WHERE geohash LIKE CONCAT(?, '%')",
                Long.class,
                geohashPrefix);
        return new LinkedHashSet<>(ids);
    }

    private String getTableForGeohash(String geohash) {
        if (hotZoneConfigService.isHotZone(geohash)) {
            return "geohash_index_hot";
        }
        return "geohash_index";
    }
}
