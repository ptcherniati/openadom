package fr.inra.oresing.persistence;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class SqlTable {

    private final SqlSchema schema;

    private final String name;

    public String getSqlIdentifier() {
        return getSchema().getSqlIdentifier() + "." + WithSqlIdentifier.escapeSqlIdentifier(getName());
    }
}
