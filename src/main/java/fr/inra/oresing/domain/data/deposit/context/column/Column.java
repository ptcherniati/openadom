package fr.inra.oresing.domain.data.deposit.context.column;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.GroovyDataInjectionConfiguration;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.groovy.Expression;
import fr.inra.oresing.domain.groovy.GroovyContextHelper;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import fr.inra.oresing.domain.groovy.StringSetGroovyExpression;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("java:S3740")
public abstract class Column implements Comparable<Column> {
    public static final String COLUMN_IN_COLUMN_SEPARATOR = "::";
    public static final String COLUMN_IN_COLUMN_PATTERN = "%s::%s";
    @SuppressWarnings("java:S115")
    public static final String __VALUE__ = "__VALUE__";
    @SuppressWarnings("java:S115")
    public static final String __COLUMN_NAME__ = "__COLUMN_NAME__";
    @SuppressWarnings("java:S115")
    public static final String __ORIGINAL_COLUMN_NAME__ = "__ORIGINAL_COLUMN_NAME__";
    private static final String NO_DEFAULT_VALUE_MSG = "pas de valeur par défaut pour ";

    @Getter
    private final DataColumn referenceColumn;
    @Getter
    private final ComponentPresenceConstraint presenceConstraint;
    @Getter
    private final ComputedValueUsage computedValueUsage;
    @SuppressWarnings("java:S1068")
    private final TransformationConfiguration defaultValue;
    public static Column computedColumnDescriptionToColumn(final DataRepository referenceValueRepository,
                                                     final DataColumn referenceColumn,
                                                     final Multiplicity multiplicity,
                                                     final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription) {
        return switch (multiplicity) {
            case ONE -> newComputedColumn(referenceColumn, referenceStaticComputedColumnDescription, referenceValueRepository);
            case MANY -> newComputedManyColumn(referenceColumn, referenceStaticComputedColumnDescription, referenceValueRepository);
        };
    }


    public static ImmutableSet<Column> dynamicColumnDescriptionToColumns(final DataRepository referenceValueRepository, final DataColumn referenceColumn, final ReferenceDynamicColumnDescription referenceDynamicColumnDescription, TransformationConfiguration defaultValue) {
        final String reference = referenceDynamicColumnDescription.reference();
        final DataColumn referenceColumnToLookForHeader = new DataColumn(referenceDynamicColumnDescription.referenceColumnToLookForHeader());
        final List<DataValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(reference);
        return allByReferenceType.stream()
                .map(referenceValue -> {
                    final DataDatum referenceDatum = referenceValue.getRefValues();
                    final Ltree naturalKey = referenceValue.getNaturalKey();
                    final DataColumnSingleValue referenceColumnValue = (DataColumnSingleValue) referenceDatum.get(referenceColumnToLookForHeader);
                    final String header = referenceColumnValue.getValue().toString();
                    final String fullHeader = referenceDynamicColumnDescription.headerPrefix() + header;
                    final ComponentPresenceConstraint presenceConstraint = referenceDynamicColumnDescription.presenceConstraint();
                    return new DynamicColumn(
                            referenceColumn,
                            presenceConstraint,
                            naturalKey,
                            Map.entry(reference, new RefsLinkedToValue(
                                            Set.of(referenceValue.getId()),
                                            naturalKey
                                    )
                            ),
                            defaultValue == null ? ComputedValueUsage.NOT_COMPUTED : ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE,
                            defaultValue) {
                        @Override
                        public String getExpectedHeader() {
                            return fullHeader;
                        }

                        @Override
                        public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                            throw new UnsupportedOperationException("pas de valeur calculable pour " + referenceColumn);
                        }
                    };
                }).collect(ImmutableSet.toImmutableSet());
    }

    private static Column newComputedManyColumn(final DataColumn referenceColumn, final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, final DataRepository referenceValueRepository) {
        final TransformationConfiguration computation = referenceStaticComputedColumnDescription.computation();
        final Map<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, computation);
        final Expression<Set<String>> computationExpression = StringSetGroovyExpression.forExpression(computation.expression());
        return new ManyValuesStaticColumn(referenceColumn, referenceColumn.column(), ComponentPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE, null) {
            @Override
            public String getExpectedHeader() {
                throw new UnsupportedOperationException("la colonne " + referenceColumn + " est calculée, il n'y a pas d'entête spécifié car elle ne doit pas être dans le CSV");
            }

            @Override
            public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                final ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                        .putAll(contextForExpression)
                        .putAll(referenceDatum.getEvaluationContext())
                        .build();
                final Set<String> evaluate = computationExpression.evaluate(evaluationContext);
                return Optional.ofNullable(evaluate)
                        .map(l -> l.stream().map(StringType::getStringTypeFromStringValue)
                                .collect(Collectors.toCollection(LinkedList<FieldType<?>>::new)))
                        .map(DataColumnMultipleValue::new);
            }
        };
    }


    private static Column newComputedColumn(final DataColumn referenceColumn, final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, final DataRepository referenceValueRepository) {
        final TransformationConfiguration computation = referenceStaticComputedColumnDescription.computation();
        final Map<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, computation);
        final Expression<String> computationExpression = StringGroovyExpression.forExpression(computation.expression(), computation.exceptionMessages());
        return new OneValueStaticColumn(referenceColumn, referenceColumn.column(), ComponentPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE, null) {
            @Override
            public String getExpectedHeader() {
                throw new UnsupportedOperationException("la colonne " + referenceColumn + " est calculée, il n'y a pas d'entête spécifié");
            }

            @Override
            public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                final ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                        .putAll(contextForExpression)
                        .putAll(referenceDatum.getEvaluationContext())
                        .build();
                final String evaluate = computationExpression.evaluate(evaluationContext);
                return Optional.ofNullable(evaluate)
                        .map(s -> StringUtils.isEmpty(s) ? "" : s)
                        .map(StringType::getStringTypeFromStringValue)
                        .map(DataColumnSingleValue::new);
            }
        };
    }

    private static Map<String, Object> computeGroovyContext(final DataRepository referenceValueRepository, final GroovyDataInjectionConfiguration groovyDataInjectionConfiguration) {
        if (groovyDataInjectionConfiguration == null || groovyDataInjectionConfiguration.getReferences() == null) {
            return Map.of();
        }
        final Set<String> configurationReferences = groovyDataInjectionConfiguration.getReferences();
        return GroovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences, null);
    }

    protected Column(final DataColumn referenceColumn, final ComponentPresenceConstraint presenceConstraint, final ComputedValueUsage computedValueUsage, TransformationConfiguration defaultValue) {
        super();
        this.referenceColumn = referenceColumn;
        this.presenceConstraint = presenceConstraint;
        this.computedValueUsage = computedValueUsage;
        this.defaultValue = defaultValue;
    }

    public static Column staticColumnDescriptionToColumn(final DataColumn referenceColumn,
                                                         String headerForColumn,
                                                         final ComponentPresenceConstraint presenceConstraint,
                                                         final Multiplicity multiplicity,
                                                         final TransformationConfiguration defaultValue) {
        return switch (multiplicity) {
            case ONE -> new OneValueStaticColumn(
                    referenceColumn,
                    headerForColumn,
                    presenceConstraint,
                    defaultValue != null ? ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE : ComputedValueUsage.NOT_COMPUTED,
                    defaultValue
            ) {
                @Override
                public String getExpectedHeader() {
                    return Optional.ofNullable(headerForColumn)
                            .orElseGet(referenceColumn::column);
                }

                @Override
                public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                    if (defaultValue == null) {
                        throw new UnsupportedOperationException(NO_DEFAULT_VALUE_MSG + referenceColumn);
                    }
                    final Optional<DataColumnValue> dataColumnValue = Optional.ofNullable(referenceColumn).map(referenceDatum.values()::get);
                    if (dataColumnValue
                            .map(DataColumnValue::getValuesToCheck)
                            .map(FieldType::getValue)
                            .map(Object::toString)
                            .filter(Strings::isNullOrEmpty).isPresent()) {
                        return Optional.of(defaultValue)
                                .map(dv -> StringGroovyExpression.forExpression(dv.expression(), dv.exceptionMessages()))
                                .map(expression -> expression.evaluate(Map.of()))
                                .map(StringType::getStringTypeFromStringValue)
                                .map(DataColumnSingleValue::new);
                    }
                    return dataColumnValue;
                }
            };
            case MANY -> new ManyValuesStaticColumn(
                    referenceColumn,
                    headerForColumn,
                    presenceConstraint,
                    defaultValue != null ? ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE : ComputedValueUsage.NOT_COMPUTED,
                    defaultValue
            ) {
                @Override
                public String getExpectedHeader() {
                    return Optional.ofNullable(headerForColumn)
                            .orElseGet(referenceColumn::column);
                }

                @Override
                public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                    if (defaultValue == null) {
                        throw new UnsupportedOperationException(NO_DEFAULT_VALUE_MSG + referenceColumn);
                    }
                    final Optional<DataColumnValue> dataColumnValue = Optional.ofNullable(referenceColumn).map(referenceDatum.values()::get);
                    if (dataColumnValue
                            .map(DataColumnValue::getValuesToCheck)
                            .map(FieldType::getValue)
                            .map(Object::toString)
                            .filter(Strings::isNullOrEmpty).isPresent()) {
                        return Optional.of(defaultValue)
                                .map(dv -> StringGroovyExpression.forExpression(dv.expression(), dv.exceptionMessages()))
                                .map(expression -> expression.evaluate(Map.of()))
                                .map(","::split)
                                .map(array -> Arrays.stream(array)
                                        .map(StringType::getStringTypeFromStringValue)
                                        .toList()
                                )
                                .map(ListType::getListTypeFromListValue)
                                .map(DataColumnMultipleValue::new);
                    }
                    return dataColumnValue;
                }
            };
        };
    }

    public static Column staticPatternColumnDescriptionToColumn(final DataColumn referenceColumn,
                                                                String headerForColumn,
                                                                String headerInFile,
                                                                final ComponentPresenceConstraint presenceConstraint,
                                                                final Multiplicity multiplicity,
                                                                final List<Column> qualifierColumns,
                                                                final List<Column> adjacentColumns,
                                                                final TransformationConfiguration defaultValue) {
        Column column;
        column = new OneValueStaticPatternColumn(
                referenceColumn,
                headerForColumn,
                headerInFile,
                multiplicity,
                defaultValue,
                presenceConstraint,
                ComputedValueUsage.NOT_COMPUTED,
                qualifierColumns,
                adjacentColumns
        ) {
            @Override
            public String getExpectedHeader() {
                return Optional.ofNullable(headerForColumn)
                        .orElseGet(referenceColumn::column);
            }

            @Override
            public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                throw new UnsupportedOperationException(NO_DEFAULT_VALUE_MSG + referenceColumn);
            }
        };
        return column;
    }

    public Column as(String columnHeader) {
        return columnHeader.equals(getReferenceColumn().column()) ? this : null;
    }

    public boolean canHandle(final String header) {
        return isExpected() && getExpectedHeader().equals(header);
    }

    public abstract void pushValue(String cellContent, DataDatum referenceDatum, Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo);

    public abstract String getCsvCellContent(DataDatum referenceDatum);

    public abstract String getExpectedHeader();

    public boolean isMandatory() {
        return presenceConstraint.isMandatory();
    }

    public boolean isExpected() {
        return presenceConstraint.isExpected();
    }

    public abstract Optional<DataColumnValue> computeValue(DataDatum referenceDatum);

    @Override
    public int compareTo(final Column o) {
        return getReferenceColumn().column().compareTo(o.getReferenceColumn().column());
    }

    @Override
    public int hashCode() {
        return getReferenceColumn().column().hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final Column column) {
            return getReferenceColumn().column().equals(column.getReferenceColumn().column());
        }
        return false;
    }
}