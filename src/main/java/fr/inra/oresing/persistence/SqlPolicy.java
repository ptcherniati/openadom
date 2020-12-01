package fr.inra.oresing.persistence;

import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SqlPolicy implements WithSqlIdentifier {

    SqlTable table;

    PermissiveOrRestrictive permissiveOrRestrictive;

    Statement statement;

    OreSiUserRole role;

    String usingExpression;

    @Override
    public String getSqlIdentifier() {
        return WithSqlIdentifier.escapeSqlIdentifier(String.join("_", role.getAsSqlRole(), table.getName(), statement.name()));
    }

    enum PermissiveOrRestrictive {
        PERMISSIVE, RESTRICTIVE
    }

    enum Statement {
        SELECT
    }
}
