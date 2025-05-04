package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.additionalfiles.OreSiAdditionalFileAuthorization;
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
public class AuthorizationAdditionalFilesRepository extends JsonTableInApplicationSchemaRepositoryTemplate<OreSiAdditionalFileAuthorization> {

    public AuthorizationAdditionalFilesRepository(final Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().authorizationAdditionalFiles();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() +
                "(id, name, oreSiUsers, application, additionalFiles) \n" +
                "SELECT id, name, oreSiUsers, application, additionalFiles \n" +
                "FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) \n" +
                "ON CONFLICT (id) \n" +
                "DO UPDATE \n" +
                "SET updateDate=current_timestamp, name=EXCLUDED.name, additionalFiles=EXCLUDED.additionalFiles, oreSiUsers=EXCLUDED.oreSiUsers"
                + " RETURNING id";
    }

    @Override
    protected Class<OreSiAdditionalFileAuthorization> getEntityClass() {
        return OreSiAdditionalFileAuthorization.class;
    }

    public List<OreSiAdditionalFileAuthorization> findAuthorizations(final UUID userId, final Application application) {
        final String query = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM %2$s t
                        WHERE t.application = :applicationId
                          AND array[:userId::entityref] <@ t.oresiusers
                        """,
                OreSiAdditionalFileAuthorization.class.getName(),
                getTable().getSqlIdentifier()
        );

        final MapSqlParameterSource sqlParams = new MapSqlParameterSource("applicationId", getApplication().getId())
                .addValue("userId", userId.toString());
        return getNamedParameterJdbcTemplate().query(query, sqlParams, getJsonRowMapper());
    }

    public List<OreSiAdditionalFileAuthorization> findPublicAuthorizations() {
        final String query = String.format("""
                        SELECT
                            '%1$s' AS "@class",
                            to_jsonb(t) AS json
                        FROM %2$s t, public.oresiuser u
                        WHERE
                            ARRAY[u.id]::entityref[] <@ oresiusers
                            AND u.login = '_public_'
                        """,
                OreSiAdditionalFileAuthorization.class.getName(),
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().query(query, Map.of(), getJsonRowMapper());
    }

}