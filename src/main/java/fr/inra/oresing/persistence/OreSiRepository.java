package fr.inra.oresing.persistence;

import fr.inra.oresing.OreSiUtils;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.ReferenceType;
import fr.inra.oresing.model.ReferenceValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OreSiRepository {

    private static final String BINARYFILE_UPSERT =
            "INSERT INTO BinaryFile (id, name, size, data) values (:id, :name, :size, :data) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, name=EXCLUDED.name, size=EXCLUDED.size, data=EXCLUDED.data"
                    + " RETURNING id";

    private static final String APPLICATION_UPSERT =
            "INSERT INTO Application (id, name, config) values (:id, :name, :config) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, name=EXCLUDED.name, config=EXCLUDED.config"
                    + " RETURNING id";

    private static final String REFERENCETYPE_UPSERT =
            "INSERT INTO ReferenceType (id, application, description, file) values (:id, :application, :description, :binaryFile) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, description=EXCLUDED.description, binaryFile=EXCLUDED.binaryFile"
                    + " RETURNING id";

    private static final String REFERENCEVALUE_UPSERT =
            "INSERT INTO ReferenceValue (id, referenceType, label) values (:id, :referenceType, :label) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, referenceType=EXCLUDED.referenceType, label=EXCLUDED.label"
                    + " RETURNING id";

    private static final String DATA_UPSERT =
            "INSERT INTO Application (id, refs, jsonData) values (:id, :binaryFile, :refs, :jsonData) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, binaryFile=EXCLUDED.binaryFile, refs=EXCLUDED.refs, jsonData=EXCLUDED.jsonData"
                    + " RETURNING id";

    private static final String TEMPLATE_SELECT_ALL = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM %s t";
    private static final String TEMPLATE_SELECT_BY_ID = TEMPLATE_SELECT_ALL + " WHERE id=:id";

    @Autowired
    protected JsonRowMapper<OreSiEntity> jsonRowMapper;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    protected UUID storeEntity(OreSiEntity e, String query, Map<String, Object> paramMap) {
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        UUID result = namedParameterJdbcTemplate.queryForObject(
                query, paramMap, UUID.class);
        return result;
    }

    public UUID store(BinaryFile e) {
        Map<String, Object> paramMap = OreSiUtils.mapOf(e::getId, e::getName, e::getSize, e::getData);
        return storeEntity(e, BINARYFILE_UPSERT, paramMap);
    }

    public UUID store(Application e) {
        Map<String, Object> paramMap = OreSiUtils.mapOf(e::getId, e::getName, e::getConfig);
        return storeEntity(e, APPLICATION_UPSERT, paramMap);
    }

    public UUID store(ReferenceType e) {
        Map<String, Object> paramMap = OreSiUtils.mapOf(e::getId, e::getApplication, e::getDescription, e::getBinaryFile);
        return storeEntity(e, REFERENCETYPE_UPSERT, paramMap);
    }

    public UUID store(ReferenceValue e) {
        Map<String, Object> paramMap = OreSiUtils.mapOf(e::getId, e::getReferenceType, e::getLabel);
        return storeEntity(e, REFERENCEVALUE_UPSERT, paramMap);
    }

    public UUID store(Data e) {
        Map<String, Object> paramMap = OreSiUtils.mapOf(e::getId, e::getBinaryFile, e::getRefs, e::getJsonData);
        return storeEntity(e, DATA_UPSERT, paramMap);
    }

    public <E extends OreSiEntity> List<E> findAll(Class<E> entityType) {
        String query = String.format(TEMPLATE_SELECT_ALL, entityType.getName(), entityType.getSimpleName());
        List<OreSiEntity> result = namedParameterJdbcTemplate.query(query, jsonRowMapper);
        return (List<E>) result;
    }

    public <E extends OreSiEntity> Optional<E> findById(Class<E> entityType, UUID id) {
        String query = String.format(TEMPLATE_SELECT_BY_ID, entityType.getName(), entityType.getSimpleName());
        Optional<OreSiEntity> result = namedParameterJdbcTemplate.query(query, Map.of("id", id), jsonRowMapper).stream().findFirst();
        return (Optional<E>)result;
    }

}
