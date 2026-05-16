package fr.inra.oresing.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-loads {@code referencevalue} table + its indexes into the PostgreSQL
 * shared_buffers cache at backend startup , using the {@code pg_prewarm}
 * extension .
 *
 * <h2>Why</h2>
 *
 * <p>After a backend restart , PostgreSQL's shared_buffers is empty . The
 * first call to {@code DataRepository.getDataIdPerKeys} ( triggered when
 * Phase 2 of any publish workflow enters the cascade prep ) issues a
 * projection scan over the referencevalue table for the target refType
 * ( ~3 . 6M rows on si_acbb / t_soil_water_content_swc , translating to
 * ~10 GB of disk IO via {@code Buffers: read=1.32M} per EXPLAIN ANALYZE ) .
 * Cold cache IO dominates this call ( ~96 s of 105 s wall-clock ) and
 * blocks every cascade publish until the buffers are populated .
 *
 * <p>Pre-warming at boot moves that one-time IO penalty out of the user-
 * facing publish path . Subsequent publishes hit the now-hot shared_buffers
 * and getDataIdPerKeys drops from ~110 s to ~10-15 s .
 *
 * <h2>How</h2>
 *
 * <p>On {@link ApplicationReadyEvent} ( Spring fully initialised ) , the
 * service enumerates every application schema by querying
 * {@code pg_tables WHERE tablename = 'referencevalue'} and issues
 * {@code SELECT pg_prewarm(...)} on each referencevalue table plus its
 * supporting indexes . Runs {@link Async} on a daemon thread so the boot
 * never waits ; HTTP acceptance proceeds normally .
 *
 * <h2>Safety</h2>
 *
 * <p>Idempotent and best-effort :
 * <ul>
 *   <li>If the {@code pg_prewarm} extension is not installed ( prod with
 *       restrictive role ) , the {@code pg_prewarm()} call throws
 *       UndefinedFunctionException ; we log warn and skip silently .</li>
 *   <li>If a schema lacks the {@code referencevalue} table ( early in
 *       application setup ) , the call returns 0 silently .</li>
 *   <li>Disabled via {@code openadom.pg-prewarm.enabled=false} ( env var
 *       {@code OPENADOM_PG_PREWARM_ENABLED=false} ) for benchmarks or
 *       memory-constrained environments .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class PgPrewarmBootstrapService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${openadom.pg-prewarm.enabled:true}")
    private boolean enabled;

    public PgPrewarmBootstrapService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            log.info("[pg-prewarm] disabled by config , skipping");
            return;
        }
        log.info("[pg-prewarm] start - enumerating application schemas hosting referencevalue");

        final List<String> schemas;
        try {
            schemas = jdbcTemplate.queryForList(
                    "SELECT schemaname FROM pg_tables "
                            + "WHERE tablename = 'referencevalue' "
                            + "AND schemaname NOT IN ('pg_catalog', 'information_schema', 'oa_audit', 'oa_staging') "
                            + "ORDER BY schemaname",
                    String.class);
        } catch (RuntimeException ex) {
            log.warn("[pg-prewarm] failed to enumerate application schemas : {}", ex.getMessage());
            return;
        }

        if (schemas.isEmpty()) {
            log.info("[pg-prewarm] no application schemas found , skipping");
            return;
        }

        long t0 = System.nanoTime();
        long totalPagesLoaded = 0L;
        for (String schema : schemas) {
            totalPagesLoaded += prewarmTable(schema, "referencevalue");
            totalPagesLoaded += prewarmTable(schema, "\"hierarchicalKey_uniqueness\"");
            totalPagesLoaded += prewarmTable(schema, "reference_reference");
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        log.info("[pg-prewarm] complete - {} pages loaded across {} schemas in {} ms",
                totalPagesLoaded, schemas.size(), ms);
    }

    /**
     * Issues a {@code pg_prewarm} call on a specific table / index in a
     * schema . Returns the number of pages loaded ( 0 on failure ) so the
     * aggregate count in {@link #onReady()} reflects the actual prewarm
     * effort .
     */
    private long prewarmTable(String schema, String tableOrIndex) {
        try {
            Long pages = jdbcTemplate.queryForObject(
                    "SELECT pg_prewarm(?::regclass)",
                    Long.class,
                    "\"" + schema + "\"." + tableOrIndex);
            return pages == null ? 0L : pages;
        } catch (RuntimeException ex) {
            log.warn("[pg-prewarm] failed to prewarm {}.{} : {}",
                    schema, tableOrIndex, ex.getMessage());
            return 0L;
        }
    }
}
