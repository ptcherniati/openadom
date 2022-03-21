package fr.inra.oresing.persistence;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ReferenceValue;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
        return "INSERT INTO " + getTable().getSqlIdentifier() + "\n" + "(id, application, referenceType, hierarchicalKey, hierarchicalReference, naturalKey, refsLinkedTo, refValues, binaryFile) \n" + "SELECT id, application, referenceType, hierarchicalKey, hierarchicalReference, naturalKey, refsLinkedTo, refValues, binaryFile \n" + "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", \n" + ":json::json) \n" + " ON CONFLICT ON CONSTRAINT \"hierarchicalKey_uniqueness\" \n" + "DO UPDATE SET updateDate=current_timestamp, hierarchicalKey=EXCLUDED.hierarchicalKey, hierarchicalReference=EXCLUDED.hierarchicalReference, naturalKey=EXCLUDED.naturalKey, refsLinkedTo=EXCLUDED.refsLinkedTo, refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile" + " RETURNING id";
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
        MultiValueMap<String, String> toto = new LinkedMultiValueMap<>();
        String query = "SELECT DISTINCT '" + ReferenceValue.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM " + getTable().getSqlIdentifier() + " t, jsonb_each_text(t.refvalues) kv WHERE application=:applicationId::uuid AND referenceType=:refType";
        MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", getApplication().getId()).addValue("refType", refType);

        AtomicInteger i = new AtomicInteger();
        // kv.value='LPF' OR t.refvalues @> '{"esp_nom":"ALO"}'::jsonb
        String cond = params.entrySet().stream().flatMap(e -> {
            String k = e.getKey();
            if (StringUtils.equalsAnyIgnoreCase("_row_id_", k)) {
                String collect = e.getValue().stream().map(v -> {
                    String arg = ":arg" + i.getAndIncrement();
                    paramSource.addValue(arg, v);
                    return String.format("'%s'::uuid", v);
                }).collect(Collectors.joining(", "));
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
        }).filter(k -> k != null).collect(Collectors.joining(" OR "));

        if (StringUtils.isNotBlank(cond)) {
            cond = " AND (" + cond + ")";
        }

        List result = getNamedParameterJdbcTemplate().query(query + cond, paramSource, getJsonRowMapper());
        return (List<ReferenceValue>) result;
    }

    public List<List<String>> findReferenceValue(String refType, String column) {
        AtomicInteger ai = new AtomicInteger(0);
        String select = Stream.of(column.split(",")).map(c -> String.format("refValues->>'%1$s' as \"%1$s" + ai.getAndIncrement() + "\"", c)).collect(Collectors.joining(", "));
        String sqlPattern = " SELECT %s " + " FROM " + getTable().getSqlIdentifier() + " t" + " WHERE application=:applicationId::uuid AND referenceType=:refType";
        String query = String.format(sqlPattern, select);
        List<List<String>> result = getNamedParameterJdbcTemplate().queryForList(query, new MapSqlParameterSource("applicationId", getApplication().getId()).addValue("refType", refType)).stream().map(m -> m.values().stream().map(v -> (String) v).collect(Collectors.toList())).collect(Collectors.toList());
        return result;
    }

    public ImmutableMap<Ltree, UUID> getReferenceIdPerKeys(String referenceType) {
        return findAllByReferenceType(referenceType).stream().collect(ImmutableMap.toImmutableMap(ReferenceValue::getHierarchicalKey, ReferenceValue::getId));
    }

    public void updateConstraintForeignReferences(List<UUID> uuids) {
        String deleteSql = "DELETE FROM " + getTable().getSchema().getSqlIdentifier() + ".Reference_Reference WHERE referenceId in (:ids)";
        String insertSql = String.join(" "
                , "INSERT INTO " + getTable().getSchema().getSqlIdentifier() + ".Reference_Reference(referenceId, referencedBy)"
                , "select id referenceId, (jsonb_array_elements_text((jsonb_each(refsLinkedTo)).value))::uuid referencedBy"
                , "from " + getTable().getSqlIdentifier()
                , "where id in (:ids)"
                , "ON CONFLICT ON CONSTRAINT \"Reference_Reference_PK\" DO NOTHING"
        );
        String sql = String.join(";", insertSql, deleteSql);
        Iterators.partition(uuids.stream().iterator(), Short.MAX_VALUE - 1)
                .forEachRemaining(uuidsByBatch -> getNamedParameterJdbcTemplate().execute(sql, ImmutableMap.of("ids", uuidsByBatch), PreparedStatement::execute));
    }
}
