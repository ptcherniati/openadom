package fr.inra.oresing.persistence;

import lombok.Value;

@Value
public class SqlTable {

    SqlSchema schema;

    String name;

    public String getSqlIdentifier() {
        return getSchema().getSqlIdentifier() + "." + WithSqlIdentifier.escapeSqlIdentifier(getName());
    }
}
