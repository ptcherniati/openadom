package fr.inra.oresing.persistence;

import fr.inra.oresing.persistence.roles.OreSiRole;
import lombok.Value;

@Value
public class SqlPolicy implements WithSqlIdentifier {

    SqlTable table;

    PermissiveOrRestrictive permissiveOrRestrictive;

    Statement statement;

    OreSiRole role;

    String usingExpression;

    @Override
    public String getSqlIdentifier() {
        return WithSqlIdentifier.escapeSqlIdentifier(String.join("_", role.getAsSqlRole(), table.getName(), statement.name()));
    }

    public enum PermissiveOrRestrictive {
        PERMISSIVE, RESTRICTIVE
    }

    public enum Statement {
        ALL, SELECT, INSERT, UPDATE, DELETE
    }
}
