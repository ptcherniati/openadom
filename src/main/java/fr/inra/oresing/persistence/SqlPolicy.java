package fr.inra.oresing.persistence;

import fr.inra.oresing.persistence.roles.OreSiRole;
import lombok.Value;

@Value
public class SqlPolicy implements WithSqlIdentifier {

    String id;

    SqlTable table;

    PermissiveOrRestrictive permissiveOrRestrictive;

    Statement statement;

    OreSiRole role;

    String usingExpression;

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
}