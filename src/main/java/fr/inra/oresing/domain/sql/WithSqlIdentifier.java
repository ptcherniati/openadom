package fr.inra.oresing.domain.sql;

public interface WithSqlIdentifier {

    static String escapeSqlIdentifier(final String sqlIdentifier) {
        final String escaped;
        if (sqlIdentifier.contains(" ") || sqlIdentifier.contains("-")) {
            escaped = "\"" + sqlIdentifier + "\"";
        } else {
            escaped = sqlIdentifier;
        }
        return escaped;
    }

    String getSqlIdentifier();

}