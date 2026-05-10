package fr.inra.oresing.persistence;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.sql.WithSqlIdentifier;

import java.util.List;
import java.util.stream.Collectors;


public record SqlPolicy(String id, SqlTable table,
                        fr.inra.oresing.persistence.SqlPolicy.PermissiveOrRestrictive permissiveOrRestrictive,
                        List<Statement> statements,
                        OreSiRole role,
                        String usingExpression,
                        String withCheckExpression,
                        String comment) implements WithSqlIdentifier {
    public SqlPolicy(String id, SqlTable table, PermissiveOrRestrictive permissiveOrRestrictive, List<Statement> statements, OreSiRole role, String usingExpression, String withCheckExpression) {
        this(id, table, permissiveOrRestrictive, statements, role, usingExpression, withCheckExpression, "no comment");
    }

    @Override
    public String getSqlIdentifier() {
        return WithSqlIdentifier.escapeSqlIdentifier(id);
    }

    public String policyToCreateSql() {
        String using = "", withCheck = "";
        if (!Strings.isNullOrEmpty(usingExpression)) {
            using = String.format(" USING (%s)", usingExpression);
        }
        if (!Strings.isNullOrEmpty(withCheckExpression)) {
            withCheck = String.format(" WITH CHECK (%s)", withCheckExpression);
        }
        return String.format(
                """
                CREATE POLICY %1$s ON %2$s AS %3$s FOR %4$s TO %5$s %6$s %7$s;
                COMMENT ON POLICY %1$s ON %2$s IS '%8$s';""",
                getSqlIdentifier(),
                table.getSqlIdentifier(),
                permissiveOrRestrictive.name(),
                statements.stream().map(SqlPolicy.Statement::name).collect(Collectors.joining(",")),
                role == null ? "public" : role.getSqlIdentifier(),
                using,
                withCheck,
                comment()
        );
    }

    public String policyToDropSql() {
        return String.format(
                "DROP POLICY IF EXISTS %s ON %s",
                getSqlIdentifier(),
                table.getSqlIdentifier()
        );
    }

    public enum PermissiveOrRestrictive {
        PERMISSIVE, RESTRICTIVE
    }

    public enum Statement {
        ALL, SELECT, INSERT, UPDATE, DELETE
    }
}