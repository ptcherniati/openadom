package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiAuthorization;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AuthorizationRepository extends JsonTableInApplicationSchemaRepositoryTemplate<OreSiAuthorization> {

    public AuthorizationRepository(Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().authorization();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, oreSiUser, application, dataType, dataGroup, authorizedScopes, timeScope) SELECT id, oreSiUser, application, dataType, dataGroup, authorizedScopes, timeScope FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, authorizedScopes=EXCLUDED.authorizedScopes, timeScope=EXCLUDED.timeScope"
                + " RETURNING id";
    }

    @Override
    protected Class<OreSiAuthorization> getEntityClass() {
        return OreSiAuthorization.class;
    }
}
