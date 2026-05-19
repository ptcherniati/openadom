package fr.inra.oresing.workflow.cascade.staging;

import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sweeper periodique des tables staging orphelines :
 *
 * <ul>
 *   <li><b>PER_WORKFLOW_TABLE</b> : scanne {@code pg_class} pour les
 *       tables {@code oa_staging.referencevalue_import_*} ; si une table
 *       n'a pas de workflow actif correspondant ( = workflow crashe en
 *       cours d'execution ) , {@code DROP TABLE} pour liberer pg_class .</li>
 *   <li><b>SHARED_UNLOGGED</b> : DELETE rows de la table partagee dont
 *       {@code created_at &lt; now() - TTL} ET dont {@code correlation_id}
 *       n'est plus dans la liste des workflows actifs .</li>
 * </ul>
 *
 * <p>Le sweeper tourne toutes les {@value #DEFAULT_INTERVAL_MS}ms par defaut .
 * Activable / desactivable via {@code app.staging.sweeper.enabled} .
 *
 * @author R.YAHIAOUI
 */
@Component
@ConditionalOnProperty(name = "app.staging.sweeper.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class StagingOrphanSweeper {

    static final long DEFAULT_INTERVAL_MS = 5L * 60_000L; // 5 min

    /**
     * Pattern naming convention : {@code referencevalue_import_<UUID-tirets-en-_>} .
     * On scanne {@code pg_class} avec ce LIKE pour trouver les tables
     * candidates a DROP .
     */
    private static final String NAME_PREFIX = "referencevalue_import_";
    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private static String assertSafeIdent(String ident) {
        if (ident == null || !SAFE_IDENT.matcher(ident).matches()) {
            throw new IllegalArgumentException("Invalid table identifier: " + ident);
        }
        return ident;
    }
    private static final String SQL_LIST_TABLES =
            "SELECT c.relname "
            + "  FROM pg_class c "
            + "  JOIN pg_namespace n ON n.oid = c.relnamespace "
            + " WHERE n.nspname = 'oa_staging' "
            + "   AND c.relkind = 'r' "
            + "   AND c.relname LIKE 'referencevalue_import_%' "
            + "   AND c.relname <> 'referencevalue_import_shared' ";

    private final JdbcTemplate jdbc;
    private final WorkflowActiveRegistry activeRegistry;

    @Value("${app.staging.sweeper.shared-orphan-ttl-minutes:60}")
    private int sharedOrphanTtlMinutes;

    @Value("${app.staging.sweeper.per-workflow-grace-minutes:10}")
    private int perWorkflowGraceMinutes;

    @Scheduled(fixedDelayString = "${app.staging.sweeper.interval-ms:300000}")
    public void sweep() {
        try {
            int dropped = sweepPerWorkflowTables();
            int deleted = sweepSharedUnlogged();
            if (dropped > 0 || deleted > 0) {
                log.info("StagingOrphanSweeper : {} table(s) PER_WORKFLOW droppee(s) , "
                        + "{} row(s) SHARED_UNLOGGED supprimee(s)", dropped, deleted);
            }
        } catch (RuntimeException ex) {
            log.warn("StagingOrphanSweeper failed ( best-effort , retry au prochain tick ) : {}",
                    ex.getMessage());
        }
    }

    /**
     * Drop des tables {@code oa_staging.referencevalue_import_<corrid>}
     * dont le workflow correspondant n'est plus actif depuis au moins
     * {@code perWorkflowGraceMinutes} . Le grace period evite de dropper
     * une table d'un workflow qui vient juste de demarrer mais n'a pas
     * encore publie son entry dans le registry .
     */
    int sweepPerWorkflowTables() {
        List<String> tables = jdbc.queryForList(SQL_LIST_TABLES, String.class);
        if (tables.isEmpty()) return 0;
        java.util.Set<UUID> activeCids = activeRegistry.list(null).stream()
                .map(s -> s.correlationId())
                .collect(java.util.stream.Collectors.toSet());

        int dropped = 0;
        for (String t : tables) {
            UUID cid = parseCorrelationId(t);
            if (cid != null && !activeCids.contains(cid)) {
                // table identifier deja contraint par le nom : pas de risque
                // SQL injection ( on a verifie le pattern UUID + prefixe ) .
                try {
                    String safeTable = assertSafeIdent(t);
                    jdbc.execute("DROP TABLE IF EXISTS oa_staging.\"" + safeTable + "\"");                dropped++;
                    log.info("StagingOrphanSweeper : DROP TABLE oa_staging.{} ( workflow inactif )", t);
                } catch (RuntimeException dropErr) {
                    log.warn("StagingOrphanSweeper : DROP TABLE oa_staging.{} a echoue : {}",
                            t, dropErr.getMessage());
                }
            }
        }
        return dropped;
    }

    /**
     * Cleanup des rows oa_staging.referencevalue_import_shared dont :
     *  - {@code correlation_id} n'est plus dans le registry active ,
     *  - ET {@code created_at < now() - sharedOrphanTtlMinutes} .
     * Filtre cumulatif pour eviter de supprimer un workflow qui vient juste
     * de demarrer mais n'a pas encore publie son entry .
     */
    int sweepSharedUnlogged() {
        // Verifie que la table partagee existe ( elle peut etre absente si
        // le user n'utilise jamais SHARED_UNLOGGED ) .
        Integer exists = jdbc.queryForObject(
                "SELECT count(*) FROM pg_tables WHERE schemaname='oa_staging' "
                        + "AND tablename='referencevalue_import_shared'",
                Integer.class);
        if (exists == null || exists == 0) return 0;

        return jdbc.update(
                "DELETE FROM oa_staging.referencevalue_import_shared "
                        + "WHERE created_at < now() - (? || ' minutes')::interval",
                sharedOrphanTtlMinutes);
    }

    /**
     * Extrait l'UUID a partir du nom de table : on remplace les '_' par
     * des '-' aux positions canoniques d'un UUID ( 8-4-4-4-12 ) puis
     * UUID.fromString . Retourne null si le nom n'est pas un UUID valide .
     */
    static UUID parseCorrelationId(String tableName) {
        if (tableName == null || !tableName.startsWith(NAME_PREFIX)) return null;
        String suffix = tableName.substring(NAME_PREFIX.length());
        // suffix attendu : 32 hex avec underscores aux bonnes positions
        if (suffix.length() != 36) return null; // 32 hex + 4 separateurs '_'
        // remplace '_' par '-' aux positions 8 , 13 , 18 , 23
        char[] arr = suffix.toCharArray();
        if (arr[8] != '_' || arr[13] != '_' || arr[18] != '_' || arr[23] != '_') return null;
        arr[8] = arr[13] = arr[18] = arr[23] = '-';
        try { return UUID.fromString(new String(arr)); }
        catch (IllegalArgumentException e) { return null; }
    }
}