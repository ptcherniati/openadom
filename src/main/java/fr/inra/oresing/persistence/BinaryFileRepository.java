package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.BinaryFileDataset;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BinaryFileRepository extends JsonTableInApplicationSchemaRepositoryTemplate<BinaryFile> {

    public BinaryFileRepository(Application application) {
        super(application);
    }

    @Override
    public BinaryFile findById(UUID id) {
        return tryFindById(id).orElse(null);
    }

    @Override
    public Optional<BinaryFile> tryFindById(UUID id) {
        SqlParameterSource parameters = new MapSqlParameterSource("id", id);
        return find("id = :id", parameters).stream().findFirst();
    }

    public Optional<BinaryFile> findPublishedVersions(BinaryFileDataset binaryFileDataset) {
        Preconditions.checkArgument(binaryFileDataset != null);
        String query = String.format("SELECT '%s' as \"@class\", " +
                        "to_jsonb(t) as json " +
                        "FROM (select id, application, name, comment, size, params from %s  " +
                        "WHERE application = :application::uuid\n" +
                        "and (params->>'published' )::bool\n" +
                        "and params->'binaryfiledataset'->'requiredAuthorizations'= :requiredAuthorizations::jsonb) t",
                getEntityClass().getName(), getTable().getSqlIdentifier()
        );
        Optional<BinaryFile> result = getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource()
                        .addValue("application", getApplication().getId())
                        .addValue("requiredAuthorizations",   getJsonRowMapper().toJson(binaryFileDataset.getRequiredAuthorizations())),
                getJsonRowMapper()
        ).stream().findFirst();
        return result;
    }

    public Optional<BinaryFile> tryFindByIdWithData(UUID id) {
        Preconditions.checkArgument(id != null);
        String query = String.format("SELECT '%s' as \"@class\", to_jsonb(t) as json FROM (select id, application, name, comment, size, convert_from(data, 'UTF8') as \"data\", params from %s  WHERE id = :id) t", getEntityClass().getName(), getTable().getSqlIdentifier());
        Optional<BinaryFile> result = getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("id", id), getJsonRowMapper()).stream().findFirst();
        return result;
    }

    protected List<BinaryFile> find(String whereClause, SqlParameterSource sqlParameterSource) {
        String sql = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM (select id, application, name, comment, size, null as \"data\", params from %s ";
        if (whereClause != null) {
            sql += " WHERE " + whereClause;
        }
        sql += ") t";
        String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        List<BinaryFile> result = getNamedParameterJdbcTemplate().query(query, sqlParameterSource, getJsonRowMapper());
        return result;
    }

    @Override
    public SqlTable getTable() {
        return getSchema().binaryFile();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, name, comment, size, data, params) " +
                "SELECT " +
                "   id, application, name, comment, size, data, " +
                "jsonb_set(jsonb_set((case when params is null then '{}' else params end ),\n" +
                "\t'{createdate}',('\"' ||CURRENT_TIMESTAMP::text ||'\"')::jsonb),\n" +
                "\t'{create_user}' , ('\"' ||current_role::text ||'\"')::jsonb)" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                + " ON CONFLICT (id) " +
                "DO UPDATE " +
                "SET " +
                "   updateDate=current_timestamp, " +
                "   application=EXCLUDED.application, " +
                "   comment=EXCLUDED.comment, " +
                "   name=EXCLUDED.name, " +
                "   size=EXCLUDED.size, " +
                "   data=CASE WHEN EXCLUDED.data IS NULL THEN " + getTable().getSqlIdentifier() + ".data ELSE EXCLUDED.data END, " +
                "   params=case \n" +
                "\t\twhen EXCLUDED.params is not null and  not((EXCLUDED.params->>'published')::boolean )\n" +
                "\t\t\tthen EXCLUDED.params\n" +
                "\t\telse \n" +
                "\t\t\tjsonb_set(jsonb_set((case when EXCLUDED.params is null then '{}' else EXCLUDED.params end),\n" +
                "\t\t\t\t'{publisheddate}',('\"' ||CURRENT_TIMESTAMP::text ||'\"')::jsonb),\n" +
                "\t\t\t\t'{publisheduser}' , ('\"' ||current_role::text ||'\"')::jsonb)\n" +
                "\t\tend"
                + " RETURNING id";
    }

    public List<BinaryFile> findByBinaryFileDataset(String datatype, BinaryFileDataset binaryFileDataset, boolean overlap) {
        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource();
        List<String> where = new LinkedList<>();
        if (Optional.ofNullable(binaryFileDataset).map(bfd -> bfd.getRequiredAuthorizations()).isPresent()) {
            for (Map.Entry<String, Ltree> entry : binaryFileDataset.getRequiredAuthorizations().entrySet()) {
                String t = String.format("coalesce(params #> '{\"binaryfiledataset\", \"requiredAuthorizations\", \"%1$s\"}', params #> '{\"binaryfiledataset\", \"requiredauthorizations\", \"%1$s\"}') @@ ('$ == \"'||:%1$s||'\"')::jsonpath", entry.getKey());
                mapSqlParameterSource.addValue(entry.getKey(), entry.getValue().getSql());
                where.add(t);
            }
        }
        if (overlap) {
            where.add("params  #> '{\"binaryfiledataset\", \"datatype\"}' @@('$ == \"" + datatype + "\"')");
            where.add("params @@ ('$.published==true')");
            String t = "(tsrange(\n" +
                    "\tcoalesce((params #>> '{\"binaryfiledataset\", \"from\"}'), '-infinity')::timestamp,\n" +
                    "\tcoalesce((params #>> '{\"binaryfiledataset\", \"to\"}'), 'infinity')::timestamp\n" +
                    "\t) && tsrange(coalesce(:from::timestamp, '-infinity')::timestamp, coalesce(:to::timestamp, 'infinity')::timestamp))\n" +
                    "\tand\n" +
                    "(tsrange(\n" +
                    "\tcoalesce((params #>> '{\"binaryfiledataset\", \"from\"}'), '-infinity')::timestamp,\n" +
                    "\tcoalesce((params #>> '{\"binaryfiledataset\", \"to\"}'), 'infinity')::timestamp\n" +
                    "\t) != tsrange(coalesce(:from::timestamp, '-infinity')::timestamp, coalesce(:to::timestamp, 'infinity')::timestamp))\n";
            where.add(t);
            mapSqlParameterSource.addValue("from", binaryFileDataset.getFrom());
            mapSqlParameterSource.addValue("to", binaryFileDataset.getTo());
        } else {
            if (Optional.ofNullable(binaryFileDataset).map(bfd -> bfd.getFrom()).isPresent()) {
                String from = binaryFileDataset.getFrom();
                String t = "params #> '{\"binaryfiledataset\", \"from\"}'  @@ ('$ == \"'||:from||'\"')::jsonpath";
                mapSqlParameterSource.addValue("from", from);
                where.add(t);
            }
            if (Optional.ofNullable(binaryFileDataset).map(bfd -> bfd.getTo()).isPresent()) {
                String to = binaryFileDataset.getTo();
                String t = "params #> '{\"binaryfiledataset\", \"to\"}'  @@ ('$ == \"'||:to||'\"')::jsonpath";
                mapSqlParameterSource.addValue("to", to);
                where.add(t);
            }
        }
        if (where.isEmpty()) {
            return new LinkedList<>();
        }
        String t = "params #> '{\"binaryfiledataset\", \"datatype\"}'  @@ ('$ == \"'||:datatype||'\"')::jsonpath";
        where.add(t);
        mapSqlParameterSource.addValue("datatype", datatype);
        return find(where.stream().collect(Collectors.joining(" AND ")), mapSqlParameterSource);
    }

    @Override
    protected Class<BinaryFile> getEntityClass() {
        return BinaryFile.class;
    }
}