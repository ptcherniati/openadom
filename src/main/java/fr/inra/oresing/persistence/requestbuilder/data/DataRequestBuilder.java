package fr.inra.oresing.persistence.requestbuilder.data;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.persistence.EmptyCellPredicate;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForApplication;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DataRequestBuilder {
    public static final String DELIMITER_OR = " || ";
    private final DownloadDatasetQuery downloadDatasetQuery;

    private final SqlSchemaForApplication schema;
    private final AtomicInteger atomicInteger = new AtomicInteger();
    private final MapSqlParameterSource paramSource = new MapSqlParameterSource();

    public DataRequestBuilder(final DownloadDatasetQuery downloadDatasetQuery) {
        super();
        this.downloadDatasetQuery = downloadDatasetQuery;
        schema = SqlSchema.forApplication(downloadDatasetQuery.application());
    }

    static String sanitize(final String key) {
        return Optional.ofNullable(key)
                .map(s -> s.replace("'", "''"))
                .map(s -> s.replace("::", "."))
                .orElse(null);
    }

    /**
     * Échappe une valeur saisie par l'utilisateur destinée à être insérée
     * comme littéral à l'intérieur d'une chaîne JSONPath ( la partie entre
     * guillemets dans {@code @ == "<valeur>"} ).
     *
     * <p>Trois sources d'erreur sont neutralisées :
     * <ol>
     *   <li>{@code \} et {@code "} casseraient la chaîne JSONPath -> erreur
     *       de syntaxe SQL côté Postgres -> HTTP 500.
     *   <li>{@code '} casserait le littéral SQL externe ( wrapper
     *       {@code 'exists(...)'} ) ; déjà couvert par {@link #sanitize}.
     *   <li>{@code %} serait interprété comme spécificateur de format par
     *       le second appel à {@link String#formatted} effectué dans
     *       {@link SelectRequest#build()} ( la template laisse encore
     *       {@code %1$s/%2$s/%3$s} pour ORDER BY / OFFSET / LIMIT ) ,
     *       provoquant {@code UnknownFormatConversionException}. La saisie
     *       utilisateur n'est jamais censée être une chaîne de format ;
     *       on double les {@code %} pour qu'ils ressortent littéraux après
     *       le second format.
     * </ol>
     *
     * <p>L'ordre des remplacements est volontaire : on traite d'abord
     * {@code \} ( pour ne pas redoubler les {@code \} introduits ensuite
     * pour échapper {@code "} ) , puis le reste.
     */
    static String sanitizeJsonPathStringValue(final String value) {
        return Optional.ofNullable(value)
                .map(s -> s.replace("\\", "\\\\"))
                .map(s -> s.replace("\"", "\\\""))
                .map(s -> s.replace("'", "''"))
                .map(s -> s.replace("%", "%%"))
                .orElse(null);
    }

    /**
     * Construit le prédicat JSONPath d'égalité d'une valeur de filtre. Une
     * valeur {@code null} ( convention "(vide)" décidée en §5.7 du rapport
     * FILTER_TEXT_LIST.md ) est traduite en {@code @ == null} ; toute autre
     * valeur est échappée via {@link #sanitizeJsonPathStringValue} et
     * comparée comme chaine. Centralisé pour que tous les sites de filtrage
     * texte ( PlainText , Regexp , Reference ) traitent {@code null} de
     * façon cohérente.
     */
    static String buildEqualityPredicate(final String filter) {
        return filter == null
                ? EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE
                : "@ == \"%s\"".formatted(sanitizeJsonPathStringValue(filter));
    }

    /**
     * Variante de {@link #buildEqualityPredicate} pour les filtres référence ,
     * qui acceptent en plus les correspondances hiérarchiques ( clé qui
     * commence par {@code "<filter>."} - matche tous les descendants ). Une
     * valeur {@code null} reste traitée via {@link EmptyCellPredicate#JSONPATH_EMPTY_PREDICATE}
     * ( cellule vide = JSON null OU chaîne JSON "" ) ; on ne compose pas
     * le {@code starts with} pour {@code null} parce qu'une branche
     * d'arbre n'a pas de sens vide.
     */
    static String buildReferencePredicate(final String filter) {
        return filter == null
                ? EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE
                : "@ == \"%1$s\"  || @ starts with \"%2$s\"".formatted(filter, filter + ".");
    }

    /**
     * Variante de {@link #buildEqualityPredicate} pour les filtres regexp
     * ( recherche LIKE , insensible à la casse ). La valeur a déjà été
     * échappée des méta-regex côté frontend ; on l'utilise telle quelle
     * dans {@code like_regex}. Pour {@code null} ( sentinelle "( vide )" ) ,
     * {@code like_regex} n'a pas de sens , on retombe sur l'égalité vide
     * unifiée via {@link EmptyCellPredicate#JSONPATH_EMPTY_PREDICATE} .
     */
    static String buildRegexpPredicate(final String filter) {
        if (filter == null) {
            return EmptyCellPredicate.JSONPATH_EMPTY_PREDICATE;
        }
        final String safe = sanitizeJsonPathStringValue(filter);
        return "@ == \"%1$s\" || @ like_regex \"%1$s\" flag \"i\" ".formatted(safe);
    }

    public static SqlRequest buildSelectRequest(final DownloadDatasetQuery downloadDatasetQuery) {
        final DataRequestBuilder dataTypeRequestBuilder = new DataRequestBuilder(downloadDatasetQuery);
        return dataTypeRequestBuilder.buildRequestSelect();
    }

    public static SqlRequest buildDeleteRequest(final DownloadDatasetQuery downloadDatasetQuery) {
        final DataRequestBuilder dataTypeRequestBuilder = new DataRequestBuilder(downloadDatasetQuery);
        return dataTypeRequestBuilder.buildRequestDelete();
    }

    public static String filter(final ComponentFilters componentFilters) {
        return switch (componentFilters) {
            case final NoComponentFilters noComponentFilters -> null;
            case final ComponentFilterForInterval componentFilterForInterval -> switch (componentFilterForInterval) {

                // FIX #465 — Filtre numérique par intervalle (min/max)
                // Le JSONPath utilise un OU logique (||) pour supporter deux structures de données :
                //   - Composant simple : la valeur est directement dans le champ → @.double() matche
                //     Exemple JSON : { "temperature": 25.3 }
                //   - PatternComponent : la valeur est dans un sous-champ __VALUE__ → @.__VALUE__.double() matche
                //     Exemple JSON : { "tel_data": { "__VALUE__": 0.2863, "tel_variable": "NDVI", ... } }
                // Sans le ||, les filtres sur PatternComponent retournaient 0 résultat car @.double()
                // ne peut pas convertir un objet JSON entier en nombre.
                case ComponentFiltersForIntervalByNumeric(
                        String componentKey,
                        List<IntervalValuesNumeric> intervalsValues,
                        Multiplicity multiplicity
                ) -> switch (multiplicity) {
                    case ONE -> """
                            $.%1$s ? (%2$s)
                            """
                            .formatted(
                                    sanitize(componentKey),
                                    intervalsValues.stream()
                                            .map(intervalValues ->
                                                    "(@.double() >= %1$s && @.double() <= %2$s) || (@.__VALUE__.double() >= %1$s && @.__VALUE__.double() <= %2$s)".formatted(
                                                            intervalValues.fromFromNumeric(),
                                                            intervalValues.fromToNumeric()
                                                    )
                                            )
                                            .collect(Collectors.joining(DELIMITER_OR))
                            );
                    case MANY -> """
                            $[*].%1$s  ? (%2$s)
                            """
                            .formatted(
                                    sanitize(componentKey),
                                    intervalsValues.stream()
                                            .map(intervalValues ->
                                                    "(@.double() >= %1$s && @.double() <= %2$s) || (@.__VALUE__.double() >= %1$s && @.__VALUE__.double() <= %2$s)".formatted(
                                                            intervalValues.fromFromNumeric(),
                                                            intervalValues.fromToNumeric()
                                                    )
                                            )
                                            .collect(Collectors.joining(DELIMITER_OR))
                            );
                };

                case final ComponentFiltersForIntervalByTemporal componentFiltersForIntervalByTemporal ->
                        switch (componentFiltersForIntervalByTemporal.multiplicity()) {
                            case ONE -> """
                                    $.%1$s ?  (%2$s)
                                    """.formatted(
                                    sanitize(componentFiltersForIntervalByTemporal.componentKey()),
                                    componentFiltersForIntervalByTemporal.intervalsValues().stream()
                                            .map(intervalvalues -> "(@ >= \"date:%1$s\" && @ < \"date:%2$s\")"
                                                    .formatted(
                                                            intervalvalues.getFromIsoString(),
                                                            intervalvalues.getToIsoString()
                                                    ))
                                            .collect(Collectors.joining(DELIMITER_OR))
                            );
                            case MANY -> """
                                    $[*].%1$s ?   (%2$s)
                                    """.formatted(
                                    sanitize(componentFiltersForIntervalByTemporal.componentKey()),
                                    componentFiltersForIntervalByTemporal.intervalsValues().stream()
                                            .map(intervalvalues -> "(@ >= \"date:%1$s\" && @ < \"date:%2$s\")"
                                                    .formatted(
                                                            intervalvalues.getFromIsoString(),
                                                            intervalvalues.getToIsoString()
                                                    ))
                                            .collect(Collectors.joining(DELIMITER_OR))
                            );
                        };
            };
            case final ComponentFilterSimpleSearch componentFilterSimpleSearch -> switch (componentFilterSimpleSearch) {
                case ComponentFiltersByBoolean(
                        String componentkey,
                        List<String> filters,
                        Multiplicity multiplicity
                ) -> switch (multiplicity) {
                    case ONE -> """
                            $.%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentkey),
                            filters.stream()
                                    .map(filter -> "@== \"%s\"".formatted(DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber(filter)))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                    case MANY -> """
                            $[*].%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentkey),
                            filters.stream()
                                    .map(filter -> "@== \"%s\"".formatted(DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber(filter)))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                };
                case ComponentFiltersByNumeric(
                        String componentKey,
                        List<String> filters,
                        Multiplicity multiplicity
                ) -> switch (multiplicity) {
                    case ONE -> """
                            $.%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(filter -> "@.double() == %s".formatted(DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber(filter)))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                    case MANY -> """
                            $[*].%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(filter -> "@.double() == %s".formatted(DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber(filter)))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                };
                case ComponentFiltersByReference(
                        String componentKey,
                        List<String> filters,
                        Multiplicity multiplicity
                ) -> switch (multiplicity) {
                    case ONE -> """
                            $.%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(DataRequestBuilder::buildReferencePredicate)
                                    .collect(Collectors.joining(DELIMITER_OR))

                    );
                    case MANY -> """
                            $[*].%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(DataRequestBuilder::buildReferencePredicate)
                                    .collect(Collectors.joining(DELIMITER_OR))

                    );
                };
                case ComponentFiltersForWordByPlainText(
                        String componentKey,
                        List<String> filters,
                        Multiplicity multiplicity
                ) -> switch (multiplicity) {
                    case ONE -> """
                            $.%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(DataRequestBuilder::buildEqualityPredicate)
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                    case MANY -> """
                            $[*].%1$s ?  (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(DataRequestBuilder::buildEqualityPredicate)
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                };
                case ComponentFiltersForWordByRegexp(
                        String componentKey,
                        List<String> filters,
                        Multiplicity multiplicity
                ) -> switch (multiplicity) {
                    case ONE -> """
                            $.%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(DataRequestBuilder::buildRegexpPredicate)
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                    case MANY -> """
                            $[*].%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(DataRequestBuilder::buildRegexpPredicate)
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                };
                case WithFormatForFilterDate withFormatForFilterDate -> switch (componentFilters.multiplicity()) {
                    case ONE -> """
                            $.%1$s ? (%3$s)
                            """.formatted(
                            sanitize(withFormatForFilterDate.componentKey()),
                            withFormatForFilterDate.format(),
                            withFormatForFilterDate.getIsoStrings().stream()
                                    .map(isoString -> "@ == \"date:%1$s:%2$s\"".formatted(
                                            isoString,
                                            withFormatForFilterDate.format()
                                    ))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                    case MANY -> """
                            $[*].%1$s ? (%3$s)
                            """.formatted(
                            sanitize(withFormatForFilterDate.componentKey()),
                            withFormatForFilterDate.format(),
                            withFormatForFilterDate.getIsoStrings().stream()
                                    .map(isoString -> "@ == \"date:%1$s:%2$s\"".formatted(
                                            isoString,
                                            withFormatForFilterDate.format()
                                    ))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                };
            };
        };
    }

    private String addArgumentAndReturnSubstitution(final Object value) {
        final int i = atomicInteger.incrementAndGet();
        final String paramName = String.format("arg%d", i);
        paramSource.addValue(paramName, value);
        return String.format(":%s", paramName);
    }

    private SqlRequest buildRequestDelete() {
        return switch (downloadDatasetQuery) {
            case final DownloadDatasetQueryNoFilter downloadDatasetQueryNoFilter ->
                    buildDeleteDatasetQuery(downloadDatasetQueryNoFilter, null);
            case final DownloadDatasetQueryAdvancedSearch downloadDatasetQueryAdvancedSearch -> buildDeleteDatasetQuery(
                    downloadDatasetQuery,
                    null
            );
            case DownloadDatasetQueryByNaturalKey downloadDatasetQueryByNaturalKey -> {
                paramSource.addValue("naturalKeys", downloadDatasetQueryByNaturalKey.naturalOrHierarchicalKey());
                final String filter = """
                           rs.naturalKey IN (:naturalKeys) or hierarchicalKey IN (:naturalKeys)
                        """;
                yield buildDeleteDatasetQuery(
                        downloadDatasetQueryByNaturalKey,
                        new SelectRequestWhereInSelect(() -> filter)
                );
            }
            case final DownloadDatasetQueryByRowId downloadDatasetQueryByRowId -> {
                paramSource.addValue("rowids", downloadDatasetQueryByRowId.rowIds().stream()
                        .map(DataRowIds::id).toList());
                final String filter = """
                           rs.id::uuid IN (:rowids)
                        """;
                yield buildDeleteDatasetQuery(
                        downloadDatasetQueryByRowId,
                        new SelectRequestWhereInSelect(() -> filter)
                );
            }
        };

    }

    private SqlRequest buildRequestSelect() {
        return getSqlRequest();
    }


    private SqlRequest getSqlRequest() {
        return switch (downloadDatasetQuery) {
            case final DownloadDatasetQueryNoFilter downloadDatasetQueryNoFilter ->
                    buildDownloadDatasetQuery(downloadDatasetQueryNoFilter, null);
            case final DownloadDatasetQueryAdvancedSearch downloadDatasetQueryAdvancedSearch ->
                    buildDownloadDatasetQuery(
                            downloadDatasetQuery,
                            new SelectRequestWhereInSelect(() ->
                                    buildDownloadDatasetFilterAdvancedSearch(downloadDatasetQueryAdvancedSearch)
                            )
                    );
            case final DownloadDatasetQueryByNaturalKey downloadDatasetQueryByNaturalKey -> {
                paramSource.addValue("naturalKeys", downloadDatasetQueryByNaturalKey.naturalOrHierarchicalKey().stream()
                        .map(Ltree::getSql)
                        .toList());
                final String filter = """
                            rs.naturalKey::text IN (:naturalKeys) or rs.hierarchicalKey::text IN (:naturalKeys)
                        """;
                yield buildDownloadDatasetQuery(
                        downloadDatasetQueryByNaturalKey,
                        new SelectRequestWhereInSelect(() -> filter)
                );
            }
            case final DownloadDatasetQueryByRowId downloadDatasetQueryByRowId -> {
                paramSource.addValue("rowids", downloadDatasetQueryByRowId.rowIds().stream()
                        .map(DataRowIds::id).toList());
                final String filter = """
                            (rs.id::uuid IN (:rowids))
                        """;
                yield buildDownloadDatasetQuery(
                        downloadDatasetQueryByRowId,
                        new SelectRequestWhereInSelect(() -> filter)
                );
            }
        };
    }

    private SqlRequest buildDeleteDatasetQuery(final DownloadDatasetQuery downloadDatasetQuery,
                                               final SelectRequestWhereInSelect filterInSelect
    ) {
        return new DeleteRequest(
                downloadDatasetQuery,
                paramSource,
                new DeleteRequest.DeleteRequestRequest(
                        downloadDatasetQuery.dataName(),
                        schema.getSqlIdentifier(),
                        filterInSelect
                )
        ).build();
    }

    private SqlRequest buildDownloadDatasetQuery(final DownloadDatasetQuery downloadDatasetQuery,
                                                 final SelectRequestWhereInSelect filterInSelect
    ) {
        List<BuildRemoveSqlSelectNotInValues> buildRemoveSqlSelectNotInValues = CollectionUtils.isEmpty(downloadDatasetQuery.componentSelects()) ?
                List.of() :

                downloadDatasetQuery.application().findData(downloadDatasetQuery.dataName())
                        .map(StandardDataDescription::componentDescriptions)
                        .map(map ->
                                map.entrySet().stream().
                                        filter(componentEntry ->
                                                componentEntry.getValue().isHiddenOrHasLangRestriction(downloadDatasetQuery.getLanguage()) ||
                                                        downloadDatasetQuery.componentSelects() == null ||
                                                        !downloadDatasetQuery.componentSelects().contains(componentEntry.getKey())
                                        )
                                        .toList())
                        .map(SelectedComponent::of)
                        .orElse(List.of());
        return new SelectRequest(
                downloadDatasetQuery,
                paramSource,
                new SelectRequest.SelectRequestRequest(
                        downloadDatasetQuery.patternDefinitionCount(),
                        downloadDatasetQuery.dataName(),
                        buildRemoveSqlSelectNotInValues,
                        schema.getSqlIdentifier(),
                        filterInSelect
                ),
                new SelectRequest.SelectRequestOrderBy(downloadDatasetQuery.componentOrderBy()),
                new SelectRequest.SelectRequestOffset(downloadDatasetQuery.outPut()),
                new SelectRequest.SelectRequestLimit(downloadDatasetQuery.outPut())
        ).build();
    }

    private String buildDownloadDatasetFilterAdvancedSearch(final DownloadDatasetQueryAdvancedSearch
                                                                    downloadDatasetQueryAdvancedSearch) {
        return downloadDatasetQueryAdvancedSearch.componentFilters().stream()
                .map(DataRequestBuilder::filter)
                .filter(f -> !Strings.isNullOrEmpty(f))
                .map("rs.refvalues @@ 'exists(%1$s)'"::formatted)
                .collect(Collectors.joining(" ) AND \n( ", "( ", " )\n"));
    }

}