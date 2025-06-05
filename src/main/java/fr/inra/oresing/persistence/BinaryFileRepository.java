package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
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
    public Optional<BinaryFile> findPublishedVersions(final BinaryFileDataset binaryFileDataset) {
        Preconditions.checkArgument(binaryFileDataset != null);

        final String query = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT id, application, name, comment, size, params
                            FROM %2$s
                            WHERE application = :application::uuid
                              AND (params->>'published')::bool
                              AND params->'binaryfiledataset'->'requiredauthorizations' = :requiredAuthorizations::jsonb
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource()
                        .addValue("application", getApplication().getId())
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
                    \tcoalesce((params #>> '{"binaryfiledataset", "from"}'), '-infinity')::timestamp,
                    \tcoalesce((params #>> '{"binaryfiledataset", "to"}'), 'infinity')::timestamp
                    \t) && tsrange(coalesce(:from::timestamp, '-infinity')::timestamp, coalesce(:to::timestamp, 'infinity')::timestamp))
                    \tand
                    (tsrange(
                    \tcoalesce((params #>> '{"binaryfiledataset", "from"}'), '-infinity')::timestamp,
                    \tcoalesce((params #>> '{"binaryfiledataset", "to"}'), 'infinity')::timestamp
                    \t) != tsrange(coalesce(:from::timestamp, '-infinity')::timestamp, coalesce(:to::timestamp, 'infinity')::timestamp))
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

    public void storeFileContent(UUID fileId, InputStream inputStream, long fileSize) {
        if (fileSize == 0L) {
            return;
        }
        String query = "UPDATE %s SET fileData = ?, size = ? WHERE id = ?::uuid".formatted(getTable().getSqlIdentifier());

        jdbcTemplate.execute(query, (PreparedStatementCallback<Void>) ps -> {
            ps.setBinaryStream(1, inputStream, fileSize);
            ps.setLong(2, fileSize);
            ps.setObject(3, fileId.toString());
            ps.executeUpdate();
            return null;
        });
    }


    public InputStream retrieveFileContentAsInputStream(UUID fileId) {
        String query = "SELECT fileData FROM %s WHERE id = ?::uuid".formatted(getTable().getSqlIdentifier());
        LobHandler lobHandler = new DefaultLobHandler();

        return jdbcTemplate.execute(query, (PreparedStatementCallback<InputStream>) ps -> {
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