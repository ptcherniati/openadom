package fr.inra.oresing.domain.application.configuration;

import com.google.common.collect.Maps;
import fr.inra.oresing.domain.application.configuration.checker.*;
import fr.inra.oresing.domain.data.read.query.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record StandardDataDescription(
        char separator,
        Integer headerLine,
        Integer firstRowLine,
        Boolean allowUnexpectedColumns,
        Set<Tag> tags,
        LinkedHashSet<String> naturalKey,
        Map<String, ComponentDescription> componentDescriptions,
        Submission submission,
        Authorization authorization,
        Map<String, ValidationDescription> validations,
        List<Depends> depends,
        TreeMap<Integer, List<MigrationDescription>> migrations
) {
    private static final Logger log = LoggerFactory.getLogger(StandardDataDescription.class);

    public Optional<ComponentDescription> findParentDescription(String dataName) {
        Predicate<ComponentDescription> isParentComponentOfDataName = componentDescription -> componentDescription.isParent(dataName);
        return componentDescriptions().values().stream()
                .filter(isParentComponentOfDataName)
                .findFirst();
    }

    public StandardDataDescription(final char separator,
                                   final Integer dataHeaderLine,
                                   final Integer dataFirstLine,
                                   final Boolean allowUnexpectedColumns,
                                   final Set<Tag> tags,
                                   final LinkedHashSet<String> naturalKey,
                                   final Map<String, ComponentDescription> componentDescriptions,
                                   final Submission submission,
                                   Authorization authorization, final
                                   Map<String, ValidationDescription> validationMap,
                                   final TreeMap<Integer, List<MigrationDescription>> migrations) {
        this(
                separator,
                dataHeaderLine,
                dataFirstLine,
                allowUnexpectedColumns,
                tags,
                naturalKey,
                componentDescriptions,
                submission,
                authorization,
                validationMap,
                Optional.ofNullable(componentDescriptions).orElseGet(HashMap::new)
                        .entrySet().stream()
                        .map(componentEntry -> Optional.ofNullable(componentEntry.getValue())
                                .map(ComponentDescription::checker)
                                .map(checker -> switch (checker) {
                                    case final ReferenceChecker referenceChecker: {
                                        final String refType = referenceChecker.refType();
                                        if (referenceChecker.isParent()) {
                                            yield new DependsParent(Depends.DependsType.DependsParent, refType, componentEntry.getKey());
                                        } else if (referenceChecker.isRecursive()) {
                                            yield new DependsRecursive(Depends.DependsType.DependsRecursive, refType, componentEntry.getKey());
                                        } else {
                                            yield new DependsReferences(Depends.DependsType.DependsReferences, refType, componentEntry.getKey());
                                        }
                                    }
                                    default:
                                        yield null;
                                }).orElse(null)).filter(Objects::nonNull).collect(Collectors.toList()),
                null
        );
    }

    public <T extends ComponentDescription> Map<String, T> getComponentByType(final Class<T> clazz) {
        return Maps.transformValues(
                Maps.filterValues(componentDescriptions(), clazz::isInstance),
                clazz::cast);
    }

    public Integer getOrder() {
        return tags().stream()
                .filter(tag -> Tag.TagDefinitions.ORDER_TAG == tag.tagDefinition())
                .map(Tag.OrderTag.class::cast)
                .map(Tag.OrderTag::tagOrder)
                .findFirst()
                .orElse(9999);
    }

    public CheckerDescription.CheckerDescriptionType getTypeForComponent(final String componentName) {
        if (componentName == null) {
            return CheckerDescription.CheckerDescriptionType.StringChecker;
        }
        return findComponent(componentName)
                .map(ComponentDescription::checker)
                .map(CheckerDescription::type)
                .orElse(CheckerDescription.CheckerDescriptionType.StringChecker);
    }

    Optional<ComponentDescription> findComponent(String componentName) {
        Function<Map<String, ComponentDescription>, ComponentDescription> getComponent = componentDescriptions -> componentDescriptions.get(componentName);
        return Optional.of(componentDescriptions())
                .map(getComponent);
    }

    public Boolean isHidden() {
        return Optional.ofNullable(tags())
                .map(tags -> tags.stream().anyMatch(tag -> Tag.HiddenTag.INSTANCE() == tag))
                .orElse(false);
    }

    public Optional<Submission.SubmissionScope> findSubmissionScope() {
        return Optional.ofNullable(submission())
                .map(Submission::submissionScope);
    }

    public Map<String, CheckerDescription> findValidationCheckers() {
        return validations().values().stream()
                .map(ValidationDescription::checkers)
                .flatMap(map -> map.entrySet().stream())
                .reduce(
                        new HashMap<>(),
                        (map, element) -> {
                            map.put(element.getKey(), element.getValue());
                            return map;
                        },
                        (map1, map2) -> {
                            map1.putAll(map2);
                            return map1;
                        }
                );
    }

    public ComponentType getTypeForComponentKey(String componentName) {
        return Optional.ofNullable(componentDescriptions().get(componentName))
                .map(ComponentDescription::checker)
                .map(this::toSqlType)
                .orElseGet(ComponentTextType::new);
    }

    public ComponentType getTypeForPatternComponentKeyAndComponentKey(String patternComponentKey, String componentName) {
        return Optional.ofNullable(componentDescriptions().get(patternComponentKey))
                .map(PatternComponent.class::cast)
                .map(patternComponent -> patternComponent.patternComponentAdjacents().get(componentName))
                .map(ComponentDescription::checker)
                .map(this::toSqlType)
                .orElseGet(ComponentTextType::new);
    }

    private ComponentType toSqlType(CheckerDescription checkerDescription) {
        return switch (checkerDescription) {
            case BooleanChecker ignored -> new ComponentBooleanType();
            case ComputationChecker ignored -> new ComponentTextType();
            case DateChecker dateChecker ->
                    new ComponentDateType(dateChecker.pattern(), DownloadDatasetQueryAdvancedSearch.FieldType.date);
            case FloatChecker ignored -> new ComponentNumericType();
            case GroovyExpressionChecker ignored -> new ComponentTextType();
            case IntegerChecker ignored -> new ComponentNumericType();
            case ReferenceChecker ignored -> new ComponentReferenceType();
            case StringChecker ignored -> new ComponentTextType();
        };
    }

    public void buildEmptyFile(OutputStream output) throws IOException {
        CSVFormat customFormat = CSVFormat.Builder.create()
                .setDelimiter(Optional.ofNullable(separator()).orElse(';'))
                .build();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(output);
        CSVPrinter csvPrinter = new CSVPrinter(outputStreamWriter, customFormat);
        buildPreOrPostHeader(csvPrinter, true);
        buildHeaderLine(csvPrinter); // TODO
        buildPreOrPostHeader(csvPrinter, false);
        buildFirstLineOfData(csvPrinter);
        csvPrinter.flush();
    }

    private void buildFirstLineOfData(CSVPrinter csvPrinter) throws IOException {
        List<String> expectedFirstLineData = componentDescriptions().values()
                .stream()
                .map(ComponentDescription::buildImportDataExempleForComponent)
                .filter(Predicate.not(Objects::isNull))
                .toList();
        csvPrinter.printRecord(expectedFirstLineData);
    }

    private void buildPreOrPostHeader(CSVPrinter csvPrinter, boolean isPre) {
        TreeMap<Integer, TreeMap<Integer, String>> headersByLineAndColumn = getHeadersByLineAndColumn();
        int start = isPre ? 1 : headerLine + 1;
        int end = isPre ? headerLine : firstRowLine;
        for (int line = start; line < end; line++) {
            TreeMap<Integer, String> columns = Optional.ofNullable(headersByLineAndColumn.get(line)).orElseGet(TreeMap::new);

            for (int column = 1; column <= columns.keySet().stream().max(Integer::compareTo).orElse(-1); column++) {
                try {
                    csvPrinter.print(columns.getOrDefault(column, null)); // Use print()
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }

            try {
                csvPrinter.println(); // New line a
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private void buildHeaderLine(CSVPrinter csvPrinter) throws IOException {
        List<String> expectedImportHeader = componentDescriptions().values()
                .stream()
                .map(ComponentDescription::buildImportHeaderForComponent)
                .filter(Predicate.not(Objects::isNull))
                .toList();
        csvPrinter.printRecord(expectedImportHeader);
    }

    private TreeMap<Integer, TreeMap<Integer, String>> getHeadersByLineAndColumn() {
        // Use the component() method here
        // if there is a clash in rowNumber choose either value
        return Optional.of(this.componentDescriptions())
                .map(components -> components.values().stream()
                        .filter(ConstantComponent.class::isInstance)
                        .map(ConstantComponent.class::cast)
                        .map(ConstantValue::of)
                        .filter(Objects::nonNull)
                        .collect(
                                Collectors.groupingBy(
                                        ConstantValue::lineNumber,
                                        TreeMap::new,
                                        Collectors.toMap(
                                                ConstantValue::rowNumber,
                                                this::exampleForConstantValue,  // Use the component() method here
                                                (oldValue, newValue) -> newValue, // if there is a clash in rowNumber choose either value
                                                TreeMap::new
                                        )
                                )
                        )
                )
                .stream().findFirst()
                .orElseGet(TreeMap::new);
    }

    private String exampleForConstantValue(ConstantValue constantValue) {
        Function<CheckerDescription, String> buildConstantExampleValueForComponent = checkerDescription ->
                "%1$s as %2$s".formatted(constantValue.component(), checkerDescription.buildImportDataExempleForheader());
        return findComponent(constantValue.component())
                .map(ComponentDescription::checker)
                .map(buildConstantExampleValueForComponent)
                .orElseGet(constantValue::component);
    }

    public long patternDefinitionCount() {
        return componentDescriptions()
                .values().stream()
                .filter(PatternComponent.class::isInstance)
                .count();
    }

    record ConstantValue(String component, int lineNumber, int rowNumber) {
        public static ConstantValue of(final ConstantComponent component) {
            String componentName = component.componentKey();
            int rowNumber = component.rowNumber();
            int columnNumber = -1;
            return switch (component.constantImportHeader()) {
                case FileColumnConstantHeader fileColumnConstantHeader ->
                        new ConstantValue(componentName, component.rowNumber(), fileColumnConstantHeader.columnNumber());
                default -> null;
            };
        }
    }
}
