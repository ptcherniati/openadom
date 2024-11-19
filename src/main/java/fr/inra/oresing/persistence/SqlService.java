package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.PolicyDescription;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.*;
import fr.inra.oresing.persistence.index.AuthorizationIndex;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Transactional()
public class SqlService {

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;


    public void createSchema(final SqlSchema schema, final OreSiRole owner) {
        namedParameterJdbcTemplate.getJdbcTemplate().
                execute("CREATE SCHEMA " + schema.getSqlIdentifier() + " AUTHORIZATION " + owner.getSqlIdentifier());
    }

    public void dropSchema(final SqlSchemaForRelationalViewsForApplication schema) {
        execute("DROP  SCHEMA IF EXISTS %s CASCADE"
                .formatted(schema.getSqlIdentifier()));
    }

    public void grantUsage(final SqlSchema schema, final OreSiRole readerOnApplicationRole) {
        execute("GRANT USAGE ON SCHEMA %s TO %s"
                .formatted(schema.getSqlIdentifier(), readerOnApplicationRole.getSqlIdentifier()));
    }

    public void setSchemaOwner(final SqlSchema schema, final OreSiRole owner) {
        execute("ALTER SCHEMA %s OWNER TO %s"
                .formatted(schema.getSqlIdentifier(), owner.getSqlIdentifier()));
    }

    public void createTable(final SqlTable table, final String contentSql) {
        execute("CREATE TABLE %s AS (%s)"
                .formatted(table.getSqlIdentifier(), contentSql));
    }

    public void dropTable(final SqlTable table) {
        execute(
                "DROP TABLE %s"
                        .formatted(table.getSqlIdentifier()
                        )
        );
    }

    public void setTableOwner(final SqlTable table, final OreSiRole owner) {
        execute(table.setTableOwnerSql(owner));
    }

    public void enableRowLevelSecurity(final SqlTable table) {
        execute("ALTER TABLE %s ENABLE ROW LEVEL SECURITY"
                .formatted(table.getSqlIdentifier()));
    }

    public void createView(final SqlTable view, final String viewSql) {
        execute("CREATE VIEW %s AS (%s)"
                .formatted(view.getSqlIdentifier(), viewSql));
    }

    public void setViewOwner(final SqlTable view, final OreSiRightOnApplicationRole owner) {
        execute("ALTER VIEW %s OWNER TO %s"
                .formatted(view.getSqlIdentifier(), owner.getSqlIdentifier()));
    }

    public void dropView(final SqlTable view) {
        execute("DROP VIEW %s"
                .formatted(view.getSqlIdentifier()));
    }

    public List<PolicyDescription> getPoliciesForRole(final OreSiRightOnApplicationRole role) {
        final String sql = """
                SELECT  policyname, schemaname, tablename FROM pg_policies WHERE array[:role::name] @> roles ;
                """;
        return namedParameterJdbcTemplate.query(sql, Map.of("role", role.getAsSqlRole()), PolicyDescription::convert);
    }

    public void createPolicy(final SqlPolicy sqlPolicy) {
        dropPolicy(sqlPolicy);
        createPolicy(sqlPolicy.policyToCreateSql());
    }

    public void createPolicy(final String createPolicySql) {
        execute(createPolicySql);
    }

    public void dropPolicy(final SqlPolicy sqlPolicy) {
        execute(sqlPolicy.policyToDropSql());
    }

    public void createRole(final OreSiRoleManagedByApplication roleManagedByApplication, String comment) {
        final String sql = """
                CREATE ROLE %1$s;
                COMMENT ON ROLE  %1$s IS %2$s;"""
                .formatted(roleManagedByApplication.getSqlIdentifier(),
                        quote(comment));
        execute(sql);
    }

    public void createRoleWithPublic(final OreSiRoleManagedByApplication roleManagedByApplication, String comment) {
        final String sql = """
            CREATE ROLE %1$s IN ROLE "%2$s";
            COMMENT ON ROLE %1$s IS %3$s;
            """
                .formatted(
                        roleManagedByApplication.getSqlIdentifier(),
                        SqlSchemaForApplication.PUBLIC_UUID.toString(),
                        quote(comment));
        execute(sql);
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    public void dropRole(final OreSiRoleManagedByApplication roleManagedByApplication) {
        final String sql = "DROP ROLE %s"
                .formatted(roleManagedByApplication.getSqlIdentifier());
        execute(sql);
    }

    public void addUserInRole(final OreSiRoleWeCanGrantOtherRolesTo roleToModify, final OreSiRoleToBeGranted roleToAdd) {
        final boolean withAdminOption = false;
        addUserInRole(roleToModify, roleToAdd, withAdminOption);
    }

    public void addUserInRoleAsAdmin(final OreSiRoleWeCanGrantOtherRolesTo roleToModify, final OreSiRoleToBeGranted roleToAdd) {
        final boolean withAdminOption = true;
        addUserInRole(roleToModify, roleToAdd, withAdminOption);
    }

    private void addUserInRole(final OreSiRoleWeCanGrantOtherRolesTo roleToModify, final OreSiRoleToBeGranted roleToAdd, final boolean withAdminOption) {
        final String sql = roleToAdd.addUserInRoleSql(roleToModify, withAdminOption);
        execute(sql);
    }

    public void removeUserInRole(final OreSiRoleWeCanGrantOtherRolesTo roleToModify, final OreSiRoleToBeGranted roleToAdd) {
        final String sql = "REVOKE %1$s FROM %2$s"
                .formatted(roleToAdd.getSqlIdentifier(),
                        roleToModify.getSqlIdentifier()
                );
        execute(sql);
    }

    public void resetRole() {
        execute("RESET ROLE");
    }

    public void setRole(final OreSiRoleToAccessDatabase roleToAccessDatabase) {
        final String sql = "SET LOCAL ROLE %s"
                .formatted(roleToAccessDatabase.getSqlIdentifier());
        execute(sql);
    }

    public boolean hasRole(final OreSiRole role) {
        final String sql = "SELECT pg_has_role('%s', 'MEMBER')"
                .formatted(role.getAsSqlRole());
        final boolean hasRole = Boolean.TRUE.equals(namedParameterJdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, Boolean.class));
        return hasRole;
    }

    private void execute(final String sql) {
        namedParameterJdbcTemplate.execute(sql, PreparedStatement::execute);
    }
}