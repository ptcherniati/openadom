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
        final String query = String.format("SELECT '%s' as \"@class\", to_jsonb(t) as json " +
                "FROM (select *  \n" +
                " from %s  WHERE id = :id) t", getEntityClass().getName(), getTable().getSqlIdentifier());
        final Optional<RightsRequest> result = getNamedParameterJdbcTemplate().query(query, new MapSqlParameterSource("id", id), getJsonRowMapper()).stream().findFirst();
        return result;
    }
    public List<RightsRequest> findAllByWhereClause(final String whereClause, final SqlParameterSource sqlParameterSource){
        return find(whereClause,sqlParameterSource);
    }

    protected List<RightsRequest> find(final String whereClause, SqlParameterSource sqlParameterSource) {
        SqlParameterSource sqlParameterSource1 = sqlParameterSource;
        if(sqlParameterSource1 ==null){
            sqlParameterSource1 = new MapSqlParameterSource();
        }
        String sql = """
                SELECT '%s' as "@class",  to_jsonb(t) as json\s
                FROM (select * \s
                from %s\s""";
        if (whereClause != null && !"()".equals(whereClause)) {
            sql += " WHERE " + whereClause;
        }
        sql += ") t";
        final String query = String.format(sql, getEntityClass().getName(), getTable().getSqlIdentifier());
        final List<RightsRequest> result = getNamedParameterJdbcTemplate().query(query, sqlParameterSource1, getJsonRowMapper());
        return result;
    }

    @Override
    public SqlTable getTable() {
        return getSchema().rightsRequest();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + " AS t (id,creationdate,updatedate,\n" +
                "application,\"user\", comment, rightsRequestForm, rightsRequest, setted)\n" +
                "select id,\n" +
                "COALESCE(creationdate,now()),\n" +
                "COALESCE(updatedate,now()),\n" +
                "application,\n" +
                "\"user\",\n" +
                "comment,\n" +
                "rightsRequestForm,\n" +
                "rightsRequest,\n" +
                "COALESCE(setted,false)\n" +
                "FROM json_populate_recordset(NULL::"+getTable().getSqlIdentifier()+", \n" +
                ":json::json) \n" +
                "ON CONFLICT (id)\n" +
                "DO UPDATE\n" +
                "set updatedate=current_timestamp,\n" +
                "rightsRequestForm=EXCLUDED.rightsRequestForm,\n" +
                "rightsRequest=EXCLUDED.rightsRequest,\n" +
                "setted=EXCLUDED.setted\n" +
                "returning id;";
    }

    @Override
    protected Class<RightsRequest> getEntityClass() {
        return RightsRequest.class;
    }

    public List<RightsRequest> findByCriteria(final RightsRequestSearchHelper rightsrequestSearchHelper) {
        final String sql = """
                SELECT '%s' as "@class",  to_jsonb(t) as json\s
                FROM (select * \s
                from %s\s""";
        String query = rightsrequestSearchHelper.buildRequest(sql, ") t");
        query = String.format(query, getEntityClass().getName(), getTable().getSqlIdentifier());
        return getNamedParameterJdbcTemplate().query(query, rightsrequestSearchHelper.getParamSource(), getJsonRowMapper());
    }
}