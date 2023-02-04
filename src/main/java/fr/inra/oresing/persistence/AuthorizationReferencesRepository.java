package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiReferenceAuthorization;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AuthorizationReferencesRepository extends JsonTableInApplicationSchemaRepositoryTemplate<OreSiReferenceAuthorization> {

    public AuthorizationReferencesRepository(Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().authorizationReference();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() +
                "(id, name, oreSiUsers, application, \"references\") \n" +
                "SELECT id, name, oreSiUsers, application, \"references\" \n" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) \n" +
                "ON CONFLICT (id) \n" +
                "DO UPDATE \n" +
                "SET updateDate=current_timestamp, name=EXCLUDED.name, \"references\"=EXCLUDED.\"references\", oreSiUsers=EXCLUDED.oreSiUsers"
                + " RETURNING id";
    }

    @Override
    protected Class<OreSiReferenceAuthorization> getEntityClass() {
        return OreSiReferenceAuthorization.class;
    }

    public List<OreSiReferenceAuthorization> findAuthorizations(UUID userId, Application application) {
        String query  = String.join("\n",
                "select '"+OreSiReferenceAuthorization.class.getName() +"' as \"@class\"   ,  to_jsonb(t) as json",
                "from " + getTable().getSqlIdentifier()+ " t",
                "where t.application = :applicationId",
               " and array[ :userId::entityref] <@ t.oresiusers"
        );
         MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", getApplication().getId())
               .addValue("userId", userId.toString());
        return getNamedParameterJdbcTemplate().query(query, sqlParams, getJsonRowMapper());
    }

    public List<OreSiReferenceAuthorization> findPublicAuthorizations() {
        String query  = String.join("\n",
                "select '"+OreSiReferenceAuthorization.class.getName() +"' as \"@class\"   ,  to_jsonb(t) as json",
                "from " + getTable().getSqlIdentifier()+ " t, public.oresiuser u",
                "where ARRAY[u.id]::entityref[] <@ oresiusers and u.login='_public_'");
        return getNamedParameterJdbcTemplate().query(query, Map.of(), getJsonRowMapper());
    }
}