package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;


public record SqlTable(SqlSchema schema, String name) {

    public String getSqlIdentifier() {
        return "%s.%s".formatted(schema.getSqlIdentifier(), WithSqlIdentifier.escapeSqlIdentifier(name));
    }

    public String setTableOwnerSql(final OreSiRole owner) {
        return "ALTER TABLE %s OWNER TO \"%s\"".formatted(getSqlIdentifier(), owner.getSqlIdentifier());
    }
}