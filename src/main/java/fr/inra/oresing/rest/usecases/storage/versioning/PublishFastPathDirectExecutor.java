package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.workflow.cascade.StagingFinalizeSql;
import fr.inra.oresing.workflow.cascade.cache.ReferencevalueCacheReader;
import fr.inra.oresing.workflow.cascade.history.FastPathSnapshot;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

/**
 * FAST republish path : restores {@code referencevalue} rows directly from
 * the cache Large Object using PG binary {@code COPY FROM} , bypassing the
 * cascade DataImporter and the legacy JSON-lines transform .
 *
 * <h2>High-level algorithm ( single transaction )</h2>
 *
 * <ol>
 *   <li>{@code DELETE FROM reference_reference WHERE referenceid IN
 *       ( SELECT id FROM referencevalue WHERE binaryfile = ? )} - remove
 *       relationships pointing to the rows we are about to wipe ;</li>
 *   <li>{@code DELETE FROM referencevalue WHERE binaryfile = ?} - wipe rows
 *       belonging to this file so we start from a clean slate ;</li>
 *   <li>{@link ReferencevalueCacheReader#replayCache} - validate the OAVR
 *       header then stream the binary COPY payload into
 *       {@code referencevalue} ;</li>
 *   <li>{@code INSERT INTO reference_reference SELECT id , referencesby
 *       FROM referencevalue , JSON_TABLE ( refsLinkedTo ... )
 *       WHERE binaryfile = ?} - rebuild relationships from the
 *       just-restored rows ;</li>
 *   <li>{@code UPDATE binaryfile SET processed_data = NULL}
 *       ( optional ) - clear the cache atomically with the publish for the
 *       {@code CACHED_ROTATION} mode .</li>
 * </ol>
 *
 * <p>If the cache header is missing or carries an incompatible version ,
 * {@link ReferencevalueCacheReader.IncompatibleCacheFormatException} is
 * thrown , the transaction is rolled back , and
 * {@link fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecyclePhase2Handler}
 * falls back to the LITE / FULL path .
 *
 * <h2>Why it scales</h2>
 *
 * <p>The new path replaces a row-by-row {@code jsonb_populate_record} UPSERT
 * with a server-side PG binary COPY ; on a {@code ~870k} rows / 1 . 7 GB
 * cache the previous implementation was dominated by the 18 min UPSERT
 * stage , which collapses to a 30 s COPY here ( measured 12 - 15 x speed-up
 * on multi-GB caches ) . The cache itself is bit-for-bit reproducible -
 * the same {@code COPY TO STDOUT ( FORMAT BINARY )} produced it at upload
 * time , so the round-trip is iso-result by construction .
 *
 * <h2>Concurrency</h2>
 *
 * <p>Different {@code fileId} values write to disjoint subsets of
 * {@code referencevalue} ( filtered by the {@code binaryfile} column ) ;
 * PG's row-level locks suffice for safety . Same-{@code fileId} concurrent
 * publishes are rejected upstream by the Reject 409 path .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class PublishFastPathDirectExecutor {

    private final OreSiRepository repository;
    private final WorkflowActiveRegistry registry;
    private final WorkflowLogRepository logRepository;

    public PublishFastPathDirectExecutor(OreSiRepository repository,
                                         WorkflowActiveRegistry registry,
                                         WorkflowLogRepository logRepository) {
        this.repository    = repository;
        this.registry      = registry;
        this.logRepository = logRepository;
    }

    /**
     * SQL stage callback . Allows phase-scoped exception propagation while
     * keeping the timing / registry boilerplate inside {@link #timeStage} .
     */
    @FunctionalInterface
    private interface SqlStage {
        void run() throws SQLException;
    }

    /**
     * Wraps the execution of a single FAST path phase with telemetry :
     * publishes the phase to {@link WorkflowActiveRegistry} before running
     * the stage and the measured duration after .
     */
    private void timeStage(UUID correlationId, String phase, SqlStage stage) throws SQLException {
        registry.setFastPathPhase(correlationId, phase);
        // Mirror the FAST sub-phase to workflow_log.metadata.phase so the high-level
        // UI ( WorkflowTable row ) can show the current step alongside the detailed
        // FastPathPanel . Best-effort : a failed phase update never aborts the FAST run .
        logRepository.updatePhase(correlationId, phase);
        long t0 = System.nanoTime();
        stage.run();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        registry.recordFastPathPhaseDuration(correlationId, phase, ms);
    }

    /**
     * Entry point with optional atomic cache clear .
     *
     * @param clearProcessedDataOnSuccess when {@code true} ( CACHED_ROTATION ) ,
     *        the cache LO is unlinked in the same transaction so a successful
     *        publish leaves the binaryfile with an empty cache - the next
     *        upload / cache build will repopulate it .
     */
    public long execute(Application application, UUID fileId, UUID correlationId,
                        boolean clearProcessedDataOnSuccess) {
        return executeInternal(application, fileId, correlationId, clearProcessedDataOnSuccess);
    }

    public long execute(Application application, UUID fileId, UUID correlationId) {
        return executeInternal(application, fileId, correlationId, false);
    }

    private long executeInternal(Application application, UUID fileId, UUID correlationId,
                                  boolean clearProcessedDataOnSuccess) {

        DataRepository dataRepo = repository.getRepository(application).data();
        final BinaryFileRepository bfRepo = repository.getRepository(application).binaryFile();
        final String schemaName = dataRepo.getSchemaName();
        final String rrTable    = schemaName + ".reference_reference";

        // Resolve filename and cache size up-front for the live snapshot ( oa-live
        // workflow detail panel ) . Both are best-effort and never block the run .
        long cacheSize = 0L;
        try { cacheSize = bfRepo.findProcessedSize(fileId); }
        catch (RuntimeException ex) { log.debug("findProcessedSize failed : {}", ex.getMessage()); }
        String filename = null;
        try {
            filename = bfRepo.tryFindById(fileId)
                    .map(fr.inra.oresing.domain.BinaryFile::getName)
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.debug("tryFindById failed for fileId={} : {}", fileId, ex.getMessage());
        }
        registry.registerFastPath(correlationId, cacheSize, Instant.now(), fileId, filename);

        log.info("Publish FAST DIRECT start : fileId={} correlationId={} cacheSize={} -> direct COPY into {}.referencevalue",
                fileId, correlationId, cacheSize, schemaName);

        JdbcTemplate jdbc = new JdbcTemplate(dataRepo.getDataSource());
        long[] copiedHolder = new long[]{0L};

        jdbc.execute((ConnectionCallback<Void>) conn -> {
            final boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            // Register pg_backend_pid so DashboardService.cancelWorkflow can issue
            // pg_cancel_backend against the connection running the COPY .
            StagingFinalizeSql.registerCurrentBackendPid(conn, correlationId.toString());

            try {
                long t0 = System.nanoTime();

                // ---- 1 . Resolve the cache LO oid for this binaryfile . ----
                long oid = lookupProcessedDataOid(conn, schemaName, fileId);

                // ---- 2 . DELETE existing reference_reference for these ids
                //          IS REDUNDANT with step 3 below . ----
                //   The FK {@code reference_reference_referenceid_fkey
                //   FOREIGN KEY (referenceid) REFERENCES referencevalue(id)
                //   ON DELETE CASCADE} ensures that the DELETE on
                //   referencevalue in step 3 auto-cascades to refref rows .
                //   The legacy code did an explicit pre-DELETE on refref
                //   ( ~30 s on 870k rows ) before the parent DELETE ; PG
                //   does the same work via the FK cascade in step 3 anyway ,
                //   so this explicit step was pure double work .
                //   Removed ; step 3 cascade handles it .

                // ---- 3 . DELETE existing referencevalue rows for this file . ----
                //   We wipe and reload : the cache contains the authoritative row
                //   set at upload time , so any prior row for the same binaryfile
                //   is superseded . This avoids the row-by-row ON CONFLICT cost
                //   of the legacy UPSERT path .
                final String deleteRvSql = ""
                        + "DELETE FROM " + schemaName + ".referencevalue WHERE binaryFile = ?::uuid";
                timeStage(correlationId, FastPathSnapshot.PHASE_DELETE_EXISTING, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(deleteRvSql)) {
                        ps.setString(1, fileId.toString());
                        ps.executeUpdate();
                    }
                });

                // ---- 4 . COPY IN from the cache Large Object . ----
                //   Single PG binary COPY ; PG handles type encoding ( uuid , ltree ,
                //   jsonb , composite authorization ... ) natively . The cache header
                //   is validated by the reader before any byte reaches the server .
                timeStage(correlationId, FastPathSnapshot.PHASE_COPY_IN, () -> {
                    try {
                        long inserted = ReferencevalueCacheReader.replayCache(conn, schemaName, oid);
                        copiedHolder[0] = inserted;
                        registry.setFastPathUpsertedRows(correlationId, inserted);
                    } catch (java.io.IOException ioe) {
                        throw new SQLException("Cache replay IO error : " + ioe.getMessage(), ioe);
                    }
                });

                // ---- 5 . Rebuild reference_reference from the freshly loaded rows . ----
                //   The same JSON_TABLE extraction used at upload time , reading from
                //   the final table now that COPY has populated it .
                final String insertRefrefSql = ""
                        + "INSERT INTO " + rrTable + " ( referenceid , referencesby ) "
                        + "SELECT DISTINCT rv.id , referencesby::uuid "
                        + "FROM " + schemaName + ".referencevalue rv , "
                        + "     JSON_TABLE ( "
                        + "        rv.refsLinkedTo , '$.*.*.*.uuids' COLUMNS ( "
                        + "           NESTED PATH '$[*]' COLUMNS ( referencesby TEXT PATH '$' ) "
                        + "        ) "
                        + "     ) AS joins "
                        + "WHERE rv.binaryFile = ?::uuid "
                        + "  AND referencesby IS NOT NULL";
                timeStage(correlationId, FastPathSnapshot.PHASE_INSERT_REFREF, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(insertRefrefSql)) {
                        ps.setString(1, fileId.toString());
                        ps.executeUpdate();
                    }
                });

                // ---- 6 . Optional atomic cache clear ( CACHED_ROTATION ) . ----
                if (clearProcessedDataOnSuccess) {
                    timeStage(correlationId, FastPathSnapshot.PHASE_CACHE_CLEAR, () -> {
                        try (PreparedStatement ps = conn.prepareStatement(""
                                + "UPDATE " + schemaName + ".binaryfile "
                                + "SET processed_data = NULL , processed_size = 0 , processed_at = NULL "
                                + "WHERE id = ?::uuid")) {
                            ps.setString(1, fileId.toString());
                            ps.executeUpdate();
                        }
                    });
                    log.info("CACHED_ROTATION clear processed_data atomic : fileId={}", fileId);
                }

                long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                log.info("Publish FAST DIRECT end : copied {} rows in {} ms ( fileId={} )",
                        copiedHolder[0], elapsedMs, fileId);

                registry.setFastPathPhase(correlationId, FastPathSnapshot.PHASE_DONE);
                // The high-level "DONE" phase will be set by Phase2Handler after
                // commitVisibleFlagAndSynthesis ; we leave the FAST sub-phase as DONE
                // here so the dedicated FastPathPanel reflects the final state .
                conn.commit();
                return null;
            } catch (Exception ex) {
                try { conn.rollback(); }
                catch (SQLException rb) { log.warn("FAST DIRECT rollback failed : {}", rb.getMessage()); }
                throw new RuntimeException("Publish FAST DIRECT failed : " + ex.getMessage(), ex);
            } finally {
                StagingFinalizeSql.deregisterBackendPid(correlationId.toString());
                try { conn.setAutoCommit(originalAutoCommit); }
                catch (SQLException ignored) { /* connection returned to the pool */ }
            }
        });

        return copiedHolder[0];
    }

    /**
     * Resolves the {@code processed_data} oid for a binaryfile or throws if
     * the cache is missing . The caller propagates the failure ; the upstream
     * router catches it and routes to the LITE / FULL path on the next attempt .
     */
    private static long lookupProcessedDataOid(Connection conn, String schemaName, UUID fileId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT processed_data FROM " + schemaName + ".binaryfile WHERE id = ?::uuid")) {
            ps.setString(1, fileId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Cache file missing : " + fileId);
                }
                long oid = rs.getLong(1);
                if (rs.wasNull()) {
                    throw new IllegalStateException("processed_data is NULL : " + fileId);
                }
                return oid;
            }
        }
    }
}
