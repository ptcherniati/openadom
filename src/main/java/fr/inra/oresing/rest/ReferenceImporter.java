package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
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

    private final RecursionStrategy recursionStrategy;

    public ReferenceImporter(ReferenceImporterContext referenceImporterContext) {
        this.referenceImporterContext = referenceImporterContext;
        if (referenceImporterContext.isRecursive()) {
            recursionStrategy = new WithRecursion();
        } else {
            recursionStrategy = new WithoutRecursion();
        }
    }

    /**
     * Importer le fichier passé en paramètre.
     *
     * @param file le fichier à lire en flux
     * @param fileId l'id du fichier file car chaque entité stockée est associée au fichier dont elle provient
     * @throws IOException en cas d'erreur pendant la lecture du fichier passé
     */
    void doImport(MultipartFile file, UUID fileId) throws IOException {
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
            checkHeader(columns, Ints.checkedCast(headerRow.getRecordNumber()));
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
                    .map(keysAndReferenceDatumAfterChecking -> toEntity(keysAndReferenceDatumAfterChecking, fileId))
                    .sorted(Comparator.comparing(a -> a.getHierarchicalKey().getSql()));
            storeAll(referenceValuesStream);
        }

        Set<CsvRowValidationCheckResult> hierarchicalKeysConflictErrors = getHierarchicalKeysConflictErrors(encounteredHierarchicalKeysForConflictDetection);
        allErrors.addAll(hierarchicalKeysConflictErrors);
        InvalidDatasetContentException.checkErrorsIsEmpty(allErrors);
    }

    private void checkHeader(ImmutableList<String> columns, int headerLineNumber) {
        ImmutableSet<String> expectedHeaders = referenceImporterContext.getExpectedHeaders();
        ImmutableSet<String> mandatoryHeaders = referenceImporterContext.getMandatoryHeaders();
        InvalidDatasetContentException.checkHeader(expectedHeaders, mandatoryHeaders, ImmutableMultiset.copyOf(columns), headerLineNumber);
    }

    /**
     * Étant donné les clé hiérarchiques qu'on a rencontré (normalement une seule par ligne), vérifie s'il y a des doublons et calcul des erreurs le cas échéant
     */
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

    /**
     * Transforme une ligne du fichier CSV ({@link CSVRecord}) en {@link ReferenceDatum}, plus simple à lire et y associe un n° de ligne.
     */
    private RowWithReferenceDatum csvRecordToRowWithReferenceDatum(ImmutableList<String> columns, CSVRecord csvRecord) {
        Iterator<String> currentHeader = columns.iterator();
        ReferenceDatum referenceDatum = new ReferenceDatum();
        SetMultimap<String, UUID> refsLinkedTo = HashMultimap.create();
        csvRecord.forEach(cellContent -> {
            String header = currentHeader.next();
            referenceImporterContext.pushValue(referenceDatum, header, cellContent, refsLinkedTo);
        });
        int lineNumber = Ints.checkedCast(csvRecord.getRecordNumber());
        return new RowWithReferenceDatum(lineNumber, referenceDatum, ImmutableSetMultimap.copyOf(refsLinkedTo));
    }

    /**
     * Passe les {@link fr.inra.oresing.checker.LineChecker} sur la ligne passée.
     *
     * Cela aura pour effet :
     *
     * <ul>
     *     <li>de passer les vérifications et d'associer à la ligne les erreurs détectées</li>
     *     <li>d'application les transformations (échappement, expressions groovy)</li>
     *     <li>détecter les référentiels utilisés (et conserver les clés vers ceux utilisés pour fixer le refsLinkedTo)</li>
     * </ul>
     */
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
        refsLinkedToBuilder.putAll(rowWithReferenceDatum.getRefsLinkedTo());
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

    /**
     * Associe à chaque ligne une clé naturelle (peut-être composite ?) et une clé hiérarchique.
     */
    private KeysAndReferenceDatumAfterChecking computeKeys(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        final ReferenceDatum referenceDatum = referenceDatumAfterChecking.getReferenceDatumAfterChecking();
        final Ltree naturalKey = computeNaturalKey(referenceDatum);
        final Ltree hierarchicalKey = recursionStrategy.getHierarchicalKey(naturalKey, referenceDatum);
        return new KeysAndReferenceDatumAfterChecking(referenceDatumAfterChecking, naturalKey, hierarchicalKey);
    }

    /**
     * Transforme une ligne de données en une entité prête à être sauvée en base de données.
     */
    private ReferenceValue toEntity(KeysAndReferenceDatumAfterChecking keysAndReferenceDatumAfterChecking, UUID fileId) {
        final ReferenceDatumAfterChecking referenceDatumAfterChecking = keysAndReferenceDatumAfterChecking.getReferenceDatumAfterChecking();
        final ReferenceDatum referenceDatum = referenceDatumAfterChecking.getReferenceDatumAfterChecking();
        final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.getHierarchicalKey();

        final ReferenceValue e = new ReferenceValue();
        final Ltree naturalKey = keysAndReferenceDatumAfterChecking.getNaturalKey();
        final Ltree hierarchicalReference = recursionStrategy.getHierarchicalReference(naturalKey);
        referenceDatum.putAll(InternationalizationDisplay.getDisplays(referenceImporterContext.getDisplayPattern(), referenceImporterContext.getDisplayColumns(), referenceDatum));

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

    /**
     * Pour une ligne passée, calcule la clé naturelle composite de cette ligne.
     *
     * Il s'agit d'aller lire les différentes colonnes qui composent la clé, de joindre le tout et de gérer
     * l'échappement.
     */
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
        ImmutableSetMultimap<String, UUID> refsLinkedTo;
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

    /**
     * Représente les variations de l'algorithme d'import selon que le référentiel soit récursif ou non.
     */
    private interface RecursionStrategy {

        Ltree getHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum);

        Ltree getHierarchicalReference(Ltree naturalKey);

        Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading);

    }

    private class WithRecursion implements RecursionStrategy {

        private final Map<Ltree, Ltree> parentReferenceMap = new LinkedHashMap<>();

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
            final Map<Ltree, UUID> afterPreloadReferenceUuids = new LinkedHashMap<>(beforePreloadReferenceUuids);
            ListMultimap<Ltree, Integer> missingParentReferences = LinkedListMultimap.create();
            List<RowWithReferenceDatum> collect = streamBeforePreloading
                    .peek(rowWithReferenceDatum -> {
                        ReferenceDatum referenceDatum = rowWithReferenceDatum.getReferenceDatum();
                        Ltree naturalKey = computeNaturalKey(referenceDatum);
                        if (!afterPreloadReferenceUuids.containsKey(naturalKey)) {
                            afterPreloadReferenceUuids.put(naturalKey, UUID.randomUUID());
                        }
                        String parentKeyAsString = ((ReferenceColumnSingleValue) referenceDatum.get(columnToLookForParentKey)).getValue();
                        if (!Strings.isNullOrEmpty(parentKeyAsString)) {
                            Ltree parentKey = Ltree.fromUnescapedString(parentKeyAsString);
                            parentReferenceMap.put(naturalKey, parentKey);
                            if (!afterPreloadReferenceUuids.containsKey(parentKey)) {
                                final UUID uuid = UUID.randomUUID();
                                afterPreloadReferenceUuids.put(parentKey, uuid);
                                missingParentReferences.put(parentKey, rowWithReferenceDatum.getLineNumber());
                            }
                        }
                        missingParentReferences.removeAll(naturalKey);
                    })
                    .collect(Collectors.toList());
            Set<Ltree> knownReferences = afterPreloadReferenceUuids.keySet();
            checkMissingParentReferencesIsEmpty(missingParentReferences, knownReferences);
            referenceLineChecker.setReferenceValues(ImmutableMap.copyOf(afterPreloadReferenceUuids));
            return collect.stream();
        }

        /**
         * Si on a détecté des lignes qui font référence à un parent mais que celui-ci n'existe pas, on lève une exception
         *
         * @param missingParentReferences pour chaque parent manquant, les lignes du CSV où il est mentionné
         */
        private void checkMissingParentReferencesIsEmpty(ListMultimap<Ltree, Integer> missingParentReferences, Set<Ltree> knownReferences) {
            List<CsvRowValidationCheckResult> rowErrors = missingParentReferences.entries().stream()
                    .map(entry -> {
                        Ltree missingParentReference = entry.getKey();
                        Integer lineNumber = entry.getValue();
                        ValidationCheckResult validationCheckResult =
                                new MissingParentLineValidationCheckResult(lineNumber, referenceImporterContext.getRefType(), missingParentReference, knownReferences);
                        return new CsvRowValidationCheckResult(validationCheckResult, lineNumber);
                    })
                    .collect(Collectors.toUnmodifiableList());
            InvalidDatasetContentException.checkErrorsIsEmpty(rowErrors);
        }
    }

    private class WithoutRecursion implements RecursionStrategy {

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