package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.ReferenceValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OreSiRepository {

    private static final String BINARYFILE_UPSERT =
            "INSERT INTO BinaryFile (id, name, size, data) SELECT id, name, size, data FROM json_populate_record(NULL::BinaryFile, :json::json) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, name=EXCLUDED.name, size=EXCLUDED.size, data=EXCLUDED.data"
                    + " RETURNING id";

    private static final String APPLICATION_UPSERT =
            "INSERT INTO Application (id, name, referenceType, dataType, configuration, configFile) SELECT id, name, referenceType, dataType, configuration, configFile FROM json_populate_record(NULL::Application, :json::json)"
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, name=EXCLUDED.name, referenceType=EXCLUDED.referenceType, dataType=EXCLUDED.dataType, configuration=EXCLUDED.configuration, configFile=EXCLUDED.configFile"
                    + " RETURNING id";

    private static final String REFERENCEVALUE_UPSERT =
            "INSERT INTO ReferenceValue (id, application, referenceType, refValues, binaryFile) SELECT id, application, referenceType, refValues, binaryFile FROM json_populate_record(NULL::ReferenceValue, :json::json) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, referenceType=EXCLUDED.referenceType, refValues=EXCLUDED.refValues, binaryFile=EXCLUDED.binaryFile"
                    + " RETURNING id";

    private static final String DATA_UPSERT =
            "INSERT INTO Data (id, application, dataType, refsLinkedTo, dataValues, binaryFile) SELECT id, application, dataType, refsLinkedTo, dataValues, binaryFile FROM json_populate_record(NULL::Data, :json::json) "
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, dataType=EXCLUDED.dataType, refsLinkedTo=EXCLUDED.refsLinkedTo, dataValues=EXCLUDED.dataValues, binaryFile=EXCLUDED.binaryFile"
                    + " RETURNING id";

    private static final String SELECT_APPLICATION =
            "SELECT '" + Application.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
            + Application.class.getSimpleName() + " t WHERE id::text=:nameOrId or name=:nameOrId";

    private static final String SELECT_REFERENCE =
            "SELECT '" + ReferenceValue.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                    + ReferenceValue.class.getSimpleName() + " t WHERE application=:applicationId::uuid AND referenceType=:refType";

    private static final String TEMPLATE_SELECT_REFERENCE_COLUMN =
            "SELECT refValues->>'%s' FROM "
                    + ReferenceValue.class.getSimpleName() + " t WHERE application=:applicationId::uuid AND referenceType=:refType";

    private static final String SELECT_DATA =
            "SELECT '" + Data.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                    + Data.class.getSimpleName() + " t WHERE application=:applicationId::uuid AND dataType=:dataType";

    private static final String TEMPLATE_SELECT_ALL = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM %s t";
    private static final String TEMPLATE_SELECT_BY_ID = TEMPLATE_SELECT_ALL + " WHERE id=:id";

    private static final String TEMPLATE_DELETE = "DELETE FROM %s WHERE id=:id";

    @Autowired
    private JsonRowMapper<OreSiEntity> jsonRowMapper;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private Map<Class, String> sqlUpsert;

    public OreSiRepository() {
        sqlUpsert = Map.of(
                BinaryFile.class, BINARYFILE_UPSERT,
                Application.class, APPLICATION_UPSERT,
                ReferenceValue.class, REFERENCEVALUE_UPSERT,
                Data.class, DATA_UPSERT
        );
    }

    public UUID store(OreSiEntity e) {
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        String query = sqlUpsert.get(e.getClass());
        String json = jsonRowMapper.toJson(e);
        UUID result = namedParameterJdbcTemplate.queryForObject(
                query, new MapSqlParameterSource("json", json), UUID.class);
        return result;
    }

    /**
     * Supprime un objet dans la base
     * @param clazz la classe de l'objet a supprimer
     * @param id l'identifiant de l'objet a supprimer (peut-etre null, dans ce cas, rien n'est supprimer)
     * @return vrai si un objet a été supprimé
     */
    protected boolean delete(Class<? extends OreSiEntity> clazz, UUID id) {
        String query = String.format(TEMPLATE_DELETE, clazz.getSimpleName());
        int count = namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("id", id));
        return count > 0;
    }

    public boolean deleteBinaryFile(UUID id) {
        return delete(BinaryFile.class, id);
    }

    public boolean deleteApplication(UUID id) {
        return delete(Application.class, id);
    }

    public boolean deleteReferenceValue(UUID id) {
        return delete(ReferenceValue.class, id);
    }

    public boolean deleteData(UUID id) {
        return delete(Data.class, id);
    }

    public <E extends OreSiEntity> List<E> findAll(Class<E> entityType) {
        String query = String.format(TEMPLATE_SELECT_ALL, entityType.getName(), entityType.getSimpleName());
        List<OreSiEntity> result = namedParameterJdbcTemplate.query(query, jsonRowMapper);
        return (List<E>) result;
    }

    public <E extends OreSiEntity> Optional<E> findById(Class<E> entityType, UUID id) {
        String query = String.format(TEMPLATE_SELECT_BY_ID, entityType.getName(), entityType.getSimpleName());
        Optional<OreSiEntity> result = namedParameterJdbcTemplate.query(query, new MapSqlParameterSource("id", id), jsonRowMapper).stream().findFirst();
        return (Optional<E>)result;
    }

    public Optional<Application> findApplication(String nameOrId) {
        Optional result = namedParameterJdbcTemplate.query(SELECT_APPLICATION, new MapSqlParameterSource("nameOrId", nameOrId), jsonRowMapper).stream().findFirst();
        return (Optional<Application>)result;
    }

    public List<ReferenceValue> findReference(UUID applicationId, String refType) {
        String query = SELECT_REFERENCE;
        List result = namedParameterJdbcTemplate.query(query,  new MapSqlParameterSource("applicationId", applicationId).addValue("refType", refType), jsonRowMapper);
        return (List<ReferenceValue>) result;
    }

    public List<String> findReferenceValue(UUID applicationId, String refType, String column) {
        String query = String.format(TEMPLATE_SELECT_REFERENCE_COLUMN, column);
        List<String> result = namedParameterJdbcTemplate.queryForList(query,  new MapSqlParameterSource("applicationId", applicationId).addValue("refType", refType), String.class);
        return result;
    }

    public List<Data> findData(UUID applicationId, String dataType) {
        String query = SELECT_DATA;
        List result = namedParameterJdbcTemplate.query(query,  new MapSqlParameterSource("applicationId", applicationId).addValue("dataType", dataType), jsonRowMapper);
        return (List<Data>) result;
    }


}
