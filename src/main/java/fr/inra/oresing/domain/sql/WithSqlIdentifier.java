package fr.inra.oresing.domain.sql;

import org.apache.commons.lang3.StringUtils;

import java.text.MessageFormat;
import java.util.regex.Pattern;

public interface WithSqlIdentifier {

    Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    Pattern SAFE_QUOTED_IDENTIFIER = Pattern.compile("[A-Za-z0-9_\\- ]+");

    String ERR_BLANK = "SQL identifier cannot be blank";
    String ERR_UNSAFE = "Unsafe SQL identifier: {0}";

    static String escapeSqlIdentifier(final String sqlIdentifier) {
        if (StringUtils.isBlank(sqlIdentifier)) {
            throw new IllegalArgumentException(ERR_BLANK);
        }
        final String escaped = sqlIdentifier.trim();
        if (StringUtils.containsAny(escaped, " ", "-")) {
            if (!SAFE_QUOTED_IDENTIFIER.matcher(escaped).matches()) {
                throw new IllegalArgumentException(MessageFormat.format(ERR_UNSAFE, sqlIdentifier));
            }
            return "\"" + escaped + "\"";
        }
        if (!SAFE_IDENTIFIER.matcher(escaped).matches()) {
            throw new IllegalArgumentException(MessageFormat.format(ERR_UNSAFE, sqlIdentifier));
        }
        return escaped;
    }

    String getSqlIdentifier();

}