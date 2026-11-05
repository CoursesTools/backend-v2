package com.winworld.coursestools.repository.ops;

import com.winworld.coursestools.dto.ops.OpsAlertReadDto;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.enums.OpsTelegramStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Search over ops alert history. Every search word must appear somewhere in the title or body
 * (case-insensitive substring match on the generated {@code search_text} column, which carries a
 * trigram GIN index when pg_trgm is available). Paging is keyset on the monotonic id.
 */
@Repository
@RequiredArgsConstructor
public class OpsAlertSearchRepository {
    private static final String COLUMNS =
            "id, category, severity, title, body, telegram_status, telegram_detail, created_at";

    private final NamedParameterJdbcTemplate jdbc;

    public record Criteria(
            List<String> terms,
            OpsAlertCategory category,
            OffsetDateTime from,
            OffsetDateTime to,
            Long beforeId,
            int limit
    ) {
    }

    public List<OpsAlertReadDto> search(Criteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT " + COLUMNS + " FROM ops_alerts" + where(criteria, params, true)
                + " ORDER BY id DESC LIMIT :limit";
        params.addValue("limit", criteria.limit());
        return jdbc.query(sql, params, ROW_MAPPER);
    }

    /** Same mapping as {@link #search}, so a single alert and the list always show the same UTC time. */
    public Optional<OpsAlertReadDto> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM ops_alerts WHERE id = :id",
                new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    public long count(Criteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ops_alerts" + where(criteria, params, false),
                params, Long.class);
        return total == null ? 0 : total;
    }

    private static String where(Criteria criteria, MapSqlParameterSource params, boolean withCursor) {
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        if (criteria.category() != null) {
            where.append(" AND category = :category");
            params.addValue("category", criteria.category().name());
        }
        if (criteria.from() != null) {
            where.append(" AND created_at >= :from");
            params.addValue("from", criteria.from());
        }
        if (criteria.to() != null) {
            where.append(" AND created_at < :to");
            params.addValue("to", criteria.to());
        }
        if (withCursor && criteria.beforeId() != null) {
            where.append(" AND id < :beforeId");
            params.addValue("beforeId", criteria.beforeId());
        }
        for (int i = 0; i < criteria.terms().size(); i++) {
            where.append(" AND search_text LIKE :term").append(i).append(" ESCAPE '\\'");
            params.addValue("term" + i, "%" + escapeLike(criteria.terms().get(i)) + "%");
        }
        return where.toString();
    }

    static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static final RowMapper<OpsAlertReadDto> ROW_MAPPER = (rs, rowNum) -> new OpsAlertReadDto(
            rs.getLong("id"),
            OpsAlertCategory.valueOf(rs.getString("category")),
            OpsAlertSeverity.valueOf(rs.getString("severity")),
            rs.getString("title"),
            rs.getString("body"),
            OpsTelegramStatus.valueOf(rs.getString("telegram_status")),
            rs.getString("telegram_detail"),
            rs.getObject("created_at", OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
    );
}
