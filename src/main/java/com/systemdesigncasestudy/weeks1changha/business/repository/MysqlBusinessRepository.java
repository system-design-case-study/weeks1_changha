package com.systemdesigncasestudy.weeks1changha.business.repository;

import com.systemdesigncasestudy.weeks1changha.business.domain.Business;
import com.systemdesigncasestudy.weeks1changha.business.domain.BusinessStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Profile("mysql")
@Repository
public class MysqlBusinessRepository implements BusinessRepository {

    private static final RowMapper<Business> BUSINESS_ROW_MAPPER = MysqlBusinessRepository::mapBusiness;

    private final JdbcTemplate jdbcTemplate;

    public MysqlBusinessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long nextId() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> connection.prepareStatement(
            "INSERT INTO business_id_sequence (id) VALUES (NULL)",
            Statement.RETURN_GENERATED_KEYS
        ), keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to allocate business id");
        }
        return key.longValue();
    }

    @Override
    public Business save(Business business) {
        jdbcTemplate.update("""
            INSERT INTO business (
              id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              owner_id = VALUES(owner_id),
              name = VALUES(name),
              category = VALUES(category),
              phone = VALUES(phone),
              address = VALUES(address),
              latitude = VALUES(latitude),
              longitude = VALUES(longitude),
              geohash = VALUES(geohash),
              status = VALUES(status),
              updated_at = VALUES(updated_at)
            """,
            business.id(),
            business.ownerId(),
            business.name(),
            business.category(),
            business.phone(),
            business.address(),
            business.latitude(),
            business.longitude(),
            business.geohash(),
            business.status().name(),
            Timestamp.from(business.createdAt()),
            Timestamp.from(business.updatedAt())
        );
        return business;
    }

    @Override
    public Optional<Business> findById(long id) {
        List<Business> results = jdbcTemplate.query(
            """
                SELECT id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at
                FROM business
                WHERE id = ?
            """,
            BUSINESS_ROW_MAPPER,
            id
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    @Override
    public List<Business> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> ordered = new ArrayList<>(ids);
        String placeholders = ordered.stream().map(value -> "?").collect(Collectors.joining(", "));
        List<Object> params = new ArrayList<>(ordered);
        String sql = """
            SELECT id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at
            FROM business
            WHERE id IN (%s)
            """.formatted(placeholders);
        return jdbcTemplate.query(sql, BUSINESS_ROW_MAPPER, params.toArray());
    }

    private static Business mapBusiness(ResultSet rs, int rowNum) throws SQLException {
        return new Business(
            rs.getLong("id"),
            rs.getLong("owner_id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getString("geohash"),
            BusinessStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }
}
