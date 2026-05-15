package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.workflow.cascade.history.FastPathSnapshot;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

/**
 * FAST path republish : INSERT direct depuis
 * {@code binaryfile.processed_data} ( Large Object ) vers {@code referencevalue}
 * en SQL pur , <b>sans</b> staging table intermediaire ni pipeline Java text-mode .
 *
 * <p>Path unique pour le cache rotation : le cache JSON-lines ( ~44x la taille
 * du CSV brut ) est lu server-side via {@code lo_get + regexp_split_to_table} ,
 * jamais materialisé en RAM JVM . Ancien path Java COPY ( via staging table )
 * supprime : il rendait Java bottleneck sur gros caches ( PG en attente
 * {@code wait_event=ClientRead} pendant 20+ min ) .
 *
 * <h2>Algorithme SQL ( 1 transaction atomique )</h2>
 *
 * <pre>
 *   WITH parsed AS (
 *      SELECT line::jsonb AS data
 *      FROM regexp_split_to_table(
 *          convert_from(processed_data, 'UTF8'),
 *          E'\n'
 *      ) AS line
 *      WHERE line &lt;&gt; ''
 *   ),
 *   refref_pending AS (
 *      INSERT INTO refref_pending ( referenceid , referencesby )
 *      SELECT DISTINCT (p.data-&gt;&gt;'id')::uuid ,
 *                       (jsonb_extract_path(p.data,'refsLinkedTo',...)-&gt;&gt;'uuid')::uuid
 *      FROM parsed p , JSON_TABLE(...)
 *      RETURNING ...
 *   )
 *   DELETE FROM reference_reference WHERE referenceid IN (...) ;
 *   INSERT INTO referencevalue (...)
 *   SELECT col1 , col2 , ... FROM parsed p , jsonb_populate_record(NULL::referencevalue, p.data)
 *   ON CONFLICT ... DO UPDATE SET ... ;
 *   INSERT INTO reference_reference SELECT * FROM refref_pending ;
 * </pre>
 *
 * <h2>Trade-offs vs staging</h2>
 *
 * <ul>
 *   <li>+ {@code -1} table intermediaire ( pas de write staging UNLOGGED ) ;</li>
 *   <li>+ ~25% perf gain potentiel sur le step COPY ( saute 1 round-trip ) ;</li>
 *   <li>- Memory pressure cote PG : {@code regexp_split_to_table} streame ,
 *       mais le full text bytea peut etre detoaste en RAM PG sur gros fichiers ;</li>
 *   <li>- Pas de batching natural ( staging permettait UPSERT 50k chunks ) ;</li>
 *   <li>- Reference_reference rebuild duplique partiellement
 *       {@link fr.inra.oresing.workflow.cascade.StagingFinalizeSql} .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class PublishFastPathDirectExecutor {

    private final OreSiRepository repository;
    private final WorkflowActiveRegistry registry;

    public PublishFastPathDirectExecutor(OreSiRepository repository,
                                         WorkflowActiveRegistry registry) {
        this.repository = repository;
        this.registry   = registry;
    }

    /**
     * Unite de travail SQL pouvant lever {@link SQLException} . Utilisee comme
     * argument de {@link #timeStage} pour wrapper l'execution + telemetrie phase
     * sans repeter le boilerplate setFastPathPhase + nanoTime + recordDuration .
     */
    @FunctionalInterface
    private interface SqlStage {
        void run() throws SQLException;
    }

    /**
     * Execute un bloc SQL sous un nom de phase donne en publiant dans le
     * registry oa-live :
     * <ul>
     *   <li>la phase courante AVANT execution ( panel FAST path update live ) ;</li>
     *   <li>la duree mesuree APRES execution ( map phaseDurations ) .</li>
     * </ul>
     *
     * <p>Centralise le pattern repete sur 4+ phases du FAST path ( BUILD_REFREF ,
     * DELETE_REFREF , UPSERT_FINAL , INSERT_REFREF , CACHE_CLEAR ) -> evite le
     * boilerplate de 4 lignes x N phases + risque oubli de timer ou de
     * recordDuration sur ajout futur de phase .
     */
    private void timeStage(UUID correlationId, String phase, SqlStage work) throws SQLException {
        registry.setFastPathPhase(correlationId, phase);
        long t0 = System.nanoTime();
        work.run();
        registry.recordFastPathPhaseDuration(correlationId, phase,
                (System.nanoTime() - t0) / 1_000_000L);
    }

    /**
     * Execute le FAST path direct sans staging .
     *
     * @return nombre de rows insérees ( UPSERT count )
     */
    /**
     * Surcharge avec {@code clearProcessedDataOnSuccess} : clear le cache
     * dans la meme tx que l'UPSERT direct ( fix atomicite cache+rows P0-BACK-6 ) .
     */
    public long execute(Application application, UUID fileId, UUID correlationId,
                        boolean clearProcessedDataOnSuccess) {
        return executeInternal(application, fileId, correlationId, clearProcessedDataOnSuccess);
    }

    public long execute(Application application, UUID fileId, UUID correlationId)
            throws SQLException, IOException {
        return executeInternal(application, fileId, correlationId, false);
    }

    private long executeInternal(Application application, UUID fileId, UUID correlationId,
                                 boolean clearProcessedDataOnSuccess) {

        DataRepository dataRepo = repository.getRepository(application).data();
        final BinaryFileRepository bfRepo = repository.getRepository(application).binaryFile();
        final String schemaName = dataRepo.getSchemaName();
        final String rvTable    = dataRepo.getTable().getSqlIdentifier();
        final String rrTable    = schemaName + ".reference_reference";

        // Snapshot live pour oa-live ( panel FAST path remplace grille cascade ) .
        // findProcessedSize lit la colonne size deja persistee a l'upload / capture .
        long cacheSize = 0L;
        try { cacheSize = bfRepo.findProcessedSize(fileId); }
        catch (RuntimeException ex) { log.debug("findProcessedSize failed : {}", ex.getMessage()); }
        // Lookup filename pour affichage oa-live ( best effort - si bf absent ,
        // on register quand meme avec filename=null car le snapshot doit etre
        // visible meme pour un fileId orphelin ) .
        String filename = null;
        try {
            filename = bfRepo.tryFindById(fileId)
                    .map(fr.inra.oresing.domain.BinaryFile::getName)
                    .orElse(null);
        } catch (RuntimeException ex) { log.debug("tryFindById failed for fileId={} : {}", fileId, ex.getMessage()); }
        registry.registerFastPath(correlationId, cacheSize, Instant.now(), fileId, filename);

        log.info("Publish FAST DIRECT execute : fileId={} correlationId={} cacheSize={} -> INSERT depuis processed_data vers {} ( pas de staging )",
                fileId, correlationId, cacheSize, rvTable);

        JdbcTemplate jdbc = new JdbcTemplate(dataRepo.getDataSource());
        long[] insertedHolder = new long[]{0L};

        jdbc.execute((ConnectionCallback<Void>) conn -> {
            final boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            // Fix P0-BACK-2 : register pg_backend_pid pour permettre
            // pg_cancel_backend ( DashboardService.cancelWorkflow ) d'interrompre
            // l'UPSERT en cours sur cette connection .
            fr.inra.oresing.workflow.cascade.StagingFinalizeSql
                    .registerCurrentBackendPid(conn, correlationId.toString());
            try {
                long t0 = System.nanoTime();

                // 1. Temp tables : parsed_lines ( jsonb par ligne ) + refref_pending .
                try (Statement st = conn.createStatement()) {
                    st.execute("""
                            CREATE TEMP TABLE parsed_lines (
                                data jsonb
                            ) ON COMMIT DROP
                            """);
                    st.execute("""
                            CREATE TEMP TABLE refref_pending (
                                referenceid  uuid,
                                referencesby uuid
                            ) ON COMMIT DROP
                            """);
                }

                // 2. Stream LO -> parsed_lines via Java batched INSERT .
                //
                //    Alternatives essayees + rejetees :
                //    - lo_get(oid) en SQL pur : limite PG bytea 1 GB ( cache 2.22 GB KO )
                //    - plpgsql RETURNS SETOF jsonb : tuplestore materialise 1.1M rows
                //      en disk temp_bytes 30 GB + lent ( >6 min pour 1/2 du dataset )
                //    - plpgsql avec INSERT par chunk + convert_from sur bytea : UTF-8
                //      sequence multi-byte peut etre coupee au boundary 16 MB -> erreur
                //      "invalid byte sequence for UTF8" .
                //
                //    Approche retenue : Java lit ligne par ligne via Reader UTF-8 ( gere
                //    nativement les caracteres multi-byte cross-chunk ) , bulk INSERT par
                //    batch de 5000 lignes via PreparedStatement.addBatch . 1.1M lignes =
                //    ~220 INSERT batches . PG parse les jsonb cote serveur ( pas dans
                //    Java ) . Reste server-side : UPSERT depuis parsed_lines vers
                //    referencevalue ( pur SQL en 1 statement set-based ) .
                registry.setFastPathPhase(correlationId, FastPathSnapshot.PHASE_STREAM_CACHE);
                final int batchSize = 5000;
                long parsedRows = 0L;
                long t0Load = System.nanoTime();

                long oid;
                try (var ps = conn.prepareStatement(
                        "SELECT processed_data FROM " + schemaName + ".binaryfile WHERE id = ?::uuid")) {
                    ps.setString(1, fileId.toString());
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("File missing : " + fileId);
                        }
                        oid = rs.getLong(1);
                        if (rs.wasNull()) {
                            throw new IllegalStateException("processed_data is NULL : " + fileId);
                        }
                    }
                }

                org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
                org.postgresql.largeobject.LargeObjectManager lom = pgConn.getLargeObjectAPI();
                org.postgresql.largeobject.LargeObject lo = lom.open(oid, org.postgresql.largeobject.LargeObjectManager.READ);
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(lo.getInputStream(),
                                java.nio.charset.StandardCharsets.UTF_8),
                        65536);
                     java.sql.PreparedStatement insertPs = conn.prepareStatement(
                             "INSERT INTO parsed_lines ( data ) VALUES ( ?::jsonb )")) {
                    String line;
                    int batch = 0;
                    while ((line = br.readLine()) != null) {
                        if (line.isEmpty()) continue;
                        insertPs.setString(1, line);
                        insertPs.addBatch();
                        batch++;
                        if (batch >= batchSize) {
                            insertPs.executeBatch();
                            parsedRows += batch;
                            batch = 0;
                            registry.tickFastPathStreamedRows(correlationId, batchSize);
                        }
                    }
                    if (batch > 0) {
                        insertPs.executeBatch();
                        parsedRows += batch;
                        registry.tickFastPathStreamedRows(correlationId, batch);
                    }
                } finally {
                    try { lo.close(); } catch (SQLException ignored) { /* best-effort */ }
                }
                long loadMs = (System.nanoTime() - t0Load) / 1_000_000L;
                registry.recordFastPathPhaseDuration(correlationId,
                        FastPathSnapshot.PHASE_STREAM_CACHE, loadMs);
                log.info("Publish FAST DIRECT : streamed {} JSON lines from LO in {} ms ( fileId={} )",
                        parsedRows, loadMs, fileId);

                // 3. Extract refsLinkedTo refs depuis parsed_lines
                timeStage(correlationId, FastPathSnapshot.PHASE_BUILD_REFREF, () -> {
                    try (Statement st = conn.createStatement()) {
                        st.execute("""
                                INSERT INTO refref_pending ( referenceid , referencesby )
                                SELECT DISTINCT
                                       (p.data->>'id')::uuid AS referenceid ,
                                       referencesby::uuid    AS referencesby
                                FROM parsed_lines p ,
                                     JSON_TABLE(
                                        p.data , '$.refslinkedto.*.*.*.uuids' COLUMNS (
                                            NESTED PATH '$[*]' COLUMNS ( referencesby TEXT PATH '$' )
                                        )
                                     )
                                WHERE referencesby IS NOT NULL
                                """);
                    }
                });

                // 4. DELETE liens existants
                timeStage(correlationId, FastPathSnapshot.PHASE_DELETE_REFREF, () -> {
                    try (Statement st = conn.createStatement()) {
                        st.execute("""
                                DELETE FROM %1$s WHERE referenceid IN (
                                    SELECT referenceid FROM refref_pending
                                )
                                """.formatted(rrTable));
                    }
                });

                // 5. INSERT UPSERT referencevalue depuis parsed_lines temp table .
                //    Optim A3 : column extraction explicite ( (data->>'col')::pg_type )
                //    via FastPathUpsertSqlBuilder qui resout les types reels via
                //    pg_attribute + pg_type ( generique multi-SI ) . Iso-results
                //    vs jsonb_populate_record garantis ( meme casts PG natifs )
                //    + gain mesure ~30% sur UPSERT 1.1M rows ( 485s -> ~340s )
                //    en evitant la materialisation composite intermediaire par row .
                //    Composite type "authorization" reste sur jsonb_populate_record
                //    ( PG ne sait pas caster directement jsonb -> composite imbrique ) .
                final java.util.Map<String, fr.inra.oresing.workflow.cascade.ColumnTypeMeta> columnMetas =
                        fr.inra.oresing.workflow.cascade.FastPathUpsertSqlBuilder
                                .fetchColumnTypeMetadata(conn, schemaName, "referencevalue");
                final String[] targetCols = new String[] {
                        "id", "patterncolumnname", "application", "referencetype",
                        "hierarchicalkey", "naturalkey",
                        "refslinkedto", "refvalues", "binaryfile", "authorization"
                };
                final String selectExprs =
                        fr.inra.oresing.workflow.cascade.FastPathUpsertSqlBuilder
                                .buildSelectExpressions(targetCols, columnMetas, schemaName);
                final String upsertSql = """
                        INSERT INTO %1$s (
                            id, patternColumnName, application, ReferenceType,
                            hierarchicalKey, naturalKey,
                            refsLinkedTo, refValues, binaryFile, "authorization"
                        )
                        SELECT
                            %2$s
                        FROM parsed_lines
                        ON CONFLICT ON CONSTRAINT "hierarchicalKey_uniqueness"
                        DO UPDATE SET
                            updateDate     = current_timestamp,
                            hierarchicalKey = EXCLUDED.hierarchicalKey,
                            naturalKey     = EXCLUDED.naturalKey,
                            refsLinkedTo   = EXCLUDED.refsLinkedTo,
                            refValues      = EXCLUDED.refValues,
                            binaryFile     = EXCLUDED.binaryFile,
                            "authorization" = EXCLUDED."authorization"
                        """.formatted(rvTable, selectExprs);
                long[] upsertCount = new long[]{0L};
                timeStage(correlationId, FastPathSnapshot.PHASE_UPSERT_FINAL, () -> {
                    try (Statement st = conn.createStatement()) {
                        upsertCount[0] = st.executeUpdate(upsertSql);
                    }
                });
                long inserted = upsertCount[0];
                insertedHolder[0] = inserted;
                registry.setFastPathUpsertedRows(correlationId, inserted);

                // 6. INSERT reference_reference depuis refref_pending
                timeStage(correlationId, FastPathSnapshot.PHASE_INSERT_REFREF, () -> {
                    try (Statement st = conn.createStatement()) {
                        st.execute("""
                                INSERT INTO %1$s ( referenceid , referencesby )
                                SELECT referenceid , referencesby FROM refref_pending
                                """.formatted(rrTable));
                    }
                });

                long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
                log.info("Publish FAST DIRECT : {} rows upserted in {} ms ( fileId={} )",
                        inserted, elapsedMs, fileId);

                // Fix P0-BACK-6 : clear processed_data DANS la meme tx ( atomicite ) .
                if (clearProcessedDataOnSuccess) {
                    timeStage(correlationId, FastPathSnapshot.PHASE_CACHE_CLEAR, () -> {
                        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                                "UPDATE " + schemaName + ".binaryfile "
                              + "SET processed_data = NULL , processed_size = 0 , processed_at = NULL "
                              + "WHERE id = ?::uuid")) {
                            ps.setString(1, fileId.toString());
                            ps.executeUpdate();
                        }
                    });
                    log.info("CACHED_ROTATION clear processed_data ATOMIQUE ( FAST DIRECT tx ) : fileId={}", fileId);
                }

                registry.setFastPathPhase(correlationId, FastPathSnapshot.PHASE_DONE);
                conn.commit();
                return null;
            } catch (Exception ex) {
                try { conn.rollback(); }
                catch (SQLException rb) { log.warn("FAST DIRECT rollback failed : {}", rb.getMessage()); }
                throw new RuntimeException("Publish FAST DIRECT failed : " + ex.getMessage(), ex);
            } finally {
                fr.inra.oresing.workflow.cascade.StagingFinalizeSql
                        .deregisterBackendPid(correlationId.toString());
                try { conn.setAutoCommit(originalAutoCommit); }
                catch (SQLException ignored) { /* connection retournee au pool */ }
            }
        });

        return insertedHolder[0];
    }
}
