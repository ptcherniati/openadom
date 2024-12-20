package fr.inra.oresing.persistence.index;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.*;

import java.util.*;
import java.util.stream.Collectors;

public record AuthorizationIndex(Application application) {
    public String createIndexes() {
        StringBuilder sqlBuilder = new StringBuilder();

        // Supprimer d'abord tous les index existants
        sqlBuilder.append(dropIndexes()).append("\n");

        // Créer les nouveaux index pour chaque dataname
        application().getConfiguration().dataDescription().keySet().stream().forEach(dataname -> sqlBuilder.append(createIndex(dataname)).append("\n"));

        return sqlBuilder.toString();
    }

    public String dropIndexes() {
        return """
                DO $$
                DECLARE
                    idx record;
                BEGIN
                    FOR idx IN (SELECT indexname FROM pg_indexes WHERE schemaname = '%1$s'
                    AND indexname LIKE 'authorization_%%_index')
                    LOOP
                        EXECUTE 'DROP INDEX IF EXISTS ' || quote_ident(idx.indexname);
                    END LOOP;
                END $$;
                """
                .formatted(application().getName());
    }

    public String createIndex(String dataname) {
        StringBuilder indexSql = new StringBuilder();

        // Index partiel pour referencetype et refvalues (toujours créé)
        indexSql.append(String.format("""
                        CREATE INDEX IF NOT EXISTS %1$s_refvalues_index
                        ON %2$s.referencevalue USING gin
                        (
                            refvalues jsonb_path_ops
                        )
                        WHERE referencetype = '%3$s';
                        
                        """,
                indexName(dataname),
                application().getName(),
                dataname
        ));

        final boolean[] hasRequiredAuthorizations = {false};
        final boolean[] hasTimeScope = {false};
        final List<String> authorizationScopes = new ArrayList<>();

        application().findData(dataname)
                .map(StandardDataDescription::authorization)
                .ifPresent(auth -> {
                    hasRequiredAuthorizations[0] = !auth.authorizationScope().isEmpty();
                    if (hasRequiredAuthorizations[0]) {
                        auth.authorizationScope().stream()
                                .map(AuthorizationScopeComponentData::data)
                                .forEach(authorizationScopes::add);
                    }
                    hasTimeScope[0] = !Strings.isNullOrEmpty(auth.timeScope());
                });

        // Si des autorisations sont requises, créer un index supplémentaire
        if (hasRequiredAuthorizations[0] || hasTimeScope[0]) {
            List<String> authIndexColumns = new ArrayList<>();
            List<String> timescopeIndexColumns = new ArrayList<>();

            if (hasRequiredAuthorizations[0]) {
                authorizationScopes.forEach(scope ->
                        authIndexColumns.add(String.format("((\"authorization\").requiredauthorizations.%s)", scope))
                );


                indexSql.append(String.format("""
                            CREATE INDEX IF NOT EXISTS %1$s_auth_index
                            ON %2$s.referencevalue USING gin
                            (
                                %3$s
                            )
                            WHERE referencetype = '%4$s';
                            
                            """,
                        indexName(dataname),
                        application().getName(),
                        String.join(",\n    ", authIndexColumns),
                        dataname
                ));
            }

            if (hasTimeScope[0]) {
                indexSql.append(String.format("""
                            CREATE INDEX IF NOT EXISTS %1$s_timescope_index
                            ON %2$s.referencevalue USING gist
                            %3$s
                            WHERE referencetype = '%4$s';
                            
                            """,
                        indexName(dataname),
                        application().getName(),
                        "(((\"authorization\").timescope))",
                        dataname
                ));
            }

        }

        return indexSql.toString();
    }


    public String sqlFilterForAuthorization(String dataName, AuthorizationForScope authorization, boolean withTimeScope) {
        List<String> conditions = new ArrayList<>();
        conditions.add("referencetype ='%s'".formatted(dataName));

        if (authorization instanceof AuthorizationNoRestriction) {
            return String.join(" AND ", conditions);
        }

        final boolean[] hasRequiredAuthorizations = {false};
        final boolean[] hasTimeScope = {false};

        application().findData(dataName)
                .map(StandardDataDescription::authorization)
                .ifPresent(auth -> {
                    hasRequiredAuthorizations[0] = !auth.authorizationScope().isEmpty();
                    hasTimeScope[0] = !Strings.isNullOrEmpty(auth.timeScope());
                });

        switch (authorization) {
            case AuthorizationForTimeScope authorizationForTimeScope -> {
                if (hasRequiredAuthorizations[0]) {
                    addEmptyReferenceConditions(conditions, dataName);
                }
                if (hasTimeScope[0] && withTimeScope) {
                    addTimeCondition(conditions, authorizationForTimeScope.timeScope());
                }
            }
            case AuthorizationForReferenceScope authorizationForReferenceScope -> {
                if (hasRequiredAuthorizations[0]) {
                    addReferenceConditions(conditions, authorizationForReferenceScope.authorizationScope());
                }
            }
            case AuthorizationForReferenceScopeAndTimeScope authorizationForReferenceScopeAndTimeScope -> {
                if (hasRequiredAuthorizations[0]) {
                    addReferenceConditions(conditions, authorizationForReferenceScopeAndTimeScope.authorizationScope());
                }
                if (hasTimeScope[0] && withTimeScope) {
                    addTimeCondition(conditions, authorizationForReferenceScopeAndTimeScope.timeScope());
                }
            }
            default -> throw new IllegalArgumentException("Type d'autorisation non reconnu");
        }

        return String.join(" AND ", conditions);
    }


    private void addEmptyReferenceConditions(List<String> conditions, String dataName) {
        application().findData(dataName)
                .map(StandardDataDescription::authorization)
                .map(Authorization::authorizationScope)
                .ifPresent(scopes -> scopes.forEach(scope ->
                        conditions.add("(\"authorization\").requiredauthorizations.%s <@ ''::ltree".formatted(scope.data()))
                ));
    }

    private void addTimeCondition(List<String> conditions, LocalDateTimeRange timeScope) {
        if (timeScope != null) {
            conditions.add("(\"authorization\").timescope && '%s'::tsrange".formatted(timeScope.toSqlExpression()));
        }
    }

    private void addReferenceConditions(List<String> conditions, Map<String, List<Ltree>> authorizationScope) {
        SortedMap<String, List<Ltree>> sortedScope = new TreeMap<>(authorizationScope);
        for (String field : sortedScope.keySet()) {
            List<Ltree> values = sortedScope.get(field);
            if (!values.isEmpty()) {
                List<Ltree> uniqueValues = eliminateNestedLtrees(values);
                conditions.add("(\"authorization\").requiredauthorizations.%s @> ARRAY[%s]::ltree[]"
                        .formatted(field, uniqueValues.stream()
                                .map(Ltree::getSql)
                                .map(s -> "'" + s + "'")
                                .collect(Collectors.joining(", "))));
            } else {
                conditions.add("(\"authorization\").requiredauthorizations.%s IS NULL".formatted(field));
            }
        }
    }

    private List<Ltree> eliminateNestedLtrees(List<Ltree> ltrees) {
        return ltrees.stream()
                .sorted(Comparator.comparing(Ltree::getSql))
                .filter(ltree -> ltrees.stream()
                        .filter(other -> !other.equals(ltree))
                        .noneMatch(other -> other.isAncestorOf(ltree)))
                .collect(Collectors.toList());
    }

    public String indexName(String dataname) {
        return "authorization_%1$s_index".formatted(dataname);
    }
}