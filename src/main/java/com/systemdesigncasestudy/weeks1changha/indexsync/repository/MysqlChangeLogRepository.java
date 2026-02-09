package com.systemdesigncasestudy.weeks1changha.indexsync.repository;

import com.systemdesigncasestudy.weeks1changha.indexsync.domain.BusinessChangeEvent;
import com.systemdesigncasestudy.weeks1changha.indexsync.domain.ChangeType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Profile("mysql")
@Repository
public class MysqlChangeLogRepository implements ChangeLogRepository {

    private static final RowMapper<BusinessChangeEvent> CHANGE_EVENT_ROW_MAPPER = MysqlChangeLogRepository::mapEvent;

    private final JdbcTemplate jdbcTemplate;

    public MysqlChangeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(long businessId, ChangeType changeType) {
        jdbcTemplate.update(
            "INSERT INTO change_log (business_id, change_type, processed) VALUES (?, ?, 0)",
            businessId,
            changeType.name()
        );
    }

    @Override
    public List<BusinessChangeEvent> pollUnprocessed(int limit) {
        return jdbcTemplate.query(
            """
                SELECT id, business_id, change_type, created_at
                FROM change_log
                WHERE processed = 0
                ORDER BY id ASC
                LIMIT ?
            """,
            CHANGE_EVENT_ROW_MAPPER,
            limit
        );
    }

    @Override
    public void markProcessed(Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(eventIds);
        String placeholders = ids.stream().map(value -> "?").collect(Collectors.joining(", "));
        String sql = """
            UPDATE change_log
            SET processed = 1,
                processed_at = CURRENT_TIMESTAMP(6)
            WHERE id IN (%s)
            """.formatted(placeholders);
        jdbcTemplate.update(sql, ids.toArray());
    }

    @Override
    public int countUnprocessed() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM change_log WHERE processed = 0",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Instant> oldestUnprocessedCreatedAt() {
        Timestamp timestamp = jdbcTemplate.queryForObject(
            "SELECT MIN(created_at) FROM change_log WHERE processed = 0",
            Timestamp.class
        );
        if (timestamp == null) {
            return Optional.empty();
        }
        return Optional.of(timestamp.toInstant());
    }

    private static BusinessChangeEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new BusinessChangeEvent(
            rs.getLong("id"),
            rs.getLong("business_id"),
            ChangeType.valueOf(rs.getString("change_type")),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
