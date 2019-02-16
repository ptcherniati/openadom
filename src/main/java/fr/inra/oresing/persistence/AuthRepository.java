package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.model.ReferenceValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.List;

@Component
public class AuthRepository {

    private static final String RESET_ROLE = "RESET ROLE";
    private static final String SET_ROLE = "SET LOCAL ROLE \":role\"";
    private static final String CREATE_USER = "CREATE ROLE \":role\" WITH LOGIN PASSWORD ':password'";
    private static final String CREATE_ROLE = "CREATE ROLE \":role\"";
    private static final String REMOVE_ROLE = "DROP ROLE \":role\"";

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public void resetRole() {
        namedParameterJdbcTemplate.execute(RESET_ROLE,
                PreparedStatement::execute);
    }

    public void setRole(String role) {
        // faire attention au SQL injection
        String sql = SET_ROLE.replaceAll(":role", role);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    public OreSiUser login(String user, String password) {
        // faire une vrai authentification basé sur les utilisateurs de la base de données
        // lever une exception mapper sur un 401 si l'utilisateur n'existe pas ou le password est faux
        OreSiUser result = new OreSiUser();
        result.setName(user);
        return result;
    }

    public OreSiUser createUser(String user, String password) {
        // faire attention au SQL injection
        String sql = CREATE_USER.replaceAll(":role", user).replaceAll(":password", password.replaceAll("'", "''"));
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
        OreSiUser result = new OreSiUser();
        result.setName(user);
        return result;
    }

    public void removeUser(String user) {
        removeRole(user);
    }

    public void removeRole(String role) {
        // faire attention au SQL injection
        String sql = REMOVE_ROLE.replaceAll(":role", role);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    public void createRightForReference(ReferenceValue ref) {
        String role = ref.getId().toString();
        String sql = CREATE_ROLE.replaceAll(":role", role);
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }

    public void removeRightForReference(ReferenceValue ref) {
        removeRole(ref.getId().toString());
    }
}
