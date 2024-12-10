package fr.inra.oresing.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import fr.inra.oresing.domain.data.read.query.DataRowIds;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.persistence.requestBuilder.data.DataRequestBuilder;
import fr.inra.oresing.persistence.requestBuilder.data.SqlRequest;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataRepository extends JsonTableInApplicationSchemaRepositoryTemplate<DataValue> implements fr.inra.oresing.domain.repository.data.DataRepository {

    /*public static final String REFERENCE_FIELD_SEARCH = """
            (lower(refvalues ->> '%1$s') ~ lower('%2$s'))
            """;*/


    public DataRepository(final Application application) {
        super(application);
    }

    private static String addReferenceConditions(final MultiValueMap<String, String> params, final MapSqlParameterSource paramSource) {
        final AtomicInteger i = new AtomicInteger();
        // kv.value='LPF' OR t.refvalues @> '{"esp_nom":"ALO"}'::jsonb
        String cond = params.entrySet().stream().flatMap(e -> {
                    final String k = e.getKey();
                    if (StringUtils.equalsAnyIgnoreCase("_row_id_", k)) {
                        final String collect = e.getValue().stream().map(v -> {
                                    final String arg = ":arg" + i.getAndIncrement();
                                    paramSource.addValue(arg, v);
                                    return String.format("'%s'::uuid", v);
                                })
                                .collect(Collectors.joining(", "));
                        return Stream.ofNullable(String.format("array[id]::uuid[] <@ array[%s]::uuid[]", collect));
                    }
                    if (StringUtils.equalsAnyIgnoreCase("_row_key_", k)) {
                        final String collect = e.getValue().stream()
                                .map(v -> {
                                    final String arg = ":arg" + i.getAndIncrement();
                                    paramSource.addValue(arg, v);
                                    return String.format("'%s'", v);
                                })
                                .collect(Collectors.joining(", "));
                        if (collect.isEmpty()) {
                            return null;
                        }
                        return Stream.ofNullable(String.format(" (naturalKey in (%1$s) or hierarchicalKey in (%1$s)) ", collect));
                    }
                    if (StringUtils.equalsAnyIgnoreCase("any", k)) {
                        return e.getValue().stream().map(v -> {
                            final String arg = ":arg" + i.getAndIncrement();
                            paramSource.addValue(arg, v);
                            return "kv.value=" + arg;
                        });
                    }
                    return e.getValue().stream().map(v -> String.format("lower(t.refvalues ->> '%s') ~ lower('.*%s.*')", k, v));
                })
                .filter(k -> k != null).
                collect(Collectors.joining(" AND "));

        if (StringUtils.isNotBlank(cond)) {
            cond = " AND (" + cond + ")";
        }
        return cond;
    }

    @Override
    public SqlTable getTable() {
        return getSchema().referenceValue();
    }

    @Override
    protected String getUpsertQuery() {
        return """
                INSERT INTO %1$s
                    (id, patternColumnName,  application, ReferenceType, hierarchicalKey, naturalKey, refsLinkedTo, refValues, binaryFile, \"authorization\")
                SELECT id, patternColumnName, application, ReferenceType, hierarchicalKey, naturalKey, refsLinkedTo, refValues, binaryFile, \"authorization\"
                FROM json_populate_recordset(
                    NULL::%2$s,
                    :json::json
                )
                ON CONFLICT ON CONSTRAINT "hierarchicalKey_uniqueness"
                DO UPDATE SET updateDate=current_timestamp, hierarchicalKey=EXCLUDED.hierarchicalKey, naturalKey=EXCLUDED.naturalKey, refsLinkedTo=EXCLUDED.refsLinkedTo, 
                refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile, \"authorization\"=EXCLUDED.\"authorization\" RETURNING id
                """.formatted(getTable().getSqlIdentifier(), getTable().getSqlIdentifier());
    }

    @Override
    protected Class<DataValue> getEntityClass() {
        return DataValue.class;
    }

    @Override

    public int removeByFileId(final UUID fileId) {
        final String query = "DELETE FROM " + getTable().getSqlIdentifier() +
                "\n  WHERE binaryfile::text in( :binaryFile)";
        ImmutableMap<String, List<String>> params = ImmutableMap.of("binaryFile", List.of(fileId.toString()));
        int unPublishdLines = getNamedParameterJdbcTemplate().update(query, params);
        flush();
        return unPublishdLines;
    }

    @Override
    public Map<String, List<Ltree>> resolveRequiredAuthorizations(Map<String, List<Ltree>> requiredAuthorizations) {
        AtomicInteger counter = new AtomicInteger();
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        String params = requiredAuthorizations.entrySet().stream()
                .map(entry -> {
                    String dataNameParam = "param%s".formatted(counter.incrementAndGet());
                    parameterSource.addValue(dataNameParam, entry.getKey());
                    String valueParam = "param%s".formatted(counter.incrementAndGet());
                    parameterSource.addValue(valueParam, entry.getValue().get(0).getSql());
                    return "row(:%s,:%s::ltree)".formatted(dataNameParam, valueParam);
                })
                .collect(Collectors.joining(", "));
        String sql = """
                select 'java.util.Map' as "@class", 
                to_jsonb(
                    jsonb_populate_record(
                        null::%1$s.requiredAuthorizations,
                        jsonb_object_agg(
                            jsonb_build_object(
                                referencetype, 
                                to_jsonb(ARRAY[hierarchicalkey])
                            )
                        )
                    ) 
                ) as json
                from %1$s.referencevalue
                where
                    (referencetype, naturalkey) in (%2$s)
                limit 1;""".formatted(getSchema().getSqlIdentifier(), params);
        // row('projet','projet_manche'), row('sites','oir__p1')
        return getNamedParameterJdbcTemplate().queryForObject(sql, parameterSource, new JsonRowMapper<Map>());
    }

    public List<UUID> delete(final DownloadDatasetQuery downloadDatasetQuery) {
        final SqlRequest sqlRequest = DataRequestBuilder.buildDeleteRequest(downloadDatasetQuery);
        List<UUID> uuids = getNamedParameterJdbcTemplate().queryForList(sqlRequest.sql(), sqlRequest.parameterSource(), UUID.class);
        return uuids;
    }

    /**
     * @param refType le type du referenciel
     * @param params  les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<UUID> deleteReferenceType(final String refType, final MultiValueMap<String, String> params) {
        String sql = "delete from %1$s%n" +
                "WHERE application=:applicationId::uuid AND ReferenceType=:refType%n";
        final MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("refType", refType);

        final AtomicInteger i = new AtomicInteger();
        // kv.value='LPF' OR t.refvalues @> '{"esp_nom":"ALO"}'::jsonb

        sql += addReferenceConditions(params, paramSource);
        sql += "%nreturning  id";
        final String query = String.format(sql, getTable().getSqlIdentifier(), getEntityClass().getName());
        List<UUID> result = getNamedParameterJdbcTemplate().queryForList(query, paramSource, UUID.class);
        return result;
    }

    public Stream<DataValue> findAllByReferenceTypeStream(final String refType) {
        String query = """
                SELECT DISTINCT '%1$s' as "@class",  
                to_jsonb(t)  as json
                FROM
                %2$s t
                WHERE application=:applicationId::uuid AND ReferenceType=:refType
                
                """
                .formatted(DataValue.class.getName(), getTable().getSqlIdentifier());
        final MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("refType", refType);
        Stream<DataValue> dataValueStream = getNamedParameterJdbcTemplate()
                .queryForStream(query, paramSource, getJsonRowMapper());
        return dataValueStream;
    }

    public Stream<DataValue> findAllByReferenceTypeWithReferencingReferencesStream(final String refType, final MultiValueMap<String, String> params) {
        final int offset = Optional.of(params)
                .map(m -> m.remove("_offset_"))
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0))
                .map(o -> {
                    try {
                        return Integer.valueOf(o);
                    } catch (final NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0);
        final String limit = Optional.of(params)
                .map(m -> m.remove("_limit_"))
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0))
                .filter(o -> o.matches("[0-9]*|ALL"))
                .orElse("ALL");
        String query = """
                with
                agg as (
                     select
                         referencesby,
                         json_object_agg(referenceid, d2.refValues) agg
                        from %1$s.reference_reference dr
                        left join %2$s d2 on dr.referenceId = d2.id
                     group by referencesby
                )
                """
                .formatted(getSchema().getSqlIdentifier(), getTable().getSqlIdentifier());
        query += """    
                SELECT DISTINCT 
                    '%1$s' as "@class",  
                    to_jsonb(t) || 
                        jsonb_build_object('referencingreferences',agg.agg) as json
                FROM
                    %2$s t
                    left join agg on agg.referencesby = t.id,
                    jsonb_each_text(t.refvalues) kv
                WHERE 
                    application=:applicationId::uuid AND 
                    ReferenceType=:refType
                """
                .formatted(DataValue.class.getName(), getTable().getSqlIdentifier());
        final MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("refType", refType);

        String cond = addReferenceConditions(params, paramSource);
        cond = String.format("%s offset %d  limit %s", cond, offset, limit);
        Stream<DataValue> dataValueStream = getNamedParameterJdbcTemplate().queryForStream(query + cond, paramSource, getJsonRowMapper());
        return dataValueStream;
    }

    public Map<String, Map<String, String>> findDisplayByNaturalKey(final String refType) {
        final String query = """
                
                SELECT 
                    'java.util.Map' AS "@class" , 
                    jsonb_build_object(
                        naturalkey, 
                        jsonb_agg(display)) json
               FROM %2$s,
                LATERAL
                (SELECT  jsonb_build_object(
                                  replace(
                                      replace(
                                           jsonb_path_query(
                                               refvalues, 
                                               '$.keyvalue()?(@.key like_regex "%1$s.*").key'
                                           )::text, 
                                           '%1$s',
                                           ''
                                      ),
                                      '"',''),
                                       TRIM('"' FROM 
                                           jsonb_path_query(
                                               refvalues, 
                                               '$.keyvalue()?(@.key like_regex "%1$s.*").value'
                                           )::text)
                                  ) AS display
                    )displays
                WHERE referencetype = :refType
                GROUP BY naturalkey"""
                .formatted(DataColumn.DISPLAY,  getTable().getSqlIdentifier());
        final Map<String, Map<String, String>> displayForNaturalKey = new HashMap<>();
        final List result = getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("refType", refType), getJsonRowMapper());
        for (final Object o : result) {
            Map<String, List<Map<String, String>>> o1 = (Map<String, List<Map<String, String>>>) o;
            Map<String, Map<String, String>> collect = o1.entrySet()
                    .stream().collect(Collectors.toMap(
                            e -> e.getKey(),
                            e -> {
                                final Map<String, String> displayMap = new HashMap<>();
                                for (final Map<String, String> s : e.getValue()) {
                                    displayMap.putAll(s);
                                }
                                return displayMap;
                            }
                    ));
            displayForNaturalKey.putAll(collect);
        }
        return displayForNaturalKey;
    }

    public List<List<String>> findDataColumn(final String refType, final String column) {
        final AtomicInteger ai = new AtomicInteger(0);
        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource("applicationId", getApplication().getId()).addValue("refType", refType);
        final String select = Stream.of(column.split(","))
                .map(c -> {
                    mapSqlParameterSource.addValue("v" + ai.get(), c);
                    return "refValues->>:v" + ai.get() + " as \"%1$s" + ai.getAndIncrement() + "\"";
                })
                .collect(Collectors.joining(", "));
        final String sqlPattern = " SELECT %s "
                + " FROM " + getTable().getSqlIdentifier() + " t"
                + " WHERE application=:applicationId::uuid AND ReferenceType=:refType";
        final String query = String.format(sqlPattern, select);
        final List<List<String>> result = getNamedParameterJdbcTemplate().queryForList(query, mapSqlParameterSource)
                .stream()
                .map(m -> m.values().stream().map(v -> (String) v).collect(Collectors.toList()))
                .collect(Collectors.toList());
        return result;
    }

    @Override
    public ImmutableMap<DataValue.LineIdentityPatternColumnName, UUID> getDataIdPerKeys(final String ReferenceType) {
        Map<DataValue.LineIdentityPatternColumnName, UUID> dataIdPerKeys = new HashMap<>();
        findAllByReferenceTypeStream(ReferenceType)
                .forEach(dataValue -> {
                    DataValue.LineIdentityPatternColumnName naturalKey = dataValue.buildLineIdentityPatternColumnName();
                    dataIdPerKeys.put(naturalKey, dataValue.getId());
                });
        return ImmutableMap.copyOf(dataIdPerKeys);
    }

    public List<ApplicationResult.DataSynthesis> buildReferenceSynthesis() {
        final String query = "select \n" +
                "ReferenceType ReferenceType, count(*) lineCount \n" +
                "from " + getTable().getSqlIdentifier() + "\n" +
                "group by ReferenceType";
        return getNamedParameterJdbcTemplate()
                .query(query, ImmutableMap.of(),
                        BeanPropertyRowMapper.newInstance(ApplicationResult.DataSynthesis.class
                        ));
    }

    public void updateConstraintForeignReferences(final List<UUID> uuids) {
        final String deleteSql = "DELETE FROM " + getTable().schema().getSqlIdentifier() + ".Reference_Reference WHERE referenceId in (:ids)";
        final String insertSql = String.join(" "
                , "INSERT INTO " + getTable().schema().getSqlIdentifier() + ".Reference_Reference(referenceId, referencesBy)"
                , "select id referenceId, (jsonb_array_elements_text(jsonb_path_query(jsonb_path_query(refslinkedto, '$.*'), '$.*')#> '{uuids}'))::uuid referencesBy"
                , "from " + getTable().getSqlIdentifier()
                , "where id in (:ids)"
                , "ON CONFLICT ON CONSTRAINT \"Reference_Reference_PK\" DO NOTHING"
        );
        final String sql = String.join(";", deleteSql, insertSql);
        Iterators.partition(uuids.stream().iterator(), Short.MAX_VALUE - 1)
                .forEachRemaining(uuidsByBatch -> getNamedParameterJdbcTemplate().execute(sql, ImmutableMap.of("ids", uuidsByBatch), PreparedStatement::execute));
    }

    public Map<Ltree, List<DataValue>> getReferenceDisplaysById(final Set<String> listOfIds) {
        if (listOfIds.isEmpty()) {
            return new HashMap<>();
        }
        final String sql = "SELECT  DISTINCT '" + DataValue.class.getName() + "' as \"@class\",  to_jsonb(r) as json \n" +
                "from " + getSchema().getSqlIdentifier() + ".reference_reference dr\n" +
                "join " + getSchema().getSqlIdentifier() + ".\"referencevalue\" d on dr.referenceId = d.id\n" +
                "join " + getTable().getSqlIdentifier() + " r on dr.referencesBy = r.id\n" +
                "where d.id::text in (:list)";
        List<DataValue> list = getNamedParameterJdbcTemplate()
                .query(sql, new MapSqlParameterSource().addValue("list", listOfIds), getJsonRowMapper());
        Map<Ltree, List<DataValue>> referencesValuesMap = list.stream()
                .collect(Collectors.groupingBy(
                                DataValue::getNaturalKey
                        )
                );
        referencesValuesMap.putAll(list.stream()
                .collect(Collectors.groupingBy(
                                DataValue::getHierarchicalKey
                        )
                )
        );
        return referencesValuesMap;
    }

    @Override
    public Map<String, String> findHierarchicalKeysByKeyForReferenceTypes(List<String> referenceTypes) {
        if(CollectionUtils.isEmpty(referenceTypes)){
            return Collections.emptyMap();
        }
        String sql = """
        SELECT naturalkey::text, hierarchicalkey::text
        FROM %s
        WHERE referencetype in (:referenceType)
        """.formatted(getTable().getSqlIdentifier());

        MapSqlParameterSource params = new MapSqlParameterSource("referenceType", referenceTypes);

        Map<String, String> hierarchicalKeyByNaturalKey = getNamedParameterJdbcTemplate().query(
                sql,
                params,
                rs -> {
                    Map<String, String> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("naturalkey"), rs.getString("hierarchicalkey"));
                    }
                    return result;
                }
        );

// Collecter les nouvelles entrées dans une liste séparée
        List<Map.Entry<String, String>> newEntries = hierarchicalKeyByNaturalKey.values().stream()
                .distinct()
                .filter(hierarchicalKey -> !hierarchicalKeyByNaturalKey.containsKey(hierarchicalKey))
                .map(hierarchicalKey -> Map.entry(hierarchicalKey, hierarchicalKey))
                .collect(Collectors.toList());

// Ajouter les nouvelles entrées à la map
        hierarchicalKeyByNaturalKey.putAll(
                newEntries.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        return hierarchicalKeyByNaturalKey;
    }

    @Value
    public static class DataValuesByDataType {
        String dataType;
        Set<DataRowIds> ids;
    }

    public Stream<DataValuesByDataType> getLinkedReferenceValuesStream(final Set<UUID> ids) {

        if (ids == null || ids.isEmpty()) {
            return Stream.of();
        }
        final String sql = """
                        WITH RECURSIVE refs AS (
                              SELECT * from %1$s rv\s
                              WHERE rv.id in (:ids)\s
                           UNION ALL
                              SELECT rv.*\s
                              FROM %2$s.reference_reference rr
                                 JOIN refs ON rr.referenceid = refs.id\s
                                 join %1$s rv on rv.id=rr.referencesby)
                         SELECT
                            '%3$s' as "@class", \s
                            jsonb_build_object(
                                   'dataType', rv.referencetype,
                                   'ids', array_agg(distinct to_jsonb(rv.id))
                                )                                       AS json_map
                            FROM refs rv
                            GROUP BY rv.referencetype;
                """;
        final String query = String.format(
                sql,
                getTable().getSqlIdentifier(),
                getTable().schema().getSqlIdentifier(),
                DataValuesByDataType.class.getCanonicalName()
        );
        final Stream<DataValuesByDataType> result = getNamedParameterJdbcTemplate()
                .queryForStream(query, new MapSqlParameterSource("ids", ids), (rs, rowNum) -> {
                    String jsonMap = rs.getString("json_map");
                    try {
                        JsonNode jsonNode = getJsonRowMapper().getJsonMapper().readTree(jsonMap);
                        String dataType = jsonNode.get("dataType").asText();
                        Set<UUID> dataValues = getJsonRowMapper().getJsonMapper().convertValue(
                                jsonNode.get("ids"),
                                new TypeReference<Set<UUID>>() {
                                }
                        );

                        return new DataValuesByDataType(dataType, dataValues.stream().map(DataRowIds::new).collect(Collectors.toSet()));
                    } catch (Exception e) {
                        return null;
                    }
                });
        return result;
    }

    public Flux<DataRows> findAllByDataTypeFlux(final DownloadDatasetQuery downloadDatasetQuery) {
        final Stream result;
        final SqlRequest sqlRequest = DataRequestBuilder.buildSelectRequest(downloadDatasetQuery);
        result = getNamedParameterJdbcTemplate().queryForStream(sqlRequest.sql(), sqlRequest.parameterSource(), new JsonRowMapper<DataRows>());
        return Flux.<DataRows>fromStream(result);//Flux.<DataRows>fromStream(result.toList().stream());
    }

    @Override
    public List<ReferenceScope.NodeDescription>     getNodesForMenu(MenuType menuType) {
        return getNamedParameterJdbcTemplate()
                .query(
                        """
                                SELECT DISTINCT '%1$s' as "@class",
                                        to_jsonb( %2$s.getnodes('%3$s')) as json """
                                .formatted(
                                        ReferenceScope.NodeDescription.class.getName(),
                                        getTable().schema().getSqlIdentifier(),
                                        menuType.getType()),
                        Map.of(),
                        new JsonRowMapper<ReferenceScope.NodeDescription>()
                );
    }

    @Override
    public Flux<FileContent> getStoredData(String dataName, SubmissionType submissionType) {
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource();

        switch (submissionType) {
            case OA_VERSIONING -> {
                sql = FileContent.EXPORT_PUBLISHED_DATA_AS_CSF_SQL;
            }
            case null, default -> {
                sql = FileContent.EXPORT_REGISTER_DATA_CSV_SQL;
            }
        }
        sql = sql.formatted(dataName, getTable().schema().getSqlIdentifier(),"%s");

        return Flux.fromStream(getNamedParameterJdbcTemplate().queryForStream(
                                sql,
                                params,
                                (rs, rowNum) -> new FileContent(rs.getString("fileName"), rs.getString("fileContent"))
                        )
                        .map(FileContent.class::cast)
        );
    }

    public enum Order {
        ASC, DESC
    }
}
