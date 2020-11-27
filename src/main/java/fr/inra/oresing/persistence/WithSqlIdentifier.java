package fr.inra.oresing.persistence;

import java.util.stream.Stream;

public interface WithSqlIdentifier {

    String getSqlIdentifier();

    static String escapeSqlIdentifier(String sqlIdentifier) {
        if (Stream.of(" ", "-").anyMatch(sqlIdentifier::contains)) {
            return "\"" + sqlIdentifier + "\"";
        } else {
            return sqlIdentifier;
        }
    }

}
