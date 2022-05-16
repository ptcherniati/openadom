package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import fr.inra.oresing.model.*;
import fr.inra.oresing.rest.ApplicationResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReferenceValueRepository extends JsonTableInApplicationSchemaRepositoryTemplate<ReferenceValue> {

    public ReferenceValueRepository(Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().referenceValue();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + "\n" +
                "(id, application, referenceType, hierarchicalKey, hierarchicalReference, naturalKey, refsLinkedTo, refValues, binaryFile) \n" +
                "SELECT id, application, referenceType, hierarchicalKey, hierarchicalReference, naturalKey, refsLinkedTo, refValues, binaryFile \n" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", \n" +
                ":json::json) \n"
                + " ON CONFLICT ON CONSTRAINT \"hierarchicalKey_uniqueness\" \n" +
                "DO UPDATE SET updateDate=current_timestamp, hierarchicalKey=EXCLUDED.hierarchicalKey, hierarchicalReference=EXCLUDED.hierarchicalReference, naturalKey=EXCLUDED.naturalKey, refsLinkedTo=EXCLUDED.refsLinkedTo, refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile"
                + " RETURNING id";
    }

    @Override
    protected Class<ReferenceValue> getEntityClass() {
        return ReferenceValue.class;
    }

    public List<ReferenceValue> findAllByReferenceType(String refType) {
        return findAllByReferenceType(refType, new LinkedMultiValueMap<>());
    }

    /**
     * @param refType le type du referenciel
     * @param params  les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<ReferenceValue> findAllByReferenceType(String refType, MultiValueMap<String, String> params) {
        int offset = Optional.of(params)
                .map(m -> m.remove("_offset_"))
                .filter(l -> l.size() > 0)
                .map(l -> l.get(0))
                .map(o -> {
                    try {
                        return Integer.valueOf(o);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0);
        String limit = Optional.of(params)
                .map(m -> m.remove("_limit_"))
                .filter(l -> l.size() > 0)
                .map(l -> l.get(0))
                .filter(o -> o.matches("[0-9]*|ALL"))
                .orElse("ALL");
        String query = "SELECT DISTINCT '" + ReferenceValue.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                + getTable().getSqlIdentifier() + " t, jsonb_each_text(t.refvalues) kv WHERE application=:applicationId::uuid AND referenceType=:refType";
        MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("refType", refType);

        AtomicInteger i = new AtomicInteger();
        // kv.value='LPF' OR t.refvalues @> '{"esp_nom":"ALO"}'::jsonb
        String cond = params.entrySet().stream().flatMap(e -> {
                    String k = e.getKey();
                    if (StringUtils.equalsAnyIgnoreCase("_row_id_", k)) {
                        String collect = e.getValue().stream().map(v -> {
                                    String arg = ":arg" + i.getAndIncrement();
                                    paramSource.addValue(arg, v);
                                    return String.format("'%s'::uuid", v);
                                })
                                .collect(Collectors.joining(", "));
                        return Stream.ofNullable(String.format("array[id]::uuid[] <@ array[%s]::uuid[]", collect));
                    } else if (StringUtils.equalsAnyIgnoreCase("any", k)) {
                        return e.getValue().stream().map(v -> {
                            String arg = ":arg" + i.getAndIncrement();
                            paramSource.addValue(arg, v);
                            return "kv.value=" + arg;
                        });
                    } else {
                        return e.getValue().stream().map(v -> "t.refvalues @> '{\"" + k + "\":\"" + v + "\"}'::jsonb");
                    }
                })
                .filter(k -> k != null).
                collect(Collectors.joining(" OR "));

        if (StringUtils.isNotBlank(cond)) {
            cond = " AND (" + cond + ")";
        }
        cond = String.format("%s offset %d  limit %s", cond, offset, limit);

        List result = getNamedParameterJdbcTemplate().query(query + cond, paramSource, getJsonRowMapper());
        return (List<ReferenceValue>) result;
    }

    public Map<String, Map<String, String>> findDisplayByNaturalKey(String refType) {
        String query = "select 'java.util.Map' as \"@class\" , jsonb_build_object(naturalkey, jsonb_agg(display)) json\n" +
                "           from " + getTable().getSqlIdentifier() + ",\n" +
                "lateral\n" +
                "(select  jsonb_build_object(\n" +
                "                trim('\"__display_' from\n" +
                "                     jsonb_path_query(refvalues, '$.keyvalue()?(@.key like_regex \"__display.*\").key')::text),\n" +
                "                trim('\"' FROM jsonb_path_query(refvalues, '$.keyvalue()?(@.key like_regex \"__display.*\").value')::text)\n" +
                "            ) as display\n" +
                "    )displays\n" +
                "where referencetype = :refType\n" +
                "group by naturalkey";
        Map<String, Map<String, String>> displayForNaturalKey = new HashMap<>();
        List result = getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("refType", refType), getJsonRowMapper());
        for (Object o : result) {
            final Map<String, List<Map<String, String>>> o1 = (Map<String, List<Map<String, String>>>) o;
            final Map<String, Map<String, String>> collect = o1.entrySet()
                    .stream().collect(Collectors.toMap(
                            e -> e.getKey(),
                            e -> {
                                Map<String, String> displayMap = new HashMap<>();
                                for (Map<String, String> s : e.getValue()) {
                                    displayMap.putAll(s);
                                }
                                return displayMap;
                            }
                    ));
            displayForNaturalKey.putAll(collect);
        }
        return displayForNaturalKey;
    }

    public List<List<String>> findReferenceValue(String refType, String column) {
        AtomicInteger ai = new AtomicInteger(0);
        final MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource("applicationId", getApplication().getId()).addValue("refType", refType);
        String select = Stream.of(column.split(","))
                .map(c -> {
                    mapSqlParameterSource.addValue("v" + ai.get(), c);
                    return "refValues->>:v" + ai.get() + " as \"%1$s" + ai.getAndIncrement() + "\"";
                })
                .collect(Collectors.joining(", "));
        String sqlPattern = " SELECT %s "
                + " FROM " + getTable().getSqlIdentifier() + " t"
                + " WHERE application=:applicationId::uuid AND referenceType=:refType";
        String query = String.format(sqlPattern, select);
        List<List<String>> result = getNamedParameterJdbcTemplate().queryForList(query, mapSqlParameterSource)
                .stream()
                .map(m -> m.values().stream().map(v -> (String) v).collect(Collectors.toList()))
                .collect(Collectors.toList());
        return result;
    }

    public ImmutableMap<Ltree, ApplicationResult.Reference.ReferenceUUIDAndDisplay> getReferenceIdAndDisplayPerKeys(String referenceType, Locale locale) {
        Function<ReferenceValue, ApplicationResult.Reference.ReferenceUUIDAndDisplay> referenceValueToReferenceUuidAndDisplayFunction = result -> {
            ReferenceDatum referenceDatum = result.getRefValues();
            ReferenceColumn referenceColumnForDisplay = ReferenceColumn.forDisplay(locale);
            String display;
            if (referenceDatum.contains(referenceColumnForDisplay)) {
                ReferenceColumnValue referenceColumnValueForDisplay = referenceDatum.get(referenceColumnForDisplay);
                Preconditions.checkState(referenceColumnValueForDisplay instanceof ReferenceColumnSingleValue);
                display = ((ReferenceColumnSingleValue) referenceColumnValueForDisplay).getValue();
            } else {
                display = null;
            }
            Map<String, Object> values = referenceDatum.toJsonForFrontend();
            return new ApplicationResult.Reference.ReferenceUUIDAndDisplay(display, result.getId(), values);
        };
        return findAllByReferenceType(referenceType).stream().collect(ImmutableMap.toImmutableMap(ReferenceValue::getHierarchicalKey, referenceValueToReferenceUuidAndDisplayFunction));
    }

    public ImmutableMap<Ltree, UUID> getReferenceIdPerKeys(String referenceType) {
        final List<ReferenceValue> allByReferenceType = findAllByReferenceType(referenceType);
        final ImmutableMap<Ltree, UUID> byHierarchicalKey = allByReferenceType.stream().collect(ImmutableMap.toImmutableMap(ReferenceValue::getNaturalKey, ReferenceValue::getId));
        final ImmutableMap<Ltree, UUID> byNaturalKey = allByReferenceType.stream().collect(ImmutableMap.toImmutableMap(ReferenceValue::getHierarchicalKey, ReferenceValue::getId));
        final ImmutableMap.Builder<Ltree, UUID> builder = ImmutableMap.builder();
        return builder
                .putAll(byHierarchicalKey)
                .putAll(byNaturalKey.entrySet().stream().filter(e->!byHierarchicalKey.containsKey(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .build();
    }

    public List<ApplicationResult.ReferenceSynthesis> buildReferenceSynthesis() {
        String query = "select \n" +
                "referencetype referenceType, count(*) lineCount \n" +
                "from " + getTable().getSqlIdentifier() + "\n" +
                "group by referencetype";
        return getNamedParameterJdbcTemplate().query(query, ImmutableMap.of(), BeanPropertyRowMapper.newInstance(ApplicationResult.ReferenceSynthesis.class));
    }

    public void updateConstraintForeignReferences(List<UUID> uuids) {
        String deleteSql = "DELETE FROM " + getTable().getSchema().getSqlIdentifier() + ".Reference_Reference WHERE referenceId in (:ids)";
        String insertSql = String.join(" "
                , "INSERT INTO " + getTable().getSchema().getSqlIdentifier() + ".Reference_Reference(referenceId, referencesBy)"
                , "select id referenceId, (jsonb_array_elements_text((jsonb_each(refsLinkedTo)).value))::uuid referencesBy"
                , "from " + getTable().getSqlIdentifier()
                , "where id in (:ids)"
                , "ON CONFLICT ON CONSTRAINT \"Reference_Reference_PK\" DO NOTHING"
        );
        String sql = String.join(";", deleteSql, insertSql);
        Iterators.partition(uuids.stream().iterator(), Short.MAX_VALUE - 1)
                .forEachRemaining(uuidsByBatch -> getNamedParameterJdbcTemplate().execute(sql, ImmutableMap.of("ids", uuidsByBatch), PreparedStatement::execute));
    }

    public Map<Ltree, List<ReferenceValue>> getReferenceDisplaysById(Set<String> listOfIds) {
        if (listOfIds.isEmpty()) {
            return new HashMap<>();
        }
        String sql = "SELECT  DISTINCT '" + ReferenceValue.class.getName() + "' as \"@class\",  to_jsonb(r) as json \n" +
                "from " + getSchema().getSqlIdentifier() + ".data_reference dr\n" +
                "join " + getSchema().getSqlIdentifier() + ".\"data\" d on dr.dataid = d.id\n" +
                "join " + getTable().getSqlIdentifier() + " r on dr.referencesBy = r.id\n" +
                "where d.rowid in (:list)";
        final List<ReferenceValue> list = getNamedParameterJdbcTemplate()
                .query(sql, new MapSqlParameterSource().addValue("list", listOfIds), getJsonRowMapper());
        final Map<Ltree, List<ReferenceValue>> referencesValuesMap = list.stream()
                .collect(Collectors.groupingBy(
                                ReferenceValue::getNaturalKey
                        )
                );
        referencesValuesMap.putAll(list.stream()
                .collect(Collectors.groupingBy(
                                ReferenceValue::getHierarchicalKey
                        )
                )
        );
        return referencesValuesMap;
    }
}
