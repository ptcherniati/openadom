package fr.inra.oresing.domain.data.deposit.csvreader;

import com.google.common.collect.*;
import com.google.common.primitives.Ints;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.context.column.OneValueStaticPatternColumn;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.recursion.WithRecursion;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.DuplicationLineValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import org.apache.commons.csv.CSVRecord;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CsvReader {
    private final DataImporterContext dataImporterContext;
    private final RecursionStrategy recursionStrategy;

    public CsvReader(DataImporterContext dataImporterContext, RecursionStrategy recursionStrategy) {
        this.dataImporterContext = dataImporterContext;
        this.recursionStrategy = recursionStrategy;
    }

    public <F extends FieldType> ImmutableSet<LineChecker<F>> buildLineCheckers(Map<DataColumn, DataColumnValue> constantColumnsValues) {
        final ImmutableSet.Builder<LineChecker<F>> linecheckersBuilder = ImmutableSet.builder();
        for (final LineChecker<F> lineChecker : dataImporterContext.getLineCheckers()) {
            if (!dataImporterContext.existsColumn(lineChecker.target(), constantColumnsValues)) {
                continue;
            }
            if (recursionStrategy instanceof WithRecursion _ && lineChecker.underlyingType() instanceof final ReferenceType referenceType) {
                final Map<DataValue.LineIdentityColumnName, UUID> map2 = dataImporterContext.getAfterPreloadReferenceUuids();
                final Map<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> map1 = referenceType.getReferenceValues();
                final ImmutableMap.Builder<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> builder = ImmutableMap.builder();
                builder.putAll(map1);
                map2.entrySet().stream()
                        .filter(ltree -> !map1.containsKey(ltree.getKey()))
                        .forEach(ltreeUUIDEntry -> builder.put(ltreeUUIDEntry.getKey(), ImmutableSet.of(ltreeUUIDEntry.getValue())));
                final ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referencesValues = builder.build();
                switch (lineChecker) {
                    case final LineChecker.ManyChecker manyChecker -> {
                        manyChecker.value().getValue()
                                .forEach(o -> ((ReferenceType) o).setReferenceValues(referencesValues));
                        referenceType.setReferenceValues(referencesValues);
                    }
                    case final LineChecker.OneChecker oneChecker -> {
                        ((ReferenceType) oneChecker.fieldTypeForOne()).setReferenceValues(referencesValues);
                        referenceType.setReferenceValues(referencesValues);
                    }
                }
            }
            linecheckersBuilder.add(lineChecker);
        }
        return linecheckersBuilder.build();
    }

    /**
     * Étant donné les clé hiérarchiques qu'on a rencontré (normalement une seule par ligne), vérifie s'il y a des doublons et calcul des erreurs le cas échéant
     */
    public Set<CsvRowValidationCheckResult> getHierarchicalKeysConflictErrors(final SetMultimap<Ltree, Long> hierarchicalKeys) {
        return hierarchicalKeys.asMap().entrySet().stream()
                .filter(entry -> {
                    final Collection<Long> lineNumbers = entry.getValue();
                    return lineNumbers.size() > 1;
                })
                .flatMap(buildCsvRowValidationCheckResult())
                .collect(Collectors.toSet());
    }

    public Function<Map.Entry<Ltree, Collection<Long>>, Stream<CsvRowValidationCheckResult>> buildCsvRowValidationCheckResult() {
        return entry -> {
            final Ltree conflictingHierarchicalKey = entry.getKey();
            final ImmutableSortedSet<Long> lineNumbers = ImmutableSortedSet.copyOf(entry.getValue());
            final SortedSet<Long> conflictingLineNumbers = new TreeSet<>(lineNumbers);
            final Long firstLineNumberToIgnore = conflictingLineNumbers.first();
            conflictingLineNumbers.remove(firstLineNumberToIgnore);
            return conflictingLineNumbers.stream()
                    .map(buildValidationsCheckResults(lineNumbers, conflictingHierarchicalKey))
                    .flatMap(Collection::stream);
        };
    }

    public Function<Long, List<CsvRowValidationCheckResult>> buildValidationsCheckResults(ImmutableSortedSet<Long> lineNumbers, Ltree conflictingHierarchicalKey) {
        return conflictingLineNumber -> {
            final Set<Long> otherLines = new TreeSet<>(lineNumbers);
            otherLines.remove(conflictingLineNumber);
            final DuplicationLineValidationCheckResult validationCheckResult =
                    new DuplicationLineValidationCheckResult(
                            DuplicationLineValidationCheckResult.FileType.REFERENCES,
                            dataImporterContext.getRefType(),
                            ValidationLevel.ERROR,
                            conflictingHierarchicalKey,
                            conflictingLineNumber,
                            lineNumbers,
                            null
                    );
            return validationCheckResult.getValidations().stream()
                    .map(validationCheckResult1 -> new CsvRowValidationCheckResult(validationCheckResult, conflictingLineNumber))
                    .toList();
        };
    }

    /**
     * Transforme une ligne du fichier CSV ({@link CSVRecord}) en {@link DataDatum}, plus simple à lire et y associe un n° de ligne.
     */
    public Stream<RowWithReferenceDatum> csvRecordToRowWithReferenceDatum(
            final ImmutableList<String> columns,
            final CSVRecord csvRecord) {
        final Iterator<String> currentHeader = columns.iterator();
        final DataDatum referenceDatum = new DataDatum();
        final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo = new HashMap<>();
        final List<PatternValueForHeader> patternValueForHeaders = new LinkedList<>();
        final int lineNumber = Ints.checkedCast(csvRecord.getRecordNumber());
        int i = 0;
        while (i < columns.size()) {
            String[] values = csvRecord.values();
            final String cellContent = values.length > i ? values[i] : "";
            final String patternComponentName = currentHeader.next();
            if (dataImporterContext.pushValue(referenceDatum, patternComponentName, cellContent.trim(), refsLinkedTo)) {
                OneValueStaticPatternColumn expectedPatternColumn1 = dataImporterContext.getPatternColumnFactory().getExpectedPatternColumn(patternComponentName);
                List<String> adjacentValues = new LinkedList<>();
                final int adjacentColumnsIndex = expectedPatternColumn1 == null ? 0 : expectedPatternColumn1.getAdjacentColumnsSize();
                for (int j = 0; j < adjacentColumnsIndex && (i + 1) < values.length; j++) {
                    i++;
                    adjacentValues.add(values[i]);
                    currentHeader.next();
                }
                patternValueForHeaders.add(new PatternValueForHeader(patternComponentName, cellContent.trim(), adjacentValues, refsLinkedTo));
            }
            i++;
        }
        if (patternValueForHeaders.isEmpty()) {
            return Stream.of(new RowWithReferenceDatum(lineNumber, "", referenceDatum, ImmutableMap.copyOf(refsLinkedTo)));
        }
        return buildRowsWithPattern(patternValueForHeaders, referenceDatum, lineNumber, refsLinkedTo);
    }

    public Stream<RowWithReferenceDatum> buildRowsWithPattern(List<PatternValueForHeader> patternValueForHeaders, DataDatum referenceDatum, int lineNumber, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
        List<RowWithReferenceDatum> rowWithReferenceData = new LinkedList<>();
        for (PatternValueForHeader patternValueForHeader : patternValueForHeaders) {
            DataDatum patternComponentDatum = dataImporterContext.getPatternColumnFactory().toQualifierDatum(patternValueForHeader.header(), patternValueForHeader);
            DataDatum dataDatum = referenceDatum.with(patternComponentDatum);
            rowWithReferenceData.add(
                    new RowWithReferenceDatum(
                            lineNumber,
                            patternValueForHeader.header(),
                            dataDatum,
                            ImmutableMap.copyOf(refsLinkedTo)
                    )
            );
        }
        return rowWithReferenceData.stream();
    }
}