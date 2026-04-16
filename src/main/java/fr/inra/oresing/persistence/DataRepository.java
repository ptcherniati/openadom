package fr.inra.oresing.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.migration.context.DataInfo;
import fr.inra.oresing.domain.application.configuration.migration.context.SchemaInfo;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.query.DataRowIds;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import fr.inra.oresing.persistence.requestbuilder.data.DataRequestBuilder;
import fr.inra.oresing.persistence.requestbuilder.data.SqlRequest;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DataRepository extends JsonTableInApplicationSchemaRepositoryTemplate<DataValue> implements fr.inra.oresing.domain.repository.data.DataRepository {
    public static final String APPLICATION_ID = "applicationId";
    public static final String REF_TYPE = "refType";
    public static final String[] ORDERED_COLUMNS = new String[]{"id", "patternColumnName", "application", "ReferenceType", "hierarchicalKey", "naturalKey", "refsLinkedTo", "refValues", "binaryFile", "\"authorization\""};
    public static final int PIPE_SIZE = 65536;

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
                .filter(Objects::nonNull).
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
                    (%3$s)
                SELECT %3$s
                FROM json_populate_recordset(
                    NULL::%2$s,
                    :json::json
                )
                ON CONFLICT ON CONSTRAINT "hierarchicalKey_uniqueness"
                DO UPDATE SET updateDate=current_timestamp, hierarchicalKey=EXCLUDED.hierarchicalKey, naturalKey=EXCLUDED.naturalKey, refsLinkedTo=EXCLUDED.refsLinkedTo,
                refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile, "authorization"=EXCLUDED."authorization" RETURNING id
                """.formatted(
                getTable().getSqlIdentifier(),
                getTable().getSqlIdentifier(),
                Arrays.stream(ORDERED_COLUMNS).collect(Collectors.joining(","))
        );
    }

    @Override
    public List<UUID> storeAll(final Path finalCsvFile) {
        // 7. Colonnes pour l'insertion
        final String columns = Arrays.stream(ORDERED_COLUMNS)
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));

        return getNamedParameterJdbcTemplate().getJdbcTemplate().execute(
                (ConnectionCallback<List<UUID>>) connection -> {
                    connection.setAutoCommit(false);
                    try {
                        connection.setAutoCommit(false);
                        PGConnection pgConn = connection.unwrap(PGConnection.class);
                        CopyManager copyManager = pgConn.getCopyAPI();
                        connection.createStatement().execute(
                                "CREATE TEMP TABLE referencevalue_import (data jsonb) ON COMMIT DROP"
                        );

                        try (BufferedReader reader = Files.newBufferedReader(finalCsvFile, StandardCharsets.UTF_8)) {
                            long rowsInserted = copyManager.copyIn("COPY referencevalue_import (data) FROM STDIN  ",
                                    reader);
                            log.info("Inserted {} rows using COPY", rowsInserted);
                        }

                        connection.createStatement().execute("""
                                DELETE FROM %1$s.reference_reference
                                WHERE referenceid IN (
                                    SELECT (data->>'id')::uuid 
                                    FROM referencevalue_import
                                )
                                """
                                .formatted(getSchema().getName()));
                        String insertSql = String.format("""
                                        INSERT INTO %1$s (%2$s)
                                        SELECT %2$s
                                        FROM referencevalue_import,
                                        jsonb_populate_record(
                                            NULL::%1$s,
                                            data
                                        )
                                        ON CONFLICT ON CONSTRAINT "hierarchicalKey_uniqueness"
                                        DO UPDATE SET 
                                            updateDate = current_timestamp,
                                            hierarchicalKey = EXCLUDED.hierarchicalKey,
                                            naturalKey = EXCLUDED.naturalKey,
                                            refsLinkedTo = EXCLUDED.refsLinkedTo,
                                            refValues = EXCLUDED.refValues,
                                            binaryFile = EXCLUDED.binaryFile,
                                            "authorization" = EXCLUDED."authorization"
                                        RETURNING id;
                                        """,
                                getTable().getSqlIdentifier(), columns
                        );

                        List<UUID> insertedIds = new ArrayList<>();
                        try (PreparedStatement ps = connection.prepareStatement(insertSql);
                             ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                insertedIds.add((UUID) rs.getObject("id"));
                            }
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        getNamedParameterJdbcTemplate().query("""
                                 SELECT DISTINCT
                                     referenceid, referencesby  
                                FROM 
                                    referencevalue_import s,
                                    JSON_TABLE(s.data, '$.refslinkedto.*.*.*' columns (
                                         referenceid UUID PATH '$.id',
                                         NESTED PATH '$.uuids' COLUMNS(referencesby  UUID PATH '$')
                                        )
                                    ) as joins;""", rs -> {
                            final UUID referenceid = rs.getObject(1, UUID.class);
                            final UUID referencesby = rs.getObject(2, UUID.class);
                        });

                        connection.createStatement().execute("""
                                INSERT INTO %1$s.reference_reference(referenceid, referencesby)
                                SELECT DISTINCT                                    
                                 (s.data->>'id')::uuid referenceid,
                                 referencesby::uuid
                                FROM
                                 referencevalue_import s,
                                     JSON_TABLE (
                                         s.data, '$.refslinkedto.*.*.*.uuids' COLUMNS (
                                         NESTED PATH '$[*]' COLUMNS(
                                                 referencesby  TEXT PATH '$')
                                             )
                                     ) as joins;
                                """
                                .formatted(getSchema().getName())
                        );
                        connection.commit();
                        return insertedIds;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }


    @Override
    protected Class<DataValue> getEntityClass() {
        return DataValue.class;
    }

    @Override

    public void removeByFileId(final UUID fileId) {
        final String query = String.format("""
                        DELETE FROM %s
                        WHERE binaryfile::text = :binaryFile
                        """,
                getTable().getSqlIdentifier()
        );

        Map<String, Object> params = Map.of("binaryFile", fileId.toString());
        int unpublishedLines = getNamedParameterJdbcTemplate().update(query, params);
        flush();
    }


    @Override
    public Map<String, List<Ltree>> resolveRequiredAuthorizations(Map<String, List<Ltree>> requiredAuthorizations) {
        if (requiredAuthorizations.isEmpty()) {
            return Map.of();
        }
        AtomicInteger counter = new AtomicInteger();
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();

        String params = requiredAuthorizations.entrySet().stream()
                .map(entry -> {
                    String dataNameParam = "param%d".formatted(counter.incrementAndGet());
                    String valueParam = "param%d".formatted(counter.incrementAndGet());
                    parameterSource.addValue(dataNameParam, entry.getKey());
                    parameterSource.addValue(valueParam, entry.getValue().getFirst().getSql());
                    return "row(:%s,:%s::ltree)".formatted(dataNameParam, valueParam);
                })
                .collect(Collectors.joining(", "));

        String sql = String.format("""
                        SELECT 'java.util.Map' AS "@class",
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
                        ) AS json
                        FROM %1$s.referencevalue
                        WHERE (referencetype, naturalkey) IN (%2$s)
                        LIMIT 1
                        """,
                getSchema().getSqlIdentifier(),
                params
        );

        return getNamedParameterJdbcTemplate().
                queryForObject(
                        sql,
                        parameterSource,
                        new JsonRowMapper<Map>()
                );
    }


    public List<UUID> delete(final DownloadDatasetQuery downloadDatasetQuery) {
        final SqlRequest sqlRequest = DataRequestBuilder.buildDeleteRequest(downloadDatasetQuery);
        return getNamedParameterJdbcTemplate().queryForList(sqlRequest.sql(), sqlRequest.parameterSource(), UUID.class);
    }

    /**
     * @param refType le type du referenciel
     * @param params  les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<UUID> deleteReferenceType(final String refType, final MultiValueMap<String, String> params) {
        String sql = "delete from %1$s%n" +
                     "WHERE application=:applicationId::uuid AND ReferenceType=:refType%n";
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, refType);

        sql += addReferenceConditions(params, paramSource);
        sql += "%nreturning  id";
        final String query = String.format(sql, getTable().getSqlIdentifier(), getEntityClass().getName());
        return getNamedParameterJdbcTemplate().queryForList(query, paramSource, UUID.class);
    }

    public Stream<DataValue> findAllByReferenceTypeStream(final String referenceName) {
        String query = """
                SELECT DISTINCT '%1$s' as "@class",
                to_jsonb(t)  as json
                FROM
                %2$s t
                WHERE application=:applicationId::uuid AND ReferenceType=:refType
                
                """
                .formatted(DataValue.class.getName(), getTable().getSqlIdentifier());
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, referenceName);
        return getNamedParameterJdbcTemplate()
                .queryForStream(query, paramSource, getJsonRowMapper());
    }

    public List<DataValue> findAllByReferenceType(final String referenceName) {
        String query = """
                SELECT DISTINCT '%1$s' as "@class",
                to_jsonb(t)  as json
                FROM
                %2$s t
                WHERE application=:applicationId::uuid AND ReferenceType=:refType
                
                """
                .formatted(DataValue.class.getName(), getTable().getSqlIdentifier());
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, referenceName);
        return getNamedParameterJdbcTemplate()
                .query(query, paramSource, getJsonRowMapper());
    }

    public Stream<DataValue> findAllByReferenceTypeWithReferencingReferencesStream(final String refType, final MultiValueMap<String, String> params) {
        final int offset = Optional.of(params)
                .map(m -> m.remove("_offset_"))
                .filter(l -> !l.isEmpty())
                .map(List::getFirst)
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
                .map(List::getFirst)
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
        final MapSqlParameterSource paramSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, refType);

        String cond = addReferenceConditions(params, paramSource);
        cond = String.format("%s offset %d  limit %s", cond, offset, limit);
        return getNamedParameterJdbcTemplate().queryForStream(query + cond, paramSource, getJsonRowMapper());
    }


    public Map<String, Map<String, String>> findDisplayByNaturalKey(final String refType) {
        final String query = String.format("""
                        SELECT 'java.util.Map' AS "@class",
                               jsonb_build_object(naturalkey, jsonb_agg(display)) AS json
                        FROM %2$s,
                        LATERAL (
                            SELECT jsonb_build_object(
                                replace(
                                    replace(
                                        jsonb_path_query(
                                            refvalues,
                                            '$.keyvalue()?(@.key like_regex "%1$s.*").key'
                                        )::text,
                                        '%1$s',
                                        ''
                                    ),
                                    '"', ''
                                ),
                                TRIM('"' FROM
                                    jsonb_path_query(
                                        refvalues,
                                        '$.keyvalue()?(@.key like_regex "%1$s.*").value'
                                    )::text
                                )
                            ) AS display
                        ) displays
                        WHERE referencetype = :refType
                        GROUP BY naturalkey
                        """,
                DataColumn.DISPLAY,
                getTable().getSqlIdentifier()
        );

        final Map<String, Map<String, String>> displayForNaturalKey = new HashMap<>();
        final List<?> result = getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource(REF_TYPE, refType),
                getJsonRowMapper()
        );

        for (final Object o : result) {
            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, String>>> o1 = (Map<String, List<Map<String, String>>>) o;
            Map<String, Map<String, String>> collect = o1.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
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
        MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource(APPLICATION_ID, getApplication().getId())
                .addValue(REF_TYPE, refType);

        final String select = Stream.of(column.split(","))
                .map(c -> {
                    String paramName = "v" + ai.get();
                    mapSqlParameterSource.addValue(paramName, c);
                    return String.format("refValues->>'%s' AS \"%s%d\"", paramName, DataColumn.DISPLAY, ai.getAndIncrement());
                })
                .collect(Collectors.joining(", "));

        final String query = String.format("""
                        SELECT %s
                        FROM %s t
                        WHERE application = :applicationId::uuid
                          AND ReferenceType = :refType
                        """,
                select,
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().queryForList(query, mapSqlParameterSource)
                .stream()
                .map(m -> m.values().stream().map(v -> (String) v).toList())
                .toList();
    }


    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> getDataIdPerKeys(final String ReferenceType) {
        Map<DataValue.LineIdentityColumnName, UUID> dataIdPerKeys = new HashMap<>();
        findAllByReferenceTypeStream(ReferenceType)
                .forEach(dataValue -> {
                    DataValue.LineIdentityColumnName naturalKey = dataValue.buildLineIdentityColumnName();
                    dataIdPerKeys.put(naturalKey, dataValue.getId());
                });
        return ImmutableMap.copyOf(dataIdPerKeys);
    }

    public List<ApplicationResult.DataSynthesis> buildReferenceSynthesis() {
        final String query = String.format("""
                        SELECT
                            ReferenceType AS ReferenceType,
                            COUNT(*) AS lineCount
                        FROM %s
                        GROUP BY ReferenceType
                        """,
                getTable().getSqlIdentifier()
        );
        return getNamedParameterJdbcTemplate().query(
                query,
                Map.of(),
                BeanPropertyRowMapper.newInstance(ApplicationResult.DataSynthesis.class)
        );
    }

    public void updateConstraintForeignReferences(final List<UUID> uuids) {
        final String deleteSql = String.format("""
                        DELETE FROM %s.Reference_Reference
                        WHERE referenceId IN (:ids)
                        """,
                getTable().schema().getSqlIdentifier()
        );
        final String insertSql = String.format("""
                        INSERT INTO %1$s.Reference_Reference(referenceId, referencesBy)
                        SELECT
                            id AS referenceId,
                            jsonb_array_elements_text(jsonb_path_query_array(refslinkedto, '$.**.uuids[*]'))::uuid AS referencesBy
                        FROM %2$s
                        WHERE id IN (:ids)
                        ON CONFLICT ON CONSTRAINT "Reference_Reference_PK" DO NOTHING
                        """,
                getTable().schema().getSqlIdentifier(),
                getTable().getSqlIdentifier()
        );
        final String sql = deleteSql + ";" + insertSql;
        Iterators.partition(uuids.stream().iterator(), Short.MAX_VALUE - 1)
                .forEachRemaining(uuidsByBatch ->
                        getNamedParameterJdbcTemplate().execute(
                                sql,
                                Map.of("ids", uuidsByBatch),
                                PreparedStatement::execute
                        )
                );
    }

    public Map<Ltree, List<DataValue>> getReferenceDisplaysById(final Set<String> listOfIds) {
        if (listOfIds.isEmpty()) {
            return new HashMap<>();
        }
        final String sql = String.format("""
                        SELECT DISTINCT
                            '%1$s' AS "@class",
                            to_jsonb(r) AS json
                        FROM %2$s.reference_reference dr
                        JOIN %2$s."referencevalue" d ON dr.referenceId = d.id
                        JOIN %3$s r ON dr.referencesBy = r.id
                        WHERE d.id::text IN (:list)
                        """,
                DataValue.class.getName(),
                getSchema().getSqlIdentifier(),
                getTable().getSqlIdentifier()
        );
        List<DataValue> list = getNamedParameterJdbcTemplate().query(
                sql,
                new MapSqlParameterSource().addValue("list", listOfIds),
                getJsonRowMapper()
        );
        Map<Ltree, List<DataValue>> referencesValuesMap = list.stream()
                .collect(Collectors.groupingBy(DataValue::getNaturalKey));
        referencesValuesMap.putAll(list.stream()
                .collect(Collectors.groupingBy(DataValue::getHierarchicalKey)));
        return referencesValuesMap;
    }

    @Override
    public Map<String, String> findHierarchicalKeysByKeyForReferenceTypes(List<String> referenceTypes) {
        if (CollectionUtils.isEmpty(referenceTypes)) {
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
        List<Map.Entry<String, String>> newEntries = Objects.requireNonNull(hierarchicalKeyByNaturalKey).values().stream()
                .distinct()
                .filter(hierarchicalKey -> !hierarchicalKeyByNaturalKey.containsKey(hierarchicalKey))
                .map(hierarchicalKey -> Map.entry(hierarchicalKey, hierarchicalKey))
                .toList();

// Ajouter les nouvelles entrées à la map
        hierarchicalKeyByNaturalKey.putAll(
                newEntries.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        return hierarchicalKeyByNaturalKey;
    }

    public Stream<DataValuesByDataType> getLinkedReferenceValuesStream(final Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Stream.of();
        }

        final String sql = String.format("""
                        WITH RECURSIVE refs AS (
                            SELECT * FROM %1$s rv
                            WHERE rv.id IN (:ids)
                            UNION ALL
                            SELECT rv.*
                            FROM %2$s.reference_reference rr
                            JOIN refs ON rr.referenceid = refs.id
                            JOIN %1$s rv ON rv.id = rr.referencesby
                        )
                        SELECT
                            '%3$s' AS "@class",
                            jsonb_build_object(
                                'dataType', rv.referencetype,
                                'ids', array_agg(DISTINCT to_jsonb(rv.id))
                            ) AS json_map
                        FROM refs rv
                        GROUP BY rv.referencetype
                        """,
                getTable().getSqlIdentifier(),
                getTable().schema().getSqlIdentifier(),
                DataValuesByDataType.class.getCanonicalName()
        );

        return getNamedParameterJdbcTemplate()
                .queryForStream(sql, new MapSqlParameterSource("ids", ids), (rs, rowNum) -> {
                    String jsonMap = rs.getString("json_map");
                    try {
                        JsonNode jsonNode = getJsonRowMapper().getJsonMapper().readTree(jsonMap);
                        String dataType = jsonNode.get("dataType").asText();
                        Set<UUID> dataValues = getJsonRowMapper().getJsonMapper().convertValue(
                                jsonNode.get("ids"),
                                new TypeReference<>() {
                                }
                        );

                        return new DataValuesByDataType(dataType, dataValues.stream()
                                .map(DataRowIds::new)
                                .collect(Collectors.toSet()));
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    public Flux<DataRows> findAllByDataTypeFlux(final DownloadDatasetQuery downloadDatasetQuery) {
        final Stream result;
        final SqlRequest sqlRequest = DataRequestBuilder.buildSelectRequest(downloadDatasetQuery);
        result = getNamedParameterJdbcTemplate().queryForStream(sqlRequest.sql(), sqlRequest.parameterSource(), new JsonRowMapper<DataRows>());
        return Flux.<DataRows>fromStream(result);
    }

    public Flux<FilterList> getFilterList(final String dataName) {
        final Stream result;
        final String query = """
                WITH
                referenceBase AS MATERIALIZED(
                    SELECT distinct listName, colonne, hk, uuid
                    FROM %1$s.referencevalue,
                    JSON_TABLE(
                        refslinkedto, '$.keyvalue()' columns(
                            listName text PATH '$.key',
                            NESTED PATH '$.value.keyvalue()' columns(
                                colonne text PATH '$.key',
                                NESTED PATH '$.value.keyvalue()' columns(
                                    hk ltree PATH '$.key',
                                    NESTED PATH '$.value.uuids[*]' columns(
                                        uuid uuid PATH '$'
                                    )
                                )
                            )
                        )
                    )
                    WHERE referencetype = :referenceType
                ),
                -- CTE pour agréger les colonnes par hk
                components_grouped AS MATERIALIZED(
                    SELECT
                        hk,
                        listName,
                        array_agg(DISTINCT colonne) AS components
                    FROM referenceBase
                    GROUP BY hk, listName
                ),
                parents_grouped AS MATERIALIZED(
                    SELECT
                        child.hierarchicalkey AS child_hkey,
                        jsonb_agg(DISTINCT jsonb_build_object(
                            'referenceType', p.referencetype,
                            'hierarchicalKey', p.hierarchicalkey,
                            'naturalKey', p.naturalkey,
                            '__display_default', p.refvalues->'__display_default',
                            '__display_fr', p.refvalues->'__display_fr',
                            '__display_en', p.refvalues->'__display_en',
                            'id', p.id
                        )) AS parents
                    FROM %1$s.referencevalue child
                    CROSS JOIN LATERAL regexp_split_to_table(
                        regexp_replace(child.hierarchicalkey::text, '\\.[^\\.]+$',''),
                        '\\.'
                    ) AS ancestor_segment(segment)
                    CROSS JOIN LATERAL (
                        SELECT
                            (regexp_match(ancestor_segment.segment, '([0-9a-z_]*)K(.*)'))[1] AS parent_type,
                            (regexp_match(ancestor_segment.segment, '([0-9a-z_]*)K(.*)'))[2] AS parent_naturalkey
                        WHERE ancestor_segment.segment != ''
                    ) AS ps
                    JOIN %1$s.referencevalue p
                        ON p.referencetype = ps.parent_type
                        AND p.naturalkey = ps.parent_naturalkey::ltree
                    WHERE child.hierarchicalkey <> ''
                      AND child.hierarchicalkey IN (SELECT hk FROM referenceBase)
                    GROUP BY child.hierarchicalkey
                ),
                referenceByReftype AS (
                    SELECT
                        components_grouped.listName AS "listName",
                        jsonb_build_object(
                            'listName', components_grouped.listName,
                            'refsLinkeds', jsonb_agg(
                                distinct jsonb_build_object(
                                    'id', parents_grouped.parents->0->>'id',
                                    'naturalKey', parents_grouped.parents->0->>'naturalKey',
                                    '__display_default', parents_grouped.parents->0->>'__display_default',
                                    '__display_fr', parents_grouped.parents->0->>'__display_fr',
                                    '__display_en', parents_grouped.parents->0->>'__display_en',
                                    'referenceType', components_grouped.listName,
                                    'hierarchicalKey', parents_grouped.parents->0->>'hierarchicalKey',
                                    'isHierarchique', jsonb_path_exists(
                                        application."configuration",
                                        ('$.datadescription.' || components_grouped.listName || '.componentdescriptions.*.checker ? (@.isparent == true)')::jsonpath
                                    ),
                                    'components', components_grouped.components,
                                    'parents', '[]'::jsonb
                                )
                            )
                        ) AS ref_object
                    FROM components_grouped
                    JOIN parents_grouped ON parents_grouped.child_hkey = components_grouped.hk
                    JOIN application ON application.name = '%1$s'
                    GROUP BY application.configuration, components_grouped.listName
                    ORDER BY components_grouped.listName
                )
                SELECT
                    'fr.inra.oresing.persistence.FilterList' AS "@class",
                    ref_object AS "json"
                FROM referenceByReftype;
                
                """.formatted(getSchema().getSqlIdentifier());
        result = getNamedParameterJdbcTemplate().queryForStream(
                query,
                Map.of("referenceType", dataName)
                , new JsonRowMapper<FilterList>());
        return Flux.<FilterList>fromStream(result);
    }

    @Override
    public List<ReferenceScope.NodeDescription> getNodesForMenu(MenuType menuType) {
        return getNamedParameterJdbcTemplate()
                .query(
                        """
                                SELECT DISTINCT '%1$s' as "@class",
                                        to_jsonb( %2$s.getnodes('%3$s')) as json"""
                                .formatted(
                                        ReferenceScope.NodeDescription.class.getName(),
                                        getTable().schema().getSqlIdentifier(),
                                        menuType.getType()),
                        Map.of(),
                        new JsonRowMapper<>()
                );
    }

    @Override
    public Flux<FileContent> getStoredData(Application application, String dataName) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = FileContent.buildFileNameRequest(application, dataName);

        return Flux.<FileContent>fromStream(
                getNamedParameterJdbcTemplate().queryForStream(
                        sql,
                        params,
                        (rs, rowNum) -> {
                            final Array sqlArray = rs.getArray("refsLinked");
                            List<String> refsLinked = sqlArray != null
                                    ? Arrays.asList((String[]) sqlArray.getArray())
                                    : Collections.emptyList();
                            return new FileContent(refsLinked,
                                    rs.getString("fileName"),
                                    rs.getBinaryStream("fileContent")
                            );
                        }
                )
        );
    }

    public enum Order {
        ASC, DESC
    }

    public record DataValuesByDataType(String dataType, Set<DataRowIds> ids) {
    }
}