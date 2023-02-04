package fr.inra.oresing.persistence;

import com.google.common.collect.MoreCollectors;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.roles.CurrentUserRoles;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class UserRepository extends JsonTableRepositoryTemplate<OreSiUser> {
    @Autowired
    private OreSiApiRequestContext request;

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + " (id, login, password, authorizations) SELECT id, login, password, authorizations FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json)"
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, login=EXCLUDED.login, password=EXCLUDED.password, authorizations=EXCLUDED.authorizations"
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

    public Optional<OreSiUser> findByLogin(String login) {
        String query = "SELECT '" + getEntityClass().getName() + "' as \"@class\",  to_jsonb(t) as json FROM " + getTable().getSqlIdentifier() + " t WHERE login = :login";

        Optional<OreSiUser> result = getNamedParameterJdbcTemplate().query(query,
                new MapSqlParameterSource("login", login), getJsonRowMapper()).stream()
                .collect(MoreCollectors.toOptional());
        return result;
    }

    public CurrentUserRoles getRolesForRole(String role) {
        String roleParam = role==null?"\"current_user\"()":String.format("\"%s\"", role);
        final RowMapper<CurrentUserRoles> rowMapper = new RowMapper<>() {

            @Override
            public CurrentUserRoles mapRow(ResultSet rs, int rowNum) throws SQLException {
                String currentUser = rs.getString("currentUser");
                List<String> memberOf = Arrays.stream((String[])rs.getArray("memberOf").getArray())
                        .collect(Collectors.toList());
                Boolean isSuper = rs.getBoolean("isSuper");
                return new CurrentUserRoles(currentUser, memberOf, isSuper);
            }
        };
        String query = "WITH RECURSIVE membership_tree(grpid, userid, isSuper) AS (\n" +
                "    SELECT r.oid, r.oid, r.rolsuper isSuper\n" +
                "    FROM pg_roles r\n" +
                "    UNION ALL\n" +
                "    SELECT m_1.roleid, t_1.userid, t_1.isSuper\n" +
                "    FROM pg_auth_members m_1, membership_tree t_1\n" +
                "    WHERE m_1.member = t_1.grpid\n" +
                ")\n" +
                "SELECT COALESCE(:roleName, CURRENT_USER) \"currentUser\",r.rolname AS usrname,t.isSuper \"isSuper\",\n" +
                "       array_agg(m.rolname) memberOf\n" +
                "FROM membership_tree t, pg_roles r, pg_roles m\n" +
                "WHERE t.grpid = m.oid AND t.userid = r.oid\n" +
                "and COALESCE(:roleName, CURRENT_USER)=r.rolname\n" +
                "group by userid, r.rolname,t.isSuper;";
        final Map<String, String> parameters =new HashMap<>();
        parameters.put("roleName", role);
        final CurrentUserRoles currentUserRoles = getNamedParameterJdbcTemplate().queryForObject(
                query,
                parameters, rowMapper);
        Optional.ofNullable(currentUserRoles).map(CurrentUserRoles::getCurrentUser).map(this::findByLogin).ifPresent(currentUserRoles::setUserOptional);
        return currentUserRoles;


    }
    public CurrentUserRoles getRolesForCurrentUser(){
        final OreSiRoleToAccessDatabase role = request.getRequestClient().getRole();
        final OreSiUser user = findById(UUID.fromString(role.getAsSqlRole()));
        return getRolesForRole(role.getAsSqlRole());
    }

    public int updateAuthorizations(UUID userId, List<String> authorizations) {
        String query = "update "+getTable().getSqlIdentifier()+" o\n" +
                "set authorizations = :authorizations\n" +
                "where id = :uuid::uuid\n";
        return getNamedParameterJdbcTemplate().update(
                query,
                new MapSqlParameterSource("authorizations", authorizations.toArray(String[]::new))
                        .addValue("uuid",userId )
        );
    }
}