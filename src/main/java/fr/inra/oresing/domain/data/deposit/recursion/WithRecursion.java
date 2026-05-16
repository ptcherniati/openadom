package fr.inra.oresing.domain.data.deposit.recursion;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public record WithRecursion(
        AsynchroneFileImporterContext dataImporterContext,
        ConcurrentHashMap<DataValue.LineIdentityColumnName, Ltree> parentReferenceMap,
        boolean orderedMode) implements RecursionStrategy {
    /**
     * Constructeur principal (mode standard, non ordonné).
     */
    public WithRecursion(final AsynchroneFileImporterContext dataImporterContext) {
        this(dataImporterContext, new ConcurrentHashMap<>(), false);
    }

    /**
     * Crée une stratégie en mode « récursion ordonnée » :
     * les parents apparaissent guarantis avant leurs enfants dans le CSV.
     *
     * @param dataImporterContext contexte de l'import
     * @return stratégie récursive en mode ordonné
     */
    public static WithRecursion ordered(final AsynchroneFileImporterContext dataImporterContext) {
        return new WithRecursion(dataImporterContext, new ConcurrentHashMap<>(), true);
    }

    @Override
    public boolean isOrderedMode() {
        return orderedMode;
    }


    @Override
    public Ltree getHierarchicalKey(final Ltree naturalKey, final DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        Optional<DataValue.LineIdentityColumnName> registerId = dataImporterContext().afterPreloadReferenceUuids().keySet()
                .stream()
                .filter(lineIdentityColumnName -> lineIdentityColumnName.naturalKey().equals(naturalKey))
                .findFirst();
        if (registerId.isPresent()) {
            return registerId.get().hierarchicalKey();
        }
        String parentType = dataImporterContext()
                .contextConstants().dataConfiguration()
                .findParentDescription(dataImporterContext().contextConstants().refType())
                .map(ComponentDescription::checker)
                .map(ReferenceChecker.class::cast)
                .map(ReferenceChecker::refType)
                .orElse(null);
        Optional<Ltree> parentValue = dataImporterContext()
                .contextConstants().dataConfiguration()
                .findParentDescription(dataImporterContext().contextConstants().refType())
                .map(ComponentDescription::componentKey)
                .map(DataColumn::new)
                .map(referenceDatumAfterChecking.referenceDatumAfterChecking()::get)
                .map(DataColumnValue::toJsonForDatabase)
                .map(Object::toString)
                .map(Ltree::fromSql)
                .map(RecursionStrategy.toNaturalKey(parentType));
        Ltree parentRecursiveValue =
                dataImporterContext()
                        .contextConstants().dataConfiguration().componentDescriptions().values()
                        .stream()
                        .map(ComponentDescription::checker)
                        .filter(ReferenceChecker.class::isInstance)
                        .map(ReferenceChecker.class::cast)
                        .filter(ReferenceChecker::isRecursive)
                        .findAny()
                        .map(ReferenceChecker::componentKey)
                        .map(DataColumn::new)
                        .map(referenceDatum.values()::get)
                        .map(DataColumnValue::getValuesToCheck)
                        .filter(ReferenceType.class::isInstance)
                        .map(ReferenceType.class::cast)
                        .map(FieldType::getValue)
                        .map(this::recursiveNodeHierarchicalKey)
                        .orElse(null);
        Ltree hierarchicalKey = recursiveNodeHierarchicalKey(naturalKey);
        if (parentRecursiveValue != null) {
            hierarchicalKey = Ltree.join(parentRecursiveValue, hierarchicalKey);
        }
        if (parentValue.isPresent()) {
            hierarchicalKey = Ltree.join(parentValue.get(), hierarchicalKey);
        }
        return hierarchicalKey;
    }

    private Ltree recursiveNodeHierarchicalKey(final Ltree naturalKey) {
        return Ltree.fromSql("%sK%s".formatted(dataImporterContext().contextConstants().refType(), naturalKey));
    }

    /*
         to construct the natural key we concatenate the values of the key columns.
         - if the column is a repository type column, its value is an ltree. We take the last element of this ltree as part of the key
         - otherwise we escape the value
         - date values receive special treatment
      */
    @Override
    public Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        UnaryOperator<String> nullOrEmptyToNull = partialKey -> Strings.isNullOrEmpty(partialKey) ? Ltree.NULL_KEY : partialKey;
        Function<DataColumn, String> toEscapedValueFromColumnRegardingColumnIsReferenceType = dataColumn -> getEscapedValueFromColumnRegardingColumnIsReferenceType(dataColumn, referenceDatumAfterChecking.referenceDatumAfterChecking());
        String naturalKey = dataImporterContext().getNaturalKeyColumns().stream()
                .map(DataColumn::new)
                .map(toEscapedValueFromColumnRegardingColumnIsReferenceType)
                .map(nullOrEmptyToNull)
                .collect(Collectors.joining(AsynchroneFileImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));
        Preconditions.checkState(!naturalKey.isEmpty(),
                ExceptionMessage.NULL_NATURAL_KEY.toMessage(),
                referenceDatumAfterChecking.lineNumber(),
                String.join(" - ", dataImporterContext().getNaturalKeyColumnsImportHeaders()));
        return Ltree.fromSql(naturalKey);
    }

    String getEscapedValueFromColumnRegardingColumnIsReferenceType(DataColumn dataColumn, DataDatum referenceDatum) {
        boolean isReferenceColumn = dataImporterContext().lineCheckers().stream()
                .filter(lineChecker -> lineChecker.target().equals(dataColumn))
                .map(LineChecker::underlyingType)
                .anyMatch(ReferenceType.class::isInstance);
        String dataValue = referenceDatum.get(dataColumn).toJsonForDatabase().toString();
        if (Strings.isNullOrEmpty(dataValue)) {
            return "";
        }
        if (isReferenceColumn) {
            return Ltree.fromSql(dataValue).last().getSql();
        } else {
            return Ltree.fromUnescapedString(dataValue).getSql();
        }
    }

    @Override
    public List<ReferenceDatumAfterChecking> testHasParent(
            Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
            RecursionStrategy recursionStrategy,
            ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        KeysAndReferenceDatumAfterChecking keys = buildKey.apply(referenceDatumAfterChecking);
        Optional<UUID> knownId = dataImporterContext().getKnownId(keys.naturalKey(), keys.patternColumnName());
        DataValue.LineIdentityColumnName key = new DataValue.LineIdentityColumnName(keys.naturalKey(), keys.hierarchicalKey(), keys.patternColumnName());
        if (knownId.isEmpty()) {
            dataImporterContext().putAfterPreload(key, UUID.randomUUID());
            dataImporterContext().getKnownId(keys.naturalKey(), keys.patternColumnName());
        }
        return List.of(referenceDatumAfterChecking);
    }

    @Override
    public void addKnownIdToReferenceValues(DataValue.LineIdentityColumnName key, UUID uuid) {
        Map<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValuesForSelfType = getReferenceValuesForSelfType();
        Map<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues = new HashMap<>(referenceValuesForSelfType);
        if (!referenceValues.containsKey(key)) {
            referenceValues.put(key, ImmutableSet.of(uuid));
            addReferenceValuesForSelfType(key, ImmutableSet.of(uuid));
        }
        for (LineChecker<? extends FieldType<?>> lineChecker : dataImporterContext().transformedLineCheckers()) {
            if (lineChecker.checkerDescription() instanceof ReferenceChecker referenceChecker && referenceChecker.refType().equals(dataImporterContext().contextConstants().refType())) {
                ReferenceType fieldType = (ReferenceType) lineChecker.fieldTypeForOne();
                fieldType.setReferenceValues(ImmutableMap.copyOf(referenceValues));
            }
        }
    }

    @Override
    public Map<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> getReferenceValuesForSelfType() {
        return dataImporterContext().afterPreloadReferenceUuids().entrySet()
                .stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ImmutableSet.of(entry.getValue())
                ));
    }

    @Override
    public void addReferenceValuesForSelfType(DataValue.LineIdentityColumnName key, ImmutableSet<UUID> uuids) {
        uuids.stream()
                .findFirst()
                .ifPresent(uuid ->
                        dataImporterContext().putAfterPreload(key, uuid)
                );
    }
}