package fr.inra.oresing.domain.data.deposit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.context.column.PatternColumnFactory;
import fr.inra.oresing.domain.data.deposit.context.column.ReferenceDynamicColumnDescription;
import fr.inra.oresing.domain.data.deposit.context.column.ReferenceStaticComputedColumnDescription;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public record BuildColumns(PatternColumnFactory patternColumnFactory, ImmutableSet<Column> columns) {


    public static BuildColumns buildColumns(Map<Class<? extends ComponentDescription>, List<Map.Entry<String, ComponentDescription>>> componentDescriptionEntryByComputedType, DataRepository referenceValueRepository) {
        final ImmutableSet<Column> staticColumns = componentDescriptionEntryByComputedType
                .getOrDefault(
                        BasicComponent.class,
                        new LinkedList<>()
                ).stream()
                .map(entry -> {
                    final ComponentDescription basicComponent = entry.getValue();
                    final TransformationConfiguration defaultValue = basicComponent.defaultValue();
                    final DataColumn referenceColumn = new DataColumn(entry.getKey());
                    final String headerForReferenceColumn = Optional.of(basicComponent)
                            .map(ComponentDescription::importHeader)
                            .orElse(entry.getKey());
                    final ComponentPresenceConstraint mandatory = Optional.of(basicComponent)
                            .map(ComponentDescription::mandatory)
                            .orElse(ComponentPresenceConstraint.MANDATORY);
                    final Set<? extends Tag> tags = Optional.of(basicComponent)
                            .map(ComponentDescription::tags)
                            .orElse(Set.of(Tag.NoTag.instance()));
                    final CheckerDescription checker = Optional.of(basicComponent)
                            .map(ComponentDescription::checker)
                            .orElse(null);
                    final Multiplicity multiplicity = Optional.ofNullable(basicComponent.checker()).map(CheckerDescription::multiplicity).orElse(Multiplicity.ONE);
                    return Optional.ofNullable(defaultValue)
                            .map(defaultValueConfiguration -> Column.staticColumnDescriptionToColumn(
                                    referenceColumn,
                                    headerForReferenceColumn,
                                    mandatory,
                                    multiplicity,
                                    defaultValueConfiguration))
                            .orElseGet(() -> Column.staticColumnDescriptionToColumn(
                                    referenceColumn,
                                    headerForReferenceColumn,
                                    mandatory,
                                    multiplicity,
                                    defaultValue));
                }).collect(ImmutableSet.toImmutableSet());

        final ImmutableSet<Column> computedColumns = componentDescriptionEntryByComputedType
                .getOrDefault(
                        ComputedComponent.class,
                        new LinkedList<>()
                ).stream()
                .map(entry -> {
                    final DataColumn referenceColumn = new DataColumn(entry.getKey());
                    final ComputedComponent computedComponent = (ComputedComponent) entry.getValue();
                    final Multiplicity multiplicity = Optional.ofNullable(computedComponent)
                            .map(ComputedComponent::checker)
                            .map(CheckerDescription::multiplicity)
                            .orElse(Multiplicity.ONE);

                    final ComponentPresenceConstraint mandatory = Optional.ofNullable(computedComponent)
                            .map(ComponentDescription::mandatory)
                            .orElse(ComponentPresenceConstraint.MANDATORY);
                    final Set<? extends Tag> tags = Optional.ofNullable(computedComponent)
                            .map(ComponentDescription::tags)
                            .orElse(Set.of(Tag.NoTag.instance()));
                    final CheckerDescription checker = Optional.ofNullable(computedComponent)
                            .map(ComputedComponent::computationChecker)
                            .orElse(null);
                    final String headerForReferenceColumn = Optional.ofNullable(entry.getValue())
                            .map(ComponentDescription::importHeader)
                            .orElse(entry.getKey());
                    final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription =
                            new ReferenceStaticComputedColumnDescription(
                                    mandatory,
                                    tags,
                                    checker,
                                    headerForReferenceColumn,
                                    Objects.requireNonNull(computedComponent).transformation());
                    return Column.computedColumnDescriptionToColumn(referenceValueRepository, referenceColumn, multiplicity, referenceStaticComputedColumnDescription);
                }).collect(ImmutableSet.toImmutableSet());

        final ImmutableSet<Column> dynamicColumns = componentDescriptionEntryByComputedType
                .getOrDefault(
                        DynamicComponent.class,
                        new LinkedList<>()).stream()
                .flatMap(entry -> {
                    final DataColumn referenceColumn = new DataColumn(entry.getKey());
                    final DynamicComponent dynamicComponent = (DynamicComponent) entry.getValue();
                    final ComponentPresenceConstraint mandatory = Optional.ofNullable(dynamicComponent)
                            .map(ComponentDescription::mandatory)
                            .orElse(ComponentPresenceConstraint.MANDATORY);
                    final Set<? extends Tag> tags = Optional.ofNullable(dynamicComponent)
                            .map(ComponentDescription::tags)
                            .orElse(Set.of(Tag.NoTag.instance()));
                    final Multiplicity multiplicity = Optional.ofNullable(Objects.requireNonNull(dynamicComponent).checker()).map(CheckerDescription::multiplicity).orElse(Multiplicity.ONE);
                    final ReferenceDynamicColumnDescription referenceDynamicColumnDescription =
                            new ReferenceDynamicColumnDescription(
                                    mandatory,
                                    tags,
                                    null,
                                    dynamicComponent.prefix(),
                                    dynamicComponent.reference(),
                                    dynamicComponent.referenceColumnToLookForHeader()
                            );
                    final ImmutableSet<Column> valuedDynamicColumns = Column.dynamicColumnDescriptionToColumns(referenceValueRepository, referenceColumn, referenceDynamicColumnDescription, dynamicComponent.defaultValue());
                    return valuedDynamicColumns.stream();
                }).collect(ImmutableSet.toImmutableSet());

        final PatternColumnFactory patternColumnFactory = PatternColumnFactory.of(
                referenceValueRepository,
                componentDescriptionEntryByComputedType.getOrDefault(
                                PatternComponent.class,
                                new LinkedList<>())
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> (PatternComponent) e.getValue()))
        );

        final ImmutableSet<Column> columns = ImmutableSet.<Column>builder()
                .addAll(staticColumns)
                .addAll(computedColumns)
                .addAll(dynamicColumns)
                .build();
        return new BuildColumns(patternColumnFactory, columns);
    }

    public ImmutableMap<String, Column> getExpectedColumnsPerHeaders() {
        return columns().stream()
                .filter(Column::isExpected)
                .collect(ImmutableMap.toImmutableMap(
                        Column::getExpectedHeader,
                        Function.identity()
                ));
    }

    public ImmutableSet<String> expectedHeaders() {
        return getExpectedColumnsPerHeaders().keySet();
    }

    public ImmutableSet<String> mandatoryHeaders() {
        return getExpectedColumnsPerHeaders().values().stream()
                .map(Column::getExpectedHeader)
                .collect(ImmutableSet.toImmutableSet());
    }
}