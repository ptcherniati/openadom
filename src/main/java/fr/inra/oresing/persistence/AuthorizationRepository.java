package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiAuthorization;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

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
        return "INSERT INTO " + getTable().getSqlIdentifier() +
                "(id, oreSiUsers, application, dataType, authorizations) \n" +
                "SELECT id, oreSiUsers, application, dataType, authorizations \n" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) \n" +
                "ON CONFLICT (id) \n" +
                "DO UPDATE \n" +
                "SET updateDate=current_timestamp, authorizations=EXCLUDED.authorizations, oreSiUsers=EXCLUDED.oreSiUsers"
                + " RETURNING id";
    }

    @Override
    protected Class<OreSiAuthorization> getEntityClass() {
        return OreSiAuthorization.class;
    }

    public List<OreSiAuthorization> findByDataType(String dataType) {
        return findByPropertyEquals("dataType", dataType);
    }
}