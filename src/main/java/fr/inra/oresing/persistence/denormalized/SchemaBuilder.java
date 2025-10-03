package fr.inra.oresing.persistence.denormalized;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.normalized.Sql;

import java.util.*;
import java.util.stream.Collectors;

public record SchemaBuilder(List<Sql> buildedSqls, Application application) {
    public SchemaBuilder(List<Sql> buildedSqls, Application application) {
        this.buildedSqls = sortSqlsByForeignKeyDependency(buildedSqls);
        this.application = application;
    }

    public static List<Sql> sortSqlsByForeignKeyDependency(List<Sql> buildedSqls) {
        Map<String, Sql> sqlByName = new HashMap<>();
        buildedSqls.forEach(sql -> sqlByName.putIfAbsent(sql.tableName(), sql));
//        buildedSqls
//                .forEach(sql -> sql.normalizedJoinManyToManies().keySet()
//                        .forEach(foreignManyTable ->  sqlByName.putIfAbsent(foreignManyTable, sql))
//                );

        // Build dependency graph: tableName -> set of referenced tables (excluding self)
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (Sql sql : buildedSqls) {
            Set<String> refs = new HashSet<>();
            for (String ref : sql.foreignKeys().keySet()) {
                if (!ref.equals(sql.tableName())) refs.add(ref);
            }
            dependencies.put(sql.tableName(), refs);
            sql.normalizedJoinManyToManies().keySet()
                    .forEach(foreignManyTable ->  dependencies.computeIfAbsent(sql.tableName(), _-> new LinkedHashSet<>()).add(foreignManyTable)) ;
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

    public String buildSchema() {
        final String tablesCreationSchema = buildedSqls.stream()
                .map(sql->new TableBuilder(sql))
                .map(TableBuilder::createTable)
                .collect(Collectors.joining("\n\t"));
        return  """
                drop schema if exists %2$s_dn cascade;
                create schema %2$s_dn;
                ALTER SCHEMA %2$s_dn
                 OWNER TO "%3$s_applicationManager";
                
                GRANT USAGE ON SCHEMA %2$s_dn TO PUBLIC;
                
                create table %2$s_dn.referenceDisplay as
                     (select id,
                             hierarchicalkey,
                             COALESCE(
                                     NULLIF(refvalues ->> '__display_fr', ''),
                                     refvalues ->> '__display_default'
                             ) display_fr,
                             COALESCE(
                                     NULLIF(refvalues ->> '__display_en', ''),
                                     NULLIF(refvalues ->> '__display_fr', ''),
                                     refvalues ->> '__display_default'
                             ) display_en
                      from %2$s.referencevalue);
                      ALTER TABLE IF EXISTS %2$s_dn.referenceDisplay
                          ADD CONSTRAINT "PK" PRIMARY KEY (id);
                       %1$s
                       drop table %2$s_dn.referenceDisplay; """
                .formatted(
                        tablesCreationSchema,
                        application.getName(),
                        application.getId().toString()
                );
    }
}