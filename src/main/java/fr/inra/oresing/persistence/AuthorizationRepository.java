package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
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

    public AuthorizationRepository(final Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().authorization();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() +
                "(id, name, description, oreSiUsers, application,  authorizations) \n" +
                "SELECT id, name, description, oreSiUsers, application,  authorizations \n" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) \n" +
                "ON CONFLICT (id) \n" +
                "DO UPDATE \n" +
                "SET updateDate=current_timestamp, name=EXCLUDED.name, description=EXCLUDED.description, authorizations=EXCLUDED.authorizations, oreSiUsers=EXCLUDED.oreSiUsers"
                + " RETURNING id";
    }

    @Override
    protected Class<OreSiAuthorization> getEntityClass() {
        return OreSiAuthorization.class;
    }

    public List<OreSiAuthorization> findAuthorizationsByUserId(final UUID userId) {
        if (userId == null) {
            return List.of();
        }

        final String query = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM %2$s t
                        WHERE t.application = :applicationId
                          AND array[:userId::entityref] <@ t.oresiusers
                        """,
                OreSiAuthorization.class.getName(),
                getTable().getSqlIdentifier()
        );

        final MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("userId", userId.toString());

        return getNamedParameterJdbcTemplate().query(query, sqlParams, getJsonRowMapper());
    }

    public List<OreSiAuthorization> findPublicAuthorizations() {
        final String query = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM %2$s t, public.oresiuser u
                        WHERE ARRAY[u.id]::entityref[] <@ oresiusers
                          AND u.login = '_public_'
                        """,
                OreSiAuthorization.class.getName(),
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().query(query, Map.of(), getJsonRowMapper());
    }

}