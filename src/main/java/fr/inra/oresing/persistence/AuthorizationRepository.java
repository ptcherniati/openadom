package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiAuthorization;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                "(id, name, oreSiUsers, application, dataType, authorizations) \n" +
                "SELECT id, name, oreSiUsers, application, dataType, authorizations \n" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) \n" +
                "ON CONFLICT (id) \n" +
                "DO UPDATE \n" +
                "SET updateDate=current_timestamp, name=EXCLUDED.name, authorizations=EXCLUDED.authorizations, oreSiUsers=EXCLUDED.oreSiUsers"
                + " RETURNING id";
    }

    @Override
    protected Class<OreSiAuthorization> getEntityClass() {
        return OreSiAuthorization.class;
    }

    public List<OreSiAuthorization> findByDataType(String dataType) {
        return findByPropertyEquals("dataType", dataType);
    }

    public List<OreSiAuthorization> findAuthorizations(UUID userId, Application application, String dataType) {
        String query  = String.join("\n",
                "select '"+OreSiAuthorization.class.getName() +"' as \"@class\"   ,  to_jsonb(t) as json",
                "from " + getTable().getSqlIdentifier()+ " t",
                "where t.application = :applicationId",
               " and t.dataType = :dataType",
               " and array[ :userId::entityref] <@ t.oresiusers"
        );
         MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("dataType", dataType)
                .addValue("userId", userId.toString());
        return getNamedParameterJdbcTemplate().query(query, sqlParams, getJsonRowMapper());
    }

    public List<OreSiAuthorization> findPublicAuthorizations() {
        String query  = String.join("\n",
                "select '"+OreSiAuthorization.class.getName() +"' as \"@class\"   ,  to_jsonb(t) as json",
                "from " + getTable().getSqlIdentifier()+ " t, public.oresiuser u",
                "where ARRAY[u.id]::entityref[] <@ oresiusers and u.login='_public_'");
        return getNamedParameterJdbcTemplate().query(query, Map.of(), getJsonRowMapper());
    }
}