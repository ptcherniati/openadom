package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.OreSiEntity;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.model.ReferenceValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuthRepository {

    private static final String ANONYMOUS = "anonymous";
    private static final String SUPERADMIN = "superadmin";

    private static final String RESET_ROLE = "RESET ROLE";
    private static final String SET_ROLE = "SET LOCAL ROLE \":role\"";
    private static final String CREATE_ROLE = "CREATE ROLE \":role\"";
    private static final String REMOVE_ROLE = "DROP ROLE \":role\"";
    private static final String ADD_USER_IN_ROLE = "GRANT :role TO :user";

    private static final String USER_UPSERT =
            "INSERT INTO OreSiUser (id, login, password) SELECT id, login, password FROM json_populate_record(NULL::OreSiUser, :json::json)"
                    + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, login=EXCLUDED.login, password=EXCLUDED.password"
                    + " RETURNING id";

    private static final String SELECT_USER = "SELECT '" + OreSiUser.class.getName() + "' as \"@class\",  to_jsonb(t) as json FROM OreSiUser t WHERE login=:login AND password=:password";

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
     * Prend le role du superadmin qui a le droit de tout faire
     */
    public void setRoleAdmin() {
        // faire attention au SQL injection
        String sql = SET_ROLE.replaceAll(":role", SUPERADMIN);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    /**
     * Prend le role du user passe en parametre, les requetes suivant ne pourra
     * pas faire des choses que l'utilisateur n'a pas le droit de faire
     * @param user
     */
    public void setRole(OreSiUser user) {
        String role = ANONYMOUS;
        if (user != null) {
            role = user.getId().toString();
        }
        // faire attention au SQL injection
        String sql = SET_ROLE.replaceAll(":role", role);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    /**
     * verifie que l'utilisateur existe et que son mot de passe est le bon
     * @return l'objet OreSiUser contenant les informations sur l'utilisateur identifié
     */
    public OreSiUser login(String login, String password) throws Throwable {
        String query = SELECT_USER;
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

        String sql = CREATE_ROLE.replaceAll(":role", id.toString());
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);

        return result;

    }

    /**
     * Ajout des droits a un utilisateur
     * @param user l'utilisateur a qui il faut ajouter des droits
     * @param appId l'application pour lequel on veut lui ajouter des droits
     * @param right le droit qu'on veut lui donner
     */
    public void addUserRight(OreSiUser user, UUID appId, ApplicationRight right) {
        // TODO
        String query = ADD_USER_IN_ROLE;
        namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("role", right.getRole(appId)).addValue("user", user));
    }

    @Transactional
    public void removeUser(UUID userId) {
        String query = DELETE_USER;
        int count = namedParameterJdbcTemplate.update(query, new MapSqlParameterSource("id", userId));
        if (count > 0)
        removeRole(userId.toString());
    }

    public void removeRole(String role) {
        // faire attention au SQL injection
        String sql = REMOVE_ROLE.replaceAll(":role", role);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    /**
     * Permet de créer un nouveau role
     * @return role name used for this ReferenceValue
     */
    public String createRightForReference(ReferenceValue ref) {
        String role = ref.getId().toString();
        String sql = CREATE_ROLE.replaceAll(":role", role);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
        return role;
    }

    /**
     * @return role name used for this application
     */
    public String createRightForApplication(Application app) {
        String role = app.getId().toString();
        String sql = CREATE_ROLE.replaceAll(":role", role);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
        return role;
    }

    public void removeRightForReference(ReferenceValue ref) {
        removeRole(ref.getId().toString());
    }

    public void removeRightForApplication(Application app) {
        removeRole(app.getId().toString());
    }
}
