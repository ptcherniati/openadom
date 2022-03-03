package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
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
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class ReferenceImporter {

    private final ReferenceImporterContext referenceImporterContext;

    private final MultipartFile file;

    private final UUID fileId;

    private final RecursionStrategy recursionStrategy;

    private final ListMultimap<Ltree, Integer> hierarchicalKeys = LinkedListMultimap.create();

    private ImmutableList<String> columns;

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
        final List<CsvRowValidationCheckResult> allErrors = new LinkedList<>();
        try (InputStream csv = file.getInputStream()) {
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();
            CSVRecord headerRow = linesIterator.next();
            columns = Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
            final Stream<RowWithReferenceDatum> recordStreamBeforePreloading = Streams.stream(csvParser).map(this::csvRecordToLineAsMap);
            final Stream<RowWithReferenceDatum> recordStream = recursionStrategy.firstPass(recordStreamBeforePreloading);
            Stream<ReferenceValue> referenceValuesStream = recordStream
                    .map(this::check)
                    .map(this::toErrorsOrEntityToStore)
                    .peek(errorsOrEntityToStore -> allErrors.addAll(errorsOrEntityToStore.getErrors()))
                    .map(ErrorsOrEntityToStore::getReferenceValueToStore)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(Comparator.comparing(a -> a.getHierarchicalKey().getSql()));
            storeAll(referenceValuesStream);
            InvalidDatasetContentException.checkErrorsIsEmpty(allErrors);
        }
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

    private ErrorsOrEntityToStore toErrorsOrEntityToStore(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        ReferenceDatum referenceDatum = referenceDatumAfterChecking.getReferenceDatumAfterChecking();
        final ReferenceValue e = new ReferenceValue();
        final Ltree naturalKey = computeNaturalKey(referenceDatum);
        recursionStrategy.getKnownId(naturalKey)
                .ifPresent(e::setId);
        final Ltree hierarchicalKey = recursionStrategy.getHierarchicalKey(naturalKey, referenceDatum);
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
        e.setRefsLinkedTo(referenceDatumAfterChecking.getRefsLinkedTo().asMap().entrySet().stream()
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue()))));
        e.setNaturalKey(naturalKey);
        e.setApplication(referenceImporterContext.getApplicationId());
        e.setRefValues(referenceDatum);
        ErrorsOrEntityToStore errorsOrEntityToStore;
        if (hierarchicalKeys.containsKey(e.getHierarchicalKey())) {
            ValidationCheckResult validationCheckResult = new DuplicationLineValidationCheckResult(DuplicationLineValidationCheckResult.FileType.REFERENCES, referenceImporterContext.getRefType(), ValidationLevel.ERROR, e.getHierarchicalKey(), referenceDatumAfterChecking.getLineNumber(), hierarchicalKeys.get(e.getHierarchicalKey()));
            CsvRowValidationCheckResult error = new CsvRowValidationCheckResult(validationCheckResult, referenceDatumAfterChecking.getLineNumber());
            hierarchicalKeys.put(e.getHierarchicalKey(), referenceDatumAfterChecking.getLineNumber());
            ImmutableList<CsvRowValidationCheckResult> build = ImmutableList.<CsvRowValidationCheckResult>builder()
                    .addAll(referenceDatumAfterChecking.getErrors())
                    .add(error)
                    .build();
            errorsOrEntityToStore = new ErrorsOrEntityToStore(Optional.empty(), build);
        } else {
            hierarchicalKeys.put(e.getHierarchicalKey(), referenceDatumAfterChecking.getLineNumber());
            errorsOrEntityToStore = new ErrorsOrEntityToStore(Optional.of(e), referenceDatumAfterChecking.getErrors());
        }
        return errorsOrEntityToStore;
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

    private RowWithReferenceDatum csvRecordToLineAsMap(CSVRecord line) {
        Iterator<String> currentHeader = columns.iterator();
        ReferenceDatum referenceDatum = new ReferenceDatum();
        line.forEach(value -> {
            String header = currentHeader.next();
            ReferenceColumn referenceColumn = new ReferenceColumn(header);
            Multiplicity multiplicity = referenceImporterContext.getMultiplicity(referenceColumn);
            ReferenceColumnValue referenceColumnValue;
            switch (multiplicity) {
                case ONE:
                    referenceColumnValue = new ReferenceColumnSingleValue(value);
                    break;
                case MANY:
                    referenceColumnValue = ReferenceColumnMultipleValue.parseCsvCellContent(value);
                    break;
                default:
                    throw new IllegalStateException("non géré " + multiplicity);
            }
            referenceDatum.put(referenceColumn, referenceColumnValue);
        });
        int lineNumber = Ints.checkedCast(line.getRecordNumber());
        return new RowWithReferenceDatum(lineNumber, referenceDatum);
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
    private static class ErrorsOrEntityToStore {
        Optional<ReferenceValue> referenceValueToStore;
        ImmutableList<CsvRowValidationCheckResult> errors;
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
