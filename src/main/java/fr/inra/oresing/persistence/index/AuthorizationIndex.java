package fr.inra.oresing.persistence.index;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Authorization;
import fr.inra.oresing.domain.application.configuration.AuthorizationScopeComponentData;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.FilterModel;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.*;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.persistence.PgIdentifier;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public record AuthorizationIndex(Application application, Set<String> dataNames, boolean legacyDefault) {
    public AuthorizationIndex {
        dataNames = CollectionUtils.isEmpty(dataNames) ? Set.copyOf(application.getAllDataNames()) : dataNames;
    }

    public AuthorizationIndex(Application application) {
        this(application, Set.copyOf(application.getAllDataNames()), false);
    }

    public AuthorizationIndex(Application application, Set<String> dataNames) {
        this(application, dataNames, false);
    }

    public String createIndexes() {
        StringBuilder sqlBuilder = new StringBuilder();

        // Supprimer d'abord tous les index existants
        sqlBuilder.append(dropIndexes()).append("\n");

        // Créer les nouveaux index pour chaque dataname
        final Set<String> dataNamesForindexes = dataNames().isEmpty() ?
                application().getConfiguration().dataDescription().keySet()
                : dataNames();

        dataNamesForindexes.forEach(dataname -> sqlBuilder.append(createIndex(dataname)).append("\n"));

        return sqlBuilder.toString();
    }

    public String dropIndexes() {
        if (dataNames() == null || dataNames().isEmpty()) {
            // Aucun index à supprimer
            return "";
        }

        String referencetypes = dataNames().stream()
                .map(name -> "'" + name + "'")
                .collect(Collectors.joining(", "));

        return """
                DO $$
                DECLARE
                    idx record;
                    ref_types TEXT[] := ARRAY[%2$s];
                    ref_type TEXT;
                BEGIN
                    FOREACH ref_type IN ARRAY ref_types
                    LOOP
                        FOR idx IN (SELECT indexname FROM pg_indexes 
                                    WHERE schemaname = '%1$s'
                                    AND indexname LIKE 'authorization_' || ref_type || '_index%%')
                        LOOP
                            EXECUTE 'DROP INDEX IF EXISTS %1$s.' || quote_ident(idx.indexname);
                        END LOOP;
                    END LOOP;
                END $$;
                """
                .formatted(application().getName(), referencetypes);
    }

    public String createIndex(String dataname) {
        StringBuilder indexSql = new StringBuilder();
        FilterModel filterModel = effectiveFilterModel(dataname);

        if (filterModel == FilterModel.LEGACY_GIN) {
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
        } else if (filterModel == FilterModel.DEFINED_FILTERS) {
            application().findData(dataname)
                    .map(StandardDataDescription::componentDescriptions)
                    .stream()
                    .flatMap(components -> components.entrySet().stream())
                    .forEach(componentEntry -> {
                        String componentKey = componentEntry.getKey();
                        ComponentDescription componentDescription = componentEntry.getValue();
                        if (componentDescription.isFilterableAsList()) {
                            indexSql.append(String.format("""
                                            CREATE INDEX IF NOT EXISTS %1$s
                                            ON %2$s.referencevalue ((refvalues->>'%3$s'))
                                            WHERE referencetype = '%4$s';
                                            
                                            """,
                                    filterColumnIndexName(dataname, componentKey, false),
                                    application().getName(),
                                    componentKey,
                                    dataname));
                        }
                        if (componentDescription.isFilterableAsText()) {
                            indexSql.append(String.format("""
                                            CREATE INDEX IF NOT EXISTS %1$s
                                            ON %2$s.referencevalue USING gin (lower(refvalues->>'%3$s') gin_trgm_ops)
                                            WHERE referencetype = '%4$s';
                                            
                                            """,
                                    filterColumnIndexName(dataname, componentKey, true),
                                    application().getName(),
                                    componentKey,
                                    dataname));
                        }
                    });
        }

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
            case AuthorizationForTimeScope(
                    Set<OperationType> _,
                    LocalDateTimeRange timeScope
            ) -> {
                if (hasRequiredAuthorizations[0]) {
                    addEmptyReferenceConditions(conditions, dataName);
                }
                if (hasTimeScope[0] && withTimeScope) {
                    addTimeCondition(conditions, timeScope);
                }
            }
            case AuthorizationForReferenceScope(
                    Set<OperationType> _,
                    Map<String, List<Ltree>> authorizationScope
            ) when hasRequiredAuthorizations[0] -> {
                addReferenceConditions(conditions, authorizationScope);
            }
            case AuthorizationForReferenceScopeAndTimeScope(
                    Set<OperationType> _,
                    Map<String, List<Ltree>> authorizationScope,
                    LocalDateTimeRange timeScope
            ) -> {
                if (hasRequiredAuthorizations[0]) {
                    addReferenceConditions(conditions, authorizationScope);
                }
                if (hasTimeScope[0] && withTimeScope) {
                    addTimeCondition(conditions, timeScope);
                }
            }
            default -> throw new IllegalArgumentException("Type d'autorisation non reconnu");
        }

        return String.join("\n AND ", conditions);
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
        for (Map.Entry<String, List<Ltree>> entry : sortedScope.entrySet()) {
            List<Ltree> values = entry.getValue();
            if (!values.isEmpty()) {
                List<Ltree> uniqueValues = eliminateNestedLtrees(values);
                conditions.add("(\"authorization\").requiredauthorizations.%s @> ARRAY[%s]::ltree[]"
                        .formatted(entry.getKey(), uniqueValues.stream()
                                .map(Ltree::getSql)
                                .map(s -> "'" + s + "'")
                                .collect(Collectors.joining(", "))));
            } else {
                conditions.add("(\"authorization\").requiredauthorizations.%s IS NULL".formatted(entry.getKey()));
            }
        }
    }

    private List<Ltree> eliminateNestedLtrees(List<Ltree> ltrees) {
        return ltrees.stream()
                .sorted(Comparator.comparing(Ltree::getSql))
                .filter(ltree -> ltrees.stream()
                        .filter(other -> !other.equals(ltree))
                        .noneMatch(other -> other.isAncestorOf(ltree)))
                .toList();
    }

    /**
     * Suffixe fixe le plus long parmi {@code _refvalues_index} ( 16 ),
     * {@code _auth_index} ( 11 ), {@code _timescope_index} ( 16 ) et
     * {@code _filter_<col>_text_index}.
     * Réservé en amont pour que la troncature laisse toujours la place
     * au suffixe ajouté par {@link #createIndex(String)}.
     */
    private static final int RESERVED_SUFFIX_LENGTH = "_filter__text_index".length();

    public String indexName(String dataname) {
        // Construit le préfixe nominal puis garantit qu'il rentre dans
        // PgIdentifier.MAX_IDENTIFIER_LENGTH ( 63 ) après concaténation
        // du suffixe le plus long ( cf. RESERVED_SUFFIX_LENGTH ). Si la
        // longueur dépasse, PgIdentifier.truncateSafe substitue les
        // octets en trop par un hash CRC32 court, garantissant l'unicité
        // entre datatypes ACBB longs ( ex. t_soil_analysis_sana_complete_long ).
        final String raw = "authorization_%1$s_index".formatted(dataname);
        return PgIdentifier.truncateSafe(raw, RESERVED_SUFFIX_LENGTH);
    }

    /**
     * Calcule l'ensemble des noms d'index ATTENDUS pour les dataNames de
     * cette application . Utilise par {@code MigrateService} pour faire
     * un diff vs {@code pg_indexes} et skip le DROP+CREATE complet quand
     * l'etat reel correspond deja ( cf AUDIT 06-05-26 #3 option A ) .
     *
     * <p>Les noms generes ici doivent correspondre EXACTEMENT a ceux
     * produits par {@link #createIndex(String)} ( meme prefix , meme
     * suffix ) sinon le diff genere des faux positifs .
     */
    public Set<String> expectedIndexNames() {
        Set<String> expected = new HashSet<>();
        Set<String> dataNamesForIndexes = dataNames().isEmpty()
                ? application().getConfiguration().dataDescription().keySet()
                : dataNames();
        for (String dataname : dataNamesForIndexes) {
            String prefix = indexName(dataname);
            FilterModel filterModel = effectiveFilterModel(dataname);
            if (filterModel == FilterModel.LEGACY_GIN) {
                expected.add(prefix + "_refvalues_index");
            } else if (filterModel == FilterModel.DEFINED_FILTERS) {
                application().findData(dataname)
                        .map(StandardDataDescription::componentDescriptions)
                        .stream()
                        .flatMap(components -> components.entrySet().stream())
                        .forEach(componentEntry -> {
                            if (componentEntry.getValue().isFilterableAsList()) {
                                expected.add(filterColumnIndexName(dataname, componentEntry.getKey(), false));
                            }
                            if (componentEntry.getValue().isFilterableAsText()) {
                                expected.add(filterColumnIndexName(dataname, componentEntry.getKey(), true));
                            }
                        });
            }
            // _auth_index / _timescope_index : conditionnels selon
            // configuration authorization.scope / timescope
            application().findData(dataname)
                    .map(StandardDataDescription::authorization)
                    .ifPresent(auth -> {
                        if (auth.authorizationScope() != null && !auth.authorizationScope().isEmpty()) {
                            expected.add(prefix + "_auth_index");
                        }
                        if (!Strings.isNullOrEmpty(auth.timeScope())) {
                            expected.add(prefix + "_timescope_index");
                        }
                    });
        }
        return expected;
    }

    private FilterModel effectiveFilterModel(String dataname) {
        FilterModel declared = application().findData(dataname)
                .map(dataDescription -> Optional.ofNullable(application().resolveFilterModel(dataDescription))
                        .orElseGet(dataDescription::filterModel))
                .orElseGet(FilterModel::defaultValue);
        if (legacyDefault() && declared == FilterModel.NONE) {
            return FilterModel.LEGACY_GIN;
        }
        return declared;
    }

    private String filterColumnIndexName(String dataname, String componentKey, boolean isText) {
        return PgIdentifier.truncateSafe(indexName(dataname) + filterColumnIndexSuffix(componentKey, isText), 0);
    }

    private String filterColumnIndexSuffix(String componentKey, boolean isText) {
        String safeComponentKey = componentKey.replaceAll("[^A-Za-z0-9_]", "_");
        return "_filter_" + safeComponentKey + (isText ? "_text_index" : "_index");
    }
}