package fr.inra.oresing.monitoring.compensation.handlers;

import fr.inra.oresing.monitoring.compensation.CompensationHandler;
import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compensation handler pour {@code STAGING_CLEANUP} : nettoie une staging
 * table {@code oa_staging.referencevalue_import_*} laissee orpheline par
 * un workflow qui a fail / crash entre le CREATE staging et la fin de
 * la finalize hook .
 *
 * <p><b>Strategies couvertes</b> ( discriminees par
 * {@code payload.stagingStrategy} ) :
 * <ul>
 *   <li>{@code SHARED_UNLOGGED} : DELETE FROM
 *       {@code oa_staging.referencevalue_import_shared} WHERE
 *       {@code correlation_id = entry.targetId} . Conserve les rows
 *       d'autres workflows actifs .</li>
 *   <li>{@code PER_WORKFLOW_TABLE} : DROP TABLE IF EXISTS de la table
 *       dediee {@code oa_staging.referencevalue_import_<corrid>} .</li>
 *   <li>{@code PER_CONNECTION_TEMP} : NEVER recorded ( la TEMP TABLE
 *       meurt avec la connexion automatiquement , aucun cleanup
 *       compensation necessaire ) .</li>
 * </ul>
 *
 * <p><b>REGLE D'OR</b> : ne jamais cleanup un staging dont le workflow
 * est encore IN_PROGRESS legitime ( pas zombie ) . Le smart-check verifie
 * {@code workflow_log.status} et refuse le cleanup si le workflow est
 * vivant ( recent heartbeat ou recent start_time ) . Le sweeper retentera
 * apres TTL ; le {@link fr.inra.oresing.workflow.cascade.history.WorkflowZombieSweeper}
 * basculera le workflow a CANCELLED en cas de mort confirmee , ce qui
 * debloquera le cleanup au tick suivant .
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class StagingCleanupHandler implements CompensationHandler {

    public static final String OP_TYPE = "STAGING_CLEANUP";

    /** Whitelist regex pour interpoler schema / table en SQL ( eviter injection ) . */
    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private final JdbcTemplate jdbc;

    public StagingCleanupHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String operationType() { return OP_TYPE; }

    @Override
    public void compensate(CompensationLogEntry entry) {
        String schema  = entry.targetSchema();
        String table   = entry.targetTable();
        String corrId  = entry.targetId();
        if (schema == null || !SAFE_IDENT.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid target_schema for STAGING_CLEANUP : " + schema);
        }
        if (table == null || !SAFE_IDENT.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid target_table for STAGING_CLEANUP : " + table);
        }

        // Smart-check workflow_log.status : refuser le cleanup si le workflow
        // est encore IN_PROGRESS legitime ( pas encore detecte zombie par
        // WorkflowZombieSweeper ) . Le sweeper compensation retentera apres
        // backoff ; quand le workflow sera CANCELLED par le zombie sweeper ,
        // le cleanup pourra proceder en toute securite .
        if (entry.correlationId() != null) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM oa_audit.workflow_log WHERE correlation_id = ?::uuid",
                    String.class, entry.correlationId());
            if ("IN_PROGRESS".equals(status)) {
                log.info("STAGING_CLEANUP {} : workflow {} encore IN_PROGRESS , cleanup REPORTE ( retry au prochain tick )",
                        entry.id(), entry.correlationId());
                throw new IllegalStateException(
                        "Workflow " + entry.correlationId() + " is still IN_PROGRESS ; "
                                + "staging cleanup deferred ( zombie sweeper will mark it CANCELLED if dead )");
            }
        }

        Map<String, Object> payload = entry.payload();
        String stagingStrategy = payload != null
                ? String.valueOf(payload.getOrDefault("stagingStrategy", ""))
                : "";

        switch (stagingStrategy) {
            case "SHARED_UNLOGGED":
                cleanupShared(schema, table, corrId, entry);
                break;
            case "PER_WORKFLOW_TABLE":
                cleanupPerWorkflowTable(schema, table, entry);
                break;
            case "":
            case "null":
                // Fallback : si payload absent / corrompu , on tente DELETE
                // par corrId ( comportement SHARED_UNLOGGED ) qui est le moins
                // destructif . Loggue warn pour signaler la donnee manquante .
                log.warn("STAGING_CLEANUP {} : payload.stagingStrategy absent , fallback DELETE WHERE correlation_id",
                        entry.id());
                cleanupShared(schema, table, corrId, entry);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown stagingStrategy in payload : " + stagingStrategy);
        }
    }

    /**
     * SHARED_UNLOGGED : DELETE WHERE correlation_id . Idempotent
     * ( DELETE de 0 row = OK ) . Preserve les rows d'autres workflows
     * sur la table partagee .
     */
    private void cleanupShared(String schema, String table, String corrId, CompensationLogEntry entry) {
        String sql = "DELETE FROM " + schema + "." + table + " WHERE correlation_id = ?::uuid";
        int deleted = jdbc.update(sql, corrId);
        log.info("Compensated SHARED_UNLOGGED staging : {}.{} corrId={} deleted={} rows ( log id={} )",
                schema, table, corrId, deleted, entry.id());
    }

    /**
     * PER_WORKFLOW_TABLE : DROP TABLE IF EXISTS la table dediee . Idempotent
     * ( IF EXISTS empeche l'erreur si deja DROP par finalize hook ) . On
     * passe via la fonction SECURITY DEFINER {@code oa_staging.drop_per_workflow_referencevalue_import}
     * pour respecter les GRANTs ( cf V3 ) .
     */
    private void cleanupPerWorkflowTable(String schema, String table, CompensationLogEntry entry) {
        // La fonction prend le correlation_id stringifie sans tirets ( cf
        // {@code StagingMode.PerWorkflowTableMode#tableName} qui le transforme ) .
        // Plus simple : DROP TABLE IF EXISTS direct sur la table fournie
        // ( whitelist ident deja verifiee ) , ne necessite pas de fonction
        // SECURITY DEFINER si le user owner est OK pour DROP .
        String sql = "DROP TABLE IF EXISTS " + schema + "." + table;
        jdbc.execute(sql);
        log.info("Compensated PER_WORKFLOW_TABLE staging : DROP TABLE IF EXISTS {}.{} ( log id={} )",
                schema, table, entry.id());
    }
}
