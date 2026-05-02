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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final JdbcTemplate jdbc;
    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        String workflowSql = """
                SELECT correlation_id, workflow_type, application_name, data_type,
                       status, records_processed, start_time, end_time, metadata
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
                    m.put("applicationName",    rs.getString("application_name"));
                    m.put("dataType",           rs.getString("data_type"));
                    m.put("status",             rs.getString("status"));
                    m.put("recordsProcessed",   rs.getLong("records_processed"));
                    m.put("startTime",          rs.getTimestamp("start_time"));
                    m.put("endTime",            rs.getTimestamp("end_time"));
                    m.put("metadata",           rs.getString("metadata"));
                    return m;
                },
                lookbackHours, limit);

        List<IntegrityRow> out = new ArrayList<>();
        for (Map<String, Object> w : workflows) {
            UUID corrId = (UUID) w.get("correlationId");
            long expected = (long) w.get("recordsProcessed");
            String status = (String) w.get("status");

            long stagingCount = 0;
            try {
                stagingCount = countStaging(corrId);
            } catch (RuntimeException ex) {
                log.warn("listIntegrity : staging count failed for {} : {}", corrId, ex.getMessage());
            }

            // referencevalue count : on lit metadata.binaryFileId pour
            // retrouver les rows {@code referencevalue WHERE binaryfile = ?} .
            // Si le workflow_log n'a pas ce champ ( workflows historiques
            // anterieurs au refactor #62 ) , finalCount reste a -1 et l'UI
            // affiche N/A sans declencher DATA_LOSS .
            String appName = (String) w.get("applicationName");
            UUID binaryFileId = extractBinaryFileId((String) w.get("metadata"));
            long finalCount = -1L;
            if (appName != null && SAFE_IDENT.matcher(appName).matches() && binaryFileId != null) {
                try {
                    finalCount = countReferencevalueByBinaryFile(appName, binaryFileId);
                } catch (RuntimeException ex) {
                    log.warn("listIntegrity : referencevalue count failed for {}.{} : {}",
                            appName, corrId, ex.getMessage());
                }
            }

            long delta;
            String integrityStatus;
            if (finalCount < 0) {
                // metadata.binaryFileId absent -> impossible de calculer
                // delta . On ne signale ni DATA_LOSS ni COHERENT .
                delta = 0;
                integrityStatus = "UNKNOWN";
            } else {
                delta = expected > 0 ? (expected - finalCount - stagingCount) : 0;
                integrityStatus = computeStatus(status, stagingCount, delta);
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
        String sql = "SELECT COUNT(*) FROM \"" + appSchema + "\".referencevalue WHERE binaryfile = ?";
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

    private String computeStatus(String workflowStatus, long stagingCount, long delta) {
        if (delta > 0) return "DATA_LOSS";
        if (stagingCount > 0) {
            if ("IN_PROGRESS".equals(workflowStatus)) return "IN_PROGRESS";
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

    public record IntegrityRow(
            UUID    correlationId,
            String  applicationName,
            String  dataType,
            String  workflowStatus,
            long    expectedTotal,
            long    finalCount,
            long    stagingCount,
            long    delta,
            String  integrityStatus      // COHERENT | INCONSISTENT | IN_PROGRESS | DATA_LOSS
    ) { }

    public record ReprocessResult(boolean started, String message) { }

    @SuppressWarnings("unused") private void __unusedRefs() {
        // keep import Schemas used somewhere
        String s = Schemas.AUDIT;
    }
}
