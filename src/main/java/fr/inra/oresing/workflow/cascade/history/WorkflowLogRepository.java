package fr.inra.oresing.workflow.cascade.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.Collection;

/**
 * Acces PostgreSQL a la table {@code oa_audit.workflow_log}.
 *
 * <p>Utilise le {@link JdbcTemplate} par defaut ( pool Hikari HTTP ).
 * Quand la branche 61 sera mergee , on pourra swap vers
 * {@code workflowJdbcTemplate} via {@code @Qualifier}.
 */
@Slf4j
@Repository
public class WorkflowLogRepository {

    // Wrapper SECURITY DEFINER ; voir V2__oa_audit_schema.sql .
    private static final String INSERT_SQL = """
            SELECT oa_audit.record_workflow(
                ?::uuid, ?::varchar(32), ?::uuid, ?::varchar(128),
                ?::varchar(256), ?::varchar(256), ?::varchar(512),
                ?::timestamptz, ?::timestamptz, ?::bigint, ?::varchar(16),
                ?::bigint, ?::bigint, ?::int,
                ?::bigint, ?::jsonb, ?::text, ?::jsonb)
            """;

    private static final String DELETE_OLDER_THAN_SQL =
            "SELECT oa_audit.delete_workflow_logs_older_than(?::int)";

    private final JdbcTemplate  jdbcTemplate;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    public WorkflowLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insertion batch d'une collection d'entries. Les doublons sur
     * {@code correlation_id} sont silencieusement ignores ( ON CONFLICT
     * DO NOTHING ) : garantit l'idempotence si le writer rejoue une queue
     * apres redemarrage.
     *
     * @return nombre d'entries effectivement inserees
     */
    public int insertBatch(Collection<WorkflowLogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int[][] results = jdbcTemplate.batchUpdate(INSERT_SQL, entries, entries.size(),
                (PreparedStatement ps, WorkflowLogEntry e) -> bindEntry(ps, e));
        int inserted = 0;
        for (int[] batch : results) {
            for (int r : batch) {
                if (r > 0) {
                    inserted += r;
                }
            }
        }
        return inserted;
    }

    /**
     * Supprime les entries dont {@code start_time} est anterieur a
     * {@code retentionDays} jours. A appeler periodiquement par
     * {@link WorkflowLogRetentionTask}.
     */
    public int deleteOlderThan(int retentionDays) {
        if (retentionDays <= 0) {
            log.warn("retentionDays <= 0 ({}) : rotation desactivee", retentionDays);
            return 0;
        }
        return jdbcTemplate.update(DELETE_OLDER_THAN_SQL, retentionDays);
    }

    private void bindEntry(PreparedStatement ps, WorkflowLogEntry e) throws SQLException {
        ps.setObject(1, e.correlationId());
        ps.setString(2, e.workflowType());
        ps.setObject(3, e.userId());
        setNullableString(ps, 4, e.userLogin());
        setNullableString(ps, 5, e.applicationName());
        setNullableString(ps, 6, e.dataType());
        setNullableString(ps, 7, e.resourceName());
        ps.setTimestamp(8, Timestamp.from(e.startTime()));
        if (e.endTime() != null) {
            ps.setTimestamp(9, Timestamp.from(e.endTime()));
        } else {
            ps.setNull(9, Types.TIMESTAMP_WITH_TIMEZONE);
        }
        Duration d = e.duration();
        if (d != null) {
            ps.setLong(10, d.toMillis());
        } else {
            ps.setNull(10, Types.BIGINT);
        }
        ps.setString(11, e.status());
        ps.setLong(12, e.recordsProcessed());
        ps.setLong(13, e.recordsFailed());
        ps.setInt(14,  e.chunksProcessed());
        ps.setLong(15, e.bytesTotal());
        ps.setString(16, serializeErrors(e.errors()));
        setNullableString(ps, 17, e.fatalError());
        setNullableString(ps, 18, serializeMetadata(e.metadata()));
    }

    private String serializeMetadata(java.util.Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Failed to serialize workflow_log metadata : {}", ex.getMessage());
            return null;
        }
    }

    private static void setNullableString(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }

    private String serializeErrors(Collection<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException ex) {
            log.warn("Echec serialisation errors en JSON", ex);
            return null;
        }
    }
}
