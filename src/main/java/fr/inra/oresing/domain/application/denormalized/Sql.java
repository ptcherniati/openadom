package fr.inra.oresing.domain.application.denormalized;

import org.springframework.jdbc.support.incrementer.HsqlSequenceMaxValueIncrementer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public record Sql(
        String schemaName,
        String tableName,
        List<String> select,
        List<String> refValuesTable,
        List<ReferenceJoin> referenceJoin,
        List<String> indexes,
        Map<String, List<String>> foreignKeys,
        List<String> timescopes,
        List<String> authorizationScopes
) {
    public static List<Sql> sortSqlsByForeignKeyDependency(List<Sql> buildedSqls) {
        Map<String, Sql> sqlByName = new HashMap<>();
        buildedSqls.forEach(sql -> sqlByName.put(sql.tableName(), sql));

        // Build dependency graph: tableName -> set of referenced tables (excluding self)
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (Sql sql : buildedSqls) {
            Set<String> refs = new HashSet<>();
            for (String ref : sql.foreignKeys().keySet()) {
                if (!ref.equals(sql.tableName())) refs.add(ref);
            }
            dependencies.put(sql.tableName(), refs);
        }

        List<Sql> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>(); // for cycle detection

        // Recursive topological sort
        class Helper {
            void visit(String name) {
                if (visited.contains(name)) return;
                if (visiting.contains(name)) throw new IllegalArgumentException("Cycle detected at " + name);
                visiting.add(name);
                for (String dep : dependencies.getOrDefault(name, Collections.emptySet())) {
                    if (sqlByName.containsKey(dep)) visit(dep);
                }
                visiting.remove(name);
                visited.add(name);
                sorted.add(sqlByName.get(name));
            }
        }
        Helper helper = new Helper();
        for (String name : sqlByName.keySet()) {
            helper.visit(name);
        }
        return sorted;
    }
    public Sql(String schemaName, String tableName) {
        this(
                schemaName,
                tableName,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new LinkedHashMap<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    public String buildSelects() {
        return select().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(",\n\t\t"));
    }

    public String buildFrom() {
        return """
                                            
                                            \t\t%1$s.referencevalue,
                                            \t\t%2$s%3$s
                                            \t\t%4$s
                                            \t\t%5$s
                                            """.formatted(
                schemaName,
                """
                        -- add simple fields
                                JSON_TABLE(
                                            refvalues, 
                                            '$' COLUMNS (
                                                %1$s
                                            )
                                         ) AS val"""
                        .formatted(
                                refValuesTable().stream()
                                        .filter(Objects::nonNull)
                                        .collect(
                                                Collectors.joining(",\n\t\t\t\t\t\t")
                                        )
                        ),
                referenceJoin().isEmpty() ? "" : ",",
                referenceJoin().isEmpty() ? "" : """
                                                    -- add references fields
                                                            JSON_TABLE(
                                                                        refslinkedto, 
                                                                        '$' COLUMNS (
                                                                            %1$s
                                                                        )
                                                                    ) AS refs"""
                        .formatted(referenceJoin().stream()
                                .filter(Objects::nonNull)
                                .map(ReferenceJoin::refsLinkedToTable)
                                .collect(Collectors.joining(",\n\t\t\t\t")
                                )
                        ),
                referenceJoin().isEmpty() ? "" :
                        referenceJoin().stream()
                                .filter(Objects::nonNull)
                                .map(ReferenceJoin::referenceJoin)
                                .collect(Collectors.joining("\n\t\t"))
        );
    }

    public String buildIndexes() {
        return indexes().stream()
                .collect(Collectors.joining("\n"));
    }

    public String createTable() {
        return """
                            --create table %2$s
                            
                            create table %1$s_dn.%2$s as (
                                select
                                    referencevalue.id,
                                    referencevalue.naturalkey,
                                    referencevalue.hierarchicalkey,
                                    %3$s
                            
                                FROM %4$s
                                WHERE referencetype = '%2$s'
                                GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
                            );
                            
                            GRANT SELECT ON TABLE %1$s_dn.%2$s TO PUBLIC;
                            
                            -- primary key
                            ALTER TABLE %1$s_dn.%2$s
                                ALTER COLUMN id SET NOT NULL;
                            ALTER TABLE %1$s_dn.%2$s
                                ADD CONSTRAINT %2$s_pk PRIMARY KEY (id);
                                
                            -- indexes
                            %5$s 
                            
                            -- foreignKeys
                            %6$s   
                            
                            -- policies
                            %7$s
                            
                            """
                .formatted(
                        schemaName,
                        tableName,
                        buildSelects(),
                        buildFrom(),
                        buildIndexes(),
                        buildForeignKeys(),
                        buildPolicies()
                );
    }

    private String buildPolicies() {
       return  """
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
                .map(_->"rec.timescope")
                .ifPresent(assignements::add);
        authorizationScopes().stream()
                .map("rec.%s"::formatted)
                .forEach(assignements::add);
        if(assignements.isEmpty()) {
            return "";
        }
        return assignements.stream().collect(Collectors.joining(", ",", ",""));
    }

    private Object selectForAuthorizations() {
        List<String> selects = new ArrayList<>();
        timescopes().stream()
                .findFirst()
                .map(_->" (authorizations #>> '{%1$s, timescope}')::tsrange                 AS timescope".formatted(tableName()))
                .ifPresent(selects::add);
        authorizationScopes().stream()
                .map(scope->"(authorizations #>> '{%1$s, authorizationscope,%2$s,0}')         AS %2$s".formatted(tableName(), scope))
                .forEach(selects::add);
        if(selects.isEmpty()) {
            return "";
        }
        return selects.stream().collect(Collectors.joining(",\n\t\t\t\t", ",\n\t\t\t\t",""));
    }

    private String usingForAuthorization() {
        List<String> usings = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(3);
        timescopes().stream()
                .findFirst()
                .map(name->"""
                        %1$s <@ '%2$s'::tsrange""".formatted(name,"%"+counter.getAndIncrement()+"$s"))
                .ifPresent(usings::add);
        authorizationScopes().stream()
                .map(name-> """
                        %1$s <@ '%2$s'::ltree""".formatted(name,"%"+counter.getAndIncrement()+"$s"))
                .forEach(usings::add);
        if(usings.isEmpty()) {
            return "true";
        }else{
            return usings.stream().collect(Collectors.joining("\n\t\t\t\tAND "));
        }
    }

    private String buildForeignKeys() {
        return foreignKeys().entrySet()
                .stream()
                .map(foreignKeyEntry -> {
                    String refType = foreignKeyEntry.getKey();
                    return foreignKeyEntry.getValue().stream()
                            .map(foreignKeyFieldName-> """
                                    
                                    ALTER TABLE IF EXISTS %1$s_dn.%4$s
                                        ADD CONSTRAINT "%2$s__%3$s_fk" FOREIGN KEY ("%3$s")
                                            REFERENCES %1$s_dn.%2$s(id);"""
                                    .formatted(
                                            schemaName(),
                                            refType,
                                            foreignKeyFieldName.replace("\"",""),
                                            tableName()
                                            )
                            )
                            .collect(Collectors.joining("\n"));
                            
                })
                .collect(Collectors.joining("\n"));
    }
}