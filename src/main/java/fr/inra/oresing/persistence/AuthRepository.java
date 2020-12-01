package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.roles.OreSiApplicationCreatorRole;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.persistence.roles.OreSiRoleManagedByApplication;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import fr.inra.oresing.persistence.roles.OreSiRoleToBeGranted;
import fr.inra.oresing.persistence.roles.OreSiRoleWeCanGrantOtherRolesTo;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import fr.inra.oresing.rest.OreSiApiRequestContext;
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
@Transactional
public class AuthRepository {

    private static final String RESET_ROLE = "RESET ROLE";
    private static final String SET_ROLE = "SET LOCAL ROLE \":role\"";
    private static final String CREATE_ROLE = "CREATE ROLE \":role\"";
    private static final String REMOVE_ROLE = "DROP ROLE \":role\"";

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
    public void resetRole() {
        namedParameterJdbcTemplate.execute(RESET_ROLE,
                PreparedStatement::execute);
    }

    /**
     * Utilise le rôle de l'utilisateur courant pour l'accès à la base de données.
     */
    public void setRoleForClient() {
        OreSiRoleToAccessDatabase roleToAccessDatabase = OreSiApiRequestContext.get().getRequestClient().getRole();
        setRole(roleToAccessDatabase);
    }

    /**
     * Prend le role du superadmin qui a le droit de tout faire
     */
    public void setRoleAdmin() {
        setRole(OreSiRole.superAdmin());
    }

    /**
     * Prend le role du user passe en parametre, les requetes suivant ne pourra
     * pas faire des choses que l'utilisateur n'a pas le droit de faire
     */
    void setRole(OreSiRoleToAccessDatabase roleToAccessDatabase) {
        // faire attention au SQL injection
        String sql = SET_ROLE.replaceAll(":role", roleToAccessDatabase.getAsSqlRole());
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    /**
     * verifie que l'utilisateur existe et que son mot de passe est le bon
     * @return l'objet OreSiUser contenant les informations sur l'utilisateur identifié
     */
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
    public OreSiUser createUser(String login, String password) {
        OreSiUser result = new OreSiUser();
        result.setLogin(login);
        result.setPassword(password);

        String query = USER_UPSERT;
        String json = jsonRowMapper.toJson(result);
        UUID id = namedParameterJdbcTemplate.queryForObject(
                query, new MapSqlParameterSource("json", json), UUID.class);
        result.setId(id);

        OreSiUserRole userRole = getUserRole(result);
        createRole(userRole);

        return result;
    }

    private void createRole(OreSiRoleManagedByApplication roleManagedByApplication) {
        String sql = CREATE_ROLE.replaceAll(":role", roleManagedByApplication.getAsSqlRole());
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    public void addUserRightCreateApplication(UUID userId) {
        OreSiUserRole roleToModify = getUserRole(userId);
        OreSiApplicationCreatorRole roleToAdd = OreSiRole.applicationCreator();
        addUserInRole(roleToModify, roleToAdd);
    }

    /**
     * Ajout des droits a un utilisateur
     * @param userId l'utilisateur a qui il faut ajouter des droits
     * @param appId l'application pour lequel on veut lui ajouter des droits
     * @param right le droit qu'on veut lui donner
     */
    public void addUserRight(UUID userId, UUID appId, ApplicationRight right, UUID... excludedReference) {
        OreSiUserRole roleToModify = getUserRole(userId);
        OreSiRightOnApplicationRole roleToAdd = right.getRole(appId);
        if (right == ApplicationRight.ADMIN) {
            addUserInRoleAsAdmin(roleToModify, roleToAdd);
        } else {
            addUserInRole(roleToModify, roleToAdd);
        }
        if (right == ApplicationRight.RESTRICTED_READER && excludedReference != null) {
            createPolicy(roleToModify, excludedReference);
        }
    }

    protected void createPolicy(OreSiUserRole userRole, UUID... excludedReference) {
        String uuids = Stream.of(excludedReference)
                .map(uuid -> "'" + uuid + "'::uuid")
                .collect(Collectors.joining(","));
        String usingExpression = "NOT refsLinkedTo && ARRAY[:uuids]".replaceAll(":uuids", uuids);
        SqlPolicy sqlPolicy = new SqlPolicy(
                SqlSchema.main().data(),
                SqlPolicy.PermissiveOrRestrictive.RESTRICTIVE,
                SqlPolicy.Statement.SELECT,
                userRole,
                usingExpression);
        createPolicy(sqlPolicy);
    }

    private void createPolicy(SqlPolicy sqlPolicy) {
        String createPolicySql = String.format(
                "CREATE POLICY %s ON %s AS %s FOR %s TO %s USING (%s)",
                sqlPolicy.getSqlIdentifier(),
                sqlPolicy.getTable().getSqlIdentifier(),
                sqlPolicy.getPermissiveOrRestrictive().name(),
                sqlPolicy.getStatement().name(),
                sqlPolicy.getRole().getSqlIdentifier(),
                sqlPolicy.getUsingExpression()
        );
        namedParameterJdbcTemplate.execute(createPolicySql, PreparedStatement::execute);
    }

    protected void addUserInRole(OreSiRoleWeCanGrantOtherRolesTo roleToModify, OreSiRoleToBeGranted roleToAdd) {
        String query = ADD_USER_IN_ROLE
                .replaceAll(":role", roleToAdd.getAsSqlRole())
                .replaceAll(":user", roleToModify.getAsSqlRole());
        namedParameterJdbcTemplate.execute(query, PreparedStatement::execute);
    }

    protected void addUserInRoleAsAdmin(OreSiRoleWeCanGrantOtherRolesTo roleToModify, OreSiRoleToBeGranted roleToAdd) {
        String query = ADD_USER_IN_ROLE_AS_ADMIN
                .replaceAll(":role", roleToAdd.getAsSqlRole())
                .replaceAll(":user", roleToModify.getAsSqlRole());
        namedParameterJdbcTemplate.execute(query, PreparedStatement::execute);
    }

    public void removeUser(UUID userId) {
        OreSiUser oreSiUser = getOreSiUser(userId);
        SqlParameterSource sqlParameterSource = new MapSqlParameterSource("id", userId);
        int count = namedParameterJdbcTemplate.update(DELETE_USER, sqlParameterSource);
        if (count > 0) {
            OreSiUserRole userRoleToDelete = getUserRole(oreSiUser);
            removeRole(userRoleToDelete);
        }
    }

    private OreSiUser getOreSiUser(UUID userId) {
        SqlParameterSource sqlParameterSource = new MapSqlParameterSource("id", userId);
        return (OreSiUser) namedParameterJdbcTemplate.query(SELECT_USER, sqlParameterSource, jsonRowMapper).stream()
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException("l'utilisateur " + userId + " n'existe pas en base"));
    }

    public void removeRole(OreSiRoleManagedByApplication roleManagedByApplication) {
        // faire attention au SQL injection
        String sql = REMOVE_ROLE.replaceAll(":role", roleManagedByApplication.getAsSqlRole());
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    /**
     * @return role name used for this application
     */
    public void createRightForApplication(Application app) {
        UUID appId = app.getId();

        // creation de tous les roles pour l'application
        for (ApplicationRight r : ApplicationRight.values()) {
            OreSiRightOnApplicationRole role = r.getRole(appId);
            createRole(role);
            namedParameterJdbcTemplate.execute(r.getAllSql(appId), PreparedStatement::execute);
        }

        // ajout du role admin dans tous les autres role pour qu'il puisse ajouter des users dedans
        EnumSet<ApplicationRight> rights = EnumSet.allOf(ApplicationRight.class);
        rights.remove(ApplicationRight.ADMIN);
        OreSiRightOnApplicationRole roleToModify = ApplicationRight.ADMIN.getRole(appId);
        for (ApplicationRight r : rights) {
            OreSiRightOnApplicationRole roleToAdd = r.getRole(appId);
            addUserInRoleAsAdmin(roleToModify, roleToAdd);
        }
    }

    public void removeRightForApplication(Application app) {
        for (ApplicationRight r : ApplicationRight.values()) {
            OreSiRightOnApplicationRole role = r.getRole(app.getId());
            removeRole(role);
        }
    }

    public OreSiUserRole getUserRole(UUID userId) {
        OreSiUser user = getOreSiUser(userId);
        return getUserRole(user);
    }

    public OreSiUserRole getUserRole(OreSiUser user) {
        return OreSiUserRole.forUser(user);
    }
}
