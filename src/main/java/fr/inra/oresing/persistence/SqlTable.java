package fr.inra.oresing.persistence;

import fr.inra.oresing.persistence.roles.OreSiRole;
import lombok.Value;

@Value
public class SqlTable {

    SqlSchema schema;

    String name;

    public String getSqlIdentifier() {
        return getSchema().getSqlIdentifier() + "." + WithSqlIdentifier.escapeSqlIdentifier(getName());
    }
    public String setTableOwnerSql(OreSiRole owner) {
        return "ALTER TABLE " + getSqlIdentifier() + " OWNER TO " + owner.getSqlIdentifier();
    }
}