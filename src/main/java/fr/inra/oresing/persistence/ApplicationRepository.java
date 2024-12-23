package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.persistence.index.AuthorizationIndex;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ApplicationRepository extends JsonTableRepositoryTemplate<Application> {

    private static final String SELECT_APPLICATION =
            """
                    SELECT '%1$s' as "@class",
                        to_jsonb(t) as json
                        FROM %2$s t
                        WHERE id::text=:nameOrId or name=:nameOrId
                    """.formatted(Application.class.getName(), Application.class.getSimpleName());


    @Override
    protected String getUpsertQuery() {
        return """
                INSERT INTO %1$s
                (id, name, "data", additionalFiles, configuration, configFile)
                SELECT id, name,  "data", additionalFiles, configuration, configFile
                FROM json_populate_recordset(NULL::%1$s, :json::json)
                ON CONFLICT (id) DO UPDATE
                SET updateDate=current_timestamp, name=EXCLUDED.name, "data"=EXCLUDED."data", additionalFiles=EXCLUDED.additionalFiles, configuration=EXCLUDED.configuration, configFile=EXCLUDED.configFile
                RETURNING id""".formatted(getTable().getSqlIdentifier());

    }

    @Override
    protected SqlTable getTable() {
        return OreSiSqlSchema.application();
    }

    @Override
    protected Class<Application> getEntityClass() {
        return Application.class;
    }

    public Application findApplication(final String nameOrId) {

        return tryFindApplication(nameOrId).orElseThrow(() -> new NoSuchApplicationException(nameOrId));
    }

    public Optional<Application> tryFindApplication(final String nameOrId) {
        return getNamedParameterJdbcTemplate()
                .query(
                        SELECT_APPLICATION,
                        new MapSqlParameterSource(
                                "nameOrId", nameOrId),
                        getJsonRowMapper()
                ).stream()
                .findFirst();
    }

    public Optional<Application> tryFindApplication(final UUID id) {
        return tryFindApplication(id.toString());
    }

    public Application findApplication(final UUID id) {
        return findApplication(id.toString());
    }

    public boolean addReferenceToAuthorizationScope(String applicationName, Collection<String> newDataIdentifiers) {
        Function<String, String> buildQueryAddIdentifier = identifier->buildQueryAddIdentifier(applicationName, identifier);
        String query = newDataIdentifiers.stream()
                .map(buildQueryAddIdentifier)
                .collect(Collectors.joining("\n"));
        return getNamedParameterJdbcTemplate().execute(query, PreparedStatement::execute);
    }

    private String buildQueryAddIdentifier(String applicationName, String identifier) {
        return """
                    alter type %1$s.requiredauthorizations add attribute %2$s ltree;"""
                .formatted(applicationName, identifier);
    }

    public int updateAuthorizationIndexes(Application application) {
        AuthorizationIndex authorizationIndex = new AuthorizationIndex(application);
        String sql = authorizationIndex.dropIndexes();
        int updateAuthorizationIndexes = getNamedParameterJdbcTemplate().update(sql, Map.of());
        authorizationIndex.createIndexes();
        return updateAuthorizationIndexes;
    }
}