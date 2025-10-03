package fr.inra.oresing.persistence.denormalized;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public record PoliciesBuilder(String schemaName, String tableName, List<String> timescopes,
                              java.util.Map<String, String> authorizationScopes) {

    public String buildPolicies() {
        return """
                ALTER TABLE IF EXISTS %1$s_dn.%2$s
                ENABLE ROW LEVEL SECURITY;
                
                DO
                $$
                    DECLARE
                        rec RECORD;
                        sql_policy
                            TEXT;
                    BEGIN
                        FOR rec IN
                            SELECT id::TEXT as id,
                                application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role%3$s
                            FROM %1$s.oresiauthorization
                            WHERE authorizations ? '%2$s'
                            LOOP
                                sql_policy := format($fmt$
                            CREATE POLICY "%%1$s_%2$s_sel"
                            ON %1$s_dn.%2$s
                            AS PERMISSIVE
                            TO "%%2$s"
                            USING (
                                %4$s
                            )
                        $fmt$, rec.id, rec.role%5$s);
                
                                EXECUTE sql_policy;
                            END LOOP;
                    END
                $$;"""
                .formatted(
                        schemaName(),
                        tableName(),
                        selectForAuthorizations(),
                        usingForAuthorization(),
                        authorizationAssignement()
                );
    }

    private String authorizationAssignement() {
        List<String> assignements = new ArrayList<>();
        timescopes().stream()
                .findFirst()
                .map(_ -> "rec.timescope")
                .ifPresent(assignements::add);
        authorizationScopes().keySet().stream()
                .map("rec.%s"::formatted)
                .forEach(assignements::add);
        if (assignements.isEmpty()) {
            return "";
        }
        return assignements.stream().collect(Collectors.joining(", ", ", ", ""));
    }

    private Object selectForAuthorizations() {
        List<String> selects = new ArrayList<>();
        timescopes().stream()
                .findFirst()
                .map(_ -> " (authorizations #>> '{%1$s, timescope}')::tsrange                 AS timescope".formatted(tableName()))
                .ifPresent(selects::add);
        authorizationScopes().keySet().stream()
                .map(refType -> "(authorizations #>> '{%1$s, authorizationscope,%2$s,0}')         AS %2$s".formatted(tableName(), refType))
                .forEach(selects::add);
        if (selects.isEmpty()) {
            return "";
        }
        return selects.stream().collect(Collectors.joining(",\n\t\t\t\t", ",\n\t\t\t\t", ""));
    }

    private String usingForAuthorization() {
        List<String> usings = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(3);
        timescopes().stream()
                .findFirst()
                .map(name -> """
                        CASE
                          WHEN '%2$s' = '' THEN TRUE
                          ELSE %1$s <@ '%2$s'::tsrange
                        END""".formatted(name, "%" + counter.getAndIncrement() + "$s"))
                .ifPresent(usings::add);
        authorizationScopes().values().stream()
                .map(name -> """
                        %1$s_hk <@ '%2$s'::ltree""".formatted(name, "%" + counter.getAndIncrement() + "$s"))
                .forEach(usings::add);
        if (usings.isEmpty()) {
            return "true";
        } else {
            return usings.stream().collect(Collectors.joining("\n\t\t\t\tAND "));
        }
    }
}