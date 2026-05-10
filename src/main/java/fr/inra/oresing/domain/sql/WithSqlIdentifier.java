package fr.inra.oresing.domain.sql;

import org.apache.commons.lang3.StringUtils;

public interface WithSqlIdentifier {

    static String escapeSqlIdentifier(final String sqlIdentifier) {
        final String escaped;
        if (StringUtils.containsAny(sqlIdentifier, " ", "-")) {
            escaped = "\"" + sqlIdentifier + "\"";
        } else {
            escaped = sqlIdentifier;
        }
        return escaped;
    }

    String getSqlIdentifier();

}