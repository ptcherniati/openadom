package fr.inra.oresing.domain;

import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.sql.SqlStatement;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class OreSiAuthorization extends OreSiEntity {
    private String name;
    private String description;
    private Set<UUID> oreSiUsers;
    private UUID application;
    private Map<String, AuthorizationForScope> authorizations = new HashMap<>();

    public String toIdForReference(SqlStatement statement, String datatype) {
        return OreSiAuthorization.class.getSimpleName() +
               "_" +
               getId().toString().substring(0, 7) +
               "_data_" +
               UUID.randomUUID().toString().substring(0, 10) +
               "_" +
               shortenDataTypeName(datatype) +
               "_" +
               statement.name().toLowerCase().substring(0, 3);
    }

    public static String shortenDataTypeName(String datatype) {
        if (datatype == null || datatype.isEmpty()) {
            return "";
        }

        // Partie avant le premier underscore
        int firstUnderscoreIndex = datatype.indexOf('_');
        String prefix;
        if (firstUnderscoreIndex == -1) {
            // Pas d'underscore - prendre au plus 2 premiers caractères
            prefix = datatype.substring(0, Math.min(datatype.length(), 2));
        } else {
            // Prendre la partie avant le premier underscore (max 2 caractères)
            prefix = datatype.substring(0, Math.min(firstUnderscoreIndex, 2));
        }

        // Partie après le dernier underscore
        int lastUnderscoreIndex = datatype.lastIndexOf('_');
        String suffix;
        if (lastUnderscoreIndex == -1 || lastUnderscoreIndex == datatype.length() - 1) {
            // Pas d'underscore ou underscore en fin - prendre au plus 4 derniers caractères
            int start = Math.max(0, datatype.length() - 4);
            suffix = datatype.substring(start);
        } else {
            // Prendre la partie après le dernier underscore (max 4 caractères)
            suffix = datatype.substring(lastUnderscoreIndex + 1);
            if (suffix.length() > 4) {
                suffix = suffix.substring(suffix.length() - 4);
            }
        }

        return prefix + "_" + suffix;
    }
}