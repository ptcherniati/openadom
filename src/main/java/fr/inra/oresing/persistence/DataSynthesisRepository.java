package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.chart.OreSiSynthesis;
import org.assertj.core.util.Strings;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataSynthesisRepository extends JsonTableInApplicationSchemaRepositoryTemplate<OreSiSynthesis> {
    public static final String BUILD_SYNTHESIS_SQL = "with\n" +
            "     vars (agregation, variable, \"datatype\", gap) as (\n" +
            "         values  %2$s\n" +
            "\n" +
            "     ),\n" +
            "datas as (\n" +
            "    select\n" +
            "        application,\n" +
            "        \"datatype\",\n" +
            "        lower((\"authorization\").timescope) mindate,\n" +
            "        upper((\"authorization\").timescope) maxdate,\n" +
            "        (\"authorization\").requiredAuthorizations requiredAuthorizations,\n" +
            "        jsonb_object_agg(datavalues) datavalues\n" +
            "\tfrom %1$s.\"data\"\n" +
            "    group by application, \"datatype\", (\"authorization\").requiredAuthorizations, (\"authorization\").timescope, rowid\n" +
            ") ,\n" +
            "infos as (\n" +
            "    select application,\n" +
            "           vars.\"datatype\",\n" +
            "           vars.variable[1]                                                                                                                                 variable,\n" +
            "           datavalues #>> vars.agregation                                                                                                                   aggregation,\n" +
            "           mindate,\n" +
            "           max(maxdate)\n" +
            "           over (partition by \"application\", vars.\"datatype\", vars.variable[1], requiredAuthorizations,datavalues #>> vars.agregation,mindate )             maxdate,\n" +
            "           requiredAuthorizations                                                                                                                           requiredAuthorizations,\n" +
            "           dense_rank()\n" +
            "           over (partition by \"application\", vars.\"datatype\", vars.variable[1], requiredAuthorizations,datavalues #>> vars.agregation order by mindate ) as \"range\",\n" +
            "           case\n" +
            "               when vars.gap = interval '0' then\n" +
            "                       true and ((mindate - lag(maxdate)\n" +
            "                                            over (partition by application, vars.\"datatype\", vars.variable[1], requiredAuthorizations, datavalues #>> vars.\"agregation\" order by mindate, maxdate) <\n" +
            "                                  maxdate - mindate))\n" +
            "               else\n" +
            "                       true and ((mindate - lag(maxdate)\n" +
            "                                            over (partition by application, vars.\"datatype\", vars.variable[1], requiredAuthorizations, datavalues #>> vars.\"agregation\" order by mindate, maxdate)) <\n" +
            "                                 vars.gap)\n" +
            "               end                                                                                                                                       as continuous\n" +
            "    from datas\n" +
            "             join vars on ((datavalues #>> vars.variable) != '')\n" +
            "        and \"datas\".\"datatype\" = vars.\"datatype\"\n" +
            "),\n" +
            "infos_agg as (\n" +
            "    select application,\n" +
            "           \"datatype\",\n" +
            "           variable,\n" +
            "           requiredAuthorizations,\n" +
            "           aggregation,\n" +
            "           range,\n" +
            "           mindate,\n" +
            "           maxdate,\n" +
            "           bool_and(continuous) continuous\n" +
            "\n" +
            "    from infos\n" +
            "    group by application, \"datatype\", variable, requiredAuthorizations, aggregation, range, mindate, maxdate\n" +
            "),\n" +
            "     synthesis as (\n" +
            "         select application,\n" +
            "                \"datatype\",\n" +
            "                variable,\n" +
            "                requiredAuthorizations,\n" +
            "                aggregation,\n" +
            "                mindate,\n" +
            "                maxdate,\n" +
            "                sum(\n" +
            "                case\n" +
            "                    when continuous\n" +
            "                        then 0\n" +
            "                    else 1\n" +
            "                    end\n" +
            "                    )\n" +
            "                over (partition by application, \"datatype\", variable, requiredAuthorizations, aggregation order by mindate) timerange\n" +
            "         from infos_agg\n" +
            "),\n" +
            "result as (\n" +
            "   select\n" +
            "                 application,\n" +
            "                 \"datatype\",\n" +
            "                 variable,\n" +
            "                 requiredAuthorizations,\n" +
            "                 aggregation,\n" +
            "                 min(mindate) \"mindate\",\n" +
            "                 max(maxdate) \"maxdate\"\n" +
            "from synthesis\n" +
            "group by  application, \"datatype\", variable, requiredAuthorizations, aggregation, timerange" +
            ")\n"+
            "select\n" +
            "        '%3$s' as \"@class\",\n" +
            "       to_jsonb((gen_random_uuid(), now(),\n" +
            "                 application,\n" +
            "                 \"datatype\",\n" +
            "                 variable,\n" +
            "                 requiredAuthorizations,\n" +
            "                 aggregation,\n" +
            "                 array_agg(tsrange(mindate,maxdate))" +
            "           )::%1$s.oresisynthesis) as json\n" +
            "from result\n" +
            "group by application, \"datatype\", variable, requiredAuthorizations, aggregation";
    public static final String BUILD_GENERIC_SYNTHESIS_SQL = " with\n" +
            "    vars ( \"datatype\") as (\n" +
            "         values  %2$s\n" +
            "\n" +
            "\n" +
            "    ),\n" +
            "    datas as (select application,\n" +
            "                       \"datatype\",\n" +
            "                       lower((\"authorization\").timescope)       mindate,\n" +
            "                       upper((\"authorization\").timescope)       maxdate,\n" +
            "                       (\"authorization\").requiredAuthorizations requiredAuthorizations,\n" +
            "                       jsonb_object_agg(datavalues)             datavalues\n" +
            "\tfrom %1$s.\"data\"\n" +
            "                group by application, \"datatype\", (\"authorization\").requiredAuthorizations, (\"authorization\").timescope,\n" +
            "                         rowid\n" +
            " ),\n" +
            "infos as (\n" +
            " select application,\n" +
            "        vars.\"datatype\",\n" +
            "        mindate,\n" +
            "        max(maxdate)\n" +
            "        over (partition by \"application\", vars.\"datatype\",mindate )             maxdate,\n" +
            "        requiredAuthorizations                                                                                                                           requiredAuthorizations,\n" +
            "        dense_rank()\n" +
            "        over (partition by \"application\", vars.\"datatype\"  order by mindate ) as \"range\",\n" +
            "        true and ((mindate - lag(maxdate)\n" +
            "                                         over (partition by application, vars.\"datatype\" order by mindate, maxdate) <\n" +
            "                               maxdate - mindate))                                                                                                                                     as continuous\n" +
            " from datas\n" +
            "join vars on \"datas\".\"datatype\" = vars.\"datatype\"),\n" +
            "infos_agg as (\n" +
            "    select application,\n" +
            "           \"datatype\",\n" +
            "           requiredAuthorizations,\n" +
            "           range,\n" +
            "           mindate,\n" +
            "           maxdate,\n" +
            "           bool_and(continuous) continuous\n" +
            "\n" +
            "    from infos\n" +
            "    group by application, \"datatype\", requiredAuthorizations, range, mindate, maxdate\n" +
            "),\n" +
            "    synthesis as (\n" +
            "        select application,\n" +
            "               \"datatype\",\n" +
            "               requiredAuthorizations,\n" +
            "               mindate,\n" +
            "               maxdate,\n" +
            "               sum(\n" +
            "               case\n" +
            "                   when continuous\n" +
            "                       then 0\n" +
            "                   else 1\n" +
            "                   end\n" +
            "                   )\n" +
            "               over (partition by application, \"datatype\", requiredAuthorizations order by mindate) timerange\n" +
            "        from infos_agg\n" +
            "    ),\n" +
            "    result as (\n" +
            "        select\n" +
            "            application,\n" +
            "            \"datatype\",\n" +
            "            null variable,\n" +
            "            requiredAuthorizations,\n" +
            "            null aggregation,\n" +
            "            min(mindate) \"mindate\",\n" +
            "            max(maxdate) \"maxdate\"\n" +
            "        from synthesis\n" +
            "        group by  application, \"datatype\", variable, requiredAuthorizations, aggregation, timerange)\n" +
            "select\n" +
            "        '%3$s' as \"@class\",\n" +
            "    to_jsonb((gen_random_uuid(), now(),\n" +
            "              application,\n" +
            "              \"datatype\",\n" +
            "              variable,\n" +
            "              requiredAuthorizations,\n" +
            "              aggregation,\n" +
            "              array_agg(tsrange(mindate,maxdate))           )::%1$s.oresisynthesis) as json\n" +
            "from result\n" +
            "group by application, \"datatype\", variable, requiredAuthorizations, aggregation";
    public static final String SELECT_SYNTHESIS_BY_APPLICATION_AND_DATATYPE = "SELECT '%s' as \"@class\", to_jsonb(t) as json FROM (" +
            "select id, updatedate, application, \"datatype\", variable, requiredauthorizations, aggregation, ranges " +
            "from %s  " +
            "WHERE \"application\" = :application::uuid and \"datatype\" = :datatype) t";
    public static final String SELECT_SYNTHESIS_BY_APPLICATION_DATATYPE_AND_VARIABLE = "SELECT '%s' as \"@class\", to_jsonb(t) as json FROM (" +
            "select id, updatedate, application, \"datatype\", variable, requiredauthorizations, aggregation, ranges " +
            "from %s  " +
            "WHERE \"application\" = :application::uuid and \"datatype\" = :datatype and \"variable\" = :variable) t";
    public static final String SYNTHESIS_UPSERT = "INSERT INTO %1$s (id, application, datatype, variable, requiredauthorizations, aggregation, ranges) \n" +
            "SELECT  \n" +
            "id, application, datatype, variable, requiredauthorizations, aggregation, ranges\n" +
            "FROM json_populate_recordset(NULL::%1$s, :json::json) \n "
            + " ON CONFLICT (id) " +
            "DO UPDATE " +
            "SET " +
            "  updateDate=current_timestamp \n"
            + " RETURNING id";
    public static final String SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE = "DELETE FROM %s\n" +
            "  WHERE \"application\" = :application::uuid and \"datatype\" = :datatype";
    public static final String SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE1 = SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE;
    public static final String SYNTHESIS_DELETE_BY_APPLICATION_DATATYPE_AND_VARIABLE = "DELETE FROM %s\n" +
            "  WHERE \"application\" = :application::uuid and \"datatype\" = :datatype and \"variable\" = :variable";

    public DataSynthesisRepository(Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().synthesis();
    }


    public List<OreSiSynthesis> buildSynthesis(String varsSql, boolean hasChartDescription) {
        if (Strings.isNullOrEmpty(varsSql)) {
            return new LinkedList<>();
        }
        String query = String.format(
                hasChartDescription?BUILD_SYNTHESIS_SQL:BUILD_GENERIC_SYNTHESIS_SQL,
                getTable().getSchema().getSqlIdentifier(),
                varsSql,
                getEntityClass().getName(),
                getTable().getSqlIdentifier());
        return getNamedParameterJdbcTemplate().query(query, getJsonRowMapper());
    }

    @Override
    protected String getUpsertQuery() {
        return String.format(SYNTHESIS_UPSERT,
                getTable().getSqlIdentifier()
        );
    }

    public int removeSynthesisByApplicationDatatype(UUID applicationId, String dataType) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType));
        String query =String.format(SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE1, getTable().getSqlIdentifier());
        return getNamedParameterJdbcTemplate().update(query, ImmutableMap.of("application", applicationId, "datatype", dataType));
    }

    public int removeSynthesisByApplicationDatatypeAndVariable(UUID applicationId, String dataType, String variable) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType) && !Strings.isNullOrEmpty(variable));
        String query = String.format(SYNTHESIS_DELETE_BY_APPLICATION_DATATYPE_AND_VARIABLE,
getTable().getSqlIdentifier()
                );
        return getNamedParameterJdbcTemplate().update(query, ImmutableMap.of("application", applicationId, "datatype", dataType, "variable", variable));
    }

    public List<OreSiSynthesis> selectSynthesisDatatype(UUID applicationId, String dataType) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType));
        String query = String.format(SELECT_SYNTHESIS_BY_APPLICATION_AND_DATATYPE, getEntityClass().getName(), getTable().getSqlIdentifier());
        List<OreSiSynthesis> result = getNamedParameterJdbcTemplate().query(query,
                new MapSqlParameterSource(ImmutableMap.of("application", applicationId, "datatype", dataType)),
                getJsonRowMapper());
        return result;
    }

    public List<OreSiSynthesis> selectSynthesisDatatypeAndVariable(UUID applicationId, String dataType, String variable) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType) && !Strings.isNullOrEmpty(variable));
        String query = String.format(SELECT_SYNTHESIS_BY_APPLICATION_DATATYPE_AND_VARIABLE, getEntityClass().getName(), getTable().getSqlIdentifier());

        List<OreSiSynthesis> result = getNamedParameterJdbcTemplate().query(query,
                new MapSqlParameterSource(ImmutableMap.of("application", applicationId, "datatype", dataType, "variable", variable)),
                getJsonRowMapper());
        return result;
    }

    @Override
    protected Class<OreSiSynthesis> getEntityClass() {
        return OreSiSynthesis.class;
    }
}