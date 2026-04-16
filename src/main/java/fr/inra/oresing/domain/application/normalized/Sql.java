package fr.inra.oresing.domain.application.normalized;

import java.util.*;

public record Sql(
        String schemaName,
        String tableName,
        UUID applicationId,
        List<String> select,
        List<String> refValuesTable,
        List<ReferenceJoin> referenceJoin,
        List<String> indexes,
        Map<String, List<String>> foreignKeys,
        List<String> timescopes,
        Map<String, String> authorizationScopes,
        Map<String, List<String>> normalizedJoinManyToManies
) {


    public Sql(String schemaName, String tableName, UUID applicationId) {
        this(
                schemaName,
                tableName,
                applicationId,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>()
        );
    }
}