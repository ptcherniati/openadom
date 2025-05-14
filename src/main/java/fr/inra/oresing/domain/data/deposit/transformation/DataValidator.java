package fr.inra.oresing.domain.data.deposit.transformation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.checker.GroovyExpressionChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.recursion.WithRecursion;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.PatternValidationCheckResult;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class DataValidator {

    /**
     * @return <p>
     * Cela aura pour effet :
     *
     * <ul>
     *     <li>de passer les vérifications et d'associer à la ligne les erreurs détectées</li>
     *     <li>d'application les transformations (échappement, expressions groovy)</li>
     *     <li>détecter les référentiels utilisés (et conserver les clés vers ceux utilisés pour fixer le refsLinkedTo)</li>
     * </ul>
     */
    public <F extends FieldType<?>> List<ReferenceDatumAfterChecking> check(
            Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
            RecursionStrategy recursionStrategy,
            final RowWithReferenceDatum rowWithReferenceDatum,
            final ImmutableSet<LineChecker<F>> transformedLineCheckers,
            PublishContext.PublishContextBuilder publishContextBuilder) {
        final DataDatum referenceDatumBeforeChecking = rowWithReferenceDatum.referenceDatum();
        final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo = new HashMap<>();
        final ImmutableList.Builder<CsvRowValidationCheckResult> allCheckerErrorsBuilder = ImmutableList.builder();
        final DataDatum referenceDatum = DataDatum.copyOf(referenceDatumBeforeChecking);
        for (final LineChecker lineChecker : transformedLineCheckers) {
            final List<ReferenceDatumAfterChecking> referenceDatumAfterCheckings = checkLineForChecker(recursionStrategy, rowWithReferenceDatum, publishContextBuilder, lineChecker, referenceDatumBeforeChecking, refsLinkedTo, referenceDatum, allCheckerErrorsBuilder);
            if (referenceDatumAfterCheckings != null) return referenceDatumAfterCheckings;
        }
        refsLinkedTo.putAll(rowWithReferenceDatum.refsLinkedTo());
        ReferenceDatumAfterChecking referenceDatumAfterChecking = new ReferenceDatumAfterChecking(
                rowWithReferenceDatum.lineNumber(),
                rowWithReferenceDatum.patternColumnName(),
                rowWithReferenceDatum.referenceDatum(),
                referenceDatum,
                ImmutableMap.copyOf(refsLinkedTo),
                allCheckerErrorsBuilder.build()
        );
        return buildReferenceDataAfterChecking(buildKey, recursionStrategy, transformedLineCheckers, publishContextBuilder, referenceDatumAfterChecking);
    }

    private static List<ReferenceDatumAfterChecking> checkLineForChecker(RecursionStrategy recursionStrategy, RowWithReferenceDatum rowWithReferenceDatum, PublishContext.PublishContextBuilder publishContextBuilder, LineChecker lineChecker, DataDatum referenceDatumBeforeChecking, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo, DataDatum referenceDatum, ImmutableList.Builder<CsvRowValidationCheckResult> allCheckerErrorsBuilder) {
        if (matchingTarget(rowWithReferenceDatum, lineChecker)) {
            return null;
        }
        if (lineChecker instanceof final LineChecker.ManyChecker manyChecker) {
            manyChecker.value().getValue().clear();
        }
        Map<String, Object> context = new HashMap<>();

        final CheckerValidationCheckResult validationCheckResults = testValues(rowWithReferenceDatum, publishContextBuilder, lineChecker, context, referenceDatumBeforeChecking);
        registerCheckedValues(lineChecker, validationCheckResults, referenceDatumBeforeChecking, refsLinkedTo, referenceDatum);

        if (validationCheckResults != null && !validationCheckResults.isSuccess()) {
            return registerErrors(recursionStrategy, rowWithReferenceDatum, lineChecker, validationCheckResults, referenceDatumBeforeChecking, allCheckerErrorsBuilder);
        }
        return null;
    }

    private <F extends FieldType<?>> List<ReferenceDatumAfterChecking> buildReferenceDataAfterChecking(Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey, RecursionStrategy recursionStrategy, ImmutableSet<LineChecker<F>> transformedLineCheckers, PublishContext.PublishContextBuilder publishContextBuilder, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        List<ReferenceDatumAfterChecking> referenceDatumAfterCheckings = List.of();
        if (recursionStrategy instanceof WithRecursion withRecursion) {
            addBuildedLineKeysToReferenceValues(buildKey, withRecursion, referenceDatumAfterChecking);
            referenceDatumAfterCheckings = testLinesRegardingRecursivity(buildKey, withRecursion, transformedLineCheckers, publishContextBuilder, referenceDatumAfterChecking);
        }
        referenceDatumAfterCheckings = ImmutableList.<ReferenceDatumAfterChecking>builder()
                .add(referenceDatumAfterChecking)
                .addAll(referenceDatumAfterCheckings)
                .build();
        if (recursionStrategy instanceof WithRecursion withRecursion) {
            withRecursion.dataImporterContext().getMissingLines()
                    .remove(buildKey.apply(referenceDatumAfterChecking).naturalKey());
        }
        return referenceDatumAfterCheckings;
    }

    static void registerCheckedValues(LineChecker lineChecker, CheckerValidationCheckResult validationCheckResults, DataDatum referenceDatumBeforeChecking, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo, DataDatum referenceDatum) {
        Optional.ofNullable(validationCheckResults)
                .filter(ValidationCheckResult::isSuccess)
                .ifPresent(validationCheckResult -> {
                            final DataColumn dataColumn = (DataColumn) validationCheckResult.target();
                            final DataColumnValue referenceColumnRawValue = referenceDatumBeforeChecking.get(dataColumn);
                            DataColumnValue valueToStoreInDatabase =
                                    validationCheckResults.transform(
                                            lineChecker,
                                            referenceColumnRawValue,
                                            dataColumn,
                                            refsLinkedTo);
                            List<DataColumn> patternOfColumn = Arrays.stream(dataColumn.column().split(Column.COLUMN_IN_COLUMN_SEPARATOR))
                                    .map(DataColumn::new)
                                    .toList();
                            DataColumn firstPatternOfColumn = patternOfColumn.get(0);
                            DataColumnValue columnValue = referenceDatum.get(firstPatternOfColumn);
                            if (columnValue instanceof DataColumnPatternValue(
                                    Map<DataColumn, DataColumnValue> values
                            )) {
                                if (patternOfColumn.size() > 1) {
                                    DataColumn secondPatternOfColumn = patternOfColumn.get(1);
                                    values.put(secondPatternOfColumn, valueToStoreInDatabase);
                                    valueToStoreInDatabase = columnValue;
                                } else {
                                    firstPatternOfColumn = new DataColumn(Column.__VALUE__);
                                    valueToStoreInDatabase = new DataColumnSingleValue(((PatternValidationCheckResult) validationCheckResults).value().getColumnValue());
                                }
                            }
                            referenceDatum.put(firstPatternOfColumn, valueToStoreInDatabase);
                        }
                );
    }

    static List<ReferenceDatumAfterChecking> registerErrors(RecursionStrategy recursionStrategy, RowWithReferenceDatum rowWithReferenceDatum, LineChecker lineChecker, CheckerValidationCheckResult validationCheckResults, DataDatum referenceDatumBeforeChecking, ImmutableList.Builder<CsvRowValidationCheckResult> allCheckerErrorsBuilder) {
        boolean isLineCheckerRecusrsiveReference = Optional.ofNullable(lineChecker.checkerDescription())
                .filter(ReferenceChecker.class::isInstance)
                .map(ReferenceChecker.class::cast)
                .stream().anyMatch(ReferenceChecker::isRecursive);
        boolean isErrorInvalidReferenceWithComponent = validationCheckResults.getValidations().stream()
                .map(ValidationCheckResult::message)
                .anyMatch("invalidReferenceWithComponent"::equals);
        if (isLineCheckerRecusrsiveReference && isErrorInvalidReferenceWithComponent) {
            return registerMissingLine(recursionStrategy, rowWithReferenceDatum, lineChecker, referenceDatumBeforeChecking);
        }
        List<ValidationCheckResult> vcrs = validationCheckResults.getValidations().stream().filter(ValidationCheckResult::isError).toList();
        vcrs.stream()
                .map(vcr -> new CsvRowValidationCheckResult(vcr, rowWithReferenceDatum.lineNumber()))
                .forEach(allCheckerErrorsBuilder::add);
        return null;
    }

    static List<ReferenceDatumAfterChecking> registerMissingLine(RecursionStrategy recursionStrategy, RowWithReferenceDatum rowWithReferenceDatum, LineChecker lineChecker, DataDatum referenceDatumBeforeChecking) {
        Optional.ofNullable(lineChecker.checkerDescription())
                .filter(ReferenceChecker.class::isInstance)
                .map(ReferenceChecker.class::cast)
                .map(ReferenceChecker::componentKey)
                .map(DataColumn::new)
                .map(referenceDatumBeforeChecking::get)
                .map(DataColumnValue::getValuesToCheck)
                .map(FieldType::getValue)
                .map(Object::toString)
                .map(Ltree::fromUnescapedString)
                .ifPresent(hierarchicalParentKey -> recursionStrategy.dataImporterContext()
                        .registerMissingLine(hierarchicalParentKey, rowWithReferenceDatum)
                );
        return List.of();
    }

    static void addBuildedLineKeysToReferenceValues(
            Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
            RecursionStrategy recursionStrategy,
            ReferenceDatumAfterChecking referenceDatumAfterChecking
    ) {
        KeysAndReferenceDatumAfterChecking keyForLine = buildKey.apply(referenceDatumAfterChecking);
        DataValue.LineIdentityColumnName key = new DataValue.LineIdentityColumnName(keyForLine.naturalKey(), keyForLine.hierarchicalKey());

        recursionStrategy.dataImporterContext().getKnownId(keyForLine.naturalKey())
                .or(() -> {
                    UUID newUuid = UUID.randomUUID();
                    recursionStrategy.dataImporterContext().addKnownIdToReferenceValues(
                            key,
                            newUuid
                    );
                    return Optional.of(newUuid);
                })
                .ifPresent(uuid -> recursionStrategy.dataImporterContext().addKnownIdToReferenceValues(
                        key,
                        uuid
                ));
    }

    static CheckerValidationCheckResult testValues(RowWithReferenceDatum rowWithReferenceDatum, PublishContext.PublishContextBuilder publishContextBuilder, LineChecker<? extends FieldType> lineChecker, Map<String, Object> context, DataDatum referenceDatumBeforeChecking) {
        switch (lineChecker.transformer()) {
            case LineChecker.LineTransformer.ChainTransformersLineTransformer transformers -> {
                for (LineChecker.LineTransformer transformer : transformers.transformers()) {
                    switch (transformer) {
                        case LineChecker.LineTransformer.TransformOneLineElementTransformer.GroovyExpressionOnOneLineElementTransformer groovyExpressionOnOneLineElementTransformer -> {
                            Set<String> groovyReferences = groovyExpressionOnOneLineElementTransformer.references();
                            context = publishContextBuilder.getGroovyContextForReferences(
                                    groovyReferences,
                                    new PublishContext.RowInfos(
                                            rowWithReferenceDatum.referenceDatum().values().values().stream()
                                                    .map(DataColumnValue::getValuesToCheck)
                                                    .map(FieldType::toString).toList(),
                                            rowWithReferenceDatum.lineNumber()
                                    )
                            );
                        }
                        default -> {
                        }
                    }
                }
            }
            default -> {
                if (lineChecker.checkerDescription() instanceof GroovyExpressionChecker groovyExpressionChecker) {
                    Set<String> groovyReferences = groovyExpressionChecker.references();
                    context = publishContextBuilder.getGroovyContextForReferences(
                            groovyReferences,
                            new PublishContext.RowInfos(
                                    rowWithReferenceDatum.referenceDatum().values().values().stream().map(DataColumnValue::getValuesToCheck).map(FieldType::toString).toList(),
                                    rowWithReferenceDatum.lineNumber()
                            )
                    );
                }
            }
        }
        return lineChecker.checkReference(referenceDatumBeforeChecking, context);
    }

    static boolean matchingTarget(RowWithReferenceDatum rowWithReferenceDatum, LineChecker<? extends FieldType> lineChecker) {
        return rowWithReferenceDatum.referenceDatum().values()
                .entrySet()
                .stream()
                .flatMap(entry -> {
                    if (entry.getValue() instanceof DataColumnPatternValue(Map<DataColumn, DataColumnValue> values)) {
                        return values.keySet().stream()
                                .map(dataColumn -> dataColumn.column().equals(Column.__VALUE__) ?
                                        entry.getKey() :
                                        new DataColumn(
                                                Column.COLUMN_IN_COLUMN_PATTERN.formatted(
                                                        entry.getKey().column(),
                                                        dataColumn.column()
                                                )
                                        )
                                );
                    }
                    return Stream.of(entry.getKey());
                }).noneMatch(column -> column.equals(lineChecker.target()) ||
                        column.column().equals(lineChecker.target().column().split(Column.COLUMN_IN_COLUMN_SEPARATOR)[0]));
    }

    private <F extends FieldType<?>> List<ReferenceDatumAfterChecking> testLinesRegardingRecursivity(
            Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
            RecursionStrategy recursionStrategy,
            ImmutableSet<LineChecker<F>> transformedLineCheckers,
            PublishContext.PublishContextBuilder publishContextBuilder,
            ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        if (recursionStrategy instanceof WithRecursion withRecursion) {
            withRecursion.testHasParent(buildKey, withRecursion, referenceDatumAfterChecking);
            List<ReferenceDatumAfterChecking> referenceDatumAfterCheckings;
            KeysAndReferenceDatumAfterChecking lineKey = buildKey.apply(referenceDatumAfterChecking);
            Map<Ltree, List<RowWithReferenceDatum>> missingLines = withRecursion.dataImporterContext().getMissingLines();
            referenceDatumAfterCheckings = Optional.ofNullable(missingLines.get(lineKey.naturalKey()))
                    .map(LinkedList::new)
                    .map(missingLines1 -> {
                        ImmutableList.Builder<ReferenceDatumAfterChecking> builder = ImmutableList.builder();
                        for (RowWithReferenceDatum missingLine : missingLines1) {
                            List<ReferenceDatumAfterChecking> check = check(
                                    buildKey,
                                    withRecursion,
                                    missingLine,
                                    transformedLineCheckers,
                                    publishContextBuilder
                            );
                            builder.addAll(check);
                        }
                        return builder.build();
                    })
                    .orElseGet(ImmutableList::of);
            referenceDatumAfterCheckings
                    .forEach(withRecursion.dataImporterContext().getMissingLines()::remove);
            return referenceDatumAfterCheckings;
        }
        return List.of();
    }


}