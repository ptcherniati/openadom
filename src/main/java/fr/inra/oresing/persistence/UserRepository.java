package fr.inra.oresing.persistence;

import fr.inra.oresing.model.OreSiUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRepository extends JsonTableRepositoryTemplate<OreSiUser> {

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + " (id, login, password) SELECT id, login, password FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json)"
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, login=EXCLUDED.login, password=EXCLUDED.password"
                + " RETURNING id";
    }
    
    @Override
    protected SqlTable getTable() {
        return SqlSchema.main().oreSiUser();
    }

    @Override
    protected Class<OreSiUser> getEntityClass() {
        return OreSiUser.class;
    }

    public OreSiUser login(String login, String password) throws Throwable {
        String query = "SELECT '" + getEntityClass().getName() + "' as \"@class\",  to_jsonb(t) as json FROM " + getTable().getSqlIdentifier() + " t WHERE login=:login AND password=:password";
        Optional result = getNamedParameterJdbcTemplate().query(query,
                new MapSqlParameterSource("login", login).addValue("password", password), getJsonRowMapper()).stream().findFirst();
        return (OreSiUser)result.orElseThrow(SecurityException::new);
    }
}
