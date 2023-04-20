package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AdditionalFileRepository extends JsonTableInApplicationSchemaRepositoryTemplate<AdditionalBinaryFile> {

    public AdditionalFileRepository(Application application) {
        super(application);
    }

    @Override
    public AdditionalBinaryFile findById(UUID id) {
        return tryFindById(id).orElse(null);
    }

    @Override
    public Optional<AdditionalBinaryFile> tryFindById(UUID id) {
        SqlParameterSource parameters = new MapSqlParameterSource("id", id);
        return find("id = :id", parameters).stream().findFirst();
    }

    public Optional<AdditionalBinaryFile> tryFindByIdWithData(UUID id) {
        Preconditions.checkArgument(id != null);
        String query = String.format("SELECT '%s' as \"@class\", to_jsonb(t) as json " +
                "FROM (select id,creationdate,updatedate,creationuser,updateuser, \n" +
                "application,fileType, fileName,comment,size,convert_from(data, 'UTF8') as \"data\",fileinfos,associates  \n" +
                " from %s  WHERE id = :id) t", getEntityClass().getName(), getTable().getSqlIdentifier());
        Optional<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("id", id), getJsonRowMapper()).stream().findFirst();
        return result;
    }

    protected List<AdditionalBinaryFile> find(String whereClause, SqlParameterSource sqlParameterSource) {
        String sql = "SELECT '%s' as \"@class\",  to_jsonb(t) as json \n" +
                "FROM (select id,creationdate,updatedate,creationuser,updateuser, \n" +
                "application,fileType, fileName,comment,size,null as \"data\",fileinfos,associates  \n" +
                "from %s ";
        if (whereClause != null) {
            sql += " WHERE " + whereClause;
        }
        sql += ") t";
        String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        List<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().query(query, sqlParameterSource, getJsonRowMapper());
        return result;
    }

    public List<AdditionalBinaryFile> getAssociatedAdditionalFiles(Set<UUID> dataIds) {
        if (dataIds == null || dataIds.isEmpty()) {
            return List.of();
        }
        String sql = "with associates as (\n" +
                "\tselect id associateid, unnest(associates) auth\n" +
                " \tfrom %1$s t\n" +
                "),\n" +
                "additionalFileAuthorizations as (\n" +
                "\tselect  distinct \n" +
                "\tassociateid, \n" +
                "\t(jsonb_populate_recordset(null::%2$s.\"authorization\", (auth).authorizations #> '{pem, associate}')) auth\n" +
                "\tfrom associates\n" +
                "),\n" +
                "aggregatedAdditionalFile as (\n" +
                "\tselect distinct associateid  , array_agg(auth) auth\n" +
                "\tfrom additionalFileAuthorizations\n" +
                "\tgroup by associateid\n" +
                "\n" +
                "),\n" +
                "additionalFileId as (\n" +
                "\n" +
                "\tselect distinct associateid id \n" +
                "\t\tfrom %1$s bf\n" +
                "\t\tjoin aggregatedAdditionalFile aaf on aaf.associateid = bf.id\n" +
                "\t\tjoin %2$s.\"data\" d on d.\"authorization\" @> aaf.auth\n" +
                "\twhere (d.rowid::uuid) in(:dataIds)\n" +
                "\t)\n" +
                "SELECT '%3$s' as \"@class\", to_jsonb(t) as json " +
                "FROM (select id,creationdate,updatedate,creationuser,updateuser, \n" +
                "application,fileType, fileName,comment,size,convert_from(data, 'UTF8') as \"data\",fileinfos,associates  \n" +
                "from additionalFileId join %1$s  using(id)) t";
        String query = String.format(
                sql,
                getTable().getSqlIdentifier(),
                getSchema().getSqlIdentifier(),
                getEntityClass().getName()
        );
        List<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().query(
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
                "application,fileType,fileName,comment,size,data,fileinfos,associates)\n" +
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
                "associates\n" +
                "FROM json_populate_recordset(NULL::monsore.additionalBinaryFile, \n" +
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
                "associates=EXCLUDED.associates\n" +
                "returning id;";
    }

    @Override
    protected Class<AdditionalBinaryFile> getEntityClass() {
        return AdditionalBinaryFile.class;
    }

    public List<AdditionalBinaryFile> findAllByFileType(String additionalFileName) {
        final SqlParameterSource sqlParameterSource = new MapSqlParameterSource("fileType", additionalFileName);
        return find("fileType=:fileType", sqlParameterSource);
    }

    public List<AdditionalBinaryFile> findByCriteria(AdditionalFileSearchHelper additionalFileSearchHelper) {
        String whereClause = additionalFileSearchHelper.buildWhereRequest();
        SqlParameterSource sqlParameterSource = additionalFileSearchHelper.getParamSource();
        ;
        if (sqlParameterSource == null) {
            sqlParameterSource = new MapSqlParameterSource();
        }
        String sql = "SELECT '%s' as \"@class\",  to_jsonb(t) as json \n" +
                "FROM (select id,creationdate,updatedate,creationuser,updateuser, \n" +
                "application,fileType, fileName,comment,size, convert_from(data, 'UTF8') as \"data\",fileinfos,associates  \n" +
                "from %s ";
        if (whereClause != null && !"()".equals(whereClause) && !"".equals(whereClause)) {
            sql += " WHERE " + whereClause;
        }
        sql += ") t";
        String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        List<AdditionalBinaryFile> result = getNamedParameterJdbcTemplate().query(query, sqlParameterSource, getJsonRowMapper());
        return result;
    }
}