package fr.inra.oresing.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.MoreCollectors;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import org.apache.commons.collections4.keyvalue.DefaultMapEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class UserRepository extends JsonTableRepositoryTemplate<OreSiUser> implements fr.inra.oresing.domain.repository.user.file.UserRepository {
    @Autowired
    private OreSiApiRequestContext request;

    @Override
    protected String getUpsertQuery() {
        return """
                INSERT INTO %1$s (
                    id, login, password, email, accountstate,  authorizations, chartes
                )
                SELECT
                    id, lower(login), password, lower(email), accountstate, authorizations, chartes
                FROM json_populate_recordset(NULL::%1$s, :json::json)
                ON CONFLICT (id)
                DO UPDATE SET
                updateDate=current_timestamp,
                login=lower(EXCLUDED.login),
                password=EXCLUDED.password,
                email=lower(EXCLUDED.email),
                accountstate=EXCLUDED.accountstate,
                authorizations=EXCLUDED.authorizations,
                chartes=EXCLUDED.chartes RETURNING id"""
                .formatted(getTable().getSqlIdentifier());
    }

    @Override
    protected SqlTable getTable() {
        return OreSiSqlSchema.oreSiUser();
    }

    @Override
    protected Class<OreSiUser> getEntityClass() {
        return OreSiUser.class;
    }

    @Override
    public Optional<OreSiUser> findByLoginOrId(final String loginOrId) {
        try {
            Optional<OreSiUser> byLogin = findByLogin(loginOrId);
            if(byLogin.isPresent()){
                return byLogin;
            }
            return tryFindById(UUID.fromString(loginOrId));
        }catch (Exception e){
            return Optional.empty();
        }
    }

    public Optional<OreSiUser> findByLogin(final String login) {
        final String query = "SELECT '" + getEntityClass().getName() + "' as \"@class\",  to_jsonb(t) as json FROM " + getTable().getSqlIdentifier() + " t " +
                "WHERE lower(login) = lower(:login)";

        return getNamedParameterJdbcTemplate().query(query,
                        new MapSqlParameterSource("login", login), getJsonRowMapper()).stream()
                .collect(MoreCollectors.toOptional());
    }

    public Optional<OreSiUser> findByEmail(final String email) {
        final String query = "SELECT '" + getEntityClass().getName() + "' as \"@class\",  to_jsonb(t) as json FROM " + getTable().getSqlIdentifier() + " t " +
                "WHERE lower(email) = lower(:email)";

        return getNamedParameterJdbcTemplate().query(query,
                        new MapSqlParameterSource("email", email), getJsonRowMapper()).stream()
                .collect(MoreCollectors.toOptional());
    }

    public Optional<OreSiUser> findByLoginAndEmail(final String login, final String email) {
        final String query = "SELECT '" + getEntityClass().getName() + "' as \"@class\",  to_jsonb(t) as json FROM " + getTable().getSqlIdentifier() + " t " +
                "WHERE lower(email) = lower(:email) and lower(login) = lower(:login)";

        final MapSqlParameterSource mapSqlParameterSource = new MapSqlParameterSource("login", login);
        mapSqlParameterSource.addValue("email", email);
        return getNamedParameterJdbcTemplate().query(query,
                        mapSqlParameterSource, getJsonRowMapper()).stream()
                .collect(MoreCollectors.toOptional());
    }

    public Map<String, List<String>> getRolesGrantedToRoles(List<String> roles) {
        Map<String, List<String>> result = new HashMap<>();

        String query = """
                    WITH RECURSIVE role_grants AS (
                      SELECT r.oid, r.rolname, m.member, m.roleid
                      FROM pg_roles r
                      JOIN pg_auth_members m ON r.oid = m.roleid
                      WHERE r.rolname = ANY(:roleNames)
                    UNION ALL
                      SELECT r.oid, r.rolname, m.member, m.roleid
                      FROM pg_roles r
                      JOIN pg_auth_members m ON r.oid = m.member
                      JOIN role_grants rg ON m.roleid = rg.oid
                    )
                    SELECT rg.rolname AS granted_role, r.rolname AS granted_to_role
                    FROM role_grants rg
                    JOIN pg_roles r ON rg.member = r.oid
                    WHERE rg.member != rg.oid
                    ORDER BY rg.rolname, r.rolname;
                """;

        RowMapper<Map.Entry<String, String>> rowMapper = (rs, rowNum) ->
                new DefaultMapEntry<>(rs.getString("granted_role"), rs.getString("granted_to_role"));

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("roleNames", roles.toArray(new String[0]));

        List<Map.Entry<String, String>> queryResults = getNamedParameterJdbcTemplate().query(query, parameters, rowMapper);

        for (Map.Entry<String, String> entry : queryResults) {
            result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
        }

        return result;
    }

    public CurrentUserRoles getRolesForRole(final String role) {
        final String roleParam = role == null ? "\"current_user\"()" : String.format("\"%s\"", role);
        RowMapper<CurrentUserRoles> rowMapper = (rs, rowNum) -> {
            final String currentUser = rs.getString("currentUser");
            final List<String> memberOf = Arrays.stream((String[]) rs.getArray("memberOf").getArray())
                    .collect(Collectors.toList());
            final boolean isSuper = rs.getBoolean("isSuper");
            return new CurrentUserRoles(memberOf, isSuper, findByLogin(currentUser).orElse(null));
        };
        final String query = """
                WITH RECURSIVE membership_tree(grpid, userid, issuper) AS (
                    SELECT r.oid, r.oid, r.rolsuper issuper
                    FROM pg_roles r
                    UNION ALL
                    SELECT m_1.roleid, t_1.userid, t_1.issuper
                    FROM pg_auth_members m_1, membership_tree t_1
                    WHERE m_1.member = t_1.grpid
                )
                SELECT COALESCE(:rolename, CURRENT_USER) currentuser,r.rolname AS usrname,t.issuper issuper,
                       array_agg(DISTINCT m.rolname) memberof
                FROM membership_tree t, pg_roles r, pg_roles m
                WHERE t.grpid = m.oid AND t.userid = r.oid
                AND COALESCE(:rolename, CURRENT_USER)=r.rolname
                GROUP BY userid, r.rolname,t.issuper;""";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("rolename", role);
        CurrentUserRoles currentUserRoles;
        try {
            currentUserRoles = getNamedParameterJdbcTemplate().queryForObject(
                    query,
                    parameters,
                    rowMapper);
        } catch (InvalidDataAccessApiUsageException | EmptyResultDataAccessException e) {
            currentUserRoles = CurrentUserRoles.EMPTY;
        }
        assert currentUserRoles != null;
        Optional<OreSiUser> oreSiUser = Optional.ofNullable(role)
                .map(this::findByLoginOrId).orElse(null);
        if(Objects.requireNonNull(oreSiUser).isPresent()) {
            currentUserRoles = currentUserRoles.withUSer(oreSiUser.get());
        }
        return currentUserRoles;


    }

    public CurrentUserRoles getRolesForCurrentUser() {
        return Optional.ofNullable(request.getRequestClient())
                .map(OreSiRequestClient::role)
                .map(OreSiRole::getAsSqlRole)
                .map(this::getRolesForRole)
                .orElseGet(CurrentUserRoles::empty);
    }

    public CurrentUserRoles getRolesForCurrentUser(String userIdOrRoleName) {
        return getRolesForRole(userIdOrRoleName);
    }

    public void updateAuthorizations(final UUID userId, final Set<String> authorizations) {
        final String query = "update " + getTable().getSqlIdentifier() + " o\n" +
                "set authorizations = :authorizations\n" +
                "where id = :uuid::uuid\n";
        getNamedParameterJdbcTemplate().update(
                query,
                new MapSqlParameterSource("authorizations", authorizations.toArray(String[]::new))
                        .addValue("uuid", userId)
        );
    }

    public Optional<OreSiUser> findByLoginOrEmail(final String loginOrEmail) {
        final String query = "SELECT '" + getEntityClass().getName() + "' as \"@class\",  to_jsonb(t) as json FROM " + getTable().getSqlIdentifier() + " t WHERE  login = :loginOrEmail or email = :loginOrEmail";

        return getNamedParameterJdbcTemplate().query(query,
                        new MapSqlParameterSource("loginOrEmail", loginOrEmail), getJsonRowMapper()).stream()
                .collect(MoreCollectors.toOptional());
    }

    public OreSiUser setState(final UUID userId, final OreSiUser.OreSiUserStates accountstate) {
        final String query = "update " + getTable().getSqlIdentifier() + " o\n" +
                "set  accountstate = :accountstate::account_state\n" +
                "where id = :uuid::uuid\n";
        getNamedParameterJdbcTemplate().update(
                query,
                new MapSqlParameterSource("accountstate", accountstate.name())
                        .addValue("uuid", userId));
        return findById(userId);
    }

    public void updateNewDate(final OreSiUser oreSiUser, final Date newDate) {
        final String query = "update " + getTable().getSqlIdentifier() + " o\n" +
                "set  accountstate = :accountstate::account_state,\n" +
                "updatedate = :updateDate,\n" +
                "email = :email,\n" +
                "chartes = :chartes::jsonb,\n" +
                "password = :password\n" +
                "where id = :uuid::uuid\n";
        final ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        final String charte;
        try {
            charte = ow.writeValueAsString(oreSiUser.getChartes());
        } catch (JsonProcessingException e) {
            throw new OreSiTechnicalException(ExceptionMessage.JSON_PROCESSING.toMessage(), e);
        }
        getNamedParameterJdbcTemplate().update(
                query,
                new MapSqlParameterSource("accountstate", oreSiUser.getAccountstate().name())
                        .addValue("uuid", oreSiUser.getId())
                        .addValue("email", oreSiUser.getEmail())
                        .addValue("chartes", charte)
                        .addValue("updateDate", newDate)
                        .addValue("password", oreSiUser.getPassword()));
        findById(oreSiUser.getId());
    }

    public OreSiUser update(final OreSiUser oreSiUser) throws JsonProcessingException {
        final String query = """
                update %s o
                set  accountstate = :accountstate::account_state,
                email = :email,
                chartes = :chartes::jsonb,
                password = :password,
                "authorizations" = :authorizations::text[]
                where id = :uuid::uuid
                """.formatted(getTable().getSqlIdentifier());
        final ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        final String charte = ow.writeValueAsString(oreSiUser.getChartes());
        getNamedParameterJdbcTemplate().update(
                query,
                new MapSqlParameterSource("accountstate", oreSiUser.getAccountstate().name())
                        .addValue("uuid", oreSiUser.getId())
                        .addValue("email", oreSiUser.getEmail())
                        .addValue("chartes", charte)
                        .addValue("authorizations", oreSiUser.getAuthorizations().stream()
                                .collect(Collectors.joining(", ", "{", "}")))
                        .addValue("password", oreSiUser.getPassword()));
        return findById(oreSiUser.getId());
    }

    public void invalidateCharte(final UUID applicationId) {
        final String sql = """
                update %s
                set chartes = chartes - '%s'
                """.formatted(getTable().getSqlIdentifier(), applicationId.toString());
        getNamedParameterJdbcTemplate().getJdbcTemplate().execute(sql);
    }

}