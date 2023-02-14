package fr.inra.oresing.persistence;

import fr.inra.oresing.persistence.roles.OreSiRole;
import lombok.Value;
import org.assertj.core.util.Strings;

import java.util.List;
import java.util.stream.Collectors;

@Value
public class SqlPolicy implements WithSqlIdentifier {

    String id;

    SqlTable table;

    PermissiveOrRestrictive permissiveOrRestrictive;

    List<Statement> statements;

    OreSiRole role;

    String usingExpression;

    String withCheckExpression;

    @Override
    public String getSqlIdentifier() {
        return WithSqlIdentifier.escapeSqlIdentifier(id);
    }

    public enum PermissiveOrRestrictive {
        PERMISSIVE, RESTRICTIVE
    }

    public enum Statement {
        ALL, SELECT, INSERT, UPDATE, DELETE
    }
    public String policyToCreateSql(){
        String using = "", withCheck = "";
        if (!Strings.isNullOrEmpty(getUsingExpression())) {
            using = String.format(" USING (%s)", getUsingExpression());
        }
        if (!Strings.isNullOrEmpty(getWithCheckExpression())) {
            withCheck = String.format(" WITH CHECK (%s)", getWithCheckExpression());
        }
        return String.format(
                "CREATE POLICY %s ON %s AS %s FOR %s TO %s %s %s",
                getSqlIdentifier(),
                getTable().getSqlIdentifier(),
                getPermissiveOrRestrictive().name(),
                getStatements().stream().map(SqlPolicy.Statement::name).collect(Collectors.joining(",")),
                getRole()==null?"public":getRole().getSqlIdentifier(),
                using,
                withCheck
        );
    }
    public String policyToDropSql(){
        return String.format(
                "DROP POLICY IF EXISTS %s ON %s",
                getSqlIdentifier(),
                getTable().getSqlIdentifier()
        );
    }
}