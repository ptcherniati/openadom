package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RightsRequestRepository extends JsonTableInApplicationSchemaRepositoryTemplate<RightsRequest> {

    public RightsRequestRepository(final Application application) {
        super(application);
    }

    @Override
    public RightsRequest findById(final UUID id) {
        return tryFindById(id).orElse(null);
    }

    @Override
    public Optional<RightsRequest> tryFindById(final UUID id) {
        final SqlParameterSource parameters = new MapSqlParameterSource("id", id);
        return find("id = :id", parameters).stream().findFirst();
    }

    public Optional<RightsRequest> tryFindByIdWithData(final UUID id) {
        Preconditions.checkArgument(id != null);

        final String query = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT *
                            FROM %2$s
                            WHERE id = :id
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier()
        );

        return getNamedParameterJdbcTemplate().query(
                query,
                new MapSqlParameterSource("id", id),
                getJsonRowMapper()
        ).stream().findFirst();
    }

    public List<RightsRequest> findAllByWhereClause(final String whereClause, final SqlParameterSource sqlParameterSource) {
        return find(whereClause, sqlParameterSource);
    }

    protected List<RightsRequest> find(final String whereClause, SqlParameterSource sqlParameterSource) {
        SqlParameterSource finalSqlParameterSource = sqlParameterSource != null ? sqlParameterSource : new MapSqlParameterSource();

        String sql = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT * 
                            FROM %2$s
                            %3$s
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier(),
                (whereClause != null && !"()".equals(whereClause)) ? "WHERE " + whereClause : ""
        );

        return getNamedParameterJdbcTemplate().query(sql, finalSqlParameterSource, getJsonRowMapper());
    }

    @Override
    public SqlTable getTable() {
        return getSchema().rightsRequest();
    }

    @Override
    protected String getUpsertQuery() {
        return String.format("""
                        INSERT INTO %1$s AS t (
                            id, creationdate, updatedate, application, "user", comment, 
                            rightsRequestForm, rightsRequest, setted
                        )
                        SELECT 
                            id,
                            COALESCE(creationdate, now()),
                            COALESCE(updatedate, now()),
                            application,
                            "user",
                            comment,
                            rightsRequestForm,
                            rightsRequest,
                            COALESCE(setted, false)
                        FROM json_populate_recordset(NULL::%1$s, :json::json) 
                        ON CONFLICT (id)
                        DO UPDATE SET 
                            updatedate = current_timestamp,
                            rightsRequestForm = EXCLUDED.rightsRequestForm,
                            rightsRequest = EXCLUDED.rightsRequest,
                            setted = EXCLUDED.setted
                        RETURNING id
                        """,
                getTable().getSqlIdentifier()
        );
    }

    @Override
    protected Class<RightsRequest> getEntityClass() {
        return RightsRequest.class;
    }

    public List<RightsRequest> findByCriteria(final RightsRequestSearchHelper rightsrequestSearchHelper) {
        String sql = String.format("""
                        SELECT '%1$s' AS "@class", to_jsonb(t) AS json
                        FROM (
                            SELECT * 
                            FROM %2$s
                            %3$s
                        ) t
                        """,
                getEntityClass().getName(),
                getTable().getSqlIdentifier(),
                rightsrequestSearchHelper.buildRequest("", "")
        );

        return getNamedParameterJdbcTemplate().query(
                sql,
                rightsrequestSearchHelper.getParamSource(),
                getJsonRowMapper()
        );
    }

}