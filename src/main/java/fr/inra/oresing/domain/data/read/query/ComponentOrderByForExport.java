package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public sealed interface ComponentOrderByForExport
        permits ComponentOrderBy, ComponentPatternOrderBy, ComponentPatternValueOrderBy, DynamicComponentOrderBy {
    Stream<String> toValue(String language, DataRepositoryWithBuffer dataRepository, Map<String, FieldType> dataRowValues, StandardDataDescription dataDescription);

    String componentKey();

    ComponentType sqlType();

    default String valueToString(
            String language,
            DataRepositoryWithBuffer dataRepository,
            StandardDataDescription dataDescription,
            FieldType fieldType) {
        if (fieldType instanceof MapType mapType) {
            return "pas trouvé";
        }
        return switch (sqlType()) {
            case null -> "";
            case ComponentDateType componentDateType -> {
                Matcher matcher = Pattern.compile(DateType.PATTERN_DATE_REGEXP_FIND_DATE).matcher(fieldType.getValue().toString());
                if (matcher.matches()) {
                    yield DateTimeFormatter.ofPattern(componentDateType.format()).format(LocalDateTime.parse(matcher.group(1)));
                }
                yield "";
            }
            case ComponentReferenceType componentReferenceType -> Optional.ofNullable(dataDescription)
                    .map(StandardDataDescription::componentDescriptions)
                    .map(components -> components.get(componentKey()))
                    .map(ComponentDescription::checker)
                    .filter(ReferenceChecker.class::isInstance)
                    .map(ReferenceChecker.class::cast)
                    .map(ReferenceChecker::refType)
                    .map(referencetype -> Optional.of(dataRepository)
                            .map(repository -> repository.findDisplayByReferenceType(referencetype))
                            .map(map -> map.get(fieldType.toString()))
                            .map(map -> map.get(language))
                            .orElse(null)
                    )
                    .orElse(fieldType == null ? "" : fieldType.toString());
            default -> fieldType == null ? "" : fieldType.toString();
        };
    }


    static Comparator<ComponentOrderByForExport> getComparator(StandardDataDescription dataDescription) {
        return (componentOrderBy1, componentOrderBy2) -> switch (componentOrderBy1) {
            case null -> 1;
            default -> switch (componentOrderBy2) {
                case null -> -1;
                default -> {
                    Integer component1Order = getComponentOrder(componentOrderBy1, dataDescription);
                    Integer component2Order = getComponentOrder(componentOrderBy2, dataDescription);
                    if (component1Order.equals(component2Order)) {
                        yield componentOrderBy1.componentKey().compareTo(componentOrderBy2.componentKey());
                    }
                    yield component1Order.compareTo(component2Order);
                }
            };
        };
    }

    private static int getComponentOrder(ComponentOrderByForExport componentOrderBy, StandardDataDescription dataDescription) {
        return Optional.of(componentOrderBy)
                .map(ComponentOrderByForExport::componentKey)
                .map(dataDescription.componentDescriptions()::get)
                .map(ComponentDescription::tags)
                .map(tags -> tags.stream()
                        .filter(Tag.OrderTag.class::isInstance)
                        .map(Tag.OrderTag.class::cast)
                        .map(Tag.OrderTag::tagOrder)
                        .findFirst()
                        .orElse(9999))
                .orElse(9999);
    }
}
