package fr.inra.oresing.domain.application.configuration;


import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationComponent;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record Configuration(Version version, Set<Tag> tags,
                            Internationalizations i18n,
                            ApplicationDescription applicationDescription,
                            Map<String, StandardDataDescription> dataDescription,
                            RightRequestDescription rightsRequest,
                            Map<String, AdditionalFileDescription> additionalFiles,
                            SortedSet<Node> hierarchicalNodes,
                            List<String> requiredAuthorizationsAttributes
) {
    public static final String OPEN_ADOM_VERSION_PATTERN = "2.0.1";
    public static final Version OPEN_ADOM_VERSION = new Version(OPEN_ADOM_VERSION_PATTERN);
    private static final String IDENTIFIER_PATTERN = "[a-z][a-z_0-9]{%d,%d}";
    private static final String IDENTIFIER_SECTION_PATTERN = "[a-z]\\w{1,49}";

    public static Predicate<String> getIsValidIdentifierPattern(int min, int max) {
        int min1 = min > 0 ? min : 1;
        int max1 = max < 64 ? max : 63;
        return Pattern.compile(String.format(IDENTIFIER_PATTERN, min1 - 1, max1 - 1)).asMatchPredicate();
    }

    public static Predicate<String> getIsValidSectionIdentifierPattern() {
        return Pattern.compile(IDENTIFIER_SECTION_PATTERN).asMatchPredicate();
    }

    public Configuration configurationAccordingToRights() {
        return new Configuration(
                this.version(),
                this.tags(),
                this.i18n(),
                this.applicationDescription(),
                componentDescriptionAccordingToRights(),
                this.rightsRequest(),
                this.additionalFiles(),
                hierarchicalNodes, this.requiredAuthorizationsAttributes()
        );
    }

    public Map<String, StandardDataDescription> componentDescriptionAccordingToRights() {
        return dataDescription().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Optional<HierarchicalNode> findCompositeReferencesUsing(final String refType) {
        return hierarchicalNodes().stream()
                .map(node -> node.findNode(refType))
                .filter(Objects::nonNull)
                .findFirst()
                .map(HierarchicalNode::new);
    }

    public Set<String> getHiddenComponentsForData(final String dataName) {
        return Optional.ofNullable(dataDescription())
                .map(dataConfiguration -> dataConfiguration.get(dataName))
                .map(StandardDataDescription::componentDescriptions)
                .map(Map::values)
                .map(values -> values.stream()
                        .filter(c -> c.tags() != null)
                        .filter(c -> c.tags().contains(Tag.HiddenTag.instance()))
                        .map(ComponentDescription::componentKey)
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of
                );
    }

    public Set<String> getHiddenData() {
        return dataDescription().entrySet().stream()
                .filter(entry -> entry.getValue().tags() != null)
                .filter(entry -> entry.getValue().tags().contains(Tag.HiddenTag.instance()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public Optional<StandardDataDescription> findData(String dataName) {
        Function<Map<String, StandardDataDescription>, StandardDataDescription> getDataDescription = data -> data.get(dataName);
        return Optional.of(dataDescription())
                .map(getDataDescription);
    }

    public SortedSet<Node> orderedNodes() {
        TreeSet<Node> nodes = new TreeSet<>();
        hierarchicalNodes().stream()
                .map(this::getNodesRecursivly)
                .forEach(nodes::addAll);
        return nodes;
    }

    private TreeSet<Node> getNodesRecursivly(Node node) {
        TreeSet<Node> nodes = new TreeSet<>(Collections.singleton(node));
        if (CollectionUtils.isEmpty(node.children())) {
            return nodes;
        }
        node.children().stream()
                .map(this::getNodesRecursivly)
                .forEach(nodes::addAll);
        return nodes;
    }

    public Map<String, InternationalizedSortedColumn> getInternationalizedSortedColumns(
            String dataname,
            String locale,
            List<String> elementsToBeSortedInFirst) {
        StandardDataDescription dataDescription = findData(dataname).orElseThrow(() -> new IllegalArgumentException("no dataDescription for %s".formatted(dataname)));
        Comparator<Map.Entry<String, InternationalizedSortedColumn>> comparator = (aEntry, bEntry) -> {
            InternationalizedSortedColumn a = aEntry.getValue();
            InternationalizedSortedColumn b = bEntry.getValue();
            if (a.equals(b)) {
                return 0;
            }
            if (elementsToBeSortedInFirst.contains(a.componentDescription.componentKey())) {
                if (elementsToBeSortedInFirst.contains(b.componentDescription.componentKey())) {
                    return Integer.compare(elementsToBeSortedInFirst.indexOf(a.componentDescription.componentKey()), elementsToBeSortedInFirst.indexOf(b.componentDescription.componentKey()));
                }
                return -1;
            }
            if (elementsToBeSortedInFirst.contains(b.componentDescription.componentKey())) {
                return 1;
            }
            return 1;
        };
        Predicate<ComponentDescription> isHidden = componentDescription -> componentDescription.isHiddenOrHasLangRestriction(locale);
        Set<ComponentDescription> componentDescriptions = dataDescription.componentDescriptions().values().stream()
                .filter(Predicate.not(isHidden))
                .filter(Predicate.not(PatternComponentAdjacents.class::isInstance))
                .filter(Predicate.not(PatternComponentQualifiers.class::isInstance))
                .collect(Collectors.toSet());
        boolean haveNoDefinedOrder = componentDescriptions.stream()
                .allMatch(Predicate.not(ComponentDescription::hasOrderTag));
        return haveNoDefinedOrder ?
                getSortedColumnsWithKeyThenAlphabeticOrder(dataname, locale, componentDescriptions, dataDescription.naturalKey()) :
                getSortedColumnsWithOrderThenAlphabeticOrder(dataname, locale, componentDescriptions)
                        .entrySet().stream()
                        .sorted(comparator)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, b) -> a,
                                LinkedHashMap::new));
    }

    private Map<String, InternationalizedSortedColumn> getSortedColumnsWithKeyThenAlphabeticOrder(String dataname, String locale, Collection<ComponentDescription> componentDescriptions, LinkedHashSet<String> naturalKeys) {
        ArrayList<String> naturalsKeys = new ArrayList<>(naturalKeys);
        Comparator<ComponentDescription> comparator = (a, b) -> {
            if (a.equals(b)) {
                return 0;
            } else if (naturalsKeys.contains(a.componentKey())) {
                if (naturalsKeys.contains(b.componentKey())) {
                    return Integer.compare(naturalsKeys.indexOf(a.componentKey()), naturalsKeys.indexOf(b.componentKey()));
                }
                return -1;
            } else if (naturalsKeys.contains(b.componentKey())) {
                return 1;
            }
            return a.componentKey().compareTo(b.componentKey());
        };
        return componentDescriptions.stream()
                .sorted(comparator)
                .collect(Collectors.toMap(
                        ComponentDescription::componentKey,
                        componentDescription -> new InternationalizedSortedColumn(
                                componentDescription,
                                getInternationalizedHeader(dataname, componentDescription.componentKey(), locale)),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public String getInternationalizedHeader(String dataName,
                                             String componentName,
                                             String locale) {
        Optional<InternationalizationTitle> localizedExportHeaders = Optional.ofNullable(i18n())
                .map(Internationalizations::getData)
                .map(stringInternationalizationDataMap -> stringInternationalizationDataMap.get(dataName))
                .map(InternationalizationData::getComponents)
                .map(stringInternationalizationComponentMap -> stringInternationalizationComponentMap.get(componentName))
                .map(InternationalizationComponent::getExportHeader);
        return localizedExportHeaders
                .map(InternationalizationTitle::getTitle)
                .map(localizationMap -> localizationMap.get(Locale.of(locale)))
                .orElse(localizedExportHeaders.map(localizationMap -> Objects.requireNonNull(localizationMap.getTitle()).get(applicationDescription().defaultLanguage())).orElse(componentName));

    }

    private Map<String, InternationalizedSortedColumn> getSortedColumnsWithOrderThenAlphabeticOrder(String dataname, String locale, Collection<ComponentDescription> componentDescriptions) {
        Comparator<ComponentDescription> comparator = (a, b) -> {
            if (a.equals(b)) {
                return 0;
            }
            if (a.hasOrderTag() || b.hasOrderTag()) {
                int compareOrder = a.componentOrder().compareTo(b.componentOrder());
                if (compareOrder != 0) {
                    return compareOrder;
                }
            }
            return a.componentKey().compareTo(b.componentKey());
        };
        return componentDescriptions.stream()
                .sorted(comparator)
                .collect(Collectors.toMap(
                        ComponentDescription::componentKey,
                        componentDescription -> new InternationalizedSortedColumn(
                                componentDescription,
                                getInternationalizedHeader(dataname, componentDescription.componentKey(), locale)),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public record InternationalizedSortedColumn(ComponentDescription componentDescription, String header) {

    }
}