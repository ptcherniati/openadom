package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BinaryFileRepository extends JsonTableInApplicationSchemaRepositoryTemplate<BinaryFile> implements fr.inra.oresing.domain.repository.file.BinaryFileRepository {

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
        final String query = """
                        SELECT '%s' as "@class", to_jsonb(t) as json FROM (select id, application, name, comment, size, params from %s  WHERE application = :application::uuid
                        and (params->>'published' )::bool
                        and params->'binaryfiledataset'->'requiredauthorizations'= :requiredAuthorizations::jsonb) t"""
                .formatted(getEntityClass().getName(), getTable().getSqlIdentifier()
        );
        final Optional<BinaryFile> result = getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource()
                        .addValue("application", getApplication().getId())
                        .addValue("requiredAuthorizations",   getJsonRowMapper().toJson(binaryFileDataset.getRequiredAuthorizations())),
                getJsonRowMapper()
        ).stream().findFirst();
        return result;
    }

    public Optional<BinaryFile> tryFindByIdWithData(final UUID id) {
        Preconditions.checkArgument(id != null);
        final String query = """
        SELECT 
            '%1$s' as "@class", 
            to_jsonb(t) as json 
        FROM (select id, application, name, comment, size, convert_from(fileData, 'UTF8') as "fileData", 
        params from %2$s  
        WHERE id = :id) t"""
                .formatted(getEntityClass().getName(), getTable().getSqlIdentifier());
        final Optional<BinaryFile> result = getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("id", id), getJsonRowMapper()).stream().findFirst();
        return result;
    }

    protected List<BinaryFile> find(final String whereClause, final SqlParameterSource sqlParameterSource) {
        String sql = """
                SELECT 
                    '%1$s' as "@class",  
                    to_jsonb(t) as json 
                FROM (select id, application, name, comment, size, null as fileData, params from %2$s """;
        if (whereClause != null) {
            sql += "\nWHERE " + whereClause;
        }
        sql += ") t";
        final String query = sql.formatted(getEntityClass().getName(), getTable().getSqlIdentifier());
        final List<BinaryFile> result = getNamedParameterJdbcTemplate().query(query, sqlParameterSource, getJsonRowMapper());
        return result;
    }

    @Override
    public SqlTable getTable() {
        return getSchema().binaryFile();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, name, comment, size, fileData, params) " +
                "SELECT " +
                "   id, application, name, comment, size, fileData, " +
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
                "   fileData=CASE WHEN EXCLUDED.fileData IS NULL THEN " + getTable().getSqlIdentifier() + ".fileData ELSE EXCLUDED.fileData END, " +
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

    public List<BinaryFile> findByBinaryFileDataset(final String data, final BinaryFileDataset binaryFileDataset, final boolean overlap) {
        final MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource();
        final List<String> where = new LinkedList<>();
        if (Optional.ofNullable(binaryFileDataset).map(bfd -> bfd.getRequiredAuthorizations()).isPresent()) {
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
            if (Optional.ofNullable(binaryFileDataset).map(bfd -> bfd.getFrom()).isPresent()) {
                final String from = binaryFileDataset.getFrom();
                final String t = "params #> '{\"binaryfiledataset\", \"from\"}'  @@ ('$ == \"'||:from||'\"')::jsonpath";
                mapSqlParameterSource.addValue("from", from);
                where.add(t);
            }
            if (Optional.ofNullable(binaryFileDataset).map(bfd -> bfd.getTo()).isPresent()) {
                final String to = binaryFileDataset.getTo();
                final String t = "params #> '{\"binaryfiledataset\", \"to\"}'  @@ ('$ == \"'||:to||'\"')::jsonpath";
                mapSqlParameterSource.addValue("to", to);
                where.add(t);
            }
        }
        if (where.isEmpty()) {
            return new LinkedList<>();
        }
        final String t = "params #> '{\"binaryfiledataset\", \"datatype\"}'  @@ ('$ == \"'||:data||'\"')::jsonpath";
        where.add(t);
        mapSqlParameterSource.addValue("data", data);
        return find(String.join(" AND ", where), mapSqlParameterSource);
    }

    @Override
    protected Class<BinaryFile> getEntityClass() {
        return BinaryFile.class;
    }
}