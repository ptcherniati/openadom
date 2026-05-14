package fr.inra.oresing.monitoring.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Acces PostgreSQL a la table {@code oa_audit.user_session_log} .
 *
 * <p>Pattern identique a
 * {@link fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository} :
 * insertion batch idempotente ( {@code ON CONFLICT DO NOTHING} ) ,
 * appelee depuis le writer async , et methodes de lecture pour
 * l'historique consomme par le dashboard .
 */
@Slf4j
@Repository
public class UserSessionLogRepository {

    // Wrapper SECURITY DEFINER ; voir V2__oa_audit_schema.sql .
    private static final String INSERT_SQL = """
            SELECT oa_audit.record_user_session(
                ?::uuid, ?::uuid, ?::text, ?::inet, ?::text,
                ?::timestamptz, ?::timestamptz, ?::bigint, ?::text)
            """;

    private static final String DELETE_OLDER_THAN_SQL =
            "SELECT oa_audit.delete_user_session_logs_older_than(?::int)";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate named;

    public UserSessionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.named = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /**
     * Insertion batch idempotente . Doublons sur {@code session_id}
     * silencieusement ignores ( garantit l'idempotence si le writer
     * rejoue une queue apres redemarrage ) .
     *
     * @return nombre d'entries effectivement inserees
     */
    public int insertBatch(Collection<UserSessionLogEntry> entries) {
        if (entries == null || entries.isEmpty()) return 0;
        int[][] results = jdbcTemplate.batchUpdate(INSERT_SQL, entries, entries.size(),
                UserSessionLogRepository::bind);
        int inserted = 0;
        for (int[] batch : results) {
            for (int r : batch) {
                if (r > 0) inserted += r;
            }
        }
        return inserted;
    }

    /** Supprime les rows anterieures a {@code retentionDays} . */
    public int deleteOlderThan(int retentionDays) {
        if (retentionDays <= 0) return 0;
        return jdbcTemplate.update(DELETE_OLDER_THAN_SQL, retentionDays);
    }

    /**
     * Lecture paginee de l'historique , triee par {@code login_time DESC} .
     * Filtres optionnels : userId , userLogin partial-match , endReason .
     *
     * @param userId        UUID exact ; null = pas de filtre
     * @param userLoginLike pattern ILIKE ( {@code %x%} ) ; null / blank = pas de filtre
     * @param endReason     valeur exacte ; null = pas de filtre
     * @param limit         max rows ( bornee a 500 ) ; default 100
     * @param offset        offset pagination ; default 0
     */
    public List<UserSessionLogEntry> findHistory(
            java.util.UUID userId, String userLoginLike, String endReason,
            int limit, int offset) {

        StringBuilder sql = new StringBuilder("""
                SELECT session_id, user_id, user_login, ip_address::text AS ip_address,
                       user_agent, login_time, logout_time, duration_ms, end_reason
                  FROM oa_audit.user_session_log
                 WHERE 1=1
                """);
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (userId != null) {
            sql.append(" AND user_id = :userId ");
            p.addValue("userId", userId);
        }
        if (userLoginLike != null && !userLoginLike.isBlank()) {
            sql.append(" AND user_login ILIKE :loginLike ");
            p.addValue("loginLike", "%" + userLoginLike + "%");
        }
        if (endReason != null && !endReason.isBlank()) {
            sql.append(" AND end_reason = :reason ");
            p.addValue("reason", endReason);
        }
        sql.append(" ORDER BY login_time DESC ");
        sql.append(" LIMIT :limit OFFSET :offset ");
        p.addValue("limit",  Math.clamp(limit, 1, 500));
        p.addValue("offset", Math.max(offset, 0));

        return named.query(sql.toString(), p, (rs, n) -> new UserSessionLogEntry(
                rs.getObject("session_id", java.util.UUID.class),
                rs.getObject("user_id",    java.util.UUID.class),
                rs.getString("user_login"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                toInstant(rs.getTimestamp("login_time")),
                toInstant(rs.getTimestamp("logout_time")),
                rs.getLong("duration_ms"),
                rs.getString("end_reason")));
    }

    public long count(java.util.UUID userId, String userLoginLike, String endReason) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM oa_audit.user_session_log WHERE 1=1 ");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (userId != null) { sql.append(" AND user_id = :userId "); p.addValue("userId", userId); }
        if (userLoginLike != null && !userLoginLike.isBlank()) {
            sql.append(" AND user_login ILIKE :loginLike "); p.addValue("loginLike", "%" + userLoginLike + "%");
        }
        if (endReason != null && !endReason.isBlank()) {
            sql.append(" AND end_reason = :reason "); p.addValue("reason", endReason);
        }
        Long c = named.queryForObject(sql.toString(), p, Long.class);
        return c == null ? 0L : c;
    }

    // -------------------------------------------------------------------- //

    private static void bind(PreparedStatement ps, UserSessionLogEntry e) throws java.sql.SQLException {
        ps.setObject(1, e.sessionId());
        ps.setObject(2, e.userId());
        ps.setString(3, Objects.toString(e.userLogin(), ""));
        if (e.ipAddress() != null) {
            ps.setString(4, e.ipAddress());                              // cast :: inet cote SQL
        } else {
            ps.setNull(4, Types.OTHER);
        }
        if (e.userAgent() != null) ps.setString(5, e.userAgent());
        else ps.setNull(5, Types.VARCHAR);
        ps.setTimestamp(6, Timestamp.from(e.loginTime()));
        if (e.logoutTime() != null) ps.setTimestamp(7, Timestamp.from(e.logoutTime()));
        else ps.setNull(7, Types.TIMESTAMP);
        ps.setLong(8, e.durationMs());
        if (e.endReason() != null) ps.setString(9, e.endReason());
        else ps.setNull(9, Types.VARCHAR);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
