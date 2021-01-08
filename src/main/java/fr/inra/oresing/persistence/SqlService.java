package fr.inra.oresing.persistence;

import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.persistence.roles.OreSiRoleManagedByApplication;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import fr.inra.oresing.persistence.roles.OreSiRoleToBeGranted;
import fr.inra.oresing.persistence.roles.OreSiRoleWeCanGrantOtherRolesTo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;

@Slf4j
@Component
@Transactional
public class SqlService {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public void createSchema(SqlSchema schema, OreSiRole owner) {
        execute("CREATE SCHEMA " + schema.getSqlIdentifier() + " AUTHORIZATION " + owner.getSqlIdentifier());
    }

    public void dropSchema(SqlSchemaForRelationalViewsForApplication schema) {
        execute("DROP SCHEMA " + schema.getSqlIdentifier());
    }

    public void grantUsage(SqlSchema schema, OreSiRole readerOnApplicationRole) {
        execute("GRANT USAGE ON SCHEMA " + schema.getSqlIdentifier() + " TO " + readerOnApplicationRole.getSqlIdentifier());
    }

    public void setSchemaOwner(SqlSchema schema, OreSiRole owner) {
        execute("ALTER SCHEMA " + schema.getSqlIdentifier() + " OWNER TO " + owner.getSqlIdentifier());
    }

    public void createTable(SqlTable table, String contentSql) {
        execute("CREATE TABLE " + table.getSqlIdentifier() + " AS (" + contentSql + ")");
    }

    public void dropTable(SqlTable table) {
        execute("DROP TABLE " + table.getSqlIdentifier());
    }

    public void setTableOwner(SqlTable table, OreSiRole owner) {
        execute("ALTER TABLE " + table.getSqlIdentifier() + " OWNER TO " + owner.getSqlIdentifier());
    }

    public void enableRowLevelSecurity(SqlTable table) {
        execute("ALTER TABLE " + table.getSqlIdentifier() + " ENABLE ROW LEVEL SECURITY");
    }

    public void createView(SqlTable view, String viewSql) {
        execute("CREATE VIEW " + view.getSqlIdentifier() + " AS (" + viewSql + ")");
    }

    public void setViewOwner(SqlTable view, OreSiRightOnApplicationRole owner) {
        execute("ALTER VIEW " + view.getSqlIdentifier() + " OWNER TO " + owner.getSqlIdentifier());
    }

    public void dropView(SqlTable view) {
        execute("DROP VIEW " + view.getSqlIdentifier());
    }

    public void createPolicy(SqlPolicy sqlPolicy) {
        String createPolicySql = String.format(
                "CREATE POLICY %s ON %s AS %s FOR %s TO %s USING (%s)",
                sqlPolicy.getSqlIdentifier(),
                sqlPolicy.getTable().getSqlIdentifier(),
                sqlPolicy.getPermissiveOrRestrictive().name(),
                sqlPolicy.getStatement().name(),
                sqlPolicy.getRole().getSqlIdentifier(),
                sqlPolicy.getUsingExpression()
        );
        execute(createPolicySql);
    }

    public void createRole(OreSiRoleManagedByApplication roleManagedByApplication) {
        String sql = "CREATE ROLE " + roleManagedByApplication.getSqlIdentifier() + "";
        execute(sql);
    }

    public void dropRole(OreSiRoleManagedByApplication roleManagedByApplication) {
        String sql = "DROP ROLE " + roleManagedByApplication.getSqlIdentifier() + "";
        execute(sql);
    }

    public void addUserInRole(OreSiRoleWeCanGrantOtherRolesTo roleToModify, OreSiRoleToBeGranted roleToAdd) {
        boolean withAdminOption = false;
        addUserInRole(roleToModify, roleToAdd, withAdminOption);
    }

    public void addUserInRoleAsAdmin(OreSiRoleWeCanGrantOtherRolesTo roleToModify, OreSiRoleToBeGranted roleToAdd) {
        boolean withAdminOption = true;
        addUserInRole(roleToModify, roleToAdd, withAdminOption);
    }

    private void addUserInRole(OreSiRoleWeCanGrantOtherRolesTo roleToModify, OreSiRoleToBeGranted roleToAdd, boolean withAdminOption) {
        String withAdminOptionClause = withAdminOption ? " WITH ADMIN OPTION" : "";
        String sql = "GRANT " + roleToAdd.getSqlIdentifier() + ""
                     + " TO " + roleToModify.getSqlIdentifier() + ""
                     + withAdminOptionClause;
        execute(sql);
    }

    public void resetRole() {
        execute("RESET ROLE");
    }

    public void setRole(OreSiRoleToAccessDatabase roleToAccessDatabase) {
        String sql = "SET LOCAL ROLE " + roleToAccessDatabase.getSqlIdentifier();
        execute(sql);
    }

    private void execute(String sql) {
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }
}
