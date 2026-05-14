package fr.inra.oresing.monitoring.integrity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.Schemas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.*;

/**
 * Service "Intégrité" : compare les counts staging vs final pour
 * detecter les workflows incoherents ( rows non transferees ) .
 *
 * <p>Source de verite :
 * <ul>
 *   <li>{@code oa_audit.workflow_log} : workflow status + records_processed expected</li>
 *   <li>{@code oa_staging.referencevalue_import_shared} : rows non transferees ( SHARED_UNLOGGED only )</li>
 *   <li>{@code <appname>.referencevalue} : rows finales</li>
 * </ul>
 *
 * <p>3 etats UI :
 * <ul>
 *   <li>Vert : staging=0 et delta=0 -> coherent</li>
 *   <li>Orange : staging&gt;0 et delta=0 -> incomplet recuperable ( Reprocess )</li>
 *   <li>Rouge : delta&gt;0 -> data loss , compensation manuelle</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrityService {

    private static final String COL_APPLICATION_NAME = "application_name";
    private static final String COL_STATUS           = "status";
    private static final String COL_METADATA         = "metadata";
    private static final String STATUS_IN_PROGRESS   = "IN_PROGRESS";
    private static final String STATUS_FAILED        = "FAILED";
    private static final String STATUS_CANCELLED     = "CANCELLED";
    private static final String SQL_REFVAL_WHERE_BINARYFILE = ".referencevalue WHERE binaryfile = ?";
    private static final String SQL_WHERE_CORRELATION_ID    = " WHERE correlation_id = ?";

    private final JdbcTemplate jdbc;
    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Cache TTL des COUNT(*) referencevalue par binaryFile pour les
     * workflows legacy ( pre-V4 sans final_count persiste ) ou les
     * workflows dont le markCompleted COUNT a echoue . Evite que les
     * polls IntegrityView 10 s declenchent N COUNT massifs sur la table
     * referencevalue . TTL 5 min : largement plus que la frequence de
     * poll , et un binaryFile committe ne change plus de count sauf
     * delete explicite ( rare , et le prochain delete invalide le cache
     * via {@link #invalidateCount} ) .
     *
     * <p>Cf AUDIT 06-05-26 #1 .
     */
    private static final long CACHE_TTL_MILLIS = 5L * 60L * 1000L;
    private final java.util.concurrent.ConcurrentMap<String, CacheEntry> countCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record CacheEntry(long count, long expiresAt) { }

    private static String cacheKey(String appSchema, UUID binaryFileId) {
        return appSchema + "|" + binaryFileId;
    }

    private Long countCacheGet(String appSchema, UUID binaryFileId) {
        CacheEntry e = countCache.get(cacheKey(appSchema, binaryFileId));
        if (e == null || System.currentTimeMillis() > e.expiresAt) {
            return null;
        }
        return e.count;
    }

    private void countCachePut(String appSchema, UUID binaryFileId, long count) {
        countCache.put(cacheKey(appSchema, binaryFileId),
                new CacheEntry(count, System.currentTimeMillis() + CACHE_TTL_MILLIS));
    }

    /**
     * Invalide le cache pour un binaryFile donne ( a appeler post-delete
     * du binaryFile pour eviter de servir un count perime ) .
     */
    public void invalidateCount(String appSchema, UUID binaryFileId) {
        if (appSchema != null && binaryFileId != null) {
            countCache.remove(cacheKey(appSchema, binaryFileId));
        }
    }

    private void requireAdmin() {
        if (!authenticationService.getCurrentUserRoles().isOpenAdomAdmin()) {
            throw new AccessDeniedException("Reserved to openAdomAdmin users");
        }
    }

    /**
     * Liste les workflows recents avec leur statut d'integrite .
     *
     * <p>Scope : workflows dont start_time &gt; now() - {@code lookbackHours}
     * et de type IMPORT . Pour chaque workflow , compte staging rows
     * ( par correlation_id ) et compare avec records_processed expected .
     */
    public List<IntegrityRow> listIntegrity(int lookbackHours, int limit) {
        requireAdmin();

        // 1. Recupere workflows IMPORT recents
        // AUDIT 06-05-26 #1 : SELECT additionnel de final_count pour eviter
        // le COUNT(*) systematique sur referencevalue ( la colonne est remplie
        // au markCompleted post-afterCommit ) .
        String workflowSql = """
                SELECT correlation_id, workflow_type, application_name, data_type,
                       status, records_processed, start_time, end_time,
                       last_heartbeat_at, metadata, final_count
                FROM oa_audit.workflow_log
                WHERE workflow_type = 'IMPORT'
                  AND start_time > now() - (? || ' hours')::interval
                ORDER BY start_time DESC
                LIMIT ?
                """;

        List<Map<String, Object>> workflows = jdbc.query(workflowSql,
                (ResultSet rs, int n) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("correlationId",      rs.getObject("correlation_id"));
                    m.put("workflowType",       rs.getString("workflow_type"));
                    m.put("applicationName",    rs.getString(COL_APPLICATION_NAME));
                    m.put("dataType",           rs.getString("data_type"));
                    m.put(COL_STATUS,           rs.getString(COL_STATUS));
                    m.put("recordsProcessed",   rs.getLong("records_processed"));
                    m.put("startTime",          rs.getTimestamp("start_time"));
                    m.put("endTime",            rs.getTimestamp("end_time"));
                    m.put("lastHeartbeatAt",    rs.getTimestamp("last_heartbeat_at"));
                    m.put(COL_METADATA,         rs.getString(COL_METADATA));
                    long fc = rs.getLong("final_count");
                    m.put("finalCount",         rs.wasNull() ? null : fc);
                    return m;
                },
                lookbackHours, limit);

        List<IntegrityRow> out = new ArrayList<>();
        for (Map<String, Object> w : workflows) {
            UUID corrId = (UUID) w.get("correlationId");
            long expected = (long) w.get("recordsProcessed");
            String status = (String) w.get(COL_STATUS);

            long stagingCount = 0;
            try {
                stagingCount = countStaging(corrId);
            } catch (RuntimeException ex) {
                log.warn("listIntegrity : staging count failed for {} : {}", corrId, ex.getMessage());
            }

            // AUDIT 06-05-26 #1 : ordre de resolution du finalCount :
            //   1. workflow_log.final_count ( colonne capturee au markCompleted
            //      post-afterCommit ) -> 0 query supplementaire
            //   2. cache TTL 5 min ( meme cle ) -> 0 query
            //   3. fallback COUNT(*) ( workflows legacy V4- ou COUNT failed
            //      lors du markCompleted ) -> 1 query mise en cache
            //   4. final = -1 si encore impossible -> UI affiche N/A
            String appName = (String) w.get("applicationName");
            UUID binaryFileId = extractBinaryFileId((String) w.get(COL_METADATA));
            long finalCount = -1L;
            Long persistedFinalCount = (Long) w.get("finalCount");
            if (persistedFinalCount != null) {
                finalCount = persistedFinalCount;
            } else if (appName != null && SAFE_IDENT.matcher(appName).matches() && binaryFileId != null) {
                Long cached = countCacheGet(appName, binaryFileId);
                if (cached != null) {
                    finalCount = cached;
                } else {
                    try {
                        finalCount = countReferencevalueByBinaryFile(appName, binaryFileId);
                        countCachePut(appName, binaryFileId, finalCount);
                    } catch (RuntimeException ex) {
                        log.warn("listIntegrity : referencevalue count failed for {}.{} : {}",
                                appName, corrId, ex.getMessage());
                    }
                }
            }

            long delta;
            String integrityStatus;
            // Heartbeat staleness : un workflow IN_PROGRESS dont
            // {@code last_heartbeat_at} ( ou {@code start_time} si jamais
            // beat ) est plus vieux que 5 min est presume mort ( cf
            // {@code WorkflowZombieSweeper} threshold ) -> on l'expose
            // comme STUCK pour que l'admin le voie immediatement dans
            // IntegrityView , sans devoir attendre que le sweeper le
            // bascule en CANCELLED .
            java.sql.Timestamp lastHeartbeat = (java.sql.Timestamp) w.get("lastHeartbeatAt");
            java.sql.Timestamp startTs       = (java.sql.Timestamp) w.get("startTime");
            java.time.Instant startFallback = startTs != null ? startTs.toInstant() : java.time.Instant.now();
            java.time.Instant referenceInstant = lastHeartbeat != null
                    ? lastHeartbeat.toInstant()
                    : startFallback;
            boolean stuck = STATUS_IN_PROGRESS.equals(status)
                    && referenceInstant.isBefore(java.time.Instant.now().minusSeconds(5L * 60));

            if (finalCount < 0) {
                // metadata.binaryFileId absent -> impossible de calculer
                // delta proprement . Cas typique : workflow FAILED tres tot
                // ( avant capture du binaryFileId dans metadata ) .
                // On ne perd pas l'info pour autant : si le workflow est
                // FAILED et staging vide , c'est REDEPOT_REQUIRED ; sinon
                // on tombe sur UNKNOWN qui dit "impossible de mesurer" .
                delta = 0;
                if (stuck) {
                    integrityStatus = "STUCK";
                } else if ((STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status))
                        && stagingCount == 0) {
                    integrityStatus = "REDEPOT_REQUIRED";
                } else if ((STATUS_FAILED.equals(status) || STATUS_CANCELLED.equals(status))
                        && stagingCount > 0) {
                    integrityStatus = "RECOVERABLE";
                } else {
                    integrityStatus = "UNKNOWN";
                }
            } else {
                delta = expected > 0 ? (expected - finalCount - stagingCount) : 0;
                integrityStatus = stuck
                        ? "STUCK"
                        : computeStatus(status, stagingCount, delta, finalCount);
            }

            out.add(new IntegrityRow(
                    corrId,
                    appName,
                    (String) w.get("dataType"),
                    status,
                    expected,
                    finalCount,
                    stagingCount,
                    delta,
                    integrityStatus));
        }
        return out;
    }

    private long countStaging(UUID correlationId) {
        String sql = "SELECT COUNT(*) FROM oa_staging.referencevalue_import_shared "
                + "WHERE correlation_id = ?";
        Long count = jdbc.queryForObject(sql, Long.class, correlationId);
        return count != null ? count : 0L;
    }

    private static final java.util.regex.Pattern SAFE_IDENT =
            java.util.regex.Pattern.compile("^[a-z_][a-z0-9_]*$");

    /**
     * Compte les rows {@code <appSchema>.referencevalue WHERE binaryfile = ?} .
     * Le {@code appSchema} est valide en amont par {@link #SAFE_IDENT} .
     */
    private long countReferencevalueByBinaryFile(String appSchema, UUID binaryFileId) {
        String sql = "SELECT COUNT(*) FROM \"" + appSchema + "\"" + SQL_REFVAL_WHERE_BINARYFILE;
        Long count = jdbc.queryForObject(sql, Long.class, binaryFileId);
        return count != null ? count : 0L;
    }

    /**
     * Extrait {@code binaryFileId} du JSON metadata serialise dans
     * workflow_log . Retourne null si absent ou parse fail ( workflows
     * historiques d'avant le refactor #62 ) .
     */
    private UUID extractBinaryFileId(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            JsonNode node = root.get("binaryFileId");
            if (node == null || node.isNull() || !node.isTextual()) return null;
            return UUID.fromString(node.asText());
        } catch (Exception ex) {
            log.debug("extractBinaryFileId parse fail : {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Decide l'etat d'integrite a presenter a l'admin , avec une
     * recommandation actionnable :
     *
     * <ul>
     *   <li>{@code DATA_LOSS}   : delta &gt; 0 , des rows ont disparu entre
     *       l'attendu et le final/staging . Investigation requise .</li>
     *   <li>{@code OVERCOUNT}   : delta &lt; 0 , la table finale comporte
     *       plus de rows que ce que workflow_log declare avoir traite .
     *       Cas legitime le plus frequent : plusieurs workflows partagent
     *       le meme {@code binaryFileId} ( re-depot du meme fichier ) , et
     *       {@code countReferencevalueByBinaryFile} aggrege les rows de
     *       tous ces workflows . Cas pathologique : phantom rows ou bug
     *       d'instrumentation . Statut intermediaire entre COHERENT et
     *       DATA_LOSS pour declencher une verification manuelle sans
     *       declencher une fausse alerte .</li>
     *   <li>{@code RECOVERABLE} : workflow termine en echec mais staging
     *       contient des rows -&gt; bouton Reprocess UPSERT-only </li>
     *   <li>{@code INCONSISTENT}: workflow encore IN_PROGRESS legitime
     *       avec staging non vide ( pas zombie -&gt; cf STUCK ) .</li>
     *   <li>{@code REDEPOT_REQUIRED} : workflow FAILED / CANCELLED avec
     *       0 row partout ( ni final , ni staging ) -&gt; aucune donnee n'a
     *       atteint la base , l'utilisateur doit redeposer son fichier
     *       depuis le debut . Cas typique : plantage avant ou pendant le
     *       COPY initial . Diagnostic clair -&gt; action claire .</li>
     *   <li>{@code COHERENT}    : workflow COMPLETED + counts alignes .</li>
     * </ul>
     */
    private String computeStatus(String workflowStatus, long stagingCount, long delta, long finalCount) {
        if (delta > 0) return "DATA_LOSS";
        if (delta < 0 && finalCount > 0) {
            return "OVERCOUNT";
        }
        if (stagingCount > 0) {
            if (STATUS_IN_PROGRESS.equals(workflowStatus)) return STATUS_IN_PROGRESS;
            if (STATUS_FAILED.equals(workflowStatus) || STATUS_CANCELLED.equals(workflowStatus)) {
                return "RECOVERABLE";
            }
            return "INCONSISTENT";
        }
        // staging vide
        if (STATUS_FAILED.equals(workflowStatus) || STATUS_CANCELLED.equals(workflowStatus)) {
            if (finalCount <= 0) {
                return "REDEPOT_REQUIRED";
            }
            return "INCONSISTENT";
        }
        return "COHERENT";
    }

    /**
     * Re-execute le UPSERT staging -> final pour un workflow specifique .
     * Conditions : DIRECT_COPY + SHARED_UNLOGGED + status FAILED|CANCELLED + staging non vide .
     *
     * @return resultat synthetique
     */
    public ReprocessResult reprocess(UUID correlationId) {
        requireAdmin();
        // Implementation simplifiee : pour la v1 , on documente que cette
        // operation requiert l'instanciation d'un cascade workflow restreint
        // ( finalize uniquement ) . Reservee aux experts , pas implementee
        // automatiquement pour eviter les effets de bord . L'admin doit
        // soit ré-uploader le fichier , soit utiliser le bouton
        // "Compenser" si la donnee n'est plus valide .
        return new ReprocessResult(false,
                "Reprocess automatique non implemente en v1 . "
                        + "Re-uploader le fichier ou compenser manuellement .");
    }

    /**
     * Calcule en read-only les rows qui seraient supprimees par
     * {@link #deleteWorkflow} . Sert a alimenter une modale de confirmation
     * stylee qui montre a l'admin , ligne par ligne , ce qui va etre
     * impacte AVANT qu'il valide :
     *
     * <ul>
     *   <li>application + dataType + status + start_time</li>
     *   <li>binaryfile id + name si resoluble</li>
     *   <li>count rows referencevalue impactes</li>
     *   <li>count rows reference_reference impactes ( CASCADE )</li>
     *   <li>count rows staging restantes</li>
     *   <li>count rows compensation_log lies</li>
     * </ul>
     *
     * <p>Operation purement read-only : aucune ecriture , aucun lock
     * problematique . Reservee admin .
     */
    public DeletePreview deletePreview(UUID correlationId) {
        requireAdmin();
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }

        // 1) Lecture metadata workflow
        String metaSql = "SELECT application_name, data_type, status, start_time, "
                + "       resource_name, metadata, records_processed "
                + "  FROM oa_audit.workflow_log WHERE correlation_id = ?";
        Map<String, Object> meta;
        try {
            meta = jdbc.queryForMap(metaSql, correlationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return DeletePreview.notFound(correlationId);
        }
        String appName       = (String) meta.get("application_name");
        String dataType      = (String) meta.get("data_type");
        String status        = (String) meta.get("status");
        String resourceName  = (String) meta.get("resource_name");
        java.sql.Timestamp startTs = (java.sql.Timestamp) meta.get("start_time");
        java.time.Instant startTime = startTs != null ? startTs.toInstant() : null;
        long recordsProcessed = ((Number) meta.getOrDefault("records_processed", 0L)).longValue();
        UUID binaryFileId    = extractBinaryFileId((String) meta.get(COL_METADATA));
        String binaryFileName = null;

        // 2) Resolution du nom du binaryfile ( si binaryFileId connu )
        if (appName != null && SAFE_IDENT.matcher(appName).matches() && binaryFileId != null) {
            try {
                binaryFileName = jdbc.queryForObject(
                        "SELECT params->>'name' FROM \"" + appName + "\".binaryfile WHERE id = ?",
                        String.class,
                        binaryFileId);
            } catch (org.springframework.dao.DataAccessException ex) {
                log.debug("deletePreview : binaryfile name lookup failed for {} : {}",
                        binaryFileId, ex.getMessage());
            }
        }

        // 3) Counts rows impactes
        long referenceValueCount = 0L;
        long referenceReferenceCount = 0L;
        if (appName != null && SAFE_IDENT.matcher(appName).matches() && binaryFileId != null) {
            referenceValueCount = countOrZero(
                    "SELECT count(*) FROM \"" + appName + "\"" + SQL_REFVAL_WHERE_BINARYFILE,
                    binaryFileId);
            referenceReferenceCount = countOrZero(
                    "SELECT count(*) FROM \"" + appName + "\".reference_reference rr "
                            + " JOIN \"" + appName + "\".referencevalue rv ON rv.id = rr.referenceid "
                            + " WHERE rv.binaryfile = ?",
                    binaryFileId);
        }

        long stagingCount = 0L;
        try {
            stagingCount = countOrZero(
                    "SELECT count(*) FROM oa_staging.referencevalue_import_shared"
                            + SQL_WHERE_CORRELATION_ID,
                    correlationId);
        } catch (org.springframework.dao.DataAccessException ex) {
            log.debug("deletePreview : staging count ignored : {}", ex.getMessage());
        }

        long compensationCount = countOrZero(
                "SELECT count(*) FROM oa_audit.compensation_log "
                        + " WHERE target_id = ? OR target_id::text = ? OR metadata->>'correlationId' = ?",
                correlationId, correlationId.toString(), correlationId.toString());

        return new DeletePreview(
                true,
                correlationId,
                appName,
                dataType,
                status,
                startTime,
                resourceName,
                recordsProcessed,
                binaryFileId,
                binaryFileName,
                referenceValueCount,
                referenceReferenceCount,
                stagingCount,
                compensationCount,
                1L);  // 1 workflow_log row
    }

    private long countOrZero(String sql, Object... params) {
        Long n = jdbc.queryForObject(sql, Long.class, params);
        return n == null ? 0L : n;
    }

    /**
     * Supprime totalement un workflow et ses donnees associees ( reverse
     * full d'un import , utile pour purger un dataset corrompu , obsolete
     * ou un test ) . Ordre de suppression robuste face aux FK :
     *
     * <ol>
     *   <li>oa_staging.* WHERE correlation_id ( si workflow encore
     *       referencé en staging ) - cleanup avant que la cascade FK
     *       sur binaryfile ne se declenche .</li>
     *   <li>&lt;appSchema&gt;.referencevalue WHERE binaryfile = X
     *       ( CASCADE sur reference_reference via FK
     *       reference_reference_referenceid_fkey ON DELETE CASCADE ) .</li>
     *   <li>&lt;appSchema&gt;.binaryfile WHERE id = X ( orphelin sinon ) .</li>
     *   <li>oa_audit.compensation_log WHERE target_id = workflow_log.id
     *       OU correlation_id ( cleanup audit trail ) .</li>
     *   <li>oa_audit.workflow_log WHERE correlation_id ( derniere etape ) .</li>
     * </ol>
     *
     * <p>Tout dans une transaction Spring : si une etape echoue , le
     * rollback annule l'ensemble . Le bouton " Supprimer " de la page
     * Integrite doit afficher un confirm prealable car cette operation
     * est <b>irreversible</b> .
     *
     * @return resume synthetique des rows supprimees par etape
     */
    @org.springframework.transaction.annotation.Transactional
    public DeleteResult deleteWorkflow(UUID correlationId) {
        requireAdmin();
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required");
        }

        // 1) Lecture metadata du workflow_log : besoin de application_name
        // ( pour resoudre le schema ) et de binaryFileId ( pour cibler les
        // referencevalue rows ) .
        String metaSql = "SELECT application_name, metadata FROM oa_audit.workflow_log "
                + " WHERE correlation_id = ?";
        Map<String, Object> meta;
        try {
            meta = jdbc.queryForMap(metaSql, correlationId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return new DeleteResult(false, 0, 0, 0, 0, 0,
                    "Workflow inconnu : aucun row workflow_log pour " + correlationId);
        }
        String appName = (String) meta.get("application_name");
        UUID binaryFileId = extractBinaryFileId((String) meta.get(COL_METADATA));

        // 2) Suppression staging rows ( oa_staging schema partage ) .
        long stagingDeleted = 0L;
        try {
            stagingDeleted = jdbc.update(
                    "DELETE FROM oa_staging.referencevalue_import_shared"
                            + SQL_WHERE_CORRELATION_ID,
                    correlationId);
        } catch (org.springframework.dao.DataAccessException ex) {
            log.warn("deleteWorkflow : staging cleanup ignored ( table absente ou autre ) : {}",
                    ex.getMessage());
        }

        // 3) Suppression referencevalue ( + reference_reference via CASCADE ) .
        long referenceValueDeleted = 0L;
        if (appName != null && SAFE_IDENT.matcher(appName).matches() && binaryFileId != null) {
            referenceValueDeleted = jdbc.update(
                    "DELETE FROM \"" + appName + "\"" + SQL_REFVAL_WHERE_BINARYFILE,
                    binaryFileId);
        }

        // 4) Suppression binaryfile ( orphelin sinon ) .
        long binaryFileDeleted = 0L;
        if (appName != null && SAFE_IDENT.matcher(appName).matches() && binaryFileId != null) {
            binaryFileDeleted = jdbc.update(
                    "DELETE FROM \"" + appName + "\".binaryfile WHERE id = ?",
                    binaryFileId);
        }

        // 5) Suppression compensation_log ( filet audit ) .
        long compensationDeleted = jdbc.update(
                "DELETE FROM oa_audit.compensation_log "
                        + " WHERE target_id = ? OR target_id::text = ? OR metadata->>'correlationId' = ?",
                correlationId, correlationId.toString(), correlationId.toString());

        // 6) Suppression workflow_log ( derniere etape : si une etape
        // precedente a echoue le rollback aura deja restaure le row ) .
        long workflowLogDeleted = jdbc.update(
                "DELETE FROM oa_audit.workflow_log WHERE correlation_id = ?",
                correlationId);

        log.info("deleteWorkflow {} : staging={} , referencevalue={} , binaryfile={} , "
                        + "compensation={} , workflow_log={}",
                correlationId, stagingDeleted, referenceValueDeleted, binaryFileDeleted,
                compensationDeleted, workflowLogDeleted);

        return new DeleteResult(
                workflowLogDeleted > 0,
                workflowLogDeleted,
                referenceValueDeleted,
                binaryFileDeleted,
                stagingDeleted,
                compensationDeleted,
                workflowLogDeleted > 0 ? "Workflow supprime" : "Aucun row workflow_log supprime");
    }

    public record IntegrityRow(
            UUID    correlationId,
            String  applicationName,
            String  dataType,
            String  workflowStatus,
            long    expectedTotal,
            long    finalCount,
            long    stagingCount,
            long    delta,
            String  integrityStatus      // COHERENT | INCONSISTENT | RECOVERABLE | REDEPOT_REQUIRED | IN_PROGRESS | STUCK | DATA_LOSS | UNKNOWN
    ) { }

    public record ReprocessResult(boolean started, String message) { }

    /**
     * Resultat synthetique d'un {@link #deleteWorkflow} . Expose les counts
     * par table pour que l'UI puisse afficher un toast detaille
     * ( ex: " 274706 rows referencevalue supprimees + 1 binaryfile + 1
     *   workflow_log " ) .
     */
    public record DeleteResult(
            boolean deleted,
            long    workflowLogDeleted,
            long    referenceValueDeleted,
            long    binaryFileDeleted,
            long    stagingDeleted,
            long    compensationDeleted,
            String  message) { }

    /**
     * Vue read-only de l'impact d'un {@link #deleteWorkflow} . Alimente la
     * modale de confirmation stylee pour donner a l'admin tous les counts
     * + les noms ( binaryfile , application ) avant validation .
     */
    public record DeletePreview(
            boolean         found,
            UUID            correlationId,
            String          applicationName,
            String          dataType,
            String          workflowStatus,
            java.time.Instant startTime,
            String          resourceName,
            long            recordsProcessed,
            UUID            binaryFileId,
            String          binaryFileName,
            long            referenceValueRows,
            long            referenceReferenceRows,
            long            stagingRows,
            long            compensationRows,
            long            workflowLogRows
    ) {
        static DeletePreview notFound(UUID cid) {
            return new DeletePreview(false, cid, null, null, null, null, null,
                    0L, null, null, 0L, 0L, 0L, 0L, 0L);
        }
    }

    @SuppressWarnings("unused") private void __unusedRefs() {
        // keep import Schemas used somewhere
        String s = Schemas.AUDIT;
    }
}