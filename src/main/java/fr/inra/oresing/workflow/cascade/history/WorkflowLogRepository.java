package fr.inra.oresing.workflow.cascade.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Suppression totale ( admin ) par batch via ctid : evite un unique
     * {@code DELETE FROM ... } sans WHERE qui , sur 1M+ lignes , tient un
     * verrou + bloque les lectures d'historique pendant 30 s+ . Chaque
     * batch ( {@link #DELETE_ALL_BATCH_SIZE} lignes ) s'auto-committe ,
     * gardant les verrous courts ( cf audit Q-5 ) . TRUNCATE ecarte :
     * exige le privilege TRUNCATE / ownership sur oa_audit que le role
     * applicatif n'a pas forcement ( DELETE direct deja autorise ) .
     */
    private static final String DELETE_ALL_BATCH_SQL =
            "DELETE FROM oa_audit.workflow_log WHERE ctid IN ("
            + "SELECT ctid FROM oa_audit.workflow_log LIMIT ?)";

    /** Taille de batch pour la purge totale ( compromis verrou court / nb d'iterations ) . */
    private static final int DELETE_ALL_BATCH_SIZE = 10_000;

    private static final String INSERT_START_SQL = """
            SELECT oa_audit.record_workflow_start(
                ?::uuid, ?::varchar(32), ?::uuid, ?::varchar(128),
                ?::varchar(256), ?::varchar(256), ?::varchar(512),
                ?::timestamptz, ?::bigint, ?::jsonb)
            """;

    private static final String MARK_ZOMBIES_SQL =
            "SELECT oa_audit.mark_zombie_workflows(?::int)";

    /**
     * Au boot Spring : passe TOUTE row {@code IN_PROGRESS} a {@code CANCELLED} ,
     * sans seuil de duree . Hypothese : la JVM vient de demarrer , donc toute
     * row IN_PROGRESS est forcement orpheline d'une JVM precedente . Plus
     * agressif que {@link #MARK_ZOMBIES_SQL} qui necessite un seuil > 0 min .
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} pour la coherence avec le sweeper
     * periodique ( evite double cancel concurrent ; ne tient pas la row si
     * une autre tx l'a deja lockee - acceptable au boot ) .
     */
    private static final String MARK_ALL_INPROGRESS_ORPHAN_SQL = """
            WITH orphans AS (
                SELECT correlation_id
                FROM oa_audit.workflow_log
                WHERE status = 'IN_PROGRESS'
                FOR UPDATE SKIP LOCKED
            )
            UPDATE oa_audit.workflow_log w
               SET status      = 'CANCELLED' ,
                   end_time    = now() ,
                   duration_ms = EXTRACT(EPOCH FROM (now() - w.start_time)) * 1000 ,
                   fatal_error = 'presumed dead at boot ( orphan from previous JVM )'
              FROM orphans o
             WHERE w.correlation_id = o.correlation_id
            """;

    private static final String BEAT_SQL =
            "SELECT oa_audit.beat_workflow(?::uuid)";

    /**
     * Met a jour le champ {@code metadata->>'phase'} d'une row workflow_log
     * en cours . Utilise {@code jsonb_set} pour preserver les autres cles
     * de {@code metadata} ( fileId , etc . ) . No-op si la row est deja
     * terminale ( WHERE status='IN_PROGRESS' ) .
     */
    /** SECURITY DEFINER function call ( V14 ) . Necessaire car la table
     *  workflow_log a seulement GRANT SELECT TO PUBLIC ; un UPDATE direct
     *  echoue sur les roles applicatifs ( ex contexte HTTP CreateDataUseCase )
     *  et abort la tx outer ( SQLState 25P02 ) , poisonnant tous les SQL
     *  subsequents . La fonction GRANT EXECUTE TO PUBLIC contourne le
     *  probleme de privileges tout en preservant la semantique
     *  ( WHERE status='IN_PROGRESS' bloque l'ecrasement des terminaux ) . */
    private static final String UPDATE_PHASE_SQL =
            "SELECT oa_audit.update_workflow_phase(?::uuid, ?::varchar)";

    /**
     * Tags the child IMPORT row in workflow_log with its parent
     * PUBLISH / UNPUBLISH / DELETE_FILE correlationId . Used by the history
     * endpoint ( {@code DashboardService.listHistory} ) to hide cascade
     * child IMPORT rows that exist only for accounting purposes ; without
     * this tag , every publish would surface as 2 rows in the audit page
     * ( parent PUBLISH + cascade IMPORT child for the same operation ) .
     * Applied at register time ( see
     * {@code PublishLifecycleCoordinator.registerChildImport} ) so the
     * filter survives backend restart / unregister cleanup .
     */
    private static final String UPDATE_PARENT_CORRELATION_SQL = """
            UPDATE oa_audit.workflow_log
               SET metadata = jsonb_set(
                       COALESCE(metadata, '{}'::jsonb),
                       '{parentCorrelationId}',
                       to_jsonb(?::text),
                       true)
             WHERE correlation_id = ?::uuid
            """;

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
        // Fix : pour 1 entry on utilise queryForObject ( la SQL est un
        // SELECT oa_audit.record_workflow ( ... ) qui retourne un boolean ;
        // jdbcTemplate.batchUpdate sur un SELECT laisse la connexion en
        // " idle in transaction " car le driver Postgres ne fire pas
        // l'auto-commit attendu pour les statements SELECT-as-DML ; cause
        // des workflow_log restant IN_PROGRESS apres recordEnd ) .
        if (entries.size() == 1) {
            WorkflowLogEntry e = entries.iterator().next();
            try {
                // Fix : ConnectionCallback + commit/rollback explicite . Avec
                // queryForObject standard sur SELECT oa_audit.record_workflow(...) ,
                // Hikari + JDBC PG laisse la connexion en " idle in transaction "
                // ( SELECT-as-function ne fire pas l'auto-commit attendu meme
                // avec hikari.autocommit=true ) , bloquant les futurs writes
                // workflow_log avec RowExclusiveLock . On force ici BEGIN /
                // executeQuery / COMMIT explicite , puis restore autoCommit
                // avant retour au pool .
                Boolean applied = jdbcTemplate.execute(
                        (java.sql.Connection conn) -> {
                            boolean prevAuto = conn.getAutoCommit();
                            conn.setAutoCommit(false);
                            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                                bindEntry(ps, e);
                                try (var rs = ps.executeQuery()) {
                                    boolean result = rs.next() && rs.getBoolean(1);
                                    conn.commit();
                                    return result;
                                }
                            } catch (SQLException sqlex) {
                                try { conn.rollback(); } catch (SQLException ignored) { /* best-effort */ }
                                throw sqlex;
                            } finally {
                                try { conn.setAutoCommit(prevAuto); } catch (SQLException ignored) { /* best-effort */ }
                            }
                        });
                return Boolean.TRUE.equals(applied) ? 1 : 0;
            } catch (RuntimeException ex) {
                log.warn("insertBatch ( single ) failed for {} : {}", e.correlationId(), ex.getMessage());
                throw ex;
            }
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
     * Met a jour le {@code metadata.phase} d'un workflow en cours .
     * Best-effort : une erreur d'UPDATE ne casse pas le workflow ; au pire
     * l'UI ne voit pas la nouvelle phase et reste sur la precedente .
     *
     * @param correlationId workflow concerne ; {@code null} = no-op
     * @param phase         nom court de la phase ( ex : {@code "DELETE_ROWS"} ,
     *                      {@code "COMMIT_VISIBILITY"} , {@code "SYNTHESIS_REBUILD"} ,
     *                      {@code "CACHE_CAPTURE"} ) . Voir
     *                      {@link fr.inra.oresing.workflow.WorkflowPhase} pour
     *                      la liste des constantes .
     * @return {@code true} si la row a ete updated ( workflow encore IN_PROGRESS ) ,
     *         {@code false} si row deja terminale ou correlationId/phase invalide
     */
    public boolean updatePhase(java.util.UUID correlationId, String phase) {
        if (correlationId == null || phase == null || phase.isBlank()) {
            return false;
        }
        try {
            // V14 : route via SECURITY DEFINER function oa_audit.update_workflow_phase
            // pour contourner le manque de privilege UPDATE sur workflow_log
            // dans les contextes HTTP applicatifs ( la table a seulement
            // GRANT SELECT TO PUBLIC ; un UPDATE direct echouait avec
            // SQLState 25P02 et poisonnait la tx outer ) . La fonction
            // retourne le ROW_COUNT - signe-toi pour savoir si la row a
            // ete touchee ( workflow encore IN_PROGRESS ) .
            Integer rows = jdbcTemplate.queryForObject(
                    UPDATE_PHASE_SQL, Integer.class, correlationId.toString(), phase);
            return rows != null && rows > 0;
        } catch (RuntimeException ex) {
            log.warn("updatePhase failed for {} ( phase={} ) : {}", correlationId, phase, ex.getMessage());
            return false;
        }
    }

    /**
     * Stamps {@code metadata.parentCorrelationId} on the child IMPORT row .
     * Idempotent and safe to call before the child row is fully visible
     * ( retried internally if the initial UPDATE matches no row , since the
     * cascade pipeline pre-persists the IN_PROGRESS row before invoking
     * {@code PublishLifecycleCoordinator.registerChildImport} ; if a slow
     * commit means the row is not yet visible , the UPDATE returns 0 and we
     * just log a warning ) .
     *
     * @param childCorrelationId  cid de la row workflow_log a tagger ; {@code null} = no-op
     * @param parentCorrelationId cid du PUBLISH / UNPUBLISH / DELETE_FILE parent ;
     *                            {@code null} = no-op
     * @return {@code true} si une row a ete tagged
     */
    public boolean setParentCorrelationId(java.util.UUID childCorrelationId,
                                          java.util.UUID parentCorrelationId) {
        if (childCorrelationId == null || parentCorrelationId == null) {
            return false;
        }
        try {
            int rows = jdbcTemplate.update(UPDATE_PARENT_CORRELATION_SQL,
                    parentCorrelationId.toString(), childCorrelationId.toString());
            if (rows == 0) {
                log.warn("setParentCorrelationId : no row matched for child {} ( parent {} ) - cascade row may not be visible yet",
                        childCorrelationId, parentCorrelationId);
            }
            return rows > 0;
        } catch (RuntimeException ex) {
            log.warn("setParentCorrelationId failed for child {} ( parent {} ) : {}",
                    childCorrelationId, parentCorrelationId, ex.getMessage());
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
    /**
     * Au boot Spring : passe toutes les rows {@code IN_PROGRESS} a
     * {@code CANCELLED} immediatement , sans seuil de duree . Voir
     * {@link #MARK_ALL_INPROGRESS_ORPHAN_SQL} pour la rationale .
     *
     * @return le nombre de rows orphelines passees a CANCELLED ( 0 si BD vierge )
     */
    public int markAllInProgressAsOrphans() {
        try {
            return jdbcTemplate.update(MARK_ALL_INPROGRESS_ORPHAN_SQL);
        } catch (RuntimeException ex) {
            log.warn("markAllInProgressAsOrphans failed : {}", ex.getMessage());
            return 0;
        }
    }

    public int markZombies(int thresholdMinutes) {
        Integer n = jdbcTemplate.queryForObject(MARK_ZOMBIES_SQL, Integer.class, thresholdMinutes);
        return n == null ? 0 : n;
    }

    /**
     * Sub-projection des workflows zombies fraichement marques FAILED par
     * {@link #markZombies} . Sert au {@link WorkflowZombieSweeper} pour
     * notifier l'admin par email apres detection ( workflows
     * IMPORT / UNPUBLISH / DELETE_FILE sont marques FAILED par la fonction
     * PG {@code oa_audit.mark_zombie_workflows} - voir migration V13 ) .
     *
     * @param withinSeconds fenetre de temps depuis end_time ( typiquement
     *                      le cron interval du sweeper + marge )
     * @return liste de {@link FailedZombieRow} - vide si rien a notifier
     */
    public java.util.List<FailedZombieRow> findRecentlyFailedZombies(int withinSeconds) {
        String sql = """
                SELECT correlation_id, workflow_type, user_login,
                       application_name, data_type, resource_name,
                       start_time, end_time, fatal_error
                FROM oa_audit.workflow_log
                WHERE status = 'FAILED'
                  AND workflow_type IN ('IMPORT', 'UNPUBLISH', 'DELETE_FILE')
                  AND end_time IS NOT NULL
                  AND end_time > now() - (? || ' seconds')::interval
                  AND ( fatal_error LIKE 'Operation interrompue%'
                        OR fatal_error LIKE 'Depot interrompu%' )
                ORDER BY end_time DESC
                """;
        return jdbcTemplate.query(sql, (rs, i) -> new FailedZombieRow(
                java.util.UUID.fromString(rs.getString("correlation_id")),
                rs.getString("workflow_type"),
                rs.getString("user_login"),
                rs.getString("application_name"),
                rs.getString("data_type"),
                rs.getString("resource_name"),
                rs.getTimestamp("start_time") != null ? rs.getTimestamp("start_time").toInstant() : null,
                rs.getTimestamp("end_time")   != null ? rs.getTimestamp("end_time").toInstant()   : null,
                rs.getString("fatal_error")
        ), withinSeconds);
    }

    /** Projection minimale pour notification email zombie FAILED . */
    public record FailedZombieRow(
            java.util.UUID  correlationId,
            String          workflowType,
            String          userLogin,
            String          applicationName,
            String          dataType,
            String          resourceName,
            java.time.Instant startTime,
            java.time.Instant endTime,
            String          fatalError
    ) {}

    /**
     * Detecte si un workflow UNPUBLISH ou DELETE_FILE recent sur le
     * fileId donne s'est termine en FAILED ( typiquement marque par le
     * sweeper zombie - cf migration V12 ) . Utilise par
     * {@code Phase2Handler.doPublishWithinScope} pour court-circuiter
     * la branche SKIP iso-data : si la depublication anterieure a
     * laisse les donnees dans un etat partiel , un republish iso-data
     * ne doit PAS declarer le workflow TERMINE immediatement mais
     * forcer une cascade FULL qui reconstruira la coherence .
     *
     * @param fileId        binaryfile uuid ( metadata.fileId dans
     *                      workflow_log )
     * @param withinHours   fenetre de recherche ( e.g. 24h ) - au-dela
     *                      on considere que le user a deja gere l'echec
     * @return true si au moins un workflow UNPUBLISH/DELETE_FILE FAILED
     *         existe dans cette fenetre pour ce fileId
     */
    public boolean hasRecentFailedUnpublish(java.util.UUID fileId, int withinHours) {
        if (fileId == null || withinHours <= 0) {
            return false;
        }
        final String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM oa_audit.workflow_log
                    WHERE status = 'FAILED'
                      AND workflow_type IN ('UNPUBLISH', 'DELETE_FILE')
                      AND metadata->>'fileId' = ?
                      AND end_time > now() - (? || ' hours')::interval
                )
                """;
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class,
                fileId.toString(), withinHours);
        return Boolean.TRUE.equals(exists);
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
        // SQL is `SELECT oa_audit.delete_workflow_logs_older_than(?::int)` which
        // returns a result set ( 1 row , 1 int column with deleted count ) .
        // `update()` calls JDBC executeUpdate() which fails with
        // `A result was returned when none was expected` on PostgreSQL .
        // Use queryForObject to consume the result row .
        Integer deleted = jdbcTemplate.queryForObject(
                DELETE_OLDER_THAN_SQL, Integer.class, retentionDays);
        return deleted == null ? 0 : deleted;
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
        // Boucle de batchs auto-commites : verrous courts , lectures
        // d'historique non bloquees pendant la purge ( cf audit Q-5 ) .
        int total = 0;
        int batch;
        do {
            batch = jdbcTemplate.update(DELETE_ALL_BATCH_SQL, DELETE_ALL_BATCH_SIZE);
            total += batch;
        } while (batch == DELETE_ALL_BATCH_SIZE);
        return total;
    }

    /**
     * Renvoie le correlationId d'un workflow IN_PROGRESS sur le fileId
     * donne , parmi les types fournis . Vide si aucun .
     *
     * @param applicationName scope applicatif
     * @param fileId          fileId stocke en {@code metadata.fileId}
     * @param workflowTypes   types eligibles ( PUBLISH , UNPUBLISH , DELETE_FILE )
     */
    /**
     * Details ( correlationId + workflow_type + user_login ) du workflow
     * IN_PROGRESS sur ce fileId , parmi les types fournis . Vide si aucun .
     *
     * <p>Utilise par {@code PublishLifecycleService.rejectIfWorkflowAlreadyInProgress}
     * pour construire un message d'erreur 409 Conflict explicite ( " un PUBLISH
     * est deja en cours par jdoe " ) en un seul round-trip DB .
     */
    public java.util.Optional<ActiveWorkflowDetails> findActiveDetailsByFileId(
            String applicationName, java.util.UUID fileId, java.util.List<String> workflowTypes) {
        if (applicationName == null || fileId == null || workflowTypes == null || workflowTypes.isEmpty()) {
            return java.util.Optional.empty();
        }
        final String sql = """
                SELECT correlation_id::text, workflow_type, user_login FROM oa_audit.workflow_log
                WHERE application_name = ?
                  AND status = 'IN_PROGRESS'
                  AND metadata->>'fileId' = ?
                  AND workflow_type = ANY (?)
                ORDER BY start_time DESC
                LIMIT 1
                """;
        try {
            return java.util.Optional.ofNullable(jdbcTemplate.query(sql, rs -> {
                if (!rs.next()) return null;
                return new ActiveWorkflowDetails(
                        java.util.UUID.fromString(rs.getString(1)),
                        rs.getString(2),
                        rs.getString(3));
            }, applicationName, fileId.toString(), workflowTypes.toArray(new String[0])));
        } catch (RuntimeException ex) {
            log.warn("findActiveDetailsByFileId failed for app={} fileId={} : {}",
                    applicationName, fileId, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Details exposes au caller pour la verification 409 Conflict ( type
     * d'action deja en cours + login auteur ) .
     */
    public record ActiveWorkflowDetails(java.util.UUID correlationId,
                                         String workflowType,
                                         String userLogin) {
    }

    /**
     * Recupere l'identifiant utilisateur du workflow IN_PROGRESS correspondant
     * a {@code correlationId} . Permet a {@code DashboardService.cancelWorkflow}
     * de fallback sur la table d'audit quand le {@code WorkflowActiveRegistry}
     * ( utilise par les uploads cascade ) n'a pas trace du workflow .
     *
     * <p>Cas d'usage : workflows publish / unpublish / delete_file qui passent
     * uniquement par {@code workflow_log} et ne sont pas enregistres dans le
     * registry live ( pas de chunks cascade a tracer ) .
     *
     * @return Optional contenant le user_id du workflow IN_PROGRESS , empty si
     *         le correlationId est inconnu ou si le workflow est deja terminal
     */
    public java.util.Optional<java.util.UUID> findActiveUserId(java.util.UUID correlationId) {
        if (correlationId == null) return java.util.Optional.empty();
        try {
            String userId = jdbcTemplate.queryForObject(
                    "SELECT user_id::text FROM oa_audit.workflow_log "
                  + "WHERE correlation_id = ?::uuid AND status = 'IN_PROGRESS'",
                    String.class,
                    correlationId.toString());
            return java.util.Optional.ofNullable(userId).map(java.util.UUID::fromString);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return java.util.Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("findActiveUserId failed for {} : {}", correlationId, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Recupere l'identifiant utilisateur d'un workflow par {@code correlationId} ,
     * quel que soit son statut ( IN_PROGRESS ou terminal ) . Utilise par
     * {@code DashboardService.cancelWorkflow} pour rendre l'operation idempotente :
     * si le workflow est deja terminal , on retourne 200 signalled=false plutot
     * que 404 ( evite l'erreur visible pour double-click ou retry reseau ) .
     */
    public java.util.Optional<java.util.UUID> findAnyUserId(java.util.UUID correlationId) {
        if (correlationId == null) return java.util.Optional.empty();
        try {
            String userId = jdbcTemplate.queryForObject(
                    "SELECT user_id::text FROM oa_audit.workflow_log WHERE correlation_id = ?::uuid",
                    String.class,
                    correlationId.toString());
            return java.util.Optional.ofNullable(userId).map(java.util.UUID::fromString);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return java.util.Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("findAnyUserId failed for {} : {}", correlationId, ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * UPDATE direct d'une row IN_PROGRESS vers un statut terminal
     * ( CANCELLED en pratique ) . Utilise par la supersedure pour
     * fermer l'audit du workflow ecrase avant d'en lancer un nouveau .
     *
     * @return 1 si la row a ete touchee , 0 si correlationId inconnu ou
     *         deja terminal
     */
    public int markCancelled(java.util.UUID correlationId, String reason) {
        if (correlationId == null) return 0;
        // SECURITY DEFINER wrapper - cf V7__oa_audit_cancel_workflow.sql .
        // Le UPDATE direct echouait sous role applicationManager ( pas de
        // privilege sur oa_audit.workflow_log ) , wrappe en " bad SQL grammar "
        // cote Spring . La fonction SECURITY DEFINER s'execute avec les
        // droits du owner ( openAdomTechUser ) , meme pattern que
        // record_workflow_start , beat_workflow , mark_zombie_workflows .
        Integer n = jdbcTemplate.queryForObject(
                "SELECT oa_audit.cancel_workflow(?::uuid, ?)",
                Integer.class,
                correlationId.toString(),
                reason);
        return n != null ? n : 0;
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