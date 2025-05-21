package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.checker.*;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.util.Strings;

import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.stream.Collectors;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.*;

@Getter
@Setter
public class ComponentFilters {
    public String componentKey;
    public List<String> filters;
    public List<IntervalValues> intervalsValues;
    public Boolean isRegExp = false;

    public ComponentFilters() {
        super();
    }

    public ComponentFilters(final String componentKey, final List<String> filters, final List<IntervalValues> intervalsValues, final Boolean isRegExp) {
        super();
        this.componentKey = componentKey;
        this.filters = filters;
        this.intervalsValues = intervalsValues;
        this.isRegExp = isRegExp;
    }

    public static fr.inra.oresing.domain.data.read.query.ComponentFilters build(
            final ComponentFilters componentFilter,
            final StandardDataDescription dataDescription) throws BadDownloadDatasetQuery {
        if (componentFilter == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        } else if (Strings.isEmpty(componentFilter.componentKey)) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_COMPONENT);
        }
        final CheckerDescription formatForFieldType = Optional.ofNullable(dataDescription)
                .map(StandardDataDescription::componentDescriptions)
                .stream()
                .flatMap(map -> map.values().stream()) // Transforme le Stream<Map> en Stream des valeurs
                .filter(component -> Objects.equals(component.componentKey(), componentFilter.componentKey))
                .findFirst() // Trouvez le premier élément correspondant
                .map(ComponentDescription::checker) // Mappez chaque composant vers son CheckerDescription
                .orElse(CheckerDescription.NO_CHECKER); // Retourne NO_CHECKER si aucun élément n'est trouvé
        final Multiplicity multiplicity = formatForFieldType.multiplicity();
        if (CollectionUtils.isNotEmpty(componentFilter.intervalsValues) && componentFilter.intervalsValues.stream().allMatch(Objects::nonNull)) {
            return switch (formatForFieldType) {
                case DateChecker(
                        CheckerDescription.CheckerDescriptionType type, Multiplicity multiplicity1, boolean required,
                        String pattern, TemporalAccessor min, TemporalAccessor max, String duration
                ) -> switch (DatePattern.of(pattern).getFieldType()) {
                    case DATE -> new ComponentFiltersForIntervalByDate(
                            componentFilter.componentKey,
                            componentFilter.getIntervalsValues().stream()
                                    .map(intervalValues ->
                                            new IntervalValuesDate(
                                                    intervalValues.getFrom(),
                                                    intervalValues.getTo(),
                                                    pattern)
                                    )
                                    .collect(Collectors.toCollection(LinkedList::new)),
                            multiplicity
                    );
                    case TIME -> new ComponentFiltersForIntervalByTime(
                            componentFilter.componentKey,
                            componentFilter.getIntervalsValues().stream()
                                    .map(intervalValues ->
                                            new IntervalValuesTime(intervalValues.from,
                                                    intervalValues.to,
                                                    pattern)
                                    )
                                    .collect(Collectors.toCollection(LinkedList::new)),
                            multiplicity
                    );
                    case DATETIME -> new ComponentFiltersForIntervalByDateTime(
                            componentFilter.componentKey,
                            componentFilter.getIntervalsValues().stream()
                                    .map(intervalValues ->
                                            new IntervalValuesDateTime(
                                                    intervalValues.from,
                                                    intervalValues.to,
                                                    pattern)
                                    )
                                    .collect(Collectors.toCollection(LinkedList::new)),
                            multiplicity
                    );
                };
                case BooleanChecker ignored -> new ComponentFiltersByBoolean(
                        componentFilter.componentKey,
                        componentFilter.filters,
                        multiplicity);
                case FloatChecker ignored -> new ComponentFiltersForIntervalByNumeric(
                        componentFilter.componentKey,
                        componentFilter.getIntervalsValues().stream()
                                .map(intervalValues ->
                                        new IntervalValuesNumeric(
                                                intervalValues.from,
                                                intervalValues.to)
                                )
                                .collect(Collectors.toCollection(LinkedList::new)),
                        multiplicity
                );
                case IntegerChecker ignored -> new ComponentFiltersForIntervalByNumeric(
                        componentFilter.componentKey,
                        componentFilter.getIntervalsValues().stream()
                                .map(intervalValues ->
                                        new IntervalValuesNumeric(
                                                intervalValues.from,
                                                intervalValues.to)
                                )
                                .collect(Collectors.toCollection(LinkedList::new)),
                        multiplicity
                );
                case null, default -> throw new BadDownloadDatasetQuery(
                        NOT_INTERVAL_VALUE_TYPE_FOR_COMPONENT,
                        Map.of(
                                "component", componentFilter.componentKey
                        )
                );
            };
        }
        if (CollectionUtils.isNotEmpty(componentFilter.getFilters())) {
            return switch (formatForFieldType) {
                case DateChecker(
                        CheckerDescription.CheckerDescriptionType type, Multiplicity multiplicity1, boolean required,
                        String pattern, TemporalAccessor min, TemporalAccessor max, String duration
                ) -> switch (DatePattern.of(pattern).getFieldType()) {
                    case DATE -> new ComponentFiltersByDate(
                            componentFilter.componentKey,
                            pattern,
                            componentFilter.getFilters(),
                            multiplicity
                    );
                    case TIME -> new ComponentFiltersByTime(
                            componentFilter.componentKey,
                            pattern,
                            componentFilter.getFilters(),
                            multiplicity
                    );
                    case DATETIME -> new ComponentFiltersByDateTime(
                            componentFilter.componentKey,
                            pattern,
                            componentFilter.getFilters(),
                            multiplicity
                    );
                };
                case IntegerChecker ignored -> new ComponentFiltersByNumeric(
                        componentFilter.componentKey,
                        componentFilter.getFilters(),
                        multiplicity
                );
                case FloatChecker ignored -> new ComponentFiltersByNumeric(
                        componentFilter.componentKey,
                        componentFilter.getFilters(),
                        multiplicity
                );
                case ReferenceChecker ignored -> new ComponentFiltersByReference(
                        componentFilter.componentKey,
                        componentFilter.getFilters(),
                        multiplicity
                );
                default -> {
                    if (Optional.ofNullable(componentFilter.isRegExp).orElse(false)) {
                        yield new ComponentFiltersForWordByRegexp(
                                componentFilter.componentKey,
                                componentFilter.getFilters(),
                                multiplicity
                        );
                    } else {
                        yield new ComponentFiltersForWordByPlainText(
                                componentFilter.componentKey,
                                componentFilter.getFilters(),
                                multiplicity
                        );
                    }
                }
            };
        }
        throw new BadDownloadDatasetQuery(FILTER_MISSING_FILTER_OR_INTERVAL, Map.of(
                "component", componentFilter.getComponentKey()
        ));
    }

    public static Set<fr.inra.oresing.domain.data.read.query.ComponentFilters> build(
            final Set<ComponentFilters> componentFilters,
            final StandardDataDescription dataTypeDescription) {
        if (CollectionUtils.isNotEmpty(componentFilters)) {
            return componentFilters.stream()
                    .map(componentFilter -> build(
                            componentFilter,
                            dataTypeDescription))
                    .collect(Collectors.toSet());
        } else {
            return Set.of(new NoComponentFilters());
        }
    }

    public String getComponentKey() {
        return componentKey == null ? null : componentKey;
    }

    public List<String> getFilters() {
        return CollectionUtils.isNotEmpty(filters) ? filters : null;
    }
}