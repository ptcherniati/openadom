package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.data.DataRows;
import fr.inra.oresing.persistence.refref.RefrefRebuildSql;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.query.DataRowIds;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import fr.inra.oresing.persistence.requestbuilder.data.DataRequestBuilder;
import fr.inra.oresing.persistence.requestbuilder.data.SqlRequest;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataRepository extends JsonTableInApplicationSchemaRepositoryTemplate<DataValue> implements fr.inra.oresing.domain.repository.data.DataRepository {
    public static final String APPLICATION_ID = "applicationId";
    public static final String REF_TYPE = "refType";
    public static final String[] ORDERED_COLUMNS = new String[]{"id", "patternColumnName", "application", "ReferenceType", "hierarchicalKey", "naturalKey", "refsLinkedTo", "refValues", "binaryFile", "\"authorization\""};
    public static final int PIPE_SIZE = 65536;

    /**
     * Lecture du compteur via la table de stats {@code referencevalue_count_stats}
     * ( maintenue par triggers AFTER INSERT/DELETE statement-level , cf.
     * migration application/V5__referencevalue_count_stats.sql ) plutot que
     * par un {@code SELECT count(*) GROUP BY referencetype} sur la table
     * source ( ~12s sur 10M rows , 2-5 min sur 200M ).
     *
     * <p>Defaut {@code true}. Pour debug ou benchmark , passer a {@code false}
     * via la propriete {@code openadom.referencevalue.count.use-stats-table}
     * ou la variable d'environnement {@code OPENADOM_REFERENCEVALUE_COUNT_USE_STATS_TABLE}
     * pour forcer le COUNT direct ( cf. {@link #buildReferenceSynthesis} ).</p>
     */
    @Value("${openadom.referencevalue.count.use-stats-table:true}")
    private boolean useReferencevalueCountStatsTable;

    public DataRepository(final Application application) {
        super(application);
    }

    private static String addReferenceConditions(final java.util.Map<String, java.util.List<String>> params, final MapSqlParameterSource paramSource) {
        final AtomicInteger i = new AtomicInteger();
        // kv.value='LPF' OR t.refvalues @> '{"esp_nom":"ALO"}'::jsonb
        String cond = params.entrySet().stream().flatMap(e -> {
                    final String k = e.getKey();
                    if (StringUtils.equalsAnyIgnoreCase("_row_id_", k)) {
                        final String collect = e.getValue().stream().map(v -> {
                                    final String arg = ":arg" + i.getAndIncrement();
                                    paramSource.addValue(arg, v);
                                    return String.format("'%s'::uuid", v);
                                })
                                .collect(Collectors.joining(", "));
                        return Stream.ofNullable(String.format("array[id]::uuid[] <@ array[%s]::uuid[]", collect));
                    }
                    if (StringUtils.equalsAnyIgnoreCase("_row_key_", k)) {
                        final String collect = e.getValue().stream()
                                .map(v -> {
                                    final String arg = ":arg" + i.getAndIncrement();
                                    paramSource.addValue(arg, v);
                                    return String.format("'%s'", v);
                                })
                                .collect(Collectors.joining(", "));
                        if (collect.isEmpty()) {
                            return null;
                        }
                        return Stream.ofNullable(String.format(" (naturalKey in (%1$s) or hierarchicalKey in (%1$s)) ", collect));
                    }
                    if (StringUtils.equalsAnyIgnoreCase("any", k)) {
                        return e.getValue().stream().map(v -> {
                            final String arg = ":arg" + i.getAndIncrement();
                            paramSource.addValue(arg, v);
                            return "kv.value=" + arg;
                        });
                    }
                    return e.getValue().stream().map(v -> String.format("lower(t.refvalues ->> '%s') ~ lower('.*%s.*')", k, v));
                })
                .filter(Objects::nonNull).
                collect(Collectors.joining(" AND "));

        if (StringUtils.isNotBlank(cond)) {
            cond = " AND (" + cond + ")";
        }
        return cond;
    }

    @Override
    public SqlTable getTable() {
        return getSchema().referenceValue();
    }

    /**
     * Exposes the application schema name for callers outside this package
     * ( e.g. {@code CascadeSinkFactory} ) . The base
     * {@link JsonTableInApplicationSchemaRepositoryTemplate#getSchema()} is
     * {@code protected} ; this accessor delegates to it .
     */
    public String getSchemaName() {
        return getSchema().getName();
    }

    /**
     * Exposes the underlying {@link javax.sql.DataSource} used by this
     * repository . Useful to build cascade {@code Sink}s ( e.g. the
     * {@code StagingPostgresSink} which needs a DataSource at construction ) .
     */
    public javax.sql.DataSource getDataSource() {
        return getNamedParameterJdbcTemplate().getJdbcTemplate().getDataSource();
    }

    @Override
    protected String getUpsertQuery() {
        return """
                INSERT INTO %1$s
                    (%3$s)
                SELECT %3$s
                FROM json_populate_recordset(
                    NULL::%2$s,
                    :json::json
                )
                ON CONFLICT ON CONSTRAINT "hierarchicalKey_uniqueness"
                DO UPDATE SET updateDate=current_timestamp, hierarchicalKey=EXCLUDED.hierarchicalKey, naturalKey=EXCLUDED.naturalKey, refsLinkedTo=EXCLUDED.refsLinkedTo,
                refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile, "authorization"=EXCLUDED."authorization" RETURNING id
                """.formatted(
                getTable().getSqlIdentifier(),
                getTable().getSqlIdentifier(),
                Arrays.stream(ORDERED_COLUMNS).collect(Collectors.joining(","))
        );
    }

    /**
     * Taille de batch pour l'INSERT bulk depuis la TEMP table {@code
     * referencevalue_import} vers la table cible. Decoupage en N batches
     * via {@code DELETE ... RETURNING} dans une CTE pour eviter les couts
     * d'OFFSET et permettre des checkpoints PG plus frequents +
     * libere les pages locks entre batches.
     *
     * <p>Configurable via {@code -Dapp.import.bulkInsertBatchSize=N}.
     */
    private static final int BULK_INSERT_BATCH_SIZE =
            Integer.getInteger("app.import.bulkInsertBatchSize", 50_000);

    @Override
    public long storeAll(final Path finalCsvFile,
                         final java.util.function.LongConsumer onBatchUpserted,
                         final java.util.function.Consumer<String> onPhaseChange) {
        final String columns = Arrays.stream(ORDERED_COLUMNS)
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));

        // Phase 1 : MERGE_LOCAL ( la concatenation est faite par cascade
        // collector AVANT cet appel ; on emit l'event juste pour aligner
        // l'UI sur la prochaine etape ) .
        try { onPhaseChange.accept("MERGE_LOCAL"); } catch (RuntimeException ignored) { /* best effort */ }

        Long upserted = getNamedParameterJdbcTemplate().getJdbcTemplate().execute(
                (ConnectionCallback<Long>) connection -> {
                    // setAutoCommit jamais restaure par l'ancien code + 4
                    // Statement createStatement() sans try-with-resources
                    // ( leak ). Restoration en finally.
                    final boolean originalAutoCommit = connection.getAutoCommit();
                    connection.setAutoCommit(false);
                    boolean committed = false;
                    try {
                        PGConnection pgConn = connection.unwrap(PGConnection.class);
                        CopyManager copyManager = pgConn.getCopyAPI();

                        try (Statement stmt = connection.createStatement()) {
                            stmt.execute("CREATE TEMP TABLE referencevalue_import (data jsonb) ON COMMIT DROP");
                        }

                        // Phase 2 : TEMP_LOAD ( COPY merged.csv -> referencevalue_import ) .
                        // Cote UI : indeterminate ( 1 statement Postgres , pas de
                        // progress incremental observable ) .
                        try { onPhaseChange.accept("TEMP_LOAD"); } catch (RuntimeException ignored) { /* best effort */ }
                        long copiedRows;
                        long copyStart = System.nanoTime();
                        try (BufferedReader reader = Files.newBufferedReader(finalCsvFile, StandardCharsets.UTF_8)) {
                            copiedRows = copyManager.copyIn(
                                    "COPY referencevalue_import (data) FROM STDIN  ", reader);
                        }
                        long copyMs = (System.nanoTime() - copyStart) / 1_000_000L;
                        log.info("storeAll : COPY phase loaded {} rows into temp table in {} ms", copiedRows, copyMs);

                        // Reconstruction reference_reference - refacto B :
                        // tout le SQL est centralise dans {@link RefrefRebuildSql}
                        // ( source of truth partagee avec StagingFinalizeSql ) .
                        // Etapes 1-2 ici ( snapshot + delete old ) , etapes 4-5
                        // apres le UPSERT batche .
                        RefrefRebuildSql.createSourceTable(connection);
                        RefrefRebuildSql.populateSource(connection, "referencevalue_import", null);
                        RefrefRebuildSql.deleteOldLinks(connection, getSchema().getName());

                        // INSERT decoupe en batches. Au lieu d'1 INSERT massif
                        // qui tient des locks sur 280k+ rows + force la pending
                        // list GIN a flusher d'un coup en fin de tx ,
                        // boucler tant qu'il reste des rows dans la TEMP table.
                        // CTE atomique : DELETE des N premieres lignes
                        // ( par ctid ) , RETURNING data , INSERT vers la cible.
                        // Pas d'OFFSET ( cher ) , pas de double-traitement
                        // ( DELETE retire les rows traitees ).
                        String insertBatchSql = String.format("""
                                        WITH batch AS (
                                            DELETE FROM referencevalue_import
                                            WHERE ctid IN (
                                                SELECT ctid FROM referencevalue_import LIMIT %3$d
                                            )
                                            RETURNING data
                                        )
                                        INSERT INTO %1$s (%2$s)
                                        SELECT %2$s
                                        FROM batch ,
                                             jsonb_populate_record( NULL::%1$s , data )
                                        ON CONFLICT ON CONSTRAINT "hierarchicalKey_uniqueness"
                                        DO UPDATE SET
                                            updateDate    = current_timestamp,
                                            hierarchicalKey = EXCLUDED.hierarchicalKey,
                                            naturalKey    = EXCLUDED.naturalKey,
                                            refsLinkedTo  = EXCLUDED.refsLinkedTo,
                                            refValues     = EXCLUDED.refValues,
                                            binaryFile    = EXCLUDED.binaryFile,
                                            "authorization" = EXCLUDED."authorization"
                                        """,
                                getTable().getSqlIdentifier(), columns, BULK_INSERT_BATCH_SIZE
                        );

                        // Phase 3 : UPSERT_FINAL ( loop batche TEMP -> table finale ) .
                        // Cote UI : determinate via {@code onBatchUpserted} qui propage
                        // le rowcount par batch au consommateur ( typiquement
                        // {@code StoreAllPathSink} -> {@code WorkflowActiveRegistry.addFinalRows} ) .
                        try { onPhaseChange.accept("UPSERT_FINAL"); } catch (RuntimeException ignored) { /* best effort */ }
                        long insertStart = System.nanoTime();
                        long totalUpserted = 0L;
                        int batchCount = 0;
                        try (PreparedStatement ps = connection.prepareStatement(insertBatchSql)) {
                            while (true) {
                                int affected = ps.executeUpdate();
                                if (affected <= 0) {
                                    break;
                                }
                                totalUpserted += affected;
                                batchCount++;
                                try {
                                    onBatchUpserted.accept((long) affected);
                                } catch (RuntimeException ignored) {
                                    /* best effort : un consommateur fautif ne doit pas
                                       casser le UPSERT en cours */
                                }
                            }
                        }
                        long insertMs = (System.nanoTime() - insertStart) / 1_000_000L;
                        log.info("storeAll : INSERT phase upserted {} rows in {} batches ( batch size = {} ) in {} ms",
                                totalUpserted, batchCount, BULK_INSERT_BATCH_SIZE, insertMs);

                        // Etapes 4-5 refacto B : refref_pending construit
                        // POST-UPSERT via {@link RefrefRebuildSql} . rv.id
                        // post-UPSERT = OLD pour existing , NEW pour fresh .
                        try { onPhaseChange.accept(fr.inra.oresing.workflow.WorkflowPhase.REFREF_REBUILD); } catch (RuntimeException ignored) { /* best effort */ }
                        long refrefStart = System.nanoTime();
                        RefrefRebuildSql.createPendingTable(connection);
                        RefrefRebuildSql.populatePending(connection, getSchema().getName());
                        int refrefInserted = RefrefRebuildSql.insertReferenceReference(connection, getSchema().getName());
                        log.info("storeAll : reference_reference rebuilt with {} link(s) in {} ms",
                                refrefInserted, (System.nanoTime() - refrefStart) / 1_000_000L);

                        connection.commit();
                        committed = true;
                        return totalUpserted;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (!committed) {
                            try { connection.rollback(); } catch (SQLException ignored) { /* best effort */ }
                        }
                        try {
                            connection.setAutoCommit(originalAutoCommit);
                        } catch (SQLException ignored) {
                            // connection may already be returned/closed by Hikari
                        }
                    }
                });
        return upserted != null ? upserted : 0L;
    }


    @Override
    protected Class<DataValue> getEntityClass() {
        return DataValue.class;
    }

    @Override

    /**
     * Counts referencevalue rows belonging to a given binaryfile . Used by
     * the unpublish / delete-file flows to populate {@code recordsTotal}
     * BEFORE the actual DELETE runs , so oa-live can display the expected
     * row count immediately in the "Lignes" column ( bar passes from
     * indeterminate to determinate ) .
     *
     * <p>Hits the {@code referencevalue_binaryfile_idx} btree index (V14) :
     * O ( log N ) scan + index-only path for a single file . Negligible
     * cost even on 100M+ row tables . Safe to call inline before DELETE .
     */
    public long countByFileId(final UUID fileId) {
        final String query = String.format("""
                        SELECT count(*) FROM %s WHERE binaryfile = :binaryFile
                        """,
                getTable().getSqlIdentifier());
        Map<String, Object> params = Map.of("binaryFile", fileId);
        Long n = getNamedParameterJdbcTemplate().queryForObject(query, params, Long.class);
        return n == null ? 0L : n;
    }

    public void removeByFileId(final UUID fileId) {
        // PERF : cast {@code binaryfile::text} eliminait l'usage de l'index sur
        // la colonne uuid -> SEQ SCAN sur referencevalue entiere ( minutes sur
        // gros datasets ) . Compare UUID a UUID directement pour utiliser le
        // btree dedie {@code referencevalue_binaryfile_idx} ( V14 ) .
        //
        // ATTENTION SCALING : sur fichiers > 1M rows , les RI triggers de la
        // FK {@code reference_reference_referenceid_fkey ON DELETE CASCADE}
        // s'executent par ligne ( ~50us / call -> ~1h sur 100M rows ) .
        // Cette methode reste pour compatibilite mais delegue a
        // {@link #removeByFileIdChunked} pour deblocage scale + cancel
        // intermediate progress reporting .
        removeByFileIdChunked(fileId, REMOVE_BY_FILE_DEFAULT_CHUNK_SIZE, null, null);
    }

    /** Default chunk size for {@link #removeByFileIdChunked} . 10k rows par
     *  chunk = compromis lock duration ( ~1s sur gros volumes ) vs nombre
     *  d'iterations ( 100M / 10k = 10000 iterations , overhead negligeable ) . */
    public static final int REMOVE_BY_FILE_DEFAULT_CHUNK_SIZE = 10_000;

    /**
     * Chunked DELETE of {@code referencevalue} rows attached to a binaryfile ,
     * with per-chunk progress reporting and cooperative cancellation .
     *
     * <h2>Why chunked</h2>
     *
     * <p>The naive single-statement DELETE has 3 scaling pathologies on
     * large files :
     * <ol>
     *   <li>Single tx = single WAL flush at commit . 100M rows = ~50 GB WAL
     *       redo + undo in one shot -> checkpoint pressure + replica lag .</li>
     *   <li>Single statement = single lock duration .
     *       {@code statement_timeout} ( typically 1h ) becomes the hard cap ;
     *       very large files time out and leave inconsistent state .</li>
     *   <li>No live progress = oa-live shows EN_ATTENTE for the full
     *       duration ( 1h+ on big files ) , no cancel capability mid-DELETE .</li>
     * </ol>
     *
     * <p>Chunking by id-batches addresses all three :
     * <ul>
     *   <li>Each chunk commits independently ( WAL pressure bounded ) ;</li>
     *   <li>Per-chunk duration ~seconds ( well under statement_timeout ) ;</li>
     *   <li>{@code onProgress} fires after each chunk for live bar ;
     *       {@code cancelCheck} consulted between chunks for SLA cancel .</li>
     * </ul>
     *
     * <h2>RI trigger cost</h2>
     *
     * <p>The {@code reference_reference_referenceid_fkey ON DELETE CASCADE}
     * trigger still fires per deleted row . Pre-deleting
     * {@code reference_reference} rows in bulk BEFORE each batch reduces
     * the per-row trigger cost from ~50us ( actual cascade DELETE ) to
     * ~5us ( index lookup returning 0 dependent rows ) - 10x improvement
     * on link-heavy datatypes .
     *
     * @param fileId       binaryfile uuid to wipe from referencevalue
     * @param chunkSize    rows per batch ( e.g. 10_000 )
     * @param onProgress   optional callback receiving cumulative rows
     *                     deleted ; called after each chunk
     * @param cancelCheck  optional cooperative cancel : if returns true
     *                     between chunks , {@link java.util.concurrent.CancellationException}
     *                     is thrown ( partial DELETE remains committed )
     * @return total rows deleted from {@code referencevalue}
     */
    public long removeByFileIdChunked(final UUID fileId,
                                      final int chunkSize,
                                      final java.util.function.LongConsumer onProgress,
                                      final java.util.function.BooleanSupplier cancelCheck) {
        final String table  = getTable().getSqlIdentifier();
        final String schema = getSchema().getName();

        final String selectIdsSql = """
                SELECT id FROM %s
                WHERE binaryfile = :binaryFile
                LIMIT :chunkSize
                """.formatted(table);
        final String deleteLinksSql = """
                DELETE FROM %s.reference_reference
                WHERE referenceid = ANY(:ids)
                """.formatted(schema);
        final String deleteRowsSql = """
                DELETE FROM %s
                WHERE id = ANY(:ids)
                """.formatted(table);

        long total = 0L;
        int batch;
        do {
            if (cancelCheck != null && cancelCheck.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException(
                        "Cancelled during removeByFileIdChunked ( total deleted so far : " + total + " )");
            }
            java.util.List<UUID> ids = getNamedParameterJdbcTemplate().queryForList(
                    selectIdsSql,
                    Map.of("binaryFile", fileId, "chunkSize", chunkSize),
                    UUID.class);
            batch = ids.size();
            if (batch == 0) break;

            UUID[] idsArray = ids.toArray(new UUID[0]);
            getNamedParameterJdbcTemplate().update(deleteLinksSql, Map.of("ids", idsArray));
            getNamedParameterJdbcTemplate().update(deleteRowsSql,  Map.of("ids", idsArray));

            total += batch;
            if (onProgress != null) {
                try { onProgress.accept(total); }
                catch (RuntimeException ignored) { /* best effort */ }
            }
        } while (batch == chunkSize);

        log.info("removeByFileIdChunked : deleted {} referencevalue rows for fileId={} ( chunk size = {} )",
                total, fileId, chunkSize);
        flush();
        return total;
    }


    @Override
    public Map<String, List<Ltree>> resolveRequiredAuthorizations(Map<String, List<Ltree>> requiredAuthorizations) {
        if (requiredAuthorizations.isEmpty()) {
            return Map.of();
        }
        AtomicInteger counter = new AtomicInteger();
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();

        String params = requiredAuthorizations.entrySet().stream()
                .map(entry -> {
                    String dataNameParam = "param%d".formatted(counter.incrementAndGet());
                    String valueParam = "param%d".formatted(counter.incrementAndGet());
                    parameterSource.addValue(dataNameParam, entry.getKey());
                    parameterSource.addValue(valueParam, entry.getValue().getFirst().getSql());
                    return "row(:%s,:%s::ltree)".formatted(dataNameParam, valueParam);
                })
                .collect(Collectors.joining(", "));

        String sql = String.format("""
                        SELECT 'java.util.Map' AS "@class",
                        to_jsonb(
                            jsonb_populate_record(
                                null::%1$s.requiredAuthorizations,
                                jsonb_object_agg(
                                    jsonb_build_object(
                                        referencetype,
                                        to_jsonb(ARRAY[hierarchicalkey])
                                    )
                                )
                            )
                        ) AS json
                        FROM %1$s.referencevalue
                        WHERE (referencetype, naturalkey) IN (%2$s)
                        LIMIT 1
                        """,
                getSchema().getSqlIdentifier(),
                params
        );

        return getNamedParameterJdbcTemplate().
                queryForObject(
                        sql,
                        parameterSource,
                        new JsonRowMapper<Map>()
                );
    }


    public List<UUID> delete(final DownloadDatasetQuery downloadDatasetQuery) {
        final SqlRequest sqlRequest = DataRequestBuilder.buildDeleteRequest(downloadDatasetQuery);
        return getNamedParameterJdbcTemplate().queryForList(sqlRequest.sql(), sqlRequest.parameterSource(), UUID.class);
    }

    /**
     * @param refType le type du referenciel
     * @param params  les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<UUID> deleteReferenceType(final String refType, final MultiValueMap<String, String> params) {
        String sql = "delete from %1$s%n" +
                     "WHERE application=:applicationId::uuid AND ReferenceType=:refType%n";
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, refType);

        sql += addReferenceConditions(params, paramSource);
        sql += "%nreturning  id";
        final String query = String.format(sql, getTable().getSqlIdentifier(), getEntityClass().getName());
        return getNamedParameterJdbcTemplate().queryForList(query, paramSource, UUID.class);
    }

    // P0.1 : DISTINCT removed . The PK
    // ( application , referencetype , hierarchicalkey , patterncolumnname )
    // already guarantees row unicity for FROM monotable + WHERE on
    // ( application , referencetype ) , so DISTINCT was a costly no-op .
    // On a 4 . 29 M-row referencetype , removing DISTINCT cuts execution
    // from 110 sec to 11 sec ( 9.76x , bench iso-rows verified by md5 of
    // sorted string_agg under role applicationManager ) by skipping a
    // HashAggregate spilling 4 . 86 GB and a Sort spilling 4 . 91 GB .

    public Stream<DataValue> findAllByReferenceTypeStream(final String referenceName) {
        String query = """
                SELECT '%1$s' as "@class",
                to_jsonb(t)  as json
                FROM
                %2$s t
                WHERE application=:applicationId::uuid AND ReferenceType=:refType

                """
                .formatted(DataValue.class.getName(), getTable().getSqlIdentifier());
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, referenceName);
        return getNamedParameterJdbcTemplate()
                .queryForStream(query, paramSource, getJsonRowMapper());
    }

    public List<DataValue> findAllByReferenceType(final String referenceName) {
        String query = """
                SELECT '%1$s' as "@class",
                to_jsonb(t)  as json
                FROM
                %2$s t
                WHERE application=:applicationId::uuid AND ReferenceType=:refType

                """
                .formatted(DataValue.class.getName(), getTable().getSqlIdentifier());
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, referenceName);
        return getNamedParameterJdbcTemplate()
                .query(query, paramSource, getJsonRowMapper());
    }

    public Stream<DataValue> findAllByReferenceTypeWithReferencingReferencesStream(final String refType, final java.util.Map<String, java.util.List<String>> params) {
        final int offset = Optional.of(params)
                .map(m -> m.remove("_offset_"))
                .filter(l -> !l.isEmpty())
                .map(List::getFirst)
                .map(o -> {
                    try {
                        return Integer.valueOf(o);
                    } catch (final NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0);
        final String limit = Optional.of(params)
                .map(m -> m.remove("_limit_"))
                .filter(l -> !l.isEmpty())
                .map(List::getFirst)
                .filter(o -> o.matches("[0-9]*|ALL"))
                .orElse("ALL");
        // P0.2 : CTE " agg " replaced by a LEFT JOIN LATERAL correlated to t.id .
        // The original form materialised json_object_agg over the entire
        // reference_reference table ( 39.7 M rows on si_acbb ) BEFORE any filter ,
        // which raised " string buffer exceeds maximum allowed length "
        // ( 1 GB jsonb cap ) on referentials with rich back-links - OOM
        // visible from 3 K rows ( see SQL_REPORT_10_05_26 . md Q2 ) . The
        // LATERAL fires once per t row retained by the outer WHERE +
        // OFFSET / LIMIT , bounded to that row's back-links , never above
        // the per-row jsonb cap .
        //
        // Iso-result verified ( md5 of sorted string_agg ) on
        // t_paturage_pat ( 1251 rows ) and t_data_sol_analyse_dsa ( 3131
        // rows ) under role applicationManager : strict equality with the
        // CTE form constrained to the same refType ( the unconstrained
        // form OOMs and cannot be benched directly ) .
        //
        // Preserved : cross join with jsonb_each_text(t.refvalues) kv +
        // DISTINCT ( both required for the addReferenceConditions ' any '
        // filter which references kv.value ) ; same WHERE predicates ;
        // RLS path unchanged ( both referencevalue accesses honour the
        // role set by setRoleForClient ) .
        String query = """
                SELECT DISTINCT
                    '%1$s' as "@class",
                    to_jsonb(t) ||
                        jsonb_build_object('referencingreferences', refs.agg) as json
                FROM
                    %2$s t
                    left join lateral (
                        select json_object_agg(rr.referenceid, d2.refvalues) as agg
                        from %3$s.reference_reference rr
                        left join %2$s d2 on d2.id = rr.referenceid
                        where rr.referencesby = t.id
                    ) refs on true,
                    jsonb_each_text(t.refvalues) kv
                WHERE
                    application=:applicationId::uuid AND
                    ReferenceType=:refType
                """
                .formatted(DataValue.class.getName(), getTable().getSqlIdentifier(), getSchema().getSqlIdentifier());
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, refType);

        String cond = addReferenceConditions(params, paramSource);
        cond = String.format("%s offset %d  limit %s", cond, offset, limit);
        return getNamedParameterJdbcTemplate().queryForStream(query + cond, paramSource, getJsonRowMapper());
    }


    public Map<String, Map<String, String>> findDisplayByNaturalKey(final String refType) {
        final String query = String.format("""
                        SELECT 'java.util.Map' AS "@class",
                               jsonb_build_object(naturalkey, jsonb_agg(display)) AS json
                        FROM %2$s,
                        LATERAL (
                            SELECT jsonb_build_object(
                                replace(
                                    replace(
                                        jsonb_path_query(
                                            refvalues,
                                            '$.keyvalue()?(@.key like_regex "%1$s.*").key'
                                        )::text,
                                        '%1$s',
                                        ''
                                    ),
                                    '"', ''
                                ),
                                TRIM('"' FROM
                                    jsonb_path_query(
                                        refvalues,
                                        '$.keyvalue()?(@.key like_regex "%1$s.*").value'
                                    )::text
                                )
                            ) AS display
                        ) displays
                        WHERE referencetype = :refType
                        GROUP BY naturalkey
                        """,
                DataColumn.DISPLAY,
                getTable().getSqlIdentifier()
        );

        final Map<String, Map<String, String>> displayForNaturalKey = new HashMap<>();
        final List<?> result = getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource(REF_TYPE, refType),
                getJsonRowMapper()
        );

        for (final Object o : result) {
            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, String>>> o1 = (Map<String, List<Map<String, String>>>) o;
            Map<String, Map<String, String>> collect = o1.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> {
                                final Map<String, String> displayMap = new HashMap<>();
                                for (final Map<String, String> s : e.getValue()) {
                                    displayMap.putAll(s);
                                }
                                return displayMap;
                            }
                    ));
            displayForNaturalKey.putAll(collect);
        }

        return displayForNaturalKey;
    }


    public List<List<String>> findDataColumn(final String refType, final String column) {
        final AtomicInteger ai = new AtomicInteger(0);
        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, refType);

        final String select = Stream.of(column.split(","))
                .map(c -> {
                    String paramName = "v" + ai.get();
                    mapSqlParameterSource.addValue(paramName, c);
                    return String.format("refValues->>'%s' AS \"%s%d\"", paramName, DataColumn.DISPLAY, ai.getAndIncrement());
                })
                .collect(Collectors.joining(", "));

        final String query = String.format("""
                        SELECT %s
                        FROM %s t
                        WHERE application = :applicationId::uuid
                          AND ReferenceType = :refType
                        """,
                select,
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().queryForList(query, mapSqlParameterSource)
                .stream()
                .map(m -> m.values().stream().map(v -> (String) v).toList())
                .toList();
    }


    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> getDataIdPerKeys(final String ReferenceType) {
        // Avant ce fix : findAllByReferenceType materialisait TOUS les rows
        // referencevalue ( jsonb refvalues complet inclus ) en List<DataValue>
        // via Jackson , juste pour extraire 4 colonnes ( id / naturalkey /
        // hierarchicalkey / patterncolumnname ) . Sur si_acbb t_soil_water_content_swc
        // = 1.26M rows × ~500-1000 bytes JSON inflation Java = ~1.5-2 GB
        // heap allocation au demarrage workflow . Cause directe d'OOM .
        //
        // Fix : SQL projection uniquement sur les 4 colonnes necessaires
        // ( ~30 bytes/row ) . Gain x40 : ~40 MB heap au lieu de 1.5-2 GB .
        final String query = """
                SELECT id, naturalkey::text AS naturalkey,
                       hierarchicalkey::text AS hierarchicalkey,
                       patterncolumnname
                  FROM %1$s
                 WHERE application = :applicationId::uuid
                   AND ReferenceType = :refType
                """.formatted(getTable().getSqlIdentifier());
        final MapSqlParameterSource params = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, ReferenceType);
        // Instrumentation timing for diagnosis ( see step 1 of the prep-time
        // investigation ) . Slices the call into measurable phases so we can
        // identify whether the cost lives in PG ( query exec / network ) ,
        // JDBC iteration ( ResultSet next ) , Java per-row parsing , or the
        // final immutable copy . No business-logic change .
        final long t0 = System.nanoTime();
        final long[] firstRowAtNs = { 0L };
        final long[] rowCount     = { 0L };
        Map<DataValue.LineIdentityColumnName, UUID> dataIdPerKeys = new HashMap<>();
        getNamedParameterJdbcTemplate().query(query, params, rs -> {
            if (firstRowAtNs[0] == 0L) firstRowAtNs[0] = System.nanoTime();
            UUID id = UUID.fromString(rs.getString("id"));
            // Skip Ltree.checkSyntax on read : the values come straight from
            // PostgreSQL where they were validated at write time . The legacy
            // Ltree.fromSql ( ligne 56 ) runs Splitter + regex per label , which
            // adds up to millions of regex matches per publish on a 1M-row
            // referencevalue table ( 2 keys per row x N refTypes preloaded )
            // and dominates the pre-cascade preparation time observed at
            // ~2m30s wall-clock . fromSqlWithoutCheck does the same allocation
            // without the per-row syntax validation .
            Ltree naturalKey      = Ltree.fromSqlWithoutCheck(rs.getString("naturalkey"));
            Ltree hierarchicalKey = Ltree.fromSqlWithoutCheck(rs.getString("hierarchicalkey"));
            String patternColName = rs.getString("patterncolumnname");
            dataIdPerKeys.put(
                    new DataValue.LineIdentityColumnName(naturalKey, hierarchicalKey, patternColName),
                    id);
            rowCount[0]++;
        });
        final long t1 = System.nanoTime();
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> result = ImmutableMap.copyOf(dataIdPerKeys);
        final long t2 = System.nanoTime();
        final long firstRow = firstRowAtNs[0];
        log.debug("[GDPK] ref={} rows={} sql_to_firstRow={}ms iter+parse={}ms immCopy={}ms total={}ms",
                ReferenceType,
                rowCount[0],
                firstRow == 0L ? 0L : (firstRow - t0) / 1_000_000L,
                firstRow == 0L ? 0L : (t1 - firstRow)  / 1_000_000L,
                (t2 - t1) / 1_000_000L,
                (t2 - t0) / 1_000_000L);
        return result;
    }

    /**
     * Variante lazy de {@link #getDataIdPerKeys(String)} : ne charge en
     * RAM que les rows {@code referencevalue} dont la {@code naturalkey}
     * appartient a {@code naturalKeysOfInterest} ( typiquement issu du
     * pre-scan du CSV en cours de publication ) .
     *
     * <h2>Motivation</h2>
     *
     * <p>Le full preload via {@link #getDataIdPerKeys} materialise TOUTES
     * les rows du refType ( 10 MB pour 100k rows , 1 GB pour 10M rows ,
     * OOM au-dela ) . Sur des refs de 100M+ rows en BDD c'est inutilisable .
     *
     * <p>Cette variante charge uniquement le sous-ensemble effectivement
     * reference dans le CSV soumis a publication ( typiquement 50-5000
     * valeurs distinctes ) . Memoire : O(M_referenced) au lieu de
     * O(N_ref_size) . Latence : O(M log N) via index btree
     * {@code nk_patternColumnNam_type} .
     *
     * <h2>Strategie SQL</h2>
     *
     * <p>{@code WHERE naturalkey::text = ANY(?::text[])} avec array
     * binding cote JDBC :
     * <ul>
     *   <li>Pas de limite parametres prepared statement ( contrairement
     *       a {@code WHERE nk IN (?, ?, ...)} limite a 32 767 ) .</li>
     *   <li>PG choisit hash join automatique sur gros arrays ; index
     *       seek per value sur petits arrays .</li>
     *   <li>Pour des sets > 1M nks , preferable d'utiliser une TEMP
     *       TABLE + JOIN ( hors scope de cette methode ; le caller doit
     *       splitter en batches ou utiliser une variante TEMP-TABLE ) .</li>
     * </ul>
     *
     * @param referenceType        refType cible
     * @param naturalKeysOfInterest set des naturalkeys ( format texte
     *                              compatible ltree ) presentes dans le CSV
     * @return mapping {@link DataValue.LineIdentityColumnName} -> UUID ,
     *         bornee a {@code naturalKeysOfInterest.size()} entrees max .
     *         Vide si le set est vide ou null .
     */
    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> getDataIdPerKeysByNaturalKeys(
            final String referenceType,
            final java.util.Set<String> naturalKeysOfInterest) {
        if (naturalKeysOfInterest == null || naturalKeysOfInterest.isEmpty()) {
            return ImmutableMap.of();
        }
        // Cast text[] -> ltree[] cote PG ; respecte le type ltree natif de
        // la colonne naturalkey + permet l'usage de l'index btree existant
        // {@code nk_patternColumnNam_type} ( referencetype , naturalkey ,
        // patterncolumnname ) .
        final String query = """
                SELECT id, naturalkey::text AS naturalkey,
                       hierarchicalkey::text AS hierarchicalkey,
                       patterncolumnname
                  FROM %1$s
                 WHERE application = :applicationId::uuid
                   AND ReferenceType = :refType
                   AND naturalkey = ANY(:nks::ltree[])
                """.formatted(getTable().getSqlIdentifier());
        String[] nksArray = naturalKeysOfInterest.toArray(new String[0]);
        final MapSqlParameterSource params = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, referenceType)
                .addValue("nks", nksArray);
        final long t0 = System.nanoTime();
        final long[] rowCount = { 0L };
        Map<DataValue.LineIdentityColumnName, UUID> dataIdPerKeys = new HashMap<>(naturalKeysOfInterest.size() * 2);
        getNamedParameterJdbcTemplate().query(query, params, rs -> {
            UUID id = UUID.fromString(rs.getString("id"));
            Ltree naturalKey      = Ltree.fromSqlWithoutCheck(rs.getString("naturalkey"));
            Ltree hierarchicalKey = Ltree.fromSqlWithoutCheck(rs.getString("hierarchicalkey"));
            String patternColName = rs.getString("patterncolumnname");
            dataIdPerKeys.put(
                    new DataValue.LineIdentityColumnName(naturalKey, hierarchicalKey, patternColName),
                    id);
            rowCount[0]++;
        });
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        log.debug("[GDPK-LAZY] ref={} nks_requested={} rows_loaded={} elapsed={}ms",
                referenceType, naturalKeysOfInterest.size(), rowCount[0], elapsedMs);
        return ImmutableMap.copyOf(dataIdPerKeys);
    }

    public List<ApplicationResult.DataSynthesis> buildReferenceSynthesis() {
        if (useReferencevalueCountStatsTable) {
            try {
                return readSynthesisFromStatsTable();
            } catch (final BadSqlGrammarException tableMissing) {
                // Migration V5 pas encore appliquee sur ce schema ( cas d'un
                // upgrade sur une instance ou Flyway n'a pas encore tourne ,
                // ou app cree avant la livraison de la table de stats ).
                // Fallback automatique sur le COUNT direct pour ne pas casser
                // l'endpoint pendant la fenetre de migration.
                log.info("referencevalue_count_stats absente sur {}, fallback COUNT direct ( applicable jusqu'a la prochaine migration Flyway )",
                        getTable().schema().getSqlIdentifier());
                return readSynthesisFromDirectCount();
            }
        }
        return readSynthesisFromDirectCount();
    }

    /**
     * Lecture rapide depuis la table de stats maintenue par triggers
     * statement-level ( cf. migration V5 ). Lookup PRIMARY KEY <1ms quel
     * que soit le volume de la table source {@code referencevalue}.
     */
    private List<ApplicationResult.DataSynthesis> readSynthesisFromStatsTable() {
        final String query = String.format("""
                        SELECT
                            referencetype AS ReferenceType,
                            line_count AS lineCount
                        FROM %s.referencevalue_count_stats
                        """,
                getTable().schema().getSqlIdentifier()
        );
        return getNamedParameterJdbcTemplate().query(
                query,
                Map.of(),
                BeanPropertyRowMapper.newInstance(ApplicationResult.DataSynthesis.class)
        );
    }

    /**
     * Mode legacy : COUNT(*) GROUP BY direct sur la table source.
     * Conserve comme fallback ( table de stats absente ) et activable
     * explicitement pour debug / benchmark via la propriete
     * {@code openadom.referencevalue.count.use-stats-table=false}.
     */
    private List<ApplicationResult.DataSynthesis> readSynthesisFromDirectCount() {
        final String query = String.format("""
                        SELECT
                            ReferenceType AS ReferenceType,
                            COUNT(*) AS lineCount
                        FROM %s
                        GROUP BY ReferenceType
                        """,
                getTable().getSqlIdentifier()
        );
        return getNamedParameterJdbcTemplate().query(
                query,
                Map.of(),
                BeanPropertyRowMapper.newInstance(ApplicationResult.DataSynthesis.class)
        );
    }

    /**
     * Renvoie l'horodatage du dernier rafraichissement de la table de stats
     * pour cette application , ou {@code Optional.empty()} si la table est
     * absente ou vide. Affiche cote frontend a cote du bouton "Recompute"
     * pour informer l'admin de la fraicheur du compteur.
     */
    public java.util.Optional<java.time.Instant> findLastReferencevalueCountStatsUpdate() {
        final String query = String.format(
                "SELECT MAX(updated_at) FROM %s.referencevalue_count_stats",
                getTable().schema().getSqlIdentifier()
        );
        try {
            final java.sql.Timestamp ts = getNamedParameterJdbcTemplate()
                    .queryForObject(query, Map.of(), java.sql.Timestamp.class);
            return java.util.Optional.ofNullable(ts).map(java.sql.Timestamp::toInstant);
        } catch (final BadSqlGrammarException tableMissing) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Reconstruit integralement la table de stats depuis l'etat actuel de
     * {@code referencevalue}. A appeler depuis l'endpoint admin
     * {@code POST /api/v1/admin/applications/{name}/recompute-stats} pour
     * resync explicite apres une dérive ( DELETE pgAdmin , restore partiel ,
     * UPDATE de referencetype , etc. ).
     *
     * <p>Cout : 1 SELECT GROUP BY sur la table source ( meme cout que le
     * COUNT direct legacy , execute uniquement a la demande de l'admin ).
     * Sur 200M rows ACBB : 2-5 min - acceptable car declenche manuellement.</p>
     *
     * @return l'horodatage de la nouvelle ligne updated_at
     */
    public java.time.Instant recomputeReferencevalueCountStats() {
        final String schema = getTable().schema().getSqlIdentifier();
        final String source = getTable().getSqlIdentifier();
        // TRUNCATE + INSERT dans une seule transaction pour atomicite :
        // pendant la duree du recompute , les lectures voient soit l'ancien
        // etat ( si la transaction n'est pas encore committee ) soit le
        // nouveau. Pas d'etat intermediaire incoherent visible.
        getNamedParameterJdbcTemplate().getJdbcTemplate().execute(
                "TRUNCATE TABLE " + schema + ".referencevalue_count_stats");
        getNamedParameterJdbcTemplate().getJdbcTemplate().execute(String.format("""
                INSERT INTO %1$s.referencevalue_count_stats (referencetype, line_count, updated_at)
                SELECT referencetype, count(*), now()
                FROM %2$s
                GROUP BY referencetype
                """, schema, source));
        return findLastReferencevalueCountStatsUpdate().orElse(java.time.Instant.now());
    }

    public void updateConstraintForeignReferences(final List<UUID> uuids) {
        final String deleteSql = String.format("""
                        DELETE FROM %s.Reference_Reference
                        WHERE referenceId IN (:ids)
                        """,
                getTable().schema().getSqlIdentifier()
        );
        final String insertSql = String.format("""
                        INSERT INTO %1$s.Reference_Reference(referenceId, referencesBy)
                        SELECT
                            id AS referenceId,
                            jsonb_array_elements_text(jsonb_path_query_array(refslinkedto, '$.**.uuids[*]'))::uuid AS referencesBy
                        FROM %2$s
                        WHERE id IN (:ids)
                        ON CONFLICT ON CONSTRAINT "Reference_Reference_PK" DO NOTHING
                        """,
                getTable().schema().getSqlIdentifier(),
                getTable().getSqlIdentifier()
        );
        final String sql = deleteSql + ";" + insertSql;
        Iterators.partition(uuids.stream().iterator(), Short.MAX_VALUE - 1)
                .forEachRemaining(uuidsByBatch ->
                        getNamedParameterJdbcTemplate().execute(
                                sql,
                                Map.of("ids", uuidsByBatch),
                                PreparedStatement::execute
                        )
                );
    }

    public Map<Ltree, List<DataValue>> getReferenceDisplaysById(final Set<String> listOfIds) {
        if (listOfIds.isEmpty()) {
            return new HashMap<>();
        }
        final String sql = String.format("""
                        SELECT DISTINCT
                            '%1$s' AS "@class",
                            to_jsonb(r) AS json
                        FROM %2$s.reference_reference dr
                        JOIN %2$s."referencevalue" d ON dr.referenceId = d.id
                        JOIN %3$s r ON dr.referencesBy = r.id
                        WHERE d.id::text IN (:list)
                        """,
                DataValue.class.getName(),
                getSchema().getSqlIdentifier(),
                getTable().getSqlIdentifier()
        );
        List<DataValue> list = getNamedParameterJdbcTemplate().query(
                sql,
                new MapSqlParameterSource().addValue("list", listOfIds),
                getJsonRowMapper()
        );
        Map<Ltree, List<DataValue>> referencesValuesMap = list.stream()
                .collect(Collectors.groupingBy(DataValue::getNaturalKey));
        referencesValuesMap.putAll(list.stream()
                .collect(Collectors.groupingBy(DataValue::getHierarchicalKey)));
        return referencesValuesMap;
    }

    @Override
    public Map<String, String> findHierarchicalKeysByKeyForReferenceTypes(List<String> referenceTypes) {
        if (CollectionUtils.isEmpty(referenceTypes)) {
            return Collections.emptyMap();
        }
        String sql = """
                SELECT naturalkey::text, hierarchicalkey::text
                FROM %s
                WHERE referencetype in (:referenceType)
                """.formatted(getTable().getSqlIdentifier());

        MapSqlParameterSource params = new MapSqlParameterSource("referenceType", referenceTypes);

        Map<String, String> hierarchicalKeyByNaturalKey = getNamedParameterJdbcTemplate().query(
                sql,
                params,
                rs -> {
                    Map<String, String> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("naturalkey"), rs.getString("hierarchicalkey"));
                    }
                    return result;
                }
        );

// Collecter les nouvelles entrées dans une liste séparée
        List<Map.Entry<String, String>> newEntries = Objects.requireNonNull(hierarchicalKeyByNaturalKey).values().stream()
                .distinct()
                .filter(hierarchicalKey -> !hierarchicalKeyByNaturalKey.containsKey(hierarchicalKey))
                .map(hierarchicalKey -> Map.entry(hierarchicalKey, hierarchicalKey))
                .toList();

// Ajouter les nouvelles entrées à la map
        hierarchicalKeyByNaturalKey.putAll(
                newEntries.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        return hierarchicalKeyByNaturalKey;
    }

    public Stream<DataValuesByDataType> getLinkedReferenceValuesStream(final Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Stream.of();
        }

        final String sql = String.format("""
                        WITH RECURSIVE refs AS (
                            SELECT * FROM %1$s rv
                            WHERE rv.id IN (:ids)
                            UNION ALL
                            SELECT rv.*
                            FROM %2$s.reference_reference rr
                            JOIN refs ON rr.referenceid = refs.id
                            JOIN %1$s rv ON rv.id = rr.referencesby
                        )
                        SELECT
                            '%3$s' AS "@class",
                            jsonb_build_object(
                                'dataType', rv.referencetype,
                                'ids', array_agg(DISTINCT to_jsonb(rv.id))
                            ) AS json_map
                        FROM refs rv
                        GROUP BY rv.referencetype
                        """,
                getTable().getSqlIdentifier(),
                getTable().schema().getSqlIdentifier(),
                DataValuesByDataType.class.getCanonicalName()
        );

        return getNamedParameterJdbcTemplate()
                .queryForStream(sql, new MapSqlParameterSource("ids", ids), (rs, rowNum) -> {
                    String jsonMap = rs.getString("json_map");
                    try {
                        JsonNode jsonNode = getJsonRowMapper().getJsonMapper().readTree(jsonMap);
                        String dataType = jsonNode.get("dataType").asText();
                        Set<UUID> dataValues = getJsonRowMapper().getJsonMapper().convertValue(
                                jsonNode.get("ids"),
                                new TypeReference<>() {
                                }
                        );

                        return new DataValuesByDataType(dataType, dataValues.stream()
                                .map(DataRowIds::new)
                                .collect(Collectors.toSet()));
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    @Override
    public java.util.stream.Stream<DataRows> findAllByDataTypeStream(final DownloadDatasetQuery downloadDatasetQuery) {
        return findAllByDataTypeFlux(downloadDatasetQuery).toStream();
    }

    public Flux<DataRows> findAllByDataTypeFlux(final DownloadDatasetQuery downloadDatasetQuery) {
        return findAllByDataTypeFlux(downloadDatasetQuery, getNamedParameterJdbcTemplate());
    }

    /**
     * Variante streaming acceptant un {@link NamedParameterJdbcTemplate}
     * explicite . Permet aux endpoints de telechargement
     * ( CSV / ZIP / charte / additional files ) de router leur cursor
     * sur le pool Hikari dedie {@code streamingDataSource} sans
     * impacter le pool main utilise par cascade + API + schedulers .
     *
     * @param downloadDatasetQuery requete de download ( SQL + parametres )
     * @param template             template JDBC a utiliser ( typiquement
     *                              {@code streamingNamedJdbcTemplate} )
     * @since AUDIT 06-05-26 streaming pool isolation phase 2
     */
    public Flux<DataRows> findAllByDataTypeFlux(final DownloadDatasetQuery downloadDatasetQuery,
                                                final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        final SqlRequest sqlRequest = DataRequestBuilder.buildSelectRequest(downloadDatasetQuery);
        final Stream<DataRows> result = template.queryForStream(
                sqlRequest.sql(), sqlRequest.parameterSource(), new JsonRowMapper<DataRows>());
        return Flux.<DataRows>fromStream(result);
    }

    public Flux<FilterList> getFilterList(final String dataName) {
        final Stream result;
        // #59 - Optimisation : precalcul du flag isHierarchique dans une CTE separee.
        // Avant : jsonb_path_exists(application.configuration, ...) etait appele pour chaque ligne
        // de la jointure components_grouped x parents_grouped (ex: 4850 appels pour t_soil_analysis_sana).
        // Apres : une CTE hierarchique_flags precalcule le flag une seule fois par listName distinct
        // (ex: 9 appels). Gain mesure : 56.7s -> 4.3s (92% de reduction).
        final String query = """
                WITH
                referenceBase AS MATERIALIZED(
                    SELECT distinct listName, colonne, hk, uuid
                    FROM %1$s.referencevalue,
                    JSON_TABLE(
                        refslinkedto, '$.keyvalue()' columns(
                            listName text PATH '$.key',
                            NESTED PATH '$.value.keyvalue()' columns(
                                colonne text PATH '$.key',
                                NESTED PATH '$.value.keyvalue()' columns(
                                    hk ltree PATH '$.key',
                                    NESTED PATH '$.value.uuids[*]' columns(
                                        uuid uuid PATH '$'
                                    )
                                )
                            )
                        )
                    )
                    WHERE referencetype = :referenceType
                ),
                -- Precalcul du flag isHierarchique une seule fois par listName distinct
                hierarchique_flags AS MATERIALIZED(
                    SELECT DISTINCT rb.listName,
                        jsonb_path_exists(
                            app.configuration,
                            ('$.datadescription.' || rb.listName || '.componentdescriptions.*.checker ? (@.isparent == true)')::jsonpath
                        ) AS is_hierarchique
                    FROM (SELECT DISTINCT listName FROM referenceBase) rb
                    CROSS JOIN application app
                    WHERE app.name = '%1$s'
                ),
                -- CTE pour agreger les colonnes par hk
                components_grouped AS MATERIALIZED(
                    SELECT
                        hk,
                        listName,
                        array_agg(DISTINCT colonne) AS components
                    FROM referenceBase
                    GROUP BY hk, listName
                ),
                parents_grouped AS MATERIALIZED(
                    SELECT
                        child.hierarchicalkey AS child_hkey,
                        jsonb_agg(DISTINCT jsonb_build_object(
                            'referenceType', p.referencetype,
                            'hierarchicalKey', p.hierarchicalkey,
                            'naturalKey', p.naturalkey,
                            '__display_default', p.refvalues->'__display_default',
                            '__display_fr', p.refvalues->'__display_fr',
                            '__display_en', p.refvalues->'__display_en',
                            'id', p.id
                        )) AS parents
                    FROM %1$s.referencevalue child
                    CROSS JOIN LATERAL regexp_split_to_table(
                        regexp_replace(child.hierarchicalkey::text, '\\.[^\\.]+$',''),
                        '\\.'
                    ) AS ancestor_segment(segment)
                    CROSS JOIN LATERAL (
                        SELECT
                            (regexp_match(ancestor_segment.segment, '([0-9a-z_]*)K(.*)'))[1] AS parent_type,
                            (regexp_match(ancestor_segment.segment, '([0-9a-z_]*)K(.*)'))[2] AS parent_naturalkey
                        WHERE ancestor_segment.segment != ''
                    ) AS ps
                    JOIN %1$s.referencevalue p
                        ON p.referencetype = ps.parent_type
                        AND p.naturalkey = ps.parent_naturalkey::ltree
                    WHERE child.hierarchicalkey <> ''
                      AND child.hierarchicalkey IN (SELECT hk FROM referenceBase)
                    GROUP BY child.hierarchicalkey
                ),
                referenceByReftype AS (
                    -- Fix bug filtre Site != colonne Site : on lit les infos
                    -- d'identification ( id , naturalKey , hierarchicalKey ) et
                    -- les labels d'affichage ( __display_* ) sur la referencevalue
                    -- du LEAF lui-meme et plus sur pg.parents->0 ( le premier
                    -- ancetre ). L'ancien code etait herite du commit f3d0516
                    -- "Duplication de nodes dans les filtre #52" qui , pour
                    -- dedupliquer les entrees de la dropdown , avait remplace
                    -- cg.hk par parents->0->>'hierarchicalKey' ; mais ce choix
                    -- masquait completement les leaves : on affichait le label
                    -- du parent ( ex : "Grandes cultures..." ) dans la dropdown
                    -- alors que la colonne du tableau affiche le leaf
                    -- ( ex : "estrees-mons" ) , et la naturalKey envoyee au
                    -- predicat de filtre ( buildReferencePredicate dans
                    -- DataRequestBuilder ) etait celle du parent qui ne matche
                    -- aucune ligne ( les refvalues stockent la naturalKey du
                    -- leaf , pas du parent ).
                    --
                    -- Le champ 'parents' est desormais peuple avec les vraies
                    -- donnees parent au lieu d'un tableau vide hardcode , afin
                    -- que le composant FilterListSelect cote frontend ( vue
                    -- arborescente actuellement commentee dans
                    -- FiltersDataCollapse.vue avec un TODO explicite ) puisse
                    -- etre rebranche sans modifier ce SQL.
                    SELECT
                        cg.listName AS "listName",
                        jsonb_build_object(
                            'listName', cg.listName,
                            'refsLinkeds', jsonb_agg(
                                distinct jsonb_build_object(
                                    'id',                leaf.id,
                                    'naturalKey',        leaf.naturalkey::text,
                                    '__display_default', leaf.refvalues->'__display_default',
                                    '__display_fr',      leaf.refvalues->'__display_fr',
                                    '__display_en',      leaf.refvalues->'__display_en',
                                    'referenceType',     cg.listName,
                                    'hierarchicalKey',   leaf.hierarchicalkey::text,
                                    'isHierarchique',    hf.is_hierarchique,
                                    'components',        cg.components,
                                    'parents',           COALESCE(pg.parents, '[]'::jsonb)
                                )
                            )
                        ) AS ref_object
                    FROM components_grouped cg
                    JOIN %1$s.referencevalue leaf
                         ON leaf.referencetype  = cg.listName
                        AND leaf.hierarchicalkey = cg.hk
                    LEFT JOIN parents_grouped pg ON pg.child_hkey = cg.hk
                    JOIN hierarchique_flags hf ON hf.listName = cg.listName
                    GROUP BY cg.listName
                    ORDER BY cg.listName
                )
                SELECT
                    'fr.inra.oresing.persistence.FilterList' AS "@class",
                    ref_object AS "json"
                FROM referenceByReftype;

                """.formatted(getSchema().getSqlIdentifier());
        result = getNamedParameterJdbcTemplate().queryForStream(
                query,
                Map.of("referenceType", dataName)
                , new JsonRowMapper<FilterList>());
        return Flux.<FilterList>fromStream(result);
    }

    /**
     * Pour une colonne marquée {@code __FILTER_LIST__} dans le YAML , retourne
     * la liste des valeurs distinctes effectivement présentes dans les données ,
     * pour alimenter la dropdown du frontend ( cf. {@link ColumnDistinctValues} ).
     *
     * <p>Une requête par colonne ; le résultat est mis en cache au niveau du
     * datatype par {@code DataService.getFilterListResult} ( cache déjà présent
     * pour les FilterList , même politique d'invalidation ).
     *
     * <p><b>Multiplicité</b> :
     * <ul>
     *   <li>{@code ONE} : {@code refvalues #>> '{key}'} renvoie la valeur scalaire ,
     *       le {@code DISTINCT} aggrège trivialement.
     *   <li>{@code MANY} : la valeur est un tableau JSON ; on utilise
     *       {@code jsonb_array_elements_text} dans une jointure
     *       {@code LATERAL} pour déplier le tableau avant le {@code DISTINCT} ,
     *       sinon une option {@code "[\"A\",\"B\"]"} apparaitrait à la place de
     *       {@code "A"} et {@code "B"} séparément.
     * </ul>
     *
     * <p><b>Valeurs vides</b> : un {@code null} dans la colonne ( ONE ) ou un
     * élément {@code null} dans le tableau ( MANY ) est conservé tel quel dans
     * le résultat. Le frontend rend cette valeur en *(vide)* ( i18n ).
     *
     * <p><b>Cardinalité</b> : on demande {@code LIMIT (LIMIT + 1)} et on détecte
     * la troncature côté Java ( si la liste contient plus de
     * {@link ColumnDistinctValues#DISTINCT_VALUES_LIMIT} entrées ) ; plus
     * simple qu'une fenêtre SQL et lisible côté lecture.
     *
     * @param dataName     nom du datatype ( = referenceType en base )
     * @param componentKey clé de la colonne à dépouiller
     * @param multiplicity multiplicité de la colonne ( gouverne la forme SQL )
     * @return entrée prête à sérialiser dans le payload {@code /filters}
     */
    public ColumnDistinctValues getColumnDistinctValues(
            final String dataName,
            final String componentKey,
            final Multiplicity multiplicity) {
        final String unfold = switch (multiplicity) {
            case ONE -> "rv.refvalues #>> ARRAY[:componentKey]";
            case MANY -> "jsonb_array_elements_text(COALESCE(rv.refvalues -> :componentKey, '[]'::jsonb))";
        };
        final String fromClause = multiplicity == Multiplicity.ONE
                ? "%1$s.referencevalue rv".formatted(getSchema().getSqlIdentifier())
                : "%1$s.referencevalue rv, LATERAL %2$s AS v".formatted(getSchema().getSqlIdentifier(), unfold);
        final String selectExpr = multiplicity == Multiplicity.ONE ? unfold + " AS v" : "v";
        final String query = """
                SELECT DISTINCT %1$s
                FROM %2$s
                WHERE rv.referencetype = :dataName
                ORDER BY v ASC NULLS LAST
                LIMIT %3$d
                """.formatted(selectExpr, fromClause, ColumnDistinctValues.DISTINCT_VALUES_LIMIT + 1);
        final List<String> values = getNamedParameterJdbcTemplate().query(
                query,
                Map.of("dataName", dataName, "componentKey", componentKey),
                (rs, rowNum) -> rs.getString(1));
        final boolean truncated = values.size() > ColumnDistinctValues.DISTINCT_VALUES_LIMIT;
        final List<String> capped = truncated
                ? List.copyOf(values.subList(0, ColumnDistinctValues.DISTINCT_VALUES_LIMIT))
                : List.copyOf(values);
        // hasEmpty est dérivable de capped : si null y figure , la colonne
        // contient au moins une valeur vide. Comme on a fait ORDER BY ASC
        // NULLS LAST , un null éventuel est en fin de liste ; pour MANY ,
        // null peut apparaitre n'importe où ( jsonb_array_elements_text
        // ne distingue pas les positions ) - on scanne intégralement.
        final boolean hasEmpty = capped.stream().anyMatch(v -> v == null);
        return new ColumnDistinctValues(componentKey, capped, truncated, hasEmpty);
    }

    /**
     * Variante de {@link #getColumnDistinctValues} dimensionnée pour les
     * colonnes {@code __FILTER_TEXT__} :
     * <ul>
     *   <li>détecte si la colonne contient des valeurs vides
     *       ( {@link ColumnDistinctValues#hasEmpty()} ) pour conditionner
     *       le bouton *"+ (vide)"* du frontend ;
     *   <li>récupère jusqu'à 2 valeurs distinctes non-null. Si exactement
     *       1 , on la renvoie ( pour permettre l'auto-select dans le
     *       TextFilter , aligné sur ListFilter / ReferenceFilter ) ;
     *       sinon on renvoie une liste vide ( on ne souhaite pas envoyer
     *       la liste complète des valeurs pour une recherche libre ).
     * </ul>
     *
     * <p>Le surcoût par rapport à un simple {@code EXISTS} reste très
     * faible : {@code DISTINCT ... LIMIT 2} sort dès le second row trouvé.
     * Le résultat est mis en cache au niveau du datatype par
     * {@code DataService.getFilterListResult} ( cache déjà existant ).
     *
     * @param dataName     nom du datatype ( = referenceType en base )
     * @param componentKey clé de la colonne à interroger
     * @param multiplicity multiplicité de la colonne ( gouverne la forme
     *                     SQL d'absence et de dépliage )
     * @return entrée {@link ColumnDistinctValues} avec {@code values} de
     *         taille 0 ou 1 , et {@code hasEmpty} renseigné
     */
    public ColumnDistinctValues getColumnHasEmpty(
            final String dataName,
            final String componentKey,
            final Multiplicity multiplicity) {
        // ONE  : null si la valeur scalaire est null ( ou si la clé
        //         absente de l'objet -> #>> renvoie aussi NULL )
        // MANY : on considère "vide" si le tableau est null/absent ( pas
        //         d'élément à filtrer ) , ou si l'un des éléments est null.
        //         jsonb_array_length renvoie NULL pour un non-array ;
        //         coalesce sur 0.
        final String emptyPredicate = switch (multiplicity) {
            case ONE -> "rv.refvalues #>> ARRAY[:componentKey] IS NULL";
            case MANY -> """
                    COALESCE(jsonb_array_length(rv.refvalues -> :componentKey), 0) = 0
                    OR EXISTS (
                        SELECT 1 FROM jsonb_array_elements(rv.refvalues -> :componentKey) elt
                        WHERE elt = 'null'::jsonb
                    )""";
        };
        final String hasEmptyQuery = """
                SELECT EXISTS (
                    SELECT 1 FROM %1$s.referencevalue rv
                    WHERE rv.referencetype = :dataName
                      AND ( %2$s )
                    LIMIT 1
                ) AS has_empty
                """.formatted(getSchema().getSqlIdentifier(), emptyPredicate);
        final Boolean hasEmpty = getNamedParameterJdbcTemplate().queryForObject(
                hasEmptyQuery,
                Map.of("dataName", dataName, "componentKey", componentKey),
                Boolean.class);

        // Sondage des 2 premières valeurs distinctes non-null pour détecter
        // un éventuel "single value" ( auto-select côté UI ).
        final String unfold = switch (multiplicity) {
            case ONE -> "rv.refvalues #>> ARRAY[:componentKey]";
            case MANY -> "jsonb_array_elements_text(COALESCE(rv.refvalues -> :componentKey, '[]'::jsonb))";
        };
        final String fromClause = multiplicity == Multiplicity.ONE
                ? "%1$s.referencevalue rv".formatted(getSchema().getSqlIdentifier())
                : "%1$s.referencevalue rv, LATERAL %2$s AS v".formatted(getSchema().getSqlIdentifier(), unfold);
        final String selectExpr = multiplicity == Multiplicity.ONE ? unfold + " AS v" : "v";
        final String previewQuery = """
                SELECT DISTINCT %1$s
                FROM %2$s
                WHERE rv.referencetype = :dataName
                  AND ( %3$s ) IS NOT NULL
                LIMIT 2
                """.formatted(selectExpr, fromClause,
                multiplicity == Multiplicity.ONE ? unfold : "v");
        final List<String> preview = getNamedParameterJdbcTemplate().query(
                previewQuery,
                Map.of("dataName", dataName, "componentKey", componentKey),
                (rs, rowNum) -> rs.getString(1));
        // Une seule valeur distincte non-null -> on la transmet au front
        // pour l'auto-select. Sinon on renvoie une liste vide ( pas de
        // sens de prébourrer le champ texte avec plusieurs valeurs ).
        final List<String> values = preview.size() == 1 ? List.copyOf(preview) : List.of();

        return new ColumnDistinctValues(
                componentKey, values, false, Boolean.TRUE.equals(hasEmpty));
    }

    @Override
    public List<ReferenceScope.NodeDescription> getNodesForMenu(MenuType menuType) {
        return getNamedParameterJdbcTemplate()
                .query(
                        """
                                SELECT DISTINCT '%1$s' as "@class",
                                        to_jsonb( %2$s.getnodes('%3$s')) as json"""
                                .formatted(
                                        ReferenceScope.NodeDescription.class.getName(),
                                        getTable().schema().getSqlIdentifier(),
                                        menuType.getType()),
                        Map.of(),
                        new JsonRowMapper<>()
                );
    }

    @Override
    public Stream<fr.inra.oresing.domain.data.deposit.bundle.BundleFileContent> getStoredData(Application application, String dataName) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = FileContent.buildFileNameRequest(application, dataName);
        return getNamedParameterJdbcTemplate().queryForStream(
                sql,
                params,
                (rs, rowNum) -> {
                    final Array sqlArray = rs.getArray("refsLinked");
                    List<String> refsLinked = sqlArray != null
                            ? Arrays.asList((String[]) sqlArray.getArray())
                            : Collections.emptyList();
                    return new fr.inra.oresing.domain.data.deposit.bundle.BundleFileContent(
                            refsLinked,
                            rs.getString("fileName"),
                            rs.getBinaryStream("fileContent")
                    );
                }
        );
    }

    @Deprecated(forRemoval = true) // migré vers domain.repository.data.DataRepository.Order
    public enum Order {
        ASC, DESC
    }

    public record DataValuesByDataType(String dataType, Set<DataRowIds> ids) {
    }
}