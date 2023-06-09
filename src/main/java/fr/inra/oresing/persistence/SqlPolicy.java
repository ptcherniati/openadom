package fr.inra.oresing.persistence;

import fr.inra.oresing.persistence.roles.OreSiRole;
import lombok.Value;

import java.util.List;

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
}