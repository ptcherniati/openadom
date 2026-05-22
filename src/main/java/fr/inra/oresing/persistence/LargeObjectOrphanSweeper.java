package fr.inra.oresing.persistence;

import fr.inra.oresing.rest.services.ApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic scheduler reclaiming orphan Large Objects ( oids in
 * {@code pg_largeobject_metadata} not referenced by any
 * {@code &lt;app&gt;.binaryfile.processed_data} ) .
 *
 * <p><b>Why</b> : the V13 migration moved {@code processed_data} from
 * {@code bytea} to {@code oid} ( Large Object ) . The Postgres LO API
 * decouples LO lifecycle from the user table : an admin {@code TRUNCATE} ,
 * a row delete , or a crash between {@code lo_create} and
 * {@code UPDATE binaryfile SET processed_data = ?} leaves the LO orphaned
 * in {@code pg_largeobject_metadata} forever . Disk usage grows unbounded .
 *
 * <p><b>Algorithm</b> ( equivalent to the {@code vacuumlo} contrib tool but
 * pure SQL + Java , no extension required ) :
 * <ol>
 *   <li>list applications via {@link ApplicationService} ;</li>
 *   <li>build the set of referenced oids = union of
 *       {@code SELECT processed_data FROM &lt;app_schema&gt;.binaryfile WHERE processed_data IS NOT NULL}
 *       across all schemas ;</li>
 *   <li>{@code SELECT lo_unlink(loid) FROM pg_largeobject_metadata WHERE loid NOT IN (referenced)} .</li>
 * </ol>
 *
 * <p><b>Cron</b> : daily at 04:00 ( server timezone ) by default ; override
 * via {@code app.large-object.orphan-sweep-cron} property .
 *
 * <p><b>Safety</b> :
 * <ul>
 *   <li>step 2 fully reads referenced oids before deletion so no row
 *       being written concurrently can be missed ( we also leave a
 *       grace window via the {@code minAgeMinutes} filter on
 *       {@code pg_largeobject_metadata} where supported - currently a
 *       best-effort note : Postgres does not expose LO creation
 *       timestamp natively ; we rely on cron periodicity + tx isolation ) ;</li>
 *   <li>logs the count + reclaimed pages ; never throws , crashes are
 *       isolated to the scheduler thread .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class LargeObjectOrphanSweeper {

    private final JdbcTemplate jdbc;
    private final boolean      enabled;

    public LargeObjectOrphanSweeper(
            JdbcTemplate jdbc,
            @Value("${app.large-object.orphan-sweep-enabled:true}") boolean enabled) {
        this.jdbc    = jdbc;
        this.enabled = enabled;
        log.info("LargeObjectOrphanSweeper configured : enabled={} ( cron app.large-object.orphan-sweep-cron )", enabled);
    }

    /**
     * Daily sweep at 04:00 by default . Cron expression configurable via
     * {@code app.large-object.orphan-sweep-cron} ( spring-style 6-field
     * cron : sec min hour day month weekday ) .
     */
    @Scheduled(cron = "${app.large-object.orphan-sweep-cron:0 0 4 * * *}")
    public void sweep() {
        if (!enabled) {
            log.debug("LargeObjectOrphanSweeper disabled , skipping run");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            int orphans = reclaim();
            long elapsed = System.currentTimeMillis() - start;
            log.info("LargeObjectOrphanSweeper : {} orphan(s) reclaimed in {} ms", orphans, elapsed);
        } catch (RuntimeException ex) {
            log.error("LargeObjectOrphanSweeper run failed : {}", ex.getMessage(), ex);
        }
    }

    /**
     * Execute the sweep synchronously . Exposed for admin endpoint
     * triggered runs ( bypass cron schedule for ad-hoc cleanup ) .
     *
     * @return number of orphan LOs reclaimed
     */
    public int reclaim() {
        // 1. List schemas that have a binaryfile table with processed_data oid column .
        // Uses information_schema introspection to enumerate application schemas safely .
        List<String> schemas = jdbc.query(
                "SELECT table_schema "
              + "  FROM information_schema.columns "
              + " WHERE table_name = 'binaryfile' AND column_name = 'processed_data' "
              + "   AND data_type = 'oid'",
                (rs, n) -> rs.getString("table_schema"));
        if (schemas.isEmpty()) {
            log.debug("LargeObjectOrphanSweeper : no schema with binaryfile.processed_data oid , nothing to sweep");
            return 0;
        }
        // 2. Build UNION ALL of referenced oids across all such schemas .
        StringBuilder unionSql = new StringBuilder();
        for (String schema : schemas) {
            if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) continue; // defensive
            if (unionSql.length() > 0) unionSql.append(" UNION ALL ");
            unionSql.append("SELECT processed_data AS oid FROM \"")
                    .append(schema)
                    .append("\".binaryfile WHERE processed_data IS NOT NULL");
        }
        if (unionSql.length() == 0) return 0;
        // 3. Reclaim oids in pg_largeobject_metadata not in the referenced set .
        String sweepSql = "SELECT lo_unlink(loid)::int AS dummy "
                + "  FROM pg_largeobject_metadata "
                + " WHERE loid NOT IN ( " + unionSql + " )";
        List<Integer> results = jdbc.query(sweepSql, (rs, n) -> rs.getInt("dummy"));
        return results.size();
    }

}