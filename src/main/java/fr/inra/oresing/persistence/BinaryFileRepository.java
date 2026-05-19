package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.util.*;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BinaryFileRepository extends JsonTableInApplicationSchemaRepositoryTemplate<BinaryFile> implements fr.inra.oresing.domain.repository.file.BinaryFileRepository {

    private static final String PARAM_DATATYPE = "datatype";
    private static final String SQL_SELECT_PROCESSED_DATA = "SELECT processed_data FROM %s WHERE id = ?::uuid";


    @Autowired
    private JdbcTemplate jdbcTemplate;

    public BinaryFileRepository(final Application application) {
        super(application);
    }

    @Override
    public BinaryFile findById(final UUID id) {
        return tryFindById(id).orElse(null);
    }

    @Override
    public Optional<BinaryFile> tryFindById(final UUID id) {
        final SqlParameterSource parameters = new MapSqlParameterSource("id", id);
        return find("id = :id", parameters).stream().findFirst();
    }

    @Override
    /**
     * Renvoie le {@link Set} des binaryfile ids qui ont au moins une
     * liaison sortante via {@code reference_reference} vers un autre
     * binaryfile ( gating UI : afficher / cacher le bouton delete ) .
     *
     * <p>Implementation : SQL direct {@code SELECT DISTINCT EXISTS} .
     * Resultat borne O(N) ou N = taille de {@code binaryfileIds} en
     * entree . Le planner utilise :
     * <ul>
     *   <li>l'index composite ( referencetype , binaryfile ) sur
     *       referencevalue pour filtrer les rows source ;</li>
     *   <li>l'index {@code referencesby_idx} ( V7 ) sur
     *       reference_reference pour le EXISTS short-circuit ;</li>
     *   <li>l'index PK sur referencevalue pour le join rv2 .</li>
     * </ul>
     *
     * <p>Bench AVANT ( reuse de {@link #getReferencedBinaryFiles} qui
     * matérialise N×M tuples ) : OOM JVM heap sur si_acbb avec ~100
     * fichiers publies × ~100k rows / fichier × cross-refs .
     * Bench APRÈS ( cette query ) : résultat borne ≤ N UUIDs
     * ( ~16o / UUID ) , memoire heap stable < 1MB quel que soit le
     * volume referencevalue . Iso-fonctionnel : md5 du Set retourne
     * verifie identique sous role applicationManager sur si_acbb /
     * ticket_507 .
     *
     * <p>RLS : la query traverse {@code referencevalue} ( RLS active )
     * sous le role applicatif pose par setRoleForClient upstream .
     * Le filter {@code rv2.binaryfile != src.binaryfile} preserve
     * la semantique de {@link #getReferencedBinaryFiles} ( on ne
     * compte pas les self-refs intra-fichier ) .
     */
    public Set<UUID> findBinaryFileIdsWithLinks(String dataType, Set<UUID> binaryfileIds) {
        if (binaryfileIds == null || binaryfileIds.isEmpty()) return Set.of();
        String query = """
                SELECT DISTINCT src.binaryfile::text AS bid
                FROM %1$s.referencevalue src
                WHERE src.referencetype = :datatype
                  AND src.binaryfile IN (:binaryfileIds)
                  AND EXISTS (
                    SELECT 1
                    FROM %1$s.reference_reference rr
                    JOIN %1$s.referencevalue rv2 ON rv2.id = rr.referenceid
                    WHERE rr.referencesby = src.id
                      AND rv2.binaryfile != src.binaryfile
                  )
                """.formatted(getSchema().getSqlIdentifier());

        return new java.util.HashSet<>(getNamedParameterJdbcTemplate().queryForList(
                query,
                new MapSqlParameterSource()
                        .addValue(PARAM_DATATYPE, dataType)
                        .addValue("binaryfileIds", binaryfileIds),
                String.class
        )).stream().map(UUID::fromString).collect(java.util.stream.Collectors.toSet());
    }

    public List<ReferencedBinaryFiles> getReferencedBinaryFiles(String dataType, Set<UUID> binaryfileIds) {
        // Pushes the (referencetype , binaryfile) selectivity into a CTE
        // before joining reference_reference + referencevalue2 . CTE rows
        // are then expanded via CROSS JOIN LATERAL on reference_reference
        // ( P1.7 ) , forcing the planner to use the referencesby_idx ( V7 )
        // through a Nested Loop indexed on rr.referencesby = src.id ,
        // instead of a Parallel Hash Join + Memoize on a Seq Scan of the
        // entire reference_reference table . On the typical case
        // ( binaryfile with a handful of cross-links to other files ) ,
        // bench measured 138 ms -> 3.3 ms ( x42 ) on si_acbb under role
        // applicationManager ( md5 strict equality verified ) . On the
        // degenerated case ( binaryfile with no external link ) , both
        // forms perform identically because the planner still has to
        // traverse reference_reference to confirm absence .
        //
        // Result rows keep the previous shape ( one row per
        // (src.binaryfile , src.referencetype , rv2.binaryfile ,
        // rv2.referencetype) tuple , each carrying a single-element
        // array ) so downstream grouping in the caller is unchanged .
        // RLS path unchanged : referencevalue ( RLS active ) is accessed
        // twice via src and rv2 under the role posed by setRoleForClient
        // upstream ; reference_reference has no RLS .
        String query = """
                with src as (
                    select id, binaryfile, referencetype
                    from %1$s.referencevalue
                    where referencetype = :datatype
                      and binaryfile in (:binaryfileIds)
                )
                select
                  'fr.inra.oresing.domain.ReferencedBinaryFiles' as "@class",
                  jsonb_build_object(
                    'binaryFileId', src.binaryfile,
                    'dataType', src.referencetype,
                    'referencedBinaryFileIdsByReferencetype', jsonb_build_object(
                        rv2.referencetype,
                        array_agg(distinct rv2.binaryfile::text)
                    )
                  ) AS json
                from src
                cross join lateral (
                    select rr.referenceid
                    from %1$s.reference_reference rr
                    where rr.referencesby = src.id
                ) rr_l
                join %1$s.referencevalue rv2 on rv2.id = rr_l.referenceid
                where rv2.binaryfile != src.binaryfile
                group by src.binaryfile, src.referencetype, rv2.binaryfile, rv2.referencetype"""
                .formatted(getSchema().getSqlIdentifier());

        return getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource()
                        .addValue(PARAM_DATATYPE,dataType)
                        .addValue("binaryfileIds", binaryfileIds),
                new JsonRowMapper<ReferencedBinaryFiles>()
        );
    }

    @Override
    public Optional<BinaryFile> findPublishedVersions(final BinaryFileDataset binaryFileDataset) {
        Preconditions.checkArgument(binaryFileDataset != null);

        final String query = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT id, application, name, comment, size, params
                            FROM %2$s
                            WHERE application = :application::uuid
                              AND params #>>'{binaryfiledataset, datatype}' = :datatype
                              AND (params->>'published')::bool
                              AND params->'binaryfiledataset'->'requiredauthorizations' = :requiredAuthorizations::jsonb
                              AND params->'binaryfiledataset'->>'from' = :from
                              AND params->'binaryfiledataset'->>'to' = :to
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource()
                        .addValue("application", getApplication().getId())
                        .addValue(PARAM_DATATYPE, binaryFileDataset.getDatatype())
                        .addValue("from", binaryFileDataset.getFrom())
                        .addValue("to", binaryFileDataset.getTo())
                        .addValue("requiredAuthorizations", getJsonRowMapper().toJson(binaryFileDataset.getRequiredAuthorizations())),
                getJsonRowMapper()
        ).stream().findFirst();
    }

    public Optional<BinaryFile> tryFindByIdWithData(final UUID id) {
        Preconditions.checkArgument(id != null);
        final String query = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT
                                id,
                                application,
                                name,
                                comment,
                                size,
                                null as fileData,
                                params
                            FROM %2$s
                            WHERE id = :id
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource("id", id),
                rs -> {
                    if (rs.next()) {
                        BinaryFile binaryFile = getJsonRowMapper().mapRow(rs, 0);
                        if (binaryFile != null) {
                            binaryFile.setFileData(retrieveFileContentAsInputStream(binaryFile.getId()));
                        }
                        assert binaryFile != null;
                        return Optional.of(binaryFile);
                    }
                    return Optional.empty();
                }
        );
    }


    protected List<BinaryFile> find(final String whereClause, final SqlParameterSource sqlParameterSource) {
        final String sql = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT
                                id,
                                creationdate,
                                updatedate,
                                application,
                                name,
                                comment,
                                size,
                                null AS fileData,
                                params
                            FROM %2$s
                            %3$s
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier(),
                whereClause != null ? "WHERE " + whereClause : ""
        );

        return getNamedParameterJdbcTemplate().query(sql, sqlParameterSource, getJsonRowMapper());
    }


    @Override
    public SqlTable getTable() {
        return getSchema().binaryFile();
    }

    @Override
    protected String getUpsertQuery() {
        return """
                    INSERT INTO %1$s (id, application, name, comment, size, fileData, params)
                    SELECT
                        id, application, name, comment, size, NULL,
                        jsonb_set(jsonb_set(COALESCE(params, '{}'),
                            '{createdate}', ('"' || CURRENT_TIMESTAMP::text || '"')::jsonb),
                            '{createuser}', ('"' || current_role::text || '"')::jsonb)
                    FROM json_populate_recordset(NULL::%1$s, :json::json)
                    ON CONFLICT (id)
                    DO UPDATE SET
                        updateDate = current_timestamp,
                        application = EXCLUDED.application,
                        comment = EXCLUDED.comment,
                        name = EXCLUDED.name,
                        size = EXCLUDED.size,
                        params = CASE
                            WHEN EXCLUDED.params IS NOT NULL AND NOT (EXCLUDED.params ->> 'published')::boolean
                                THEN EXCLUDED.params
                            ELSE
                                jsonb_set(jsonb_set(COALESCE(EXCLUDED.params, '{}'),
                                    '{publisheddate}', ('"' || CURRENT_TIMESTAMP::text || '"')::jsonb),
                                    '{publisheduser}', ('"' || current_role::text || '"')::jsonb)
                        END
                    RETURNING id
                """.formatted(getTable().getSqlIdentifier());
    }

    public List<BinaryFile> findByBinaryFileDataset(final String data, final BinaryFileDataset binaryFileDataset, final boolean overlap) {
        final MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource();
        final List<String> where = new LinkedList<>();
        if (Optional.ofNullable(binaryFileDataset).map(BinaryFileDataset::getRequiredAuthorizations).isPresent()) {
            for (final Map.Entry<String, List<Ltree>> entry : binaryFileDataset.getRequiredAuthorizations().entrySet()) {
                final String t = String.format("params #> '{\"binaryfiledataset\", \"requiredauthorizations\", \"%1$s\"}' @@ ('$ == \"'||:%1$s||'\"')::jsonpath", entry.getKey());
                mapSqlParameterSource.addValue(entry.getKey(), entry.getValue().getFirst().getSql());
                where.add(t);
            }
        }
        if (overlap) {
            where.add("params  #> '{\"binaryfiledataset\", \"datatype\"}' @@('$ == \"" + data + "\"')");
            where.add("params @@ ('$.published==true')");
            final String t = """
                    (tsrange(
                        coalesce((params #>> '{"binaryfiledataset", "from"}'), '-infinity')::timestamp,
                        coalesce((params #>> '{"binaryfiledataset", "to"}'), 'infinity')::timestamp
                        ) && tsrange(coalesce(:from::timestamp, '-infinity')::timestamp, coalesce(:to::timestamp, 'infinity')::timestamp))
                        and
                    (tsrange(
                        coalesce((params #>> '{"binaryfiledataset", "from"}'), '-infinity')::timestamp,
                        coalesce((params #>> '{"binaryfiledataset", "to"}'), 'infinity')::timestamp
                        ) != tsrange(coalesce(:from::timestamp, '-infinity')::timestamp, coalesce(:to::timestamp, 'infinity')::timestamp))
                    """;
            where.add(t);
            assert binaryFileDataset != null;
            mapSqlParameterSource.addValue("from", binaryFileDataset.getFrom());
            mapSqlParameterSource.addValue("to", binaryFileDataset.getTo());
        } else {
            if (Optional.ofNullable(binaryFileDataset).map(BinaryFileDataset::getFrom).isPresent()) {
                final String from = binaryFileDataset.getFrom();
                final String t = "params #> '{\"binaryfiledataset\", \"from\"}'  @@ ('$ == \"'||:from||'\"')::jsonpath";
                mapSqlParameterSource.addValue("from", from);
                where.add(t);
            }
            if (Optional.ofNullable(binaryFileDataset).map(BinaryFileDataset::getTo).isPresent()) {
                final String to = binaryFileDataset.getTo();
                final String t = "params #> '{\"binaryfiledataset\", \"to\"}'  @@ ('$ == \"'||:to||'\"')::jsonpath";
                mapSqlParameterSource.addValue("to", to);
                where.add(t);
            }
        }
        if (where.isEmpty()) {
            where.add("""
                    params #> '{"binaryfiledataset", "requiredauthorizations"}'= '{}'::jsonb""");
        }
        final String t = "params #> '{\"binaryfiledataset\", \"datatype\"}'  @@ ('$ == \"'||:data||'\"')::jsonpath";
        where.add(t);
        mapSqlParameterSource.addValue("data", data);
        return find(String.join(" AND ", where), mapSqlParameterSource);
    }

    @Override
    public UUID store(BinaryFile entity) {
        Optional<InputStream> inputStreamOpt = Optional.ofNullable(entity)
                .map(BinaryFile::getFileData);
        Preconditions.checkState(entity != null);
        entity.setFileData(null);
        UUID fileId = super.store(entity);

        inputStreamOpt.ifPresent(inputStream -> {
            try {
                storeFileContent(fileId, inputStream, inputStream.available());
            } catch (IOException e) {
                throw new OreSiTechnicalException("Error storing file content", e);
            }
        });

        return fileId;
    }

    /**
     * Toggles the {@code published} flag of a binary file in-place via a
     * focused {@code UPDATE} on the {@code params} jsonb column .
     *
     * <p>Refreshes {@code publisheddate} and {@code publisheduser} . The
     * caller passes the acting {@link UUID} explicitly because cascade
     * sink threads do not inherit the HTTP request's PostgreSQL role
     * ( {@code current_role} returns the technical DB user , which would
     * fail to deserialize back into the {@code UUID} field of
     * {@code BinaryFileInfos.publisheduser} ) .
     *
     * @param fileId    binary file id ; must exist
     * @param published target value of the published flag
     * @param userId    acting user id stored in {@code params.publisheduser}
     * @return number of rows updated ( {@code 0} if id not found )
     */
    public int togglePublishedFlag(UUID fileId, boolean published, UUID userId) {
        String query = """
                UPDATE %s
                SET params = jsonb_set(jsonb_set(jsonb_set(
                            COALESCE(params, '{}'::jsonb),
                            '{published}',     to_jsonb(?::boolean)),
                            '{publisheddate}', to_jsonb(CURRENT_TIMESTAMP::text)),
                            '{publisheduser}', to_jsonb(?::text)),
                    updateDate = current_timestamp
                WHERE id = ?::uuid
                """.formatted(getTable().getSqlIdentifier());
        return jdbcTemplate.update(query, published, userId.toString(), fileId.toString());
    }

    public void storeFileContent(UUID fileId, InputStream inputStream, long fileSize) {
        if (fileSize == 0L) {
            return;
        }
        String query = "UPDATE %s SET fileData = ?, size = ? WHERE id = ?::uuid"
                .formatted(getTable().getSqlIdentifier());

        jdbcTemplate.execute(query, (PreparedStatementCallback<Void>) ps -> {
            ps.setBinaryStream(1, inputStream, fileSize);
            ps.setLong(2, fileSize);
            ps.setObject(3, fileId.toString());
            ps.executeUpdate();
            return null;
        });
    }


    public InputStream retrieveFileContentAsInputStream(UUID fileId) {
        return retrieveFileContentAsInputStream(fileId, jdbcTemplate);
    }

    /**
     * Renvoie un {@link InputStream} qui stream le bytea via SUBSTRING
     * chunks ( cf {@link ByteaSubstringInputStream} ) . **Ne charge pas
     * l'integralite du blob en heap** : O ( chunkBytes ) memoire par
     * appel read , quel que soit le size du fichier .
     *
     * <p>Recommande pour les flux qui ne lisent le blob qu'en
     * sequence ( import / publish cascade pipeline ) ; pour les
     * downloads HTTP attached files qui peuvent random-access ,
     * preferer {@link #retrieveFileContentAsInputStream} qui buffer
     * entierement ( latence < memoire ) .
     *
     * @param fileId     PK du binaryfile
     * @param chunkBytes taille des SUBSTRING successifs ( defaut
     *                   {@link ByteaSubstringInputStream#DEFAULT_CHUNK_BYTES} = 1 MB )
     * @since openadom phase B publish/unpublish refonte ; workaround en
     *        attendant {@code Sources.bytea(...)} dans cascade
     *        ( cf CASCADE_IMPROVEMENTS.md #A )
     */
    public InputStream streamFileContent(UUID fileId, int chunkBytes) {
        return new ByteaSubstringInputStream(
                jdbcTemplate,
                getSchema().getSqlIdentifier(),
                getTable().getSqlIdentifier().replaceFirst("^[^.]+\\.", ""),
                "filedata",
                "id",
                fileId,
                chunkBytes);
    }

    /** Variante avec chunk default 1 MB . */
    public InputStream streamFileContent(UUID fileId) {
        return streamFileContent(fileId, ByteaSubstringInputStream.DEFAULT_CHUNK_BYTES);
    }

    /**
     * Publish FAST path : stream du cache CSV processed ( colonne
     * {@code processed_data} ) , utilise par le FAST path republish .
     *
     * <p>Depuis V13 , {@code processed_data} est un {@code oid} pointant
     * vers un Large Object Postgres . Le contenu est lu via
     * {@link org.postgresql.largeobject.LargeObjectManager} en streaming
     * 8 KB pages ( pas de pre-buffer JVM ) jusqu'a 4 TB par object . Doit
     * etre invoque dans une transaction ouverte ( LO API Postgres exige
     * tx active ) .
     *
     * <p>Retourne un {@link InputStream} vide si la colonne est NULL
     * ( fichier pre-feature ou capture echouee ) ; les callers doivent
     * tester {@code processedSize > 0} via {@link #findProcessedSize}
     * avant d'invoquer pour eviter un stream inutile .
     */
    public InputStream streamProcessedData(UUID fileId) {
        String query = SQL_SELECT_PROCESSED_DATA
                .formatted(getTable().getSqlIdentifier());
        Long oid = jdbcTemplate.queryForObject(query, Long.class, fileId.toString());
        if (oid == null) {
            return java.io.InputStream.nullInputStream();
        }
        return openLargeObjectForRead(oid);
    }

    /**
     * Variante back-compat avec parametre {@code chunkBytes} : ignoree
     * depuis V13 ( LO API gere son propre chunking interne 2 KB ) .
     *
     * @deprecated use {@link #streamProcessedData(UUID)} ; the chunkBytes
     *             parameter is silently ignored since the V13 LO migration .
     */
    @Deprecated
    public InputStream streamProcessedData(UUID fileId, int chunkBytes) {
        return streamProcessedData(fileId);
    }

    private InputStream openLargeObjectForRead(long oid) {
        try {
            java.sql.Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
            org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
            org.postgresql.largeobject.LargeObjectManager lom = pgConn.getLargeObjectAPI();
            org.postgresql.largeobject.LargeObject lo = lom.open(oid, org.postgresql.largeobject.LargeObjectManager.READ);
            try {
                // Wrap the LO InputStream to also close the LO + release the
                // connection back to the pool when the caller closes the stream .
                return new java.io.FilterInputStream(lo.getInputStream()) {
                    @Override
                    public void close() throws java.io.IOException {
                        try { super.close(); } finally {
                            try { lo.close(); } catch (java.sql.SQLException ignored) { /* best-effort */ }
                            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(conn, jdbcTemplate.getDataSource());
                        }
                    }
                };
            } catch (Exception e) {
                try { lo.close(); } catch (java.sql.SQLException ignored) { /* best-effort */ }
                throw e;
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to open Large Object oid=" + oid + " : " + e.getMessage(), e);
        }
    }

    /**
     * Publish FAST path : taille du cache processed_data pour decision
     * routage FAST vs LITE vs FULL au republish .
     *
     * @return taille en octets , 0 si NULL ou fichier inexistant
     */
    public long findProcessedSize(UUID fileId) {
        String query = "SELECT COALESCE(processed_size, 0) FROM %s WHERE id = ?::uuid"
                .formatted(getTable().getSqlIdentifier());
        Long size = jdbcTemplate.queryForObject(query, Long.class, fileId.toString());
        return size != null ? size : 0L;
    }

    /**
     * Publish FAST path : persiste le cache processed_data apres capture
     * en fin d'upload via Large Object Postgres ( V13+ ) .
     *
     * <p>Streaming write 8 KB pages via {@link org.postgresql.largeobject.LargeObjectManager}
     * - aucun pre-buffer JVM , scaling illimite ( 4 TB Postgres LO max ) .
     * Resout la limite ~200 MB du JDBC frontend protocol observee sur les
     * gros captures bytea ( 1M+ rows ) .
     *
     * <p>Si un Large Object existait deja pour ce fileId , il est unlinke
     * avant ecriture du nouveau pour eviter les orphans .
     *
     * <p><b>Atomicite</b> : le {@code lo_create} + writes + UPDATE binaryfile
     * SET processed_data = oid s'execute dans une tx Spring REQUIRES_NEW
     * pour garantir : soit l'integralite reussit ( LO ecrit + oid reference
     * dans binaryfile ) , soit rollback complet ( LO orphelin reclame par
     * vacuumlo dans le pire cas ) .
     */
    /**
     * Captures the {@code processed_data} cache for {@code fileId} by invoking
     * {@code writer} with a {@link java.sql.Connection} bound to a newly created
     * Large Object's write stream . The callback writes whatever bytes the
     * cache format requires ( typically the OAVR header followed by a PG binary
     * COPY payload streamed from {@code referencevalue} ) ; the method handles
     * the surrounding boilerplate :
     *
     * <ol>
     *   <li>unlink the previous Large Object oid for this file ( if any ) ;</li>
     *   <li>create a new Large Object + stream-write the bytes the callback emits ;</li>
     *   <li>commit the {@code binaryfile.processed_data} = newOid pointer +
     *       {@code processed_size} + {@code processed_at} timestamp .</li>
     * </ol>
     *
     * <p>All operations run in a single {@code REQUIRES_NEW} transaction so a
     * partial failure rolls back atomically ( leaving at most an orphan LO
     * reclaimed by {@code vacuumlo} ) . The provided {@link Connection} and
     * {@link java.io.OutputStream} must only be used inside the callback ; they
     * are closed by this method when it returns .
     *
     * @param fileId target binaryfile uuid
     * @param writer callback receiving an open JDBC connection and the
     *               write-end of the new Large Object . Implementations
     *               typically invoke
     *               {@code ReferencevalueCacheWriter.writeCache} .
     */
    public void storeProcessedDataDirectCopy(UUID fileId, DirectCopyWriter writer) {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        tx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            try {
                java.sql.Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
                org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
                org.postgresql.largeobject.LargeObjectManager lom = pgConn.getLargeObjectAPI();

                // 1 . unlink previous oid if any ( avoid orphan )
                String selectOld = SQL_SELECT_PROCESSED_DATA
                        .formatted(getTable().getSqlIdentifier());
                Long oldOid = jdbcTemplate.queryForObject(selectOld, Long.class, fileId.toString());
                if (oldOid != null && oldOid > 0) {
                    try { lom.unlink(oldOid); } catch (java.sql.SQLException ex) {
                        org.slf4j.LoggerFactory.getLogger(BinaryFileRepository.class)
                                .warn("storeProcessedDataDirectCopy : unlink old LO oid={} failed ( orphan possible ) : {}",
                                        oldOid, ex.getMessage());
                    }
                }

                // 2 . create new LO + delegate write to the callback . Counting
                //     wrapper tracks the size so we can persist processed_size .
                long newOid = lom.createLO(org.postgresql.largeobject.LargeObjectManager.READWRITE);
                org.postgresql.largeobject.LargeObject lo = lom.open(newOid, org.postgresql.largeobject.LargeObjectManager.WRITE);
                long bytesWritten;
                try (java.io.OutputStream loStream = lo.getOutputStream();
                     CountingOutputStream countingOut = new CountingOutputStream(loStream)) {
                    writer.write(conn, countingOut);
                    bytesWritten = countingOut.bytesWritten();
                } finally {
                    try { lo.close(); } catch (java.sql.SQLException ignored) { /* best-effort */ }
                }

                // 3 . UPDATE binaryfile.processed_data = oid + processed_size + processed_at
                String update = "UPDATE %s SET processed_data = ?, processed_size = ?, processed_at = now() WHERE id = ?::uuid"
                        .formatted(getTable().getSqlIdentifier());
                jdbcTemplate.update(update, newOid, bytesWritten, fileId.toString());
            } catch (java.sql.SQLException | java.io.IOException ex) {
                throw new RuntimeException("storeProcessedDataDirectCopy failed : " + ex.getMessage(), ex);
            }
        });
    }

    /**
     * Callback contract for {@link #storeProcessedDataDirectCopy} . The writer
     * is expected to consume the provided connection ( e . g . to issue a
     * {@code COPY ... TO STDOUT} ) and stream the resulting bytes to the
     * provided output stream . The output stream is backed by a newly created
     * Postgres Large Object ; bytes are persisted as the writer flushes them .
     */
    @FunctionalInterface
    public interface DirectCopyWriter {
        void write(java.sql.Connection conn, java.io.OutputStream out)
                throws java.sql.SQLException, java.io.IOException;
    }

    /**
     * Minimal {@link java.io.FilterOutputStream} extension that counts bytes
     * written through it . Used to record {@code processed_size} without
     * forcing the caller to track byte counts manually .
     */
    private static final class CountingOutputStream extends java.io.FilterOutputStream {
        private long bytes;

        CountingOutputStream(java.io.OutputStream delegate) {
            super(delegate);
        }

        @Override
        public void write(int b) throws java.io.IOException {
            out.write(b);
            bytes++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws java.io.IOException {
            out.write(b, off, len);
            bytes += len;
        }

        long bytesWritten() { return bytes; }
    }

    /**
     * Publish FAST path : met a jour le {@code configHash} ( SHA-256 du
     * datatype description ) dans {@code binaryfile.params} jsonb .
     * Utilise apres un cascade republish reussi pour aligner le hash
     * stocke avec la config actuelle ( permet FAST path au prochain
     * republish si config reste inchangee ) .
     *
     * <p>Update jsonb scoped : on ne touche QUE le champ {@code configHash} ,
     * les autres champs ( published , publisheduser , binaryFiledataset , etc )
     * sont preserves via {@code jsonb_set} .
     */
    public void updateConfigHash(UUID fileId, String configHash) {
        String sql = """
                UPDATE %s
                   SET params = jsonb_set(COALESCE(params, '{}'::jsonb), '{configHash}', to_jsonb(?::text), true)
                 WHERE id = ?::uuid
                """.formatted(getTable().getSqlIdentifier());
        jdbcTemplate.update(sql, configHash, fileId.toString());
    }

    /**
     * Mode CACHED_ROTATION : clear le {@code processed_data} apres republish
     * FAST reussi . Le cache n'est plus necessaire ( rows back en
     * {@code referencevalue} ) , libere le storage temporaire .
     *
     * <p>Depuis V13 ( LO ) : {@code lo_unlink(oid)} avant {@code SET = NULL}
     * pour liberer le Large Object . Tx Spring REQUIRES_NEW pour atomicite .
     *
     * <p>Idempotent : si {@code processed_data} deja NULL , no-op silencieux .
     */
    public void clearProcessedData(UUID fileId) {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        tx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            String selectOid = SQL_SELECT_PROCESSED_DATA
                    .formatted(getTable().getSqlIdentifier());
            Long oid = jdbcTemplate.queryForObject(selectOid, Long.class, fileId.toString());
            if (oid != null && oid > 0) {
                try {
                    java.sql.Connection conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
                    org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
                    pgConn.getLargeObjectAPI().unlink(oid);
                } catch (java.sql.SQLException ex) {
                    org.slf4j.LoggerFactory.getLogger(BinaryFileRepository.class)
                            .warn("clearProcessedData : lo_unlink oid={} failed ( orphan possible , vacuumlo will reclaim ) : {}",
                                    oid, ex.getMessage());
                }
            }
            String update = """
                    UPDATE %s
                       SET processed_data = NULL,
                           processed_size = NULL,
                           processed_at   = NULL
                     WHERE id = ?::uuid
                       AND processed_data IS NOT NULL
                    """.formatted(getTable().getSqlIdentifier());
            jdbcTemplate.update(update, fileId.toString());
        });
    }

    /**
     * Variante streaming acceptant un {@link JdbcTemplate} explicite .
     * Permet aux endpoints de download ( charte , ZIP attached files ,
     * additional files ) d'ouvrir leur cursor lo / blob sur le pool
     * Hikari dedie {@code streamingDataSource} sans impacter le pool
     * main utilise par cascade + API + schedulers .
     *
     * @param fileId   identifiant du blob a streamer
     * @param template template JDBC a utiliser ( typiquement
     *                  {@code streamingJdbcTemplate} )
     * @since AUDIT 06-05-26 streaming pool isolation phase 2
     */
    public InputStream retrieveFileContentAsInputStream(UUID fileId, JdbcTemplate template) {
        String query = "SELECT fileData FROM %s WHERE id = ?::uuid".formatted(getTable().getSqlIdentifier());
        LobHandler lobHandler = new DefaultLobHandler();

        return template.execute(query, (PreparedStatementCallback<InputStream>) ps -> {
            ps.setObject(1, fileId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return lobHandler.getBlobAsBinaryStream(rs, "fileData");
            }
            return null;
        });
    }

    @Override
    protected Class<BinaryFile> getEntityClass() {
        return BinaryFile.class;
    }
}