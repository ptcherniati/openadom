package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.rest.exceptions.application.NoSuchApplicationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ApplicationRepository extends JsonTableRepositoryTemplate<Application> {

    private static final String SELECT_APPLICATION =
            "SELECT '" + Application.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM "
                    + Application.class.getSimpleName() + " t WHERE id::text=:nameOrId or name=:nameOrId";

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + " (id, name, comment, referenceType, dataType, configuration, configFile) SELECT id, name,  comment, referenceType, dataType, configuration, configFile FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json)"
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, comment=EXCLUDED.comment, name=EXCLUDED.name, referenceType=EXCLUDED.referenceType, dataType=EXCLUDED.dataType, configuration=EXCLUDED.configuration, configFile=EXCLUDED.configFile"
                + " RETURNING id";
    }

    @Override
    protected SqlTable getTable() {
        return SqlSchema.main().application();
    }

    @Override
    protected Class<Application> getEntityClass() {
        return Application.class;
    }

    public Application findApplication(String nameOrId) {

        return tryFindApplication(nameOrId).orElseThrow(() -> new NoSuchApplicationException(nameOrId));
    }

    public Optional<Application> tryFindApplication(String nameOrId) {
        Optional<Application> result = getNamedParameterJdbcTemplate().query(SELECT_APPLICATION, new MapSqlParameterSource("nameOrId", nameOrId), getJsonRowMapper()).stream().findFirst();
        return result;
    }

    public Optional<Application> tryFindApplication(UUID id) {
        return tryFindApplication(id.toString());
    }

    public Application findApplication(UUID id) {
        return findApplication(id.toString());
    }
}