package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import com.systemdesigncasestudy.weeks1changha.indexsync.config.HotZoneConfigService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MysqlGeohashIndexRepository implements GeohashIndexRepository {

    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate hotJdbcTemplate;
    private final HotZoneConfigService hotZoneConfigService;
    private final RedisGeoIndexRepository redisGeoIndexRepository;

    public MysqlGeohashIndexRepository(
            @Qualifier("primaryJdbcTemplate") JdbcTemplate primaryJdbcTemplate,
            @Qualifier("hotJdbcTemplate") JdbcTemplate hotJdbcTemplate,
            HotZoneConfigService hotZoneConfigService,
            RedisGeoIndexRepository redisGeoIndexRepository) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.hotJdbcTemplate = hotJdbcTemplate;
        this.hotZoneConfigService = hotZoneConfigService;
        this.redisGeoIndexRepository = redisGeoIndexRepository;
    }

    @Override
    @Transactional
    public void upsert(String geohash, long businessId) {
        upsertMysql(geohash, businessId);
    }

    @Override
    @Transactional
    public void upsertWithCoordinates(String geohash, long businessId, double latitude, double longitude) {
        upsertMysql(geohash, businessId);
        // Sync to Redis GeoSet
        redisGeoIndexRepository.add(businessId, latitude, longitude);
    }

    private void upsertMysql(String geohash, long businessId) {
        String tableName = getTableForGeohash(geohash);
        JdbcTemplate targetTemplate = getTemplateForGeohash(geohash);

        // Remove from the OTHER table/DB to ensure no stale data if zone changed
        if (targetTemplate == hotJdbcTemplate) {
            primaryJdbcTemplate.update("DELETE FROM geohash_index WHERE business_id = ?", businessId);
        } else {
            hotJdbcTemplate.update("DELETE FROM geohash_index_hot WHERE business_id = ?", businessId);
        }

        String sql = String.format(
                "INSERT INTO %s (geohash, business_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE geohash = VALUES(geohash)",
                tableName);
        targetTemplate.update(sql, geohash, businessId);
    }

    @Override
    @Transactional
    public void deleteByBusinessId(long businessId) {
        primaryJdbcTemplate.update("DELETE FROM geohash_index WHERE business_id = ?", businessId);
        hotJdbcTemplate.update("DELETE FROM geohash_index_hot WHERE business_id = ?", businessId);
        // Also remove from Redis
        redisGeoIndexRepository.remove(businessId);
    }

    @Override
    public Set<Long> findBusinessIdsByPrefix(String geohashPrefix) {
        String tableName = getTableForGeohash(geohashPrefix);
        JdbcTemplate template = getTemplateForGeohash(geohashPrefix);
        String sql = String.format("SELECT business_id FROM %s WHERE geohash LIKE ?", tableName);
        List<Long> ids = template.queryForList(sql, Long.class, geohashPrefix + "%");
        return new HashSet<>(ids);
    }

    private String getTableForGeohash(String geohash) {
        if (hotZoneConfigService.isHotZone(geohash)) {
            return "geohash_index_hot";
        }
        return "geohash_index";
    }

    private JdbcTemplate getTemplateForGeohash(String geohash) {
        if (hotZoneConfigService.isHotZone(geohash)) {
            return hotJdbcTemplate;
        }
        return primaryJdbcTemplate;
    }
}
