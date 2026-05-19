package fr.inra.oresing.monitoring.compensation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acces JDBC a la table {@code oa_audit.compensation_log} .
 *
 * <p>Toutes les ecritures passent par les fonctions wrappers
 * {@code SECURITY DEFINER} declarees dans la migration V2 . Pattern :
 * la fonction tourne sous identite owner ( {@code openAdomTechUser} ) ,
 * autorisant un caller au role applicatif ( {@code <UUID>_writer} ,
 * {@code applicationCreator} , ... ) a operer sur la queue sans GRANT
 * direct sur la table . Voir
 * {@link fr.inra.oresing.persistence.Schemas} et le commentaire en tete
 * de {@code V2__oa_audit_schema.sql} pour la justification securite .
 *
 * <p>Les SELECT simples ( findById , findByStatus ) restent direct car
 * SELECT est ouvert a PUBLIC sur les tables oa_audit ( lecture audit
 * sans donnee metier sensible ) .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Repository
public class CompensationLogRepository {

    private static final String FN_RECORD = """
            SELECT oa_audit.record_compensation(
                ?::varchar(64), ?::varchar(64), ?::varchar(128), ?::text,
                ?::uuid, ?::uuid, ?::varchar(128), ?::jsonb, ?::int)
            """;

    private static final String FN_DELETE =
            "SELECT oa_audit.delete_compensation(?::uuid)";

    private static final String FN_RECORD_FAILURE =
            "SELECT oa_audit.record_compensation_failure(?::uuid, ?::text, ?::int)";

    private static final String FN_LOCK_STALE_PENDING = """
            SELECT id, operation_type, target_schema, target_table, target_id,
                   correlation_id, user_id, user_login, payload,
                   status, started_at, ttl_minutes,
                   attempt_count, last_attempt_at, last_error
              FROM oa_audit.lock_stale_pending_compensations(?::int)
            """;

    private static final String FN_DELETE_FAILED_OLDER_THAN =
            "SELECT oa_audit.delete_failed_compensations_older_than(?::int)";

    private static final String SELECT_BY_STATUS_SQL = """
            SELECT id, operation_type, target_schema, target_table, target_id,
                   correlation_id, user_id, user_login, payload,
                   status, started_at, ttl_minutes,
                   attempt_count, last_attempt_at, last_error
              FROM oa_audit.compensation_log
             WHERE status = ?
             ORDER BY started_at DESC
             LIMIT ?
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, operation_type, target_schema, target_table, target_id,
                   correlation_id, user_id, user_login, payload,
                   status, started_at, ttl_minutes,
                   attempt_count, last_attempt_at, last_error
              FROM oa_audit.compensation_log
             WHERE id = ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompensationLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * INSERT row PENDING via {@code oa_audit.record_compensation(...)} .
     * La fonction genere l'UUID + status + started_at via DEFAULTs ; on
     * ne pousse que les champs metier . Retourne l'UUID genere .
     */
    public UUID recordPending(String operationType,
                              String targetSchema, String targetTable, String targetId,
                              UUID correlationId, UUID userId, String userLogin,
                              Map<String, Object> payload,
                              int ttlMinutes) {
        return jdbc.queryForObject(FN_RECORD, UUID.class,
                operationType, targetSchema, targetTable, targetId,
                correlationId, userId, userLogin,
                serializeJson(payload),
                ttlMinutes);
    }

    /** DELETE row via fonction wrapper . */
    public boolean delete(UUID id) {
        Boolean deleted = jdbc.queryForObject(FN_DELETE, Boolean.class, id);
        return Boolean.TRUE.equals(deleted);
    }

    /** Marque l'echec d'une tentative ; passe FAILED si seuil atteint . */
    public void recordFailure(UUID id, String error, int maxRetries) {
        jdbc.queryForObject(FN_RECORD_FAILURE, Object.class, id, error, maxRetries);
    }

    /**
     * Lock-and-fetch des rows PENDING dont le TTL est depasse via la
     * fonction {@code oa_audit.lock_stale_pending_compensations} .
     * La fonction wrappe un {@code SELECT FOR UPDATE SKIP LOCKED} ;
     * les rows lockees doivent etre traitees avant le commit de la
     * transaction caller ( = thread sweeper ) .
     */
    public List<CompensationLogEntry> lockStalePendingBatch(int batchSize) {
        return jdbc.query(FN_LOCK_STALE_PENDING, ROW_MAPPER, batchSize);
    }

    public List<CompensationLogEntry> findByStatus(String status, int limit) {
        return jdbc.query(SELECT_BY_STATUS_SQL, ROW_MAPPER, status, limit);
    }

    public CompensationLogEntry findById(UUID id) {
        List<CompensationLogEntry> list = jdbc.query(SELECT_BY_ID_SQL, ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Cleanup periodique des rows FAILED tres anciennes . */
    public int deleteFailedOlderThan(int retentionDays) {
        if (retentionDays <= 0) return 0;
        Integer n = jdbc.queryForObject(FN_DELETE_FAILED_OLDER_THAN, Integer.class, retentionDays);
        return n == null ? 0 : n;
    }

    private final RowMapper<CompensationLogEntry> ROW_MAPPER = (ResultSet rs, int rowNum) -> {
        Timestamp lastAttempt = rs.getTimestamp("last_attempt_at");
        return new CompensationLogEntry(
                (UUID) rs.getObject("id"),
                rs.getString("operation_type"),
                rs.getString("target_schema"),
                rs.getString("target_table"),
                rs.getString("target_id"),
                (UUID) rs.getObject("correlation_id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("user_login"),
                deserializeJson(rs.getString("payload")),
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getInt("ttl_minutes"),
                rs.getInt("attempt_count"),
                lastAttempt != null ? lastAttempt.toInstant() : null,
                rs.getString("last_error"));
    };

    private PGobject serializeJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            PGobject pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(objectMapper.writeValueAsString(payload));
            return pg;
        } catch (JsonProcessingException | SQLException ex) {
            log.warn("Failed to serialize compensation_log payload : {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> deserializeJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Failed to deserialize compensation_log payload : {}", ex.getMessage());
            return new HashMap<>();
        }
    }
}
