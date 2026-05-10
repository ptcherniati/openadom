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

    // Wrapper SECURITY DEFINER ; voir V5__record_workflow_final_count.sql .
    // Le dernier parametre p_final_count ( bigint ) est nullable : NULL pour
    // les workflows non-IMPORT ou si le COUNT post-afterCommit a echoue .
    private static final String INSERT_SQL = """
            SELECT oa_audit.record_workflow(
                ?::uuid, ?::varchar(32), ?::uuid, ?::varchar(128),
                ?::varchar(256), ?::varchar(256), ?::varchar(512),
                ?::timestamptz, ?::timestamptz, ?::bigint, ?::varchar(16),
                ?::bigint, ?::bigint, ?::int,
                ?::bigint, ?::jsonb, ?::text, ?::jsonb, ?::varchar(32), ?::bigint)
            """;

    private static final String DELETE_OLDER_THAN_SQL =
            "SELECT oa_audit.delete_workflow_logs_older_than(?::int)";

    /** Suppression d'une seule ligne d'historique par correlation_id . */
    private static final String DELETE_BY_CORRELATION_ID_SQL =
            "DELETE FROM oa_audit.workflow_log WHERE correlation_id = ?::uuid";

    /** Suppression totale ( admin ) . */
    private static final String DELETE_ALL_SQL =
            "DELETE FROM oa_audit.workflow_log";

    private static final String INSERT_START_SQL = """
            SELECT oa_audit.record_workflow_start(
                ?::uuid, ?::varchar(32), ?::uuid, ?::varchar(128),
                ?::varchar(256), ?::varchar(256), ?::varchar(512),
                ?::timestamptz, ?::bigint, ?::jsonb)
            """;

    private static final String MARK_ZOMBIES_SQL =
            "SELECT oa_audit.mark_zombie_workflows(?::int)";

    private static final String BEAT_SQL =
            "SELECT oa_audit.beat_workflow(?::uuid)";

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
     * Insertion synchrone d'une row IN_PROGRESS au demarrage du workflow .
     * Ferme le trou d'observabilite SIGKILL : sans cet appel , un crash
     * JVM avant le flush async laissait le workflow sans aucune trace .
     *
     * <p>Idempotent ( ON CONFLICT DO NOTHING cote SQL ) : un retry reseau
     * ne genere pas de doublon ; les fields end / records / errors sont
     * remplis plus tard par {@link #insertBatch} ( UPSERT ) .
     *
     * @return true si une row a ete cree , false si un doublon existait deja
     */
    public boolean recordStart(WorkflowLogEntry start) {
        if (start == null) {
            return false;
        }
        Boolean inserted = jdbcTemplate.queryForObject(INSERT_START_SQL, Boolean.class,
                start.correlationId(),
                start.workflowType(),
                start.userId(),
                start.userLogin(),
                start.applicationName(),
                start.dataType(),
                start.resourceName(),
                Timestamp.from(start.startTime()),
                start.bytesTotal(),
                serializeMetadata(start.metadata()));
        return Boolean.TRUE.equals(inserted);
    }

    /**
     * Emet un heartbeat sur la row IN_PROGRESS du workflow . Appele par
     * {@code HeartbeatService} pendant les phases longues ( finalize hook ) .
     * Idempotent , thread-safe ( UPDATE indexed atomic ) .
     *
     * @return true si la row a ete touchee ( workflow encore IN_PROGRESS ) ,
     *         false sinon ( deja terminal , inconnu , ou DB transient error )
     */
    public boolean beat(java.util.UUID correlationId) {
        if (correlationId == null) {
            return false;
        }
        try {
            Boolean updated = jdbcTemplate.queryForObject(BEAT_SQL, Boolean.class, correlationId);
            return Boolean.TRUE.equals(updated);
        } catch (RuntimeException e) {
            // Best-effort : un heartbeat manque ne doit pas casser le workflow .
            // Au pire le sweeper detectera le workflow comme zombie apres N min
            // -> on log warn et on continue .
            log.warn("Heartbeat failed for {} : {}", correlationId, e.getMessage());
            return false;
        }
    }

    /**
     * Passe a CANCELLED toutes les rows IN_PROGRESS dont
     * {@code COALESCE ( last_heartbeat_at , start_time )} est anterieur a
     * {@code thresholdMinutes} . Appele par {@link WorkflowZombieSweeper
     * @Scheduled} pour detecter les workflows orphelins ( SIGKILL , crash
     * JVM , panne machine ) .
     *
     * <p>Le COALESCE permet de gerer 2 cas :
     * <ul>
     *   <li>Workflow avec heartbeat ( phases longues ) : detecte zombie si
     *       last_heartbeat_at &gt; threshold . Marge x10 par rapport au beat
     *       interval ( 30 sec defaut ) -&gt; threshold 5 min OK .</li>
     *   <li>Workflow sans heartbeat ( phase courte ou pre-V5 ) : fallback
     *       sur start_time . Threshold doit alors couvrir le plus long
     *       workflow legitime sans heartbeat .</li>
     * </ul>
     *
     * @param thresholdMinutes seuil ( min ) ; recommande 5 avec heartbeat
     * @return nombre de rows passees a CANCELLED
     */
    public int markZombies(int thresholdMinutes) {
        Integer n = jdbcTemplate.queryForObject(MARK_ZOMBIES_SQL, Integer.class, thresholdMinutes);
        return n == null ? 0 : n;
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

    /**
     * Supprime une seule entree par correlation_id ( endpoint admin
     * d'oa-live , bouton corbeille par ligne ) .
     *
     * @return 1 si supprimee , 0 si l'id n'existait pas
     */
    public int deleteByCorrelationId(java.util.UUID correlationId) {
        if (correlationId == null) return 0;
        return jdbcTemplate.update(DELETE_BY_CORRELATION_ID_SQL, correlationId.toString());
    }

    /**
     * Supprime toutes les entries de {@code oa_audit.workflow_log} ( endpoint
     * admin d'oa-live , bouton " purge totale " avec confirmation textuelle
     * style GitLab ) . Operation irreversible .
     *
     * @return nombre de lignes supprimees
     */
    public int deleteAll() {
        return jdbcTemplate.update(DELETE_ALL_SQL);
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
        setNullableString(ps, 19, e.failedStage());
        if (e.finalCount() != null) {
            ps.setLong(20, e.finalCount());
        } else {
            ps.setNull(20, Types.BIGINT);
        }
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
