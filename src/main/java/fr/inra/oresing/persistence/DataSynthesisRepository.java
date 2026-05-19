package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.repository.synthesis.SynthesisRepository;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataSynthesisRepository extends JsonTableInApplicationSchemaRepositoryTemplate<OreSiSynthesis>
        implements SynthesisRepository {
    public static final String BUILD_SYNTHESIS_SQL = """
            with
                 vars (agregation, variable, "datatype", gap) as (
                     values  %2$s
                 ),
            datas as (
                select
                    application,
                    "datatype",
                    lower(("authorization").timescope) mindate,
                    upper(("authorization").timescope) maxdate,
                    ("authorization").requiredAuthorizations requiredAuthorizations,
                    jsonb_object_agg(datavalues) datavalues
            \tfrom %1$s."referencevalue"
                group by application, "datatype", ("authorization").requiredAuthorizations, ("authorization").timescope, rowid
            ) ,
            infos as (
                select application,
                       vars."datatype",
                       vars.variable[1]                                                                                                                                 variable,
                       datavalues #>> vars.agregation                                                                                                                   aggregation,
                       mindate,
                       max(maxdate)
                       over (partition by "application", vars."datatype", vars.variable[1], requiredAuthorizations,datavalues #>> vars.agregation,mindate )             maxdate,
                       requiredAuthorizations                                                                                                                           requiredAuthorizations,
                       dense_rank()
                       over (partition by "application", vars."datatype", vars.variable[1], requiredAuthorizations,datavalues #>> vars.agregation order by mindate ) as "range",
                       case
                           when vars.gap = interval '0' then
                                   true and ((mindate - lag(maxdate)
                                                        over (partition by application, vars."datatype", vars.variable[1], requiredAuthorizations, datavalues #>> vars."agregation" order by mindate, maxdate) <
                                              maxdate - mindate))
                           else
                                   true and ((mindate - lag(maxdate)
                                                        over (partition by application, vars."datatype", vars.variable[1], requiredAuthorizations, datavalues #>> vars."agregation" order by mindate, maxdate)) <
                                             vars.gap)
                           end                                                                                                                                       as continuous
                from datas
                         join vars on ((datavalues #>> vars.variable) != '')
                    and "datas"."datatype" = vars."datatype"
            ),
            infos_agg as (
                select application,
                       "datatype",
                       variable,
                       requiredAuthorizations,
                       aggregation,
                       range,
                       mindate,
                       maxdate,
                       bool_and(continuous) continuous
            
                from infos
                group by application, "datatype", variable, requiredAuthorizations, aggregation, range, mindate, maxdate
            ),
                 synthesis as (
                     select application,
                            "datatype",
                            variable,
                            requiredAuthorizations,
                            aggregation,
                            mindate,
                            maxdate,
                            sum(
                            case
                                when continuous
                                    then 0
                                else 1
                                end
                                )
                            over (partition by application, "datatype", variable, requiredAuthorizations, aggregation order by mindate) timerange
                     from infos_agg
            ),
            result as (
               select
                             application,
                             "datatype",
                             variable,
                             requiredAuthorizations,
                             aggregation,
                             min(mindate) "mindate",
                             max(maxdate) "maxdate"
            from synthesis
            group by  application, "datatype", variable, requiredAuthorizations, aggregation, timerange)
            select
                    '%3$s' as "@class",
                   to_jsonb((gen_random_uuid(), now(),
                             application,
                             "datatype",
                             variable,
                             requiredAuthorizations,
                             aggregation,
                             array_agg(tsrange(mindate,maxdate))           )::%1$s.oresisynthesis) as json
            from result
            group by application, "datatype", variable, requiredAuthorizations, aggregation""";
    public static final String BUILD_GENERIC_SYNTHESIS_SQL =
            """
                    with
                        vars ( "datatype") as (
                     values  %2$s
                    ),
                        datas as (select application,
                                         "referencetype",
                                         lower(("authorization").timescope)       mindate,
                                         upper(("authorization").timescope)       maxdate,
                                         ("authorization").requiredAuthorizations requiredAuthorizations
                                  from %1$s."referencevalue"
                                  where "referencetype" in (select "datatype" from vars)
                                  group by application, "referencetype", ("authorization").requiredAuthorizations, ("authorization").timescope,
                                           hierarchicalkey, linehierarchicalkeypatterncolumnname
                        ),
                        infos as (
                            select application,
                                   vars."datatype",
                                   mindate,
                                   max(maxdate)
                                   over (partition by "application", vars."datatype",mindate )             maxdate,
                                   requiredAuthorizations                                                                                                                           requiredAuthorizations,
                                   dense_rank()
                                   over (partition by "application", vars."datatype"  order by mindate ) as "range",
                                   true and ((mindate - lag(maxdate)
                                                        over (partition by application, vars."datatype" order by mindate, maxdate) <
                                              maxdate - mindate))                                                                                                                                     as continuous
                            from datas
                                     join vars on "datas"."referencetype" = vars."datatype"),
                        infos_agg as (
                            select application,
                                   "datatype",
                                   requiredAuthorizations,
                                   range,
                                   mindate,
                                   maxdate,
                                   bool_and(continuous) continuous
                    
                            from infos
                            group by application, "datatype", requiredAuthorizations, range, mindate, maxdate
                        ),
                        synthesis as (
                            select application,
                                   "datatype",
                                   requiredAuthorizations,
                                   mindate,
                                   maxdate,
                                   sum(
                                   case
                                       when continuous
                                           then 0
                                       else 1
                                       end
                                      )
                                   over (partition by application, "datatype", requiredAuthorizations order by mindate) timerange
                            from infos_agg
                        ),
                        result as (
                            select
                                application,
                                "datatype",
                                '' variable,
                                requiredAuthorizations,
                                '' aggregation,
                                min(mindate) "mindate",
                                max(maxdate) "maxdate"
                            from synthesis
                            group by  application, "datatype", variable, requiredAuthorizations, aggregation, timerange)
                    select
                    '%3$s' as "@class",
                        to_jsonb((gen_random_uuid(), now(),
                                  application,
                                  "datatype",
                                  variable,
                                  requiredAuthorizations,
                                  aggregation,
                                  array_agg(tsrange(mindate,maxdate))           )::%1$s.oresisynthesis) as json
                    from result
                    group by application, "datatype", variable, requiredAuthorizations, aggregation""";
    public static final String SELECT_SYNTHESIS_BY_APPLICATION_AND_DATATYPE = "SELECT '%s' as \"@class\", to_jsonb(t) as json FROM (" +
            "select id, updatedate, application, \"datatype\", variable, requiredAuthorizations, aggregation, ranges " +
            "from %s  " +
            "WHERE \"application\" = :application::uuid and \"datatype\" = :datatype) t";
    public static final String SELECT_SYNTHESIS_BY_APPLICATION_DATATYPE_AND_VARIABLE = "SELECT '%s' as \"@class\", to_jsonb(t) as json FROM (" +
            "select id, updatedate, application, \"datatype\", variable, requiredAuthorizations, aggregation, ranges " +
            "from %s  " +
            "WHERE \"application\" = :application::uuid and \"datatype\" = :datatype and \"variable\" = :variable) t";
    public static final String SYNTHESIS_UPSERT = """
            INSERT INTO %1$s (id, application, datatype, variable, requiredAuthorizations, aggregation, ranges)\s
            SELECT \s
            id, application, datatype, variable, requiredAuthorizations, aggregation, ranges
            FROM json_populate_recordset(NULL::%1$s, :json::json)\s
              ON CONFLICT (id) DO UPDATE SET   updateDate=current_timestamp\s
             RETURNING id""";
    public static final String SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE = "DELETE FROM %s\n" +
            "  WHERE \"application\" = :application::uuid and \"datatype\" = :datatype";
    public static final String SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE1 = SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE;
    public static final String SYNTHESIS_DELETE_BY_APPLICATION_DATATYPE_AND_VARIABLE = "DELETE FROM %s\n" +
            "  WHERE \"application\" = :application::uuid and \"datatype\" = :datatype and \"variable\" = :variable";

    public DataSynthesisRepository(final Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().synthesis();
    }


    public List<OreSiSynthesis> buildSynthesis(final String varsSql, final boolean hasChartDescription) {
        if (Strings.isNullOrEmpty(varsSql)) {
            return new LinkedList<>();
        }
        final String query = String.format(
                hasChartDescription ? BUILD_SYNTHESIS_SQL : BUILD_GENERIC_SYNTHESIS_SQL,
                getTable().schema().getSqlIdentifier(),
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

    public int removeSynthesisByApplicationDatatype(final UUID applicationId, final String dataType) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType));
        final String query = String.format(SYNTHESIS_DELETE_BY_APPLICATION_AND_DATATYPE1, getTable().getSqlIdentifier());
        return getNamedParameterJdbcTemplate().update(query, ImmutableMap.of("application", applicationId, "datatype", dataType));
    }

    public int removeSynthesisByApplicationDatatypeAndVariable(final UUID applicationId, final String dataType, final String variable) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType) && !Strings.isNullOrEmpty(variable));
        final String query = String.format(SYNTHESIS_DELETE_BY_APPLICATION_DATATYPE_AND_VARIABLE,
                getTable().getSqlIdentifier()
        );
        return getNamedParameterJdbcTemplate().update(query, ImmutableMap.of("application", applicationId, "datatype", dataType, "variable", variable));
    }

    public List<OreSiSynthesis> selectSynthesisDatatype(final UUID applicationId, final String dataType) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType));
        final String query = String.format(SELECT_SYNTHESIS_BY_APPLICATION_AND_DATATYPE, getEntityClass().getName(), getTable().getSqlIdentifier());
        return getNamedParameterJdbcTemplate().query(query,
                new MapSqlParameterSource(ImmutableMap.of("application", applicationId, "datatype", dataType)),
                getJsonRowMapper());
    }

    public List<OreSiSynthesis> selectSynthesisDatatypeAndVariable(final UUID applicationId, final String dataType, final String variable) {
        Preconditions.checkArgument(applicationId != null && !Strings.isNullOrEmpty(dataType) && !Strings.isNullOrEmpty(variable));
        final String query = String.format(SELECT_SYNTHESIS_BY_APPLICATION_DATATYPE_AND_VARIABLE, getEntityClass().getName(), getTable().getSqlIdentifier());

        return getNamedParameterJdbcTemplate().query(query,
                new MapSqlParameterSource(ImmutableMap.of("application", applicationId, "datatype", dataType, "variable", variable)),
                getJsonRowMapper());
    }

    @Override
    protected Class<OreSiSynthesis> getEntityClass() {
        return OreSiSynthesis.class;
    }
}