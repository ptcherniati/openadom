package fr.inra.oresing.persistence;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ReferenceValue;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, referenceType, compositeKey, refValues, binaryFile) SELECT id, application, referenceType, compositeKey, refValues, binaryFile FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, referenceType=EXCLUDED.referenceType, compositeKey=EXCLUDED.compositeKey, refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile"
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
        String query = "SELECT DISTINCT '" + ReferenceValue.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                + getTable().getSqlIdentifier() + " t, jsonb_each_text(t.refvalues) kv WHERE application=:applicationId::uuid AND referenceType=:refType";
        MapSqlParameterSource paramSource = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("refType", refType);

        AtomicInteger i = new AtomicInteger();
        // kv.value='LPF' OR t.refvalues @> '{"esp_nom":"ALO"}'::jsonb
        String cond = params.entrySet().stream().flatMap(e -> {
            String k = e.getKey();
            if (StringUtils.equalsAnyIgnoreCase("any", k)) {
                return e.getValue().stream().map(v -> {
                    String arg = ":arg" + i.getAndIncrement();
                    paramSource.addValue(arg, v);
                    return "kv.value=" + arg;
                });
            } else {
                return e.getValue().stream().map(v -> "t.refvalues @> '{\"" + k + "\":\"" + v + "\"}'::jsonb");
            }
        }).collect(Collectors.joining(" OR "));

        if (StringUtils.isNotBlank(cond)) {
            cond = " AND (" + cond + ")";
        }

        List result = getNamedParameterJdbcTemplate().query(query + cond, paramSource, getJsonRowMapper());
        return (List<ReferenceValue>) result;
    }

    public List<String> findReferenceValue(String refType, String column) {
        String sqlPattern = " SELECT refValues->>'%s' "
                + " FROM " + getTable().getSqlIdentifier() + " t"
                + " WHERE application=:applicationId::uuid AND referenceType=:refType";
        String query = String.format(sqlPattern, column);
        List<String> result = getNamedParameterJdbcTemplate().queryForList(query, new MapSqlParameterSource("applicationId", getApplication().getId()).addValue("refType", refType), String.class);
        return result;
    }

    public ImmutableMap<String, UUID> getReferenceIdPerKeys(String referenceType) {
        return findAllByReferenceType(referenceType).stream()
                .collect(ImmutableMap.toImmutableMap(ReferenceValue::getCompositeKey, ReferenceValue::getId));
    }

    public ImmutableMap<String, UUID> getReferenceIdPerColumnKeys(String referenceType, String keysColumnsSearch, String keyValueSearch) {
        return findAllByReferenceType(referenceType).stream()
                .collect(ImmutableMap.toImmutableMap(
                        ref->{
                            return Stream.of(keysColumnsSearch.split(","))
                                    .map(col -> ref.getRefValues().getOrDefault(col,""))
                                    .collect(Collectors.joining("."));
                        }
                        ,
                        ReferenceValue::getId)
                );
    }
}
