package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.rest.NoSuchApplicationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class OreSiRepository implements InitializingBean {

    private static final String APPLICATION_UPSERT =
            "INSERT INTO Application (id, name, referenceType, dataType, configuration, configFile) SELECT id, name, referenceType, dataType, configuration, configFile FROM json_populate_record(NULL::Application, :json::json)"
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, name=EXCLUDED.name, referenceType=EXCLUDED.referenceType, dataType=EXCLUDED.dataType, configuration=EXCLUDED.configuration, configFile=EXCLUDED.configFile"
                    + " RETURNING id";

    private static final String SELECT_APPLICATION =
            "SELECT '" + Application.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
            + Application.class.getSimpleName() + " t WHERE id::text=:nameOrId or name=:nameOrId";

    private static final String TEMPLATE_SELECT_ALL = "SELECT '%s' as \"@class\",  to_jsonb(t) as json FROM %s t";
    private static final String TEMPLATE_SELECT_BY_ID = TEMPLATE_SELECT_ALL + " WHERE id=:id";

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    private JsonRowMapper<OreSiEntity> jsonRowMapper;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private Map<Class, String> sqlUpsert = Map.of(
            Application.class, APPLICATION_UPSERT
    );

    @Override
    public void afterPropertiesSet() {
        // pour force la recuperation petit a petit et pas tout en meme temps (probleme memoire)
        namedParameterJdbcTemplate.getJdbcTemplate().setFetchSize(1000);
    }

    public UUID store(OreSiEntity e) {
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        String query = Objects.requireNonNull(sqlUpsert.get(e.getClass()));
        String json = jsonRowMapper.toJson(e);
        UUID result = namedParameterJdbcTemplate.queryForObject(
                query, new MapSqlParameterSource("json", json), UUID.class);
        return result;
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

    public Application findApplication(String nameOrId) {
        return tryFindApplication(nameOrId).orElseThrow(() -> new NoSuchApplicationException(nameOrId));
    }

    public Optional<Application> tryFindApplication(String nameOrId) {
        Optional result = namedParameterJdbcTemplate.query(SELECT_APPLICATION, new MapSqlParameterSource("nameOrId", nameOrId), jsonRowMapper).stream().findFirst();
        return (Optional<Application>) result;
    }

    public Optional<Application> tryFindApplication(UUID nameOrId) {
        return tryFindApplication(nameOrId.toString());
    }

    public Application findApplication(UUID applicationId) {
        return findApplication(applicationId.toString());
    }

    public List<ReferenceValue> findReference(UUID applicationId, String refType) {
        return findReference(applicationId, refType, new LinkedMultiValueMap<>());
    }

    /**
     *
     * @param applicationId l'id de l'application
     * @param refType le type du referenciel
     * @param params les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<ReferenceValue> findReference(UUID applicationId, String refType, MultiValueMap<String, String> params) {
        Application application = findApplication(applicationId);
        ApplicationRepository applicationRepository = getRepository(application);
        return applicationRepository.findReference(applicationId, refType, params);
    }

    public List<String> findReferenceValue(UUID applicationId, String refType, String column) {
        Application application = findApplication(applicationId);
        ApplicationRepository applicationRepository = getRepository(application);
        return applicationRepository.findReferenceValue(applicationId, refType, column);
    }

    public List<Data> findData(UUID applicationId, String dataType, List<UUID> ... nuppletRefs) {
        Application application = findApplication(applicationId);
        ApplicationRepository applicationRepository = getRepository(application);
        return applicationRepository.findData(applicationId, dataType, nuppletRefs);
    }

    public ApplicationRepository getRepository(Application application) {
        return beanFactory.getBean(ApplicationRepository.class, application);
    }
}
