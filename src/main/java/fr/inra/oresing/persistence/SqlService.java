package fr.inra.oresing.persistence;

import fr.inra.oresing.model.PolicyDescription;
import fr.inra.oresing.persistence.roles.*;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public List<PolicyDescription> getPoliciesForRole(OreSiRightOnApplicationRole role) {
        String sql = "select  policyname, schemaname, tablename from pg_policies " +
                "where Array[:role::name] @> roles ;";
        return namedParameterJdbcTemplate.query(sql, Map.of("role", role.getAsSqlRole()), PolicyDescription::convert);
    }

    public void createPolicy(SqlPolicy sqlPolicy) {
        dropPolicy(sqlPolicy);
        String using = "", withCheck = "";
        if (!Strings.isNullOrEmpty(sqlPolicy.getUsingExpression())) {
            using = String.format(" USING (%s)", sqlPolicy.getUsingExpression());
        }
        if (!Strings.isNullOrEmpty(sqlPolicy.getWithCheckExpression())) {
            withCheck = String.format(" WITH CHECK (%s)", sqlPolicy.getWithCheckExpression());
        }
        String createPolicySql = String.format(
                "CREATE POLICY %s ON %s AS %s FOR %s TO %s %s %s",
                sqlPolicy.getSqlIdentifier(),
                sqlPolicy.getTable().getSqlIdentifier(),
                sqlPolicy.getPermissiveOrRestrictive().name(),
                sqlPolicy.getStatements().stream().map(SqlPolicy.Statement::name).collect(Collectors.joining(",")),
                sqlPolicy.getRole().getSqlIdentifier(),
                using,
                withCheck
        );
        execute(createPolicySql);
    }

    public void dropPolicy(SqlPolicy sqlPolicy) {
        String createPolicySql = String.format(
                "DROP POLICY IF EXISTS %s ON %s",
                sqlPolicy.getSqlIdentifier(),
                sqlPolicy.getTable().getSqlIdentifier()
        );
        execute(createPolicySql);
    }

    public void createRole(OreSiRoleManagedByApplication roleManagedByApplication) {
        String sql = "CREATE ROLE " + roleManagedByApplication.getSqlIdentifier() + "";
        execute(sql);
    }

    public void createRoleWithPublic(OreSiRoleManagedByApplication roleManagedByApplication) {
        String sql = "CREATE ROLE " + roleManagedByApplication.getSqlIdentifier() + " in role \"9032ffe5-bfc1-453d-814e-287cd678484a\"";
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

    public void removeUserInRole(OreSiRoleWeCanGrantOtherRolesTo roleToModify, OreSiRoleToBeGranted roleToAdd) {
        String sql = "REVOKE " + roleToAdd.getSqlIdentifier() + ""
                + " FROM " + roleToModify.getSqlIdentifier();
        execute(sql);
    }

    public void resetRole() {
        execute("RESET ROLE");
    }

    public void setRole(OreSiRoleToAccessDatabase roleToAccessDatabase) {
        String sql = "SET LOCAL ROLE " + roleToAccessDatabase.getSqlIdentifier();
        execute(sql);
    }

    public boolean hasRole(OreSiRole role) {
        String sql = "SELECT pg_has_role('" + role.getAsSqlRole() + "', 'MEMBER')";
        boolean hasRole = namedParameterJdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, Boolean.class);
        return hasRole;
    }

    private void execute(String sql) {
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }
}