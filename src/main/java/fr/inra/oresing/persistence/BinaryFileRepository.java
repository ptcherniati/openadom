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
     * Lightweight version of {@link #getReferencedBinaryFiles} : retourne
     * uniquement le {@link Set} des binaryfile ids qui ont au moins une
     * liaison sortante via {@code reference_reference} ( vers un autre
     * binaryfile ) . Pour le gating UI ( bouton publish/depublie + delete )
     * qui n'a besoin que d'un boolean par fichier .
     *
     * <p>Implementation : reuse la query lourde sous-jacente
     * ( {@link #getReferencedBinaryFiles} ) car les variantes EXISTS
     * forcaient le planner sur un Hash Right Semi Join 30s+ ( vs
     * 3s pour la query CTE-filtree apres index V6 ) . On garde la
     * meme query et on extrait juste les ids distincts cote Java .
     * Le vrai gain perf vient du cache memoire ( cf. {@link
     * fr.inra.oresing.rest.binaryFile.BinaryFileService#findBinaryFileIdsWithLinks }) ;
     * le payload reduit ( Set<UUID> au lieu de List<ReferencedBinaryFiles> )
     * ne sert qu'a alleger la JSON serialization cote handler .
     */
    public Set<UUID> findBinaryFileIdsWithLinks(String dataType, Set<UUID> binaryfileIds) {
        if (binaryfileIds == null || binaryfileIds.isEmpty()) return Set.of();
        return getReferencedBinaryFiles(dataType, binaryfileIds).stream()
                .map(ReferencedBinaryFiles::binaryFileId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public List<ReferencedBinaryFiles> getReferencedBinaryFiles(String dataType, Set<UUID> binaryfileIds) {
        // Pushes the (referencetype , binaryfile) selectivity into a CTE
        // before joining reference_reference + referencevalue2 . The
        // previous form started from reference_reference ( millions of
        // rows ) and used Memoize'd PK lookups - measured 3-5s for one
        // binaryfile on a 9.9M-row dataset . Filtering the source rows
        // first ( a handful of ids ) shrinks the joined input by orders
        // of magnitude .
        //
        // Result rows keep the previous shape ( one row per
        // (src.binaryfile , src.referencetype , rv2.binaryfile ,
        // rv2.referencetype) tuple , each carrying a single-element
        // array ) so downstream grouping in the caller is unchanged .
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
                join %1$s.reference_reference rr on rr.referencesby = src.id
                join %1$s.referencevalue rv2     on rv2.id          = rr.referenceid
                where rv2.binaryfile != src.binaryfile
                group by src.binaryfile, src.referencetype, rv2.binaryfile, rv2.referencetype"""
                .formatted(getSchema().getSqlIdentifier());

        return getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource()
                        .addValue("datatype",dataType)
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
                        .addValue("datatype", binaryFileDataset.getDatatype())
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