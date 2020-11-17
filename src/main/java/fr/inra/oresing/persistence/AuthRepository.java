package fr.inra.oresing.persistence;

import fr.inra.oresing.OreSiUserRole;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.OreSiUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class AuthRepository {

    private static final String RESET_ROLE = "RESET ROLE";
    private static final String SET_ROLE = "SET LOCAL ROLE \":role\"";
    private static final String CREATE_ROLE = "CREATE ROLE \":role\"";
    private static final String REMOVE_ROLE = "DROP ROLE \":role\"";
    private static final String CREATE_SELECT_POLICY =
            "CREATE POLICY \":user_Data_select\" ON Data AS RESTRICTIVE" +
            "            FOR SELECT TO \":user\"" +
            "            USING ( NOT refsLinkedTo && ARRAY[:uuids] );";
    private static final String ADD_USER_IN_ROLE = "GRANT \":role\" TO \":user\"";
    private static final String ADD_USER_IN_ROLE_AS_ADMIN = "GRANT \":role\" TO \":user\" WITH ADMIN OPTION";

    private static final String USER_UPSERT =
            "INSERT INTO OreSiUser (id, login, password) SELECT id, login, password FROM json_populate_record(NULL::OreSiUser, :json::json)"
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, login=EXCLUDED.login, password=EXCLUDED.password"
                    + " RETURNING id";

    private static final String SELECT_USER_FOR_AUTHENTICATION = "SELECT '" + OreSiUser.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM OreSiUser t WHERE login=:login AND password=:password";

    private static final String SELECT_USER = "SELECT '" + OreSiUser.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM OreSiUser t WHERE id=:id";

    private static final String DELETE_USER = "DELETE FROM OreSiUser WHERE id=:id";

    @Autowired
    private JsonRowMapper<OreSiEntity> jsonRowMapper;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    /**
     * Reprend le role de l'utilisateur utilisé pour la connexion à la base de données
     */
    @Transactional
    public void resetRole() {
        namedParameterJdbcTemplate.execute(RESET_ROLE,
                PreparedStatement::execute);
    }

    /**
     * Prend le role du superadmin qui a le droit de tout faire
     */
    @Transactional
    public void setRoleAdmin() {
        setRole(OreSiUserRole.superadmin());
    }

    /**
     * Prend le role du user passe en parametre, les requetes suivant ne pourra
     * pas faire des choses que l'utilisateur n'a pas le droit de faire
     */
    @Transactional
    public void setRole(OreSiUserRole userRole) {
        // faire attention au SQL injection
        String sql = SET_ROLE.replaceAll(":role", userRole.getAsSqlRole());
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    /**
     * verifie que l'utilisateur existe et que son mot de passe est le bon
     * @return l'objet OreSiUser contenant les informations sur l'utilisateur identifié
     */
    @Transactional
    public OreSiUser login(String login, String password) throws Throwable {
        String query = SELECT_USER_FOR_AUTHENTICATION;
        Optional result = namedParameterJdbcTemplate.query(query,
                new MapSqlParameterSource("login", login).addValue("password", password), jsonRowMapper).stream().findFirst();
        return (OreSiUser)result.orElseThrow(SecurityException::new);
    }

    /**
     * Permet de créer un nouvel utilisateur
     * @return l'objet OreSiUser qui vient d'être créé
     */
    @Transactional
    public OreSiUser createUser(String login, String password) {
        OreSiUser result = new OreSiUser();
        result.setLogin(login);
        result.setPassword(password);

        String query = USER_UPSERT;
        String json = jsonRowMapper.toJson(result);
        UUID id = namedParameterJdbcTemplate.queryForObject(
                query, new MapSqlParameterSource("json", json), UUID.class);
        result.setId(id);

        String sql = CREATE_ROLE.replaceAll(":role", OreSiUserRole.forUser(result).getAsSqlRole());
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);

        return result;
    }

    @Transactional
    public void addUserRightCreateApplication(UUID userId) {
        addUserInRole(userId.toString(), OreSiUserRole.applicationCreator());
    }

    /**
     * Ajout des droits a un utilisateur
     * @param userId l'utilisateur a qui il faut ajouter des droits
     * @param appId l'application pour lequel on veut lui ajouter des droits
     * @param right le droit qu'on veut lui donner
     */
    @Transactional
    public void addUserRight(UUID userId, UUID appId, ApplicationRight right, UUID... excludedReference) {
        if (right == ApplicationRight.ADMIN) {
            addUserInRoleAsAdmin(userId.toString(), right.getRole(appId));
        } else {
            addUserInRole(userId.toString(), right.getRole(appId));
        }
        if (right == ApplicationRight.RESTRICTED_READER && excludedReference != null) {
            createPolicy(userId, excludedReference);
        }
    }

    protected void createPolicy(UUID userId, UUID... excludedReference) {
        String uuids = Stream.of(excludedReference)
                .map(uuid -> "'" + uuid + "'::uuid")
                .collect(Collectors.joining(","));
        String query = CREATE_SELECT_POLICY
                .replaceAll(":user", userId.toString())
                .replaceAll(":uuids", uuids);
        namedParameterJdbcTemplate.execute(query, PreparedStatement::execute);
    }

    protected void addUserInRole(String userId, OreSiUserRole role) {
        String query = ADD_USER_IN_ROLE.replaceAll(":role", role.getAsSqlRole()).replaceAll(":user", userId);
        namedParameterJdbcTemplate.execute(query, PreparedStatement::execute);
    }

    protected void addUserInRoleAsAdmin(String userId, OreSiUserRole role) {
        String query = ADD_USER_IN_ROLE_AS_ADMIN.replaceAll(":role", role.getAsSqlRole()).replaceAll(":user", userId);
        namedParameterJdbcTemplate.execute(query, PreparedStatement::execute);
    }

    @Transactional
    public void removeUser(UUID userId) {
        SqlParameterSource sqlParameterSource = new MapSqlParameterSource("id", userId);
        OreSiUser oreSiUser = (OreSiUser) namedParameterJdbcTemplate.query(SELECT_USER, sqlParameterSource, jsonRowMapper).stream()
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("ne peut pas supprimer l'utilisateur " + userId + " car il n'existe pas en base"));
        int count = namedParameterJdbcTemplate.update(DELETE_USER, sqlParameterSource);
        if (count > 0) {
            removeRole(OreSiUserRole.forUser(oreSiUser));
        }
    }

    @Transactional
    public void removeRole(OreSiUserRole role) {
        // faire attention au SQL injection
        String sql = REMOVE_ROLE.replaceAll(":role", role.getAsSqlRole());
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    /**
     * @return role name used for this application
     */
    @Transactional
    public void createRightForApplication(Application app) {
        UUID appId = app.getId();

        // creation de tous les roles pour l'application
        for (ApplicationRight r : ApplicationRight.values()) {
            OreSiUserRole role = r.getRole(appId);
            String sql = CREATE_ROLE.replaceAll(":role", role.getAsSqlRole());
            namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
            namedParameterJdbcTemplate.execute(r.getAllSql(appId), PreparedStatement::execute);
        }

        // ajout du role admin dans tous les autres role pour qu'il puisse ajouter des users dedans
        EnumSet<ApplicationRight> rights = EnumSet.allOf(ApplicationRight.class);
        rights.remove(ApplicationRight.ADMIN);
        for (ApplicationRight r : rights) {
            addUserInRoleAsAdmin(ApplicationRight.ADMIN.getRole(appId).getAsSqlRole(), r.getRole(appId));
        }
    }

    @Transactional
    public void removeRightForApplication(Application app) {
        for (ApplicationRight r : ApplicationRight.values()) {
            OreSiUserRole role = r.getRole(app.getId());
            removeRole(role);
        }
    }
}
