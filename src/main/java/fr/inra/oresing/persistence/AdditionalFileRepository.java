package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.application.Application;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AdditionalFileRepository extends JsonTableInApplicationSchemaRepositoryTemplate<AdditionalBinaryFile> {

    public AdditionalFileRepository(final Application application) {
        super(application);
    }

    @Override
    public AdditionalBinaryFile findById(final UUID id) {
        return tryFindById(id).orElse(null);
    }

    @Override
    public Optional<AdditionalBinaryFile> tryFindById(final UUID id) {
        final SqlParameterSource parameters = new MapSqlParameterSource("id", id);
        return find("id = :id", parameters).stream().findFirst();
    }

    public Optional<AdditionalBinaryFile> tryFindByIdWithData(final UUID id) {
        Preconditions.checkArgument(id != null);
        final String query = String.format("""
                        SELECT '%s' as "@class", to_jsonb(t) as json 
                        FROM (
                            SELECT 
                                id,
                                creationdate,
                                updatedate,
                                creationuser,
                                updateuser,
                                application,
                                fileType,
                                fileName,
                                comment,
                                size,
                                convert_from(fileData, 'UTF8') as "data",
                                fileinfos,
                                associates,
                                forapplication
                            FROM %s
                            WHERE id = :id
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier()
        );
        return getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("id", id), getJsonRowMapper()).stream().findFirst();
    }

    @Override
    protected List<AdditionalBinaryFile> find(final String whereClause, final SqlParameterSource sqlParameterSource) {
        String query = String.format("""
                        SELECT '%1$s' as "@class", to_jsonb(t) as json
                        FROM (
                            SELECT 
                                id,
                                creationdate,
                                updatedate,
                                creationuser,
                                updateuser,
                                application,
                                fileType,
                                fileName,
                                comment,
                                size,
                                null as "data",
                                fileinfos,
                                associates,
                                forapplication
                            FROM %2$s
                            %3$s
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier(),
                whereClause != null ? "WHERE " + whereClause : ""
        );

        return getNamedParameterJdbcTemplate().query(query, sqlParameterSource, getJsonRowMapper());
    }

    public List<String> getFileNamesForFiletype(final String fileType) {
        if (fileType == null) {
            return List.of();
        }

        final String sql = String.format("""
                        SELECT fileName
                        FROM %1$s
                        WHERE fileType = :fileType
                        """,
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().queryForList(
                sql,
                Map.of("fileType", fileType),
                String.class
        );
    }


    public List<AdditionalBinaryFile> getAssociatedAdditionalFiles(final Set<UUID> dataIds) {
        return getAssociatedAdditionalFilesStream(dataIds).toList();
    }

    public Stream<AdditionalBinaryFile> getAssociatedAdditionalFilesStream(final Set<UUID> dataIds) {
        if (dataIds == null || dataIds.isEmpty()) {
            return Stream.of();
        }

        final String sql = String.format("""
                        WITH associates AS (
                            SELECT id AS associateid, unnest(associates) AS auth
                            FROM %1$s t
                        ),
                        additionalFileAuthorizations AS (
                            SELECT DISTINCT
                                associateid,
                                (jsonb_populate_recordset(null::%2$s."authorization", (auth).authorizations #> '{pem, associate}')) AS auth
                            FROM associates
                        ),
                        aggregatedAdditionalFile AS (
                            SELECT DISTINCT associateid, array_agg(auth) AS auth
                            FROM additionalFileAuthorizations
                            GROUP BY associateid
                        ),
                        additionalFileId AS (
                            SELECT DISTINCT associateid AS id
                            FROM %1$s bf
                            JOIN aggregatedAdditionalFile aaf ON aaf.associateid = bf.id
                            JOIN %2$s.referencevalue d ON d."authorization" @> aaf.auth
                            WHERE (d.id::uuid) IN (:dataIds)
                            UNION
                            SELECT id
                            FROM %1$s bf
                            WHERE forApplication
                        )
                        SELECT DISTINCT '%3$s' AS "@class", to_jsonb(t) AS json 
                        FROM (
                            SELECT 
                                id, creationdate, updatedate, creationuser, updateuser,
                                application, fileType, fileName, comment, size,
                                convert_from(data, 'UTF8') AS "data", fileinfos,
                                associates, forapplication
                            FROM additionalFileId 
                            JOIN %1$s USING (id)
                        ) t
                        """,
                getTable().getSqlIdentifier(),
                getSchema().getSqlIdentifier(),
                getEntityClass().getName()
        );

        return getNamedParameterJdbcTemplate().queryForStream(
                sql,
                new MapSqlParameterSource("dataIds", dataIds),
                getJsonRowMapper()
        );
    }

    @Override
    public SqlTable getTable() {
        return getSchema().additionalBinaryFile();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + " AS t (id,creationdate,updatedate,creationuser,updateuser,\n" +
                "application,fileType,fileName,comment,size,data,fileinfos,associates,forapplication)\n" +
                "select id,\n" +
                "COALESCE(creationdate,now()),\n" +
                "COALESCE(updatedate,now()),\n" +
                "creationuser,\n" +
                "updateuser,\n" +
                "application,\n" +
                "fileType,\n" +
                "fileName,\n" +
                "comment,\n" +
                "size,\n" +
                "data,\n" +
                "fileinfos,\n" +
                "associates,\n" +
                "forapplication\n" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", \n" +
                ":json::json) \n" +
                "ON CONFLICT (id)\n" +
                "DO UPDATE\n" +
                "set updatedate=current_timestamp,\n" +
                "updateuser=EXCLUDED.updateuser,\n" +
                "fileName=COALESCE(EXCLUDED.fileName, t.fileName),\n" +
                "comment=EXCLUDED.comment,\n" +
                "size=COALESCE(EXCLUDED.size, t.size),\n" +
                "data=COALESCE(EXCLUDED.data, t.data),\n" +
                "fileinfos=EXCLUDED.fileinfos,\n" +
                "associates=EXCLUDED.associates,\n" +
                "forapplication=EXCLUDED.forapplication\n" +
                "returning id;";
    }

    @Override
    protected Class<AdditionalBinaryFile> getEntityClass() {
        return AdditionalBinaryFile.class;
    }

    public List<AdditionalBinaryFile> findAllByFileType(final String additionalFileName) {
        SqlParameterSource sqlParameterSource = new MapSqlParameterSource("fileType", additionalFileName);
        return find("fileType=:fileType", sqlParameterSource);
    }

    public List<AdditionalBinaryFile> findByCriteria(final AdditionalFileSearchHelper additionalFileSearchHelper) {
        return findByCriteriaStream(additionalFileSearchHelper).toList();
    }

    public Stream<AdditionalBinaryFile> findByCriteriaStream(final AdditionalFileSearchHelper additionalFileSearchHelper) {
        final String whereClause = additionalFileSearchHelper.buildWhereRequest();
        SqlParameterSource sqlParameterSource = additionalFileSearchHelper.getParamSource();
        if (sqlParameterSource == null) {
            sqlParameterSource = new MapSqlParameterSource();
        }

        String sql = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT 
                                id, creationdate, updatedate, creationuser, updateuser,
                                application, fileType, fileName, comment, size,
                                convert_from(data, 'UTF8') AS "data", fileinfos, associates, forapplication
                            FROM %2$s
                            %3$s
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier(),
                (whereClause != null && !"()".equals(whereClause) && !whereClause.isEmpty())
                        ? "WHERE " + whereClause
                        : ""
        );

        return getNamedParameterJdbcTemplate().queryForStream(sql, sqlParameterSource, getJsonRowMapper());
    }


    public List<UUID> deleteByCriteria(final AdditionalFileSearchHelper additionalFileSearchHelper) {
        final String whereClause = additionalFileSearchHelper.buildWhereRequest();
        SqlParameterSource sqlParameterSource = additionalFileSearchHelper.getParamSource();
        if (sqlParameterSource == null) {
            sqlParameterSource = new MapSqlParameterSource();
        }

        if (whereClause == null || "()".equals(whereClause) || whereClause.isEmpty()) {
            return List.of();
        }

        String sql = String.format("""
                        DELETE FROM %1$s
                        WHERE %2$s
                        RETURNING '%3$s' AS "@class", 
                        to_jsonb((
                            id, creationdate, updatedate, creationuser, updateuser,
                            application, fileType, fileName, comment, size,
                            null, null, null, null
                        )::%1$s) AS json
                        """,
                getTable().getSqlIdentifier(),
                whereClause,
                getEntityClass().getName()
        );

        return getNamedParameterJdbcTemplate().query(sql, sqlParameterSource, getJsonRowMapper())
                .stream()
                .map(AdditionalBinaryFile::getId)
                .toList();
    }

}