package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.primitives.Ints;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.checker.Multiplicity;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceColumnMultipleValue;
import fr.inra.oresing.model.ReferenceColumnSingleValue;
import fr.inra.oresing.model.ReferenceColumnValue;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.rest.validationcheckresults.DateValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DuplicationLineValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.MissingParentLineValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.ReferenceValidationCheckResult;
import lombok.Value;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Streams;
import org.assertj.core.util.Strings;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class ReferenceImporter {

    private final ReferenceImporterContext referenceImporterContext;

    private final MultipartFile file;

    private final UUID fileId;

    private final RecursionStrategy recursionStrategy;

    public ReferenceImporter(ReferenceImporterContext referenceImporterContext, MultipartFile file, UUID fileId) {
        this.referenceImporterContext = referenceImporterContext;
        this.file = file;
        this.fileId = fileId;
        if (referenceImporterContext.isRecursive()) {
            recursionStrategy = new WithRecursion();
        } else {
            recursionStrategy = new WithoutRecursion();
        }
    }

    void doImport() throws IOException {
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(referenceImporterContext.getCsvSeparator())
                .withSkipHeaderRecord();

        final SetMultimap<Ltree, Integer> encounteredHierarchicalKeysForConflictDetection = HashMultimap.create();
        final Consumer<KeysAndReferenceDatumAfterChecking> storeHierarchicalKeyForConflictDetection = keysAndReferenceDatumAfterChecking -> {
            int lineNumber = keysAndReferenceDatumAfterChecking.getLineNumber();
            Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.getHierarchicalKey();
            encounteredHierarchicalKeysForConflictDetection.put(hierarchicalKey, lineNumber);
        };
        final List<CsvRowValidationCheckResult> allErrors = new LinkedList<>();

        try (InputStream csv = file.getInputStream()) {
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();
            CSVRecord headerRow = linesIterator.next();
            ImmutableList<String> columns = Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
            Stream<CSVRecord> csvRecordsStream = Streams.stream(csvParser);
            Function<CSVRecord, RowWithReferenceDatum> csvRecordToReferenceDatumFn = csvRecord -> csvRecordToRowWithReferenceDatum(columns, csvRecord);
            final Stream<RowWithReferenceDatum> recordStreamBeforePreloading = csvRecordsStream.map(csvRecordToReferenceDatumFn);
            final Stream<RowWithReferenceDatum> recordStream = recursionStrategy.firstPass(recordStreamBeforePreloading);
            Stream<ReferenceValue> referenceValuesStream = recordStream
                    .map(this::check)
                    .peek(referenceDatumAfterChecking -> allErrors.addAll(referenceDatumAfterChecking.getErrors()))
                    .filter(referenceDatumAfterChecking -> referenceDatumAfterChecking.getErrors().isEmpty())
                    .map(this::computeKeys)
                    .peek(storeHierarchicalKeyForConflictDetection)
                    .filter(keysAndReferenceDatumAfterChecking -> {
                        Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.getHierarchicalKey();
                        boolean canSave = encounteredHierarchicalKeysForConflictDetection.get(hierarchicalKey).size() == 1;
                        return canSave;
                    })
                    .map(this::toEntity)
                    .sorted(Comparator.comparing(a -> a.getHierarchicalKey().getSql()));
            storeAll(referenceValuesStream);
        }

        Set<CsvRowValidationCheckResult> hierarchicalKeysConflictErrors = getHierarchicalKeysConflictErrors(encounteredHierarchicalKeysForConflictDetection);
        allErrors.addAll(hierarchicalKeysConflictErrors);
        InvalidDatasetContentException.checkErrorsIsEmpty(allErrors);
    }

    private Set<CsvRowValidationCheckResult> getHierarchicalKeysConflictErrors(SetMultimap<Ltree, Integer> hierarchicalKeys) {
        Set<CsvRowValidationCheckResult> hierarchicalKeysConflictErrors = hierarchicalKeys.asMap().entrySet().stream()
                .filter(entry -> {
                    Collection<Integer> lineNumbers = entry.getValue();
                    boolean hierarchicalKeyConflictDetected = lineNumbers.size() > 1;
                    return hierarchicalKeyConflictDetected;
                })
                .flatMap(entry -> {
                    Ltree conflictingHierarchicalKey = entry.getKey();
                    ImmutableSortedSet<Integer> lineNumbers = ImmutableSortedSet.copyOf(entry.getValue());
                    SortedSet<Integer> conflictingLineNumbers = new TreeSet<>(lineNumbers);
                    Integer firstLineNumberToIgnore = conflictingLineNumbers.first();
                    conflictingLineNumbers.remove(firstLineNumberToIgnore);
                    return conflictingLineNumbers.stream().map(conflictingLineNumber -> {
                        TreeSet<Integer> otherLines = new TreeSet<>(lineNumbers);
                        otherLines.remove(conflictingLineNumber);
                        ValidationCheckResult validationCheckResult =
                                new DuplicationLineValidationCheckResult(
                                        DuplicationLineValidationCheckResult.FileType.REFERENCES,
                                        referenceImporterContext.getRefType(),
                                        ValidationLevel.ERROR,
                                        conflictingHierarchicalKey,
                                        conflictingLineNumber,
                                        lineNumbers
                                );
                        CsvRowValidationCheckResult error = new CsvRowValidationCheckResult(validationCheckResult, conflictingLineNumber);
                        return error;
                    });
                })
                .collect(Collectors.toSet());
        return hierarchicalKeysConflictErrors;
    }

    private RowWithReferenceDatum csvRecordToRowWithReferenceDatum(ImmutableList<String> columns, CSVRecord csvRecord) {
        Iterator<String> currentHeader = columns.iterator();
        ReferenceDatum referenceDatum = new ReferenceDatum();
        csvRecord.forEach(cellContent -> {
            String header = currentHeader.next();
            ReferenceColumn referenceColumn = new ReferenceColumn(header);
            Multiplicity multiplicity = referenceImporterContext.getMultiplicity(referenceColumn);
            ReferenceColumnValue referenceColumnValue;
            switch (multiplicity) {
                case ONE:
                    referenceColumnValue = new ReferenceColumnSingleValue(cellContent);
                    break;
                case MANY:
                    referenceColumnValue = ReferenceColumnMultipleValue.parseCsvCellContent(cellContent);
                    break;
                default:
                    throw new IllegalStateException("non géré " + multiplicity);
            }
            referenceDatum.put(referenceColumn, referenceColumnValue);
        });
        int lineNumber = Ints.checkedCast(csvRecord.getRecordNumber());
        return new RowWithReferenceDatum(lineNumber, referenceDatum);
    }

    private ReferenceDatumAfterChecking check(RowWithReferenceDatum rowWithReferenceDatum) {
        ReferenceDatum referenceDatumBeforeChecking = rowWithReferenceDatum.getReferenceDatum();
        ImmutableSetMultimap.Builder<String, UUID> refsLinkedToBuilder = ImmutableSetMultimap.builder();
        ImmutableList.Builder<CsvRowValidationCheckResult> allCheckerErrorsBuilder = ImmutableList.builder();
        ReferenceDatum referenceDatum = ReferenceDatum.copyOf(referenceDatumBeforeChecking);
        referenceImporterContext.getLineCheckers().forEach(lineChecker -> {
            Set<ValidationCheckResult> validationCheckResults = lineChecker.checkReference(referenceDatumBeforeChecking);
            if (lineChecker instanceof DateLineChecker) {
                validationCheckResults.stream()
                        .filter(ValidationCheckResult::isSuccess)
                        .filter(DateValidationCheckResult.class::isInstance)
                        .map(DateValidationCheckResult.class::cast)
                        .forEach(dateValidationCheckResult -> {
                            ReferenceColumn referenceColumn = (ReferenceColumn) dateValidationCheckResult.getTarget().getTarget();
                            ReferenceColumnValue referenceColumnRawValue = referenceDatumBeforeChecking.get(referenceColumn);
                            ReferenceColumnValue valueToStoreInDatabase = referenceColumnRawValue
                                    .transform(rawValue ->
                                            String.format("date:%s:%s", dateValidationCheckResult.getLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), rawValue)
                                    );
                            referenceDatum.put(referenceColumn, valueToStoreInDatabase);
                        });
            } else if (lineChecker instanceof ReferenceLineChecker) {
                ReferenceLineChecker referenceLineChecker = (ReferenceLineChecker) lineChecker;
                ReferenceColumn referenceColumn = (ReferenceColumn) referenceLineChecker.getTarget().getTarget();
                SetMultimap<ReferenceColumn, String> rawValueReplacedByKeys = HashMultimap.create();
                String reference = referenceLineChecker.getRefType();
                validationCheckResults.stream()
                        .filter(ValidationCheckResult::isSuccess)
                        .filter(ReferenceValidationCheckResult.class::isInstance)
                        .map(ReferenceValidationCheckResult.class::cast)
                        .forEach(referenceValidationCheckResult -> {
                            UUID referenceId = referenceValidationCheckResult.getMatchedReferenceId();
                            rawValueReplacedByKeys.put(referenceColumn, referenceValidationCheckResult.getMatchedReferenceHierarchicalKey().getSql());
                            refsLinkedToBuilder.put(Ltree.escapeToLabel(reference), referenceId);
                        });
                Multiplicity multiplicity = referenceLineChecker.getConfiguration().getMultiplicity();
                Set<String> values = rawValueReplacedByKeys.get(referenceColumn);
                ReferenceColumnValue referenceColumnNewValue;
                switch (multiplicity) {
                    case ONE:
                        if (values.isEmpty()) {
                            referenceColumnNewValue = ReferenceColumnSingleValue.empty();
                        } else {
                            referenceColumnNewValue = new ReferenceColumnSingleValue(Iterables.getOnlyElement(values));
                        }
                        break;
                    case MANY:
                        referenceColumnNewValue = new ReferenceColumnMultipleValue(values);
                        break;
                    default:
                        throw new IllegalStateException("multiplicity = " + multiplicity);
                }
                referenceDatum.put(referenceColumn, referenceColumnNewValue);
            }
            List<CsvRowValidationCheckResult> checkerErrors = validationCheckResults.stream()
                    .filter(validationCheckResult -> !validationCheckResult.isSuccess())
                    .map(validationCheckResult -> new CsvRowValidationCheckResult(validationCheckResult, rowWithReferenceDatum.getLineNumber()))
                    .collect(Collectors.toUnmodifiableList());
            allCheckerErrorsBuilder.addAll(checkerErrors);
        });
        ReferenceDatumAfterChecking referenceDatumAfterChecking =
                new ReferenceDatumAfterChecking(
                        rowWithReferenceDatum.getLineNumber(),
                        rowWithReferenceDatum.getReferenceDatum(),
                        referenceDatum,
                        refsLinkedToBuilder.build(),
                        allCheckerErrorsBuilder.build()
                );
        return referenceDatumAfterChecking;
    }

    private KeysAndReferenceDatumAfterChecking computeKeys(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        final ReferenceDatum referenceDatum = referenceDatumAfterChecking.getReferenceDatumAfterChecking();
        final Ltree naturalKey = computeNaturalKey(referenceDatum);
        final Ltree hierarchicalKey = recursionStrategy.getHierarchicalKey(naturalKey, referenceDatum);
        return new KeysAndReferenceDatumAfterChecking(referenceDatumAfterChecking, naturalKey, hierarchicalKey);
    }

    private ReferenceValue toEntity(KeysAndReferenceDatumAfterChecking keysAndReferenceDatumAfterChecking) {
        final ReferenceDatumAfterChecking referenceDatumAfterChecking = keysAndReferenceDatumAfterChecking.getReferenceDatumAfterChecking();
        final ReferenceDatum referenceDatum = referenceDatumAfterChecking.getReferenceDatumAfterChecking();
        final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.getHierarchicalKey();

        final ReferenceValue e = new ReferenceValue();
        final Ltree naturalKey = keysAndReferenceDatumAfterChecking.getNaturalKey();
        recursionStrategy.getKnownId(naturalKey)
                .ifPresent(e::setId);
        final Ltree hierarchicalReference = recursionStrategy.getHierarchicalReference(naturalKey);
        referenceDatum.putAll(InternationalizationDisplay.getDisplays(referenceImporterContext.getDisplayPattern(), referenceImporterContext.getDisplayColumns(), referenceDatum));

        /**
         * on remplace l'id par celle en base si elle existe
         * a noter que pour les references récursives on récupère l'id depuis  referenceLineChecker.getReferenceValues() ce qui revient au même
         */

        referenceImporterContext.getIdForSameHierarchicalKeyInDatabase(hierarchicalKey)
                .ifPresent(e::setId);
        e.setBinaryFile(fileId);
        e.setReferenceType(referenceImporterContext.getRefType());
        e.setHierarchicalKey(hierarchicalKey);
        e.setHierarchicalReference(hierarchicalReference);
        e.setRefsLinkedTo(Maps.transformValues(referenceDatumAfterChecking.getRefsLinkedTo().asMap(), Set::copyOf));
        e.setNaturalKey(naturalKey);
        e.setApplication(referenceImporterContext.getApplicationId());
        e.setRefValues(referenceDatum);
        return e;
    }

    private Ltree computeNaturalKey(ReferenceDatum referenceDatum) {
        String naturalKeyAsString = referenceImporterContext.getKeyColumns().stream()
                .map(referenceColumn -> {
                    ReferenceColumnValue referenceColumnValue = referenceDatum.get(referenceColumn);
                    Preconditions.checkState(referenceColumnValue instanceof ReferenceColumnSingleValue, "dans le référentiel " + referenceImporterContext.getRefType() + " la colonne " + referenceColumn + " est utilisée comme clé. Par conséquent, il ne peut pas y avoir une valeur multiple.");
                    return referenceColumnValue;
                })
                .map(ReferenceColumnSingleValue.class::cast)
                .map(ReferenceColumnSingleValue::getValue)
                .filter(StringUtils::isNotEmpty)
                .map(Ltree::escapeToLabel)
                .collect(Collectors.joining(referenceImporterContext.getCompositeNaturalKeyComponentsSeparator()));
        final Ltree naturalKey = Ltree.fromSql(naturalKeyAsString);
        return naturalKey;
    }

    abstract void storeAll(Stream<ReferenceValue> stream);

    @Value
    private static class RowWithReferenceDatum {
        int lineNumber;
        ReferenceDatum referenceDatum;
    }

    @Value
    private static class ReferenceDatumAfterChecking {
        int lineNumber;
        ReferenceDatum referenceDatumBeforeChecking;
        ReferenceDatum referenceDatumAfterChecking;
        ImmutableSetMultimap<String, UUID> refsLinkedTo;
        ImmutableList<CsvRowValidationCheckResult> errors;
    }

    @Value
    private static class KeysAndReferenceDatumAfterChecking {
        ReferenceDatumAfterChecking referenceDatumAfterChecking;
        Ltree naturalKey;
        Ltree hierarchicalKey;

        public int getLineNumber() {
            return getReferenceDatumAfterChecking().getLineNumber();
        }
    }

    private interface RecursionStrategy {

        Ltree getHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum);

        Ltree getHierarchicalReference(Ltree naturalKey);

        Optional<UUID> getKnownId(Ltree naturalKey);

        Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading);

    }

    private class WithRecursion implements RecursionStrategy {

        private final Map<Ltree, Ltree> parentReferenceMap = new LinkedHashMap<>();

        private final Map<Ltree, UUID> afterPreloadReferenceUuids = new LinkedHashMap<>();

        @Override
        public Optional<UUID> getKnownId(Ltree naturalKey) {
            if (afterPreloadReferenceUuids.containsKey(naturalKey)) {
                return Optional.of(afterPreloadReferenceUuids.get(naturalKey));
            }
            return Optional.empty();
        }

        @Override
        public Ltree getHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum) {
            Ltree recursiveNaturalKey = getRecursiveNaturalKey(naturalKey);
            final Ltree hierarchicalKey = referenceImporterContext.newHierarchicalKey(recursiveNaturalKey, referenceDatum);
            return hierarchicalKey;
        }

        private Ltree getRecursiveNaturalKey(Ltree naturalKey) {
            Ltree recursiveNaturalKey = naturalKey;
            Ltree parentKey = parentReferenceMap.getOrDefault(recursiveNaturalKey, null);
            while (parentKey != null) {
                recursiveNaturalKey = Ltree.join(parentKey, recursiveNaturalKey);
                parentKey = parentReferenceMap.getOrDefault(parentKey, null);
            }
            return recursiveNaturalKey;
        }

        @Override
        public Ltree getHierarchicalReference(Ltree naturalKey) {
            Ltree recursiveNaturalKey = getRecursiveNaturalKey(naturalKey);
            Ltree partialSelfHierarchicalReference = referenceImporterContext.getRefTypeAsLabel();
            for (int i = 1; i < recursiveNaturalKey.getSql().split("\\.").length; i++) {
                partialSelfHierarchicalReference = Ltree.fromSql(partialSelfHierarchicalReference.getSql() + ".".concat(referenceImporterContext.getRefType()));
            }
            final Ltree selfHierarchicalReference = partialSelfHierarchicalReference;
            final Ltree hierarchicalReference = referenceImporterContext.newHierarchicalReference(selfHierarchicalReference);
            return hierarchicalReference;
        }

        @Override
        public Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading) {
            final ReferenceColumn columnToLookForParentKey = referenceImporterContext.getColumnToLookForParentKey();
            ReferenceLineChecker referenceLineChecker = referenceImporterContext.getReferenceLineChecker();
            final ImmutableMap<Ltree, UUID> beforePreloadReferenceUuids = referenceLineChecker.getReferenceValues();
            afterPreloadReferenceUuids.putAll(beforePreloadReferenceUuids);
            ListMultimap<Ltree, Integer> missingParentReferences = LinkedListMultimap.create();
            List<RowWithReferenceDatum> collect = streamBeforePreloading
                    .peek(rowWithReferenceDatum -> {
                        ReferenceDatum referenceDatum = rowWithReferenceDatum.getReferenceDatum();
                        String sAsString = ((ReferenceColumnSingleValue) referenceDatum.get(columnToLookForParentKey)).getValue();
                        Ltree naturalKey = computeNaturalKey(referenceDatum);
                        if (!afterPreloadReferenceUuids.containsKey(naturalKey)) {
                            afterPreloadReferenceUuids.put(naturalKey, UUID.randomUUID());
                        }
                        if (!Strings.isNullOrEmpty(sAsString)) {
                            Ltree s;
                            try {
                                s = Ltree.fromUnescapedString(sAsString);
                            } catch (IllegalArgumentException e) {
                                return;
                            }
                            parentReferenceMap.put(naturalKey, s);
                            if (!afterPreloadReferenceUuids.containsKey(s)) {
                                final UUID uuid = UUID.randomUUID();
                                afterPreloadReferenceUuids.put(s, uuid);
                                missingParentReferences.put(s, rowWithReferenceDatum.getLineNumber());
                            }
                        }
                        missingParentReferences.removeAll(naturalKey);
                    })
                    .collect(Collectors.toList());
            checkMissingParentReferencesIsEmpty(missingParentReferences);
            referenceLineChecker.setReferenceValues(ImmutableMap.copyOf(afterPreloadReferenceUuids));
            return collect.stream();
        }

        private void checkMissingParentReferencesIsEmpty(ListMultimap<Ltree, Integer> missingParentReferences) {
            List<CsvRowValidationCheckResult> rowErrors = missingParentReferences.entries().stream()
                    .map(entry -> {
                        Ltree missingParentReference = entry.getKey();
                        Integer lineNumber = entry.getValue();
                        ValidationCheckResult validationCheckResult =
                                new MissingParentLineValidationCheckResult(lineNumber, referenceImporterContext.getRefType(), missingParentReference, afterPreloadReferenceUuids.keySet());
                        return new CsvRowValidationCheckResult(validationCheckResult, lineNumber);
                    })
                    .collect(Collectors.toUnmodifiableList());
            InvalidDatasetContentException.checkErrorsIsEmpty(rowErrors);
        }
    }

    private class WithoutRecursion implements RecursionStrategy {

        @Override
        public Optional<UUID> getKnownId(Ltree naturalKey) {
            return Optional.empty();
        }

        @Override
        public Ltree getHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum) {
            return referenceImporterContext.newHierarchicalKey(naturalKey, referenceDatum);
        }

        @Override
        public Ltree getHierarchicalReference(Ltree naturalKey) {
            final Ltree refTypeAsLabel = referenceImporterContext.getRefTypeAsLabel();
            return referenceImporterContext.newHierarchicalReference(refTypeAsLabel);
        }

        @Override
        public Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading) {
            return streamBeforePreloading;
        }
    }
}
