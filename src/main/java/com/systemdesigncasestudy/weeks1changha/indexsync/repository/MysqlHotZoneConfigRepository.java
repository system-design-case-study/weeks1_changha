package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import com.systemdesigncasestudy.weeks1changha.indexsync.config.HotZoneConfig;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MysqlHotZoneConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public MysqlHotZoneConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HotZoneConfig> findAllActive() {
        return jdbcTemplate.query(
                "SELECT geohash_prefix, description, radius_limit, is_active FROM hot_zone_config WHERE is_active = TRUE",
                (rs, rowNum) -> new HotZoneConfig(
                        rs.getString("geohash_prefix"),
                        rs.getString("description"),
                        rs.getInt("radius_limit"),
                        rs.getBoolean("is_active")));
    }
}
