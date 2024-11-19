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
                SELECT '%s' as "@class", to_jsonb(t) as json FROM (select id,creationdate,updatedate,creationuser,updateuser,\s
                application,fileType, fileName,comment,size,convert_from(fileData, 'UTF8') as "data",fileinfos,associates,forapplication \s
                 from %s  WHERE id = :id) t""", getEntityClass().getName(), getTable().getSqlIdentifier());
        final Optional<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("id", id), getJsonRowMapper()).stream().findFirst();
        return result;
    }

    protected List<AdditionalBinaryFile> find(final String whereClause, final SqlParameterSource sqlParameterSource) {
        String sql = """
                SELECT '%s' as "@class",  to_jsonb(t) as json\s
                FROM (select id,creationdate,updatedate,creationuser,updateuser,\s
                application,fileType, fileName,comment,size,null as "data",fileinfos,associates,forapplication \s
                from %s\s""";
        if (whereClause != null) {
            sql += " WHERE " + whereClause;
        }
        sql += ") t";
        final String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        final List<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().query(query, sqlParameterSource, getJsonRowMapper());
        return result;
    }
    public List<String> getFileNamesForFiletype(final String fileType){
        if(fileType==null){
            return List.of();
        }
        final String sql = "SELECT fileName \n" +
                "from %s " +
                "where fileType=:fileType;" ;
        return  getNamedParameterJdbcTemplate().queryForList(
                String.format(sql, getTable().getSqlIdentifier()),
                Map.of("fileType", fileType) ,
                String.class);
    }

    public List<AdditionalBinaryFile> getAssociatedAdditionalFiles(final Set<UUID> dataIds) {
        return getAssociatedAdditionalFilesStream(dataIds).collect(Collectors.toList());
    }
    public Stream<AdditionalBinaryFile> getAssociatedAdditionalFilesStream(final Set<UUID> dataIds) {
        if (dataIds == null || dataIds.isEmpty()) {
            return Stream.of();
        }
        final String sql = """
                with associates as (
                \tselect id associateid, unnest(associates) auth
                 \tfrom %1$s t
                ),
                additionalFileAuthorizations as (
                \tselect  distinct\s
                \tassociateid,\s
                \t(jsonb_populate_recordset(null::%2$s."authorization", (auth).authorizations #> '{pem, associate}')) auth
                \tfrom associates
                ),
                aggregatedAdditionalFile as (
                \tselect distinct associateid  , array_agg(auth) auth
                \tfrom additionalFileAuthorizations
                \tgroup by associateid

                ),
                additionalFileId as (

                \tselect distinct associateid id\s
                \t\tfrom %1$s bf
                \t\tjoin aggregatedAdditionalFile aaf on aaf.associateid = bf.id
                \t\tjoin %2$s.referencevalue d on d."authorization" @> aaf.auth
                \twhere (d.id::uuid) in(:dataIds)
                \tunion\s
                \tselect id
                \t\tfrom %1$s bf
                \twhere forApplication
                \t)
                SELECT distinct '%3$s' as "@class", to_jsonb(t) as json FROM (select id,creationdate,updatedate,creationuser,updateuser,\s
                application,fileType, fileName,comment,size,convert_from(data, 'UTF8') as "data",fileinfos,associates,forapplication \s
                from additionalFileId join %1$s  using(id)) t""";
        final String query = String.format(
                sql,
                getTable().getSqlIdentifier(),
                getSchema().getSqlIdentifier(),
                getEntityClass().getName()
        );
        final Stream<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().queryForStream(
                query,
                new MapSqlParameterSource("dataIds", dataIds),
                getJsonRowMapper()
        );
        return result;
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
        return findByCriteriaStream(additionalFileSearchHelper).collect(Collectors.toList());
    }

    public Stream<AdditionalBinaryFile> findByCriteriaStream(final AdditionalFileSearchHelper additionalFileSearchHelper) {
        final String whereClause = additionalFileSearchHelper.buildWhereRequest();
        SqlParameterSource sqlParameterSource = additionalFileSearchHelper.getParamSource();
        if (sqlParameterSource == null) {
            sqlParameterSource = new MapSqlParameterSource();
        }
        String sql = """
                SELECT '%s' as "@class",  to_jsonb(t) as json\s
                FROM (select id,creationdate,updatedate,creationuser,updateuser,\s
                application,fileType, fileName,comment,size, convert_from(data, 'UTF8') as "data",fileinfos,associates, forapplication \s
                from %s\s""";
        if (whereClause != null && !"()".equals(whereClause) && !whereClause.isEmpty()) {
            sql += " WHERE " + whereClause;
        }
        sql += ") t";
        final String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        final Stream<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().queryForStream(query, sqlParameterSource, getJsonRowMapper());
        return result;
    }

    public List<UUID> deleteByCriteria(final AdditionalFileSearchHelper additionalFileSearchHelper) {
        final String whereClause = additionalFileSearchHelper.buildWhereRequest();
        SqlParameterSource sqlParameterSource = additionalFileSearchHelper.getParamSource();
        if (sqlParameterSource == null) {
            sqlParameterSource = new MapSqlParameterSource();
        }
        String sql = "delete from %1$s";
        if (whereClause != null && !"()".equals(whereClause) && !whereClause.isEmpty()) {
            sql += " WHERE " + whereClause+"\n";
        }else{
            return List.of();
        }
            sql += "returning  '%2$s' as \"@class\",  to_jsonb(" +
                    "(id,creationdate,updatedate,creationuser,updateuser, \n" +
                    "\"application\",fileType, fileName,comment,size, null,null,null,null" +
                    ")::%1$s) as json";

        final String query = String.format(sql, getTable().getSqlIdentifier(), getEntityClass().getName());
        List<UUID> result = getNamedParameterJdbcTemplate().query(query, sqlParameterSource, getJsonRowMapper())
                .stream()
                .map(AdditionalBinaryFile::getId)
                .collect(Collectors.toList());
        return result;
    }
}