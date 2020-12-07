package fr.inra.oresing.persistence;

import org.apache.commons.lang3.StringUtils;

public interface WithSqlIdentifier {

    String getSqlIdentifier();

    static String escapeSqlIdentifier(String sqlIdentifier) {
        String escaped;
        if (StringUtils.containsAny(sqlIdentifier, " ", "-")) {
            escaped = "\"" + sqlIdentifier + "\"";
        } else {
            escaped = sqlIdentifier;
        }
        return escaped;
    }

}
