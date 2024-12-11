package fr.inra.oresing.persistence.requestBuilder.data;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.*;
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
    private MapSqlParameterSource paramSource = new MapSqlParameterSource();

    public DataRequestBuilder(final DownloadDatasetQuery downloadDatasetQuery) {
        super();
        this.downloadDatasetQuery = downloadDatasetQuery;
        schema = SqlSchema.forApplication(downloadDatasetQuery.application());
    }

    static String sanitize(final String key) {
        return Optional.ofNullable(key)
                .map(s -> s.replaceAll("'", "''"))
                .orElse(null);
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
            case final ComponentFilterForInterval componentFilterForInterval -> {
                yield switch (componentFilterForInterval) {

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
                                                            "(@.double() >= %1$s && @.double() <= %2$s)".formatted(
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
                                                        "(@.double() >= %1$s && @.double() <= %2$s)".formatted(
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
            }
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
                                    .map(filter -> "@ == \"%1$s\"  || @ starts with \"%2$s\""
                                            .formatted(
                                                    filter,
                                                    filter + ".")
                                    )
                                    .collect(Collectors.joining(DELIMITER_OR))

                    );
                    case MANY -> """
                            $[*].%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(filter -> "@ == \"%1$s\"  || @ starts with \"%2$s\""
                                            .formatted(
                                                    filter,
                                                    filter + ".")
                                    )
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
                                    .map(filter -> "@ == \"%s\"".formatted(sanitize(filter)))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                    case MANY -> """
                            $[*].%1$s ?  (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(filter -> "@ == \"%s\"".formatted(sanitize(filter)))
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
                                    .map(filter -> "@ == \"%1$s\" || @ like_regex \"%1$s\" flag \"i\" ".formatted(sanitize(filter)))
                                    .collect(Collectors.joining(DELIMITER_OR))
                    );
                    case MANY -> """
                            $[*].%1$s ? (%2$s)
                            """.formatted(
                            sanitize(componentKey),
                            filters.stream()
                                    .map(filter -> "@ == \"%1$s\" OR @ like_regex \"%1$s\" flag i ".formatted(sanitize(filter)))
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
                           \srs.naturalKey::text IN (:naturalKeys) or rs.hierarchicalKey::text IN (:naturalKeys)
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
                           \s(rs.id::uuid IN (:rowids))
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

