package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.persistence.RefsLinked;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public sealed interface ComponentOrderByForExport
        permits ComponentOrderBy, ComponentPatternOrderBy, ComponentPatternValueOrderBy, DynamicComponentOrderBy {
    static BiFunction<RefsLinked, String, String> toLocale = (refLinked, locale) -> {
        String localized = null;
        if("fr".equals(locale)){
            localized = refLinked.__display_fr();
        }else  if("en".equals(locale)){
            localized = refLinked.__display_en();
        }
        return localized==null? refLinked.__display_default():localized;
    };
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

    Stream<String> toValue(List<RefsLinked> refsLinkeds, String language, Map<String, FieldType<?>> dataRowValues, StandardDataDescription dataDescription);

    String componentKey();

    ComponentType sqlType();

    default String valueToString(
            List<RefsLinked> refsLinkeds,
            String language,
            StandardDataDescription dataDescription,
            FieldType<?> fieldType) {
        if (fieldType instanceof MapType _) {
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
                    .flatMap(referencetype -> refsLinkeds
                            .stream()
                            .filter(refsLinked -> refsLinked.referenceType().equals(referencetype))
                            .filter(refsLinked -> refsLinked.naturalKey().getSql().equals(fieldType.toString()))
                            .map(refsLinked ->toLocale.apply(refsLinked, language))
                            .findFirst())
                    .orElse(fieldType == null ? "" : fieldType.toString());
            default -> fieldType == null ? "" : fieldType.toString();
        };
    }
}