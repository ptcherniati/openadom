package fr.inra.oresing.domain.data.deposit.context.column;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.PatternComponentAdjacents;
import fr.inra.oresing.domain.application.configuration.PatternComponentQualifiers;
import fr.inra.oresing.domain.application.configuration.PatternComponent;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.csvreader.PatternValueForHeader;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PatternColumnFactory {

    private final List<PatternDescription> patternComponentDescriptions;
    private final DataRepository dataRepository;

    public OneValueStaticPatternColumn getExpectedPatternColumn(String headerInfile) {
        return expectedPatternColumns.stream()
                .filter(OneValueStaticPatternColumn.class::isInstance)
                .map(OneValueStaticPatternColumn.class::cast)
                .filter(column -> column.getHeaderInFile().equals(headerInfile))
                .findFirst()
                .orElse(null);
    }

    @Getter
    private List<Column> expectedPatternColumns = ImmutableList.of();
    @Getter
    private final Map<String, PatternColumn> patternColumns = new HashMap<>();

    public PatternColumnFactory(final DataRepository dataRepository, final List<PatternDescription> patternComponentDescriptions) {
        super();
        this.patternComponentDescriptions = patternComponentDescriptions;
        this.dataRepository = dataRepository;
    }

    public static PatternColumnFactory of(
            final DataRepository dataRepository,
            final Map<String, PatternComponent> patternComponentDescriptions) {
        return new PatternColumnFactory(
                dataRepository,
                PatternDescription.of(patternComponentDescriptions));
    }

    public DataDatum toQualifierDatum(final String patternComponentName, final PatternValueForHeader patternValueForHeader) {
        PatternColumn patternColumn = patternColumns.get(patternComponentName).copy();
        DataDatum adjacentComponents = this.getExpectedPatternColumn(patternComponentName)
                .buildAdjacentComponents(patternValueForHeader.adjacentCellContent());
        final DataDatum qualifierComponents = patternColumn.qualifierComponents();
        return ((OneValueStaticPatternColumn) patternColumn.column())
                .buildValue(
                        patternColumn.column.getExpectedHeader(),
                        patternValueForHeader.cellContent(),
                        qualifierComponents,
                        adjacentComponents,
                        patternValueForHeader.refsLinkedTo());
    }

    public DataDatum toAdjacentDatum(final PatternValueForHeader patternValueForHeader) {
        final PatternColumn patternColumn = patternColumns.get(patternValueForHeader.header());
        final DataDatum adjacentComponents = patternColumn.adjacentComponents();
        patternColumn.column().pushValue(patternValueForHeader.cellContent(), adjacentComponents, patternValueForHeader.refsLinkedTo());
        return adjacentComponents;
    }

    private PatternDescription.RapportForPatterns test(final ContextHeader potentialPatternColumn, final Map<String, PatternColumn> patternColumnData) {
        final Predicate<PatternDescription> matches = p -> p.matches.test(potentialPatternColumn.columnHeader());
        final Function<PatternDescription, PatternDescription.RapportForPatterns> toRapport = p -> p.toRapport(dataRepository, potentialPatternColumn, patternColumnData);
        return patternComponentDescriptions.stream()
                .filter(matches)
                .map(toRapport)
                .filter(PatternDescription.MatchingPattern.class::isInstance)
                .findFirst()
                .orElse(new PatternDescription.ExceptionPattern(potentialPatternColumn.columnHeader()));
    }

    public boolean test(final List<ContextHeader> potentialPatternColumns) {
        final Function<ContextHeader, PatternDescription.RapportForPatterns> test = (s) -> test(s, patternColumns);
        AtomicInteger atomicLong = new AtomicInteger(0);
        final List<PatternDescription.RapportForPatterns> rapports = potentialPatternColumns.stream()
                .map(test)
                .filter(rapport -> switch (rapport) {
                    case PatternDescription.MatchingPattern matchingPattern -> {
                        atomicLong.set(((OneValueStaticPatternColumn) matchingPattern.column()).getAdjacentColumnsSize());
                        yield true;
                    }
                    case PatternDescription.ExceptionPattern exceptionPattern -> atomicLong.decrementAndGet() < 0;
                })
                .toList();
        final boolean rapportWithNoErrors = rapports.stream()
                .collect(Collectors.partitioningBy(PatternDescription.MatchingPattern.class::isInstance))
                .get(false)
                .isEmpty();
        if (rapportWithNoErrors) {

            expectedPatternColumns = rapports.stream()
                    .collect(Collectors.partitioningBy(PatternDescription.MatchingPattern.class::isInstance))
                    .get(true).stream()
                    .map(PatternDescription.MatchingPattern.class::cast)
                    .map(PatternDescription.MatchingPattern::column)
                    .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
        }

        return rapportWithNoErrors;

    }

    record PatternColumn(Column column, String headerName, DataDatum qualifierComponents,
                         DataDatum adjacentComponents) {
        public PatternColumn copy() {
            return new PatternColumn(
                    column(),
                    headerName(),
                    new DataDatum(qualifierComponents().values()),
                    new DataDatum(adjacentComponents().values())
            );
        }
    }

    record PatternDescription(
            String key,
            PatternComponent patternComponentDescriptions,
            Pattern pattern,
            Predicate<String> matches
    ) {
        public static List<PatternDescription> of(final Map<String, PatternComponent> patternComponentDescriptions) {
            return patternComponentDescriptions.entrySet().stream()
                    .map(entry -> of(entry.getKey(), entry.getValue()))
                    .toList();
        }

        private static PatternDescription of(final String key, final PatternComponent patternComponentDescription) {
            final Pattern pattern = Pattern.compile(patternComponentDescription.patternForComponents());
            return new PatternDescription(
                    key,
                    patternComponentDescription,
                    pattern,
                    pattern.asMatchPredicate()
            );
        }

        public RapportForPatterns toRapport(final DataRepository dataRepository,
                                            final ContextHeader potentialPatternColumn,
                                            final Map<String, PatternColumn> patternColumnData) {
            final Matcher matcher = pattern().matcher(potentialPatternColumn.columnHeader());
            Preconditions.checkArgument(matcher.matches(), "verified before");
            if (matches().test(potentialPatternColumn.columnHeader())) {
                final String componentKey = patternComponentDescriptions().componentKey();
                final Multiplicity multiplicity = Optional.of(patternComponentDescriptions())
                        .map(PatternComponent::checker)
                        .map(CheckerDescription::multiplicity)
                        .orElse(Multiplicity.ONE);

                final String headerForReferenceColumn = Optional.of(patternComponentDescriptions())
                        .map(ComponentDescription::importHeader)
                        .orElse(componentKey);
                final ComponentPresenceConstraint mandatory = Optional.of(patternComponentDescriptions())
                        .map(ComponentDescription::mandatory)
                        .orElse(ComponentPresenceConstraint.MANDATORY);
                final TransformationConfiguration defaultValue = patternComponentDescriptions().defaultValue();
                final DataDatum qualifierComponents = new DataDatum();
                final DataDatum adjacentComponents = new DataDatum();
                final List<Column> qualifierColumns = new LinkedList<>();
                final List<Column> adjacentColumns = new LinkedList<>();
                patternComponentDescriptions()
                        .patternComponentQualifiers().forEach((key1, patternColumnComponent) -> {
                            final String componentComponentKey = patternColumnComponent.componentKey();
                            final int patternNumber = patternColumnComponent.patternNumber();
                            final Multiplicity multiplicityForComponentComponent = Optional.of(patternColumnComponent)
                                    .map(PatternComponentQualifiers::checker)
                                    .map(CheckerDescription::multiplicity)
                                    .orElse(Multiplicity.ONE);
                            final ComponentPresenceConstraint mandatoryForComponentComponent = Optional.of(patternColumnComponent)
                                    .map(ComponentDescription::mandatory)
                                    .orElse(ComponentPresenceConstraint.MANDATORY);
                            final String constantValue = Optional.ofNullable(matcher.group(patternNumber))
                                    .filter(match ->
                                            !Strings.isNullOrEmpty(match) ||
                                                    patternColumnComponent.defaultValue() == null ||
                                                    patternColumnComponent.defaultValue().expression() == null)
                                    .orElse(
                                            Optional.of(patternColumnComponent)
                                                    .map(PatternComponentQualifiers::defaultValue)
                                                    .map(ComputationChecker::expression)
                                                    .filter(Predicate.not(Strings::isNullOrEmpty))
                                                    .map(expression -> StringGroovyExpression.forExpression(
                                                                            expression, Set.of()
                                                                    )
                                                                    .evaluate(Map.of())
                                                    )
                                                    .orElse("")
                                    );


                            final DataColumn dataColumn = new DataColumn(componentComponentKey);
                            Column patternQualifierColumn = Column.staticColumnDescriptionToColumn(
                                    new DataColumn(componentComponentKey),
                                    componentComponentKey,
                                    mandatoryForComponentComponent,
                                    multiplicityForComponentComponent,
                                    dataRepository,
                                    defaultValue
                            );
                            qualifierColumns.add(patternQualifierColumn);
                            String s = Optional.of(constantValue)
                                    .filter(column -> patternQualifierColumn.getComputedValueUsage() != ComputedValueUsage.NOT_COMPUTED)
                                    .orElse(null);

                            switch (multiplicityForComponentComponent) {
                                case Multiplicity.MANY -> {
                                    final List<StringType> valuesToList = Arrays.stream(constantValue.split(","))
                                            .map(StringType::getStringTypeFromStringValue)
                                            .toList();
                                    final DataColumnValue dataColumnValue = new DataColumnMultipleValue(valuesToList);
                                    qualifierComponents
                                            .put(dataColumn, dataColumnValue);

                                }
                                default -> {
                                    final DataColumnValue<FieldType, FieldType> dataColumnValue = new DataColumnSingleValue(StringType.getStringTypeFromStringValue(constantValue));
                                    qualifierComponents
                                            .put(dataColumn, dataColumnValue);
                                }
                            }

                        });
                List<AdjacentDescription> adjacentColumnNames = patternComponentDescriptions()
                        .patternComponentAdjacents().values()
                        .stream().map(patternColumnComponent -> {
                            final String componentComponentKey = patternColumnComponent.componentKey();
                            final String patternNumber = patternColumnComponent.importHeaderPattern();
                            final Multiplicity multiplicityForComponentComponent = Optional.of(patternColumnComponent)
                                    .map(PatternComponentAdjacents::checker)
                                    .map(CheckerDescription::multiplicity)
                                    .orElse(Multiplicity.ONE);
                            final ComponentPresenceConstraint mandatoryForComponentComponent = Optional.of(patternColumnComponent)
                                    .map(ComponentDescription::mandatory)
                                    .orElse(ComponentPresenceConstraint.MANDATORY);
                            return new AdjacentDescription(
                                    componentComponentKey,
                                    matcher.replaceAll(patternNumber.replaceAll("\\{(\\$[0-9])\\}", "$1")),
                                    mandatoryForComponentComponent,
                                    multiplicityForComponentComponent
                            );
                        })
                        .toList();
                for (int i = potentialPatternColumn.columnIndex() + 1; i < potentialPatternColumn.headersForRow().size(); i++) {
                    String nextColumnName = potentialPatternColumn.headersForRow().get(i);
                    if (adjacentColumnNames.stream().anyMatch(adjacentDescription -> adjacentDescription.adjacentColumnName().equals(nextColumnName))) {
                        AdjacentDescription resolvedAdjacentDescription = adjacentColumnNames.stream()
                                .filter(adjacentDescription -> adjacentDescription.adjacentColumnName().equals(nextColumnName))
                                .findFirst()
                                .orElseThrow(IllegalStateException::new);
                        adjacentColumns.add(Column.staticColumnDescriptionToColumn(
                                new DataColumn(resolvedAdjacentDescription.componentKey()),
                                nextColumnName,
                                resolvedAdjacentDescription.mandatoryForComponentComponent(),
                                resolvedAdjacentDescription.multiplicityForComponentComponent(),
                                dataRepository,
                                defaultValue
                        ));
                    } else {
                        break;
                    }
                }
                final Column column = Column.staticPatternColumnDescriptionToColumn(
                        new DataColumn(componentKey),
                        headerForReferenceColumn,
                        potentialPatternColumn.columnHeader(),
                        mandatory,
                        multiplicity,
                        dataRepository,
                        qualifierColumns,
                        adjacentColumns,
                        defaultValue
                );
                patternColumnData.put(potentialPatternColumn.columnHeader(), new PatternColumn(column, potentialPatternColumn.columnHeader(), qualifierComponents, adjacentComponents));
                return new MatchingPattern(potentialPatternColumn.columnHeader(), column);
            } else {
                return new ExceptionPattern(potentialPatternColumn.columnHeader());
            }
        }

        public sealed interface RapportForPatterns permits MatchingPattern, ExceptionPattern {

            String columnName();
        }

        public record MatchingPattern(
                String columnName,
                Column column
        ) implements RapportForPatterns {

            public Set<Column> toMapColumn() {
                ImmutableMap.Builder<String, Column> columnBuilder = ImmutableMap.builder();
                columnBuilder.put("__VALUE__", column());
                return Set.of();

            }

            public Set<Column> allColumns() {
                ImmutableSet.Builder<Column> columns = ImmutableSet.builder();
                columns.add(column());
                return columns.build();
            }
        }

        public record ExceptionPattern(String columnName) implements RapportForPatterns {

        }
    }
}