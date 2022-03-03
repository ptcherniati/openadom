package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.SetMultimap;
import com.google.common.primitives.Ints;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.checker.Multiplicity;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceColumnMultipleValue;
import fr.inra.oresing.model.ReferenceColumnSingleValue;
import fr.inra.oresing.model.ReferenceColumnValue;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.internationalization.Internationalization;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.model.internationalization.InternationalizationReferenceMap;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class ReferenceImporter {

    /**
     * Séparateur pour les clés naturelles composites.
     * <p>
     * Lorsqu'une clé naturelle est composite, c'est à dire composée de plusieurs {@link Configuration.ReferenceDescription#getKeyColumns()},
     * les valeurs qui composent la clé sont séparées avec ce séparateur.
     */
    private static final String COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR = "__";

    private final String refType;

    private final ImmutableMap<Ltree, UUID> storedReferences;

    private final MultipartFile file;

    private final UUID fileId;

    private final UUID applicationId;

    private final Configuration conf;

    private final ImmutableSet<LineChecker> lineCheckers;

    private final ImmutableMap<ReferenceColumn, Multiplicity> multiplicityPerColumns;

    private final RecursionStrategy recursionStrategy;

    private final Configuration.ReferenceDescription ref;

    private final ListMultimap<Ltree, Integer> hierarchicalKeys = LinkedListMultimap.create();

    private final Map<String, Internationalization> displayColumns;

    private final Optional<Map<String, String>> displayPattern;

    private final List<CsvRowValidationCheckResult> allErrors = new LinkedList<>();

    private ImmutableList<String> columns;

    public ReferenceImporter(String refType, ImmutableMap<Ltree, UUID> storedReferences, MultipartFile file, UUID fileId, UUID applicationId, Configuration conf, ImmutableSet<LineChecker> lineCheckers) {
        this.refType = refType;
        this.storedReferences = storedReferences;
        this.file = file;
        this.fileId = fileId;
        this.applicationId = applicationId;
        this.conf = conf;
        this.lineCheckers = lineCheckers;
        this.multiplicityPerColumns = lineCheckers.stream()
                .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker)
                .map(lineChecker -> (ReferenceLineChecker) lineChecker)
                .collect(ImmutableMap.toImmutableMap(referenceLineChecker -> (ReferenceColumn) referenceLineChecker.getTarget().getTarget(), referenceLineChecker -> referenceLineChecker.getConfiguration().getMultiplicity()));
        Optional<InternationalizationReferenceMap> internationalizationReferenceMap = Optional.ofNullable(conf)
                .map(configuration -> configuration.getInternationalization())
                .map(inter -> inter.getReferences())
                .map(references -> references.getOrDefault(refType, null));
        displayColumns = internationalizationReferenceMap
                .map(internationalisationSection -> internationalisationSection.getInternationalizedColumns())
                .orElseGet(HashMap::new);
        displayPattern = internationalizationReferenceMap
                .map(internationalisationSection -> internationalisationSection.getInternationalizationDisplay())
                .map(internationalizationDisplay -> internationalizationDisplay.getPattern());
        ref = conf.getReferences().get(refType);
        recursionStrategy = getRecursionStrategy();
    }

    void doImport() throws IOException {
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(ref.getSeparator())
                .withSkipHeaderRecord();
        try (InputStream csv = file.getInputStream()) {
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();
            CSVRecord headerRow = linesIterator.next();
            columns = Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
            final Stream<RowWithReferenceDatum> recordStreamBeforePreloading = Streams.stream(csvParser).map(this::csvRecordToLineAsMap);
            final Stream<RowWithReferenceDatum> recordStream = recursionStrategy.firstPass(recordStreamBeforePreloading);
            Stream<ReferenceValue> referenceValuesStream = recordStream
                    .map(this::toEntity)
                    .filter(e -> e != null)
                    .sorted(Comparator.comparing(a -> a.getHierarchicalKey().getSql()));
            storeAll(referenceValuesStream);
            InvalidDatasetContentException.checkErrorsIsEmpty(allErrors);
        }
    }

    private ReferenceValue toEntity(RowWithReferenceDatum rowWithReferenceDatum) {
        ReferenceDatum referenceDatumBeforeChecking = rowWithReferenceDatum.getReferenceDatum();
        Map<String, Set<UUID>> refsLinkedTo = new LinkedHashMap<>();
        ReferenceDatum referenceDatum = ReferenceDatum.copyOf(referenceDatumBeforeChecking);
        lineCheckers.forEach(lineChecker -> {
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
                            refsLinkedTo
                                    .computeIfAbsent(Ltree.escapeToLabel(reference), k -> new LinkedHashSet<>())
                                    .add(referenceId);
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
            List<CsvRowValidationCheckResult> rowErrors = validationCheckResults.stream()
                    .filter(validationCheckResult -> !validationCheckResult.isSuccess())
                    .map(validationCheckResult -> new CsvRowValidationCheckResult(validationCheckResult, rowWithReferenceDatum.getLineNumber()))
                    .collect(Collectors.toUnmodifiableList());
            allErrors.addAll(rowErrors);
        });
        final ReferenceValue e = new ReferenceValue();
        Preconditions.checkState(!ref.getKeyColumns().isEmpty(), "aucune colonne désignée comme clé naturelle pour le référentiel " + refType);
        String naturalKeyAsString = ref.getKeyColumns().stream()
                .map(ReferenceColumn::new)
                .map(referenceColumn -> {
                    ReferenceColumnValue referenceColumnValue = Objects.requireNonNullElse(referenceDatum.get(referenceColumn), ReferenceColumnSingleValue.empty());
                    Preconditions.checkState(referenceColumnValue instanceof ReferenceColumnSingleValue, "dans le référentiel " + refType + " la colonne " + referenceColumn + " est utilisée comme clé. Par conséquent, il ne peut avoir une valeur multiple.");
                    ReferenceColumnSingleValue referenceColumnSingleValue = ((ReferenceColumnSingleValue) referenceColumnValue);
                    return referenceColumnSingleValue;
                })
                .map(ReferenceColumnSingleValue::getValue)
                .filter(StringUtils::isNotEmpty)
                .map(Ltree::escapeToLabel)
                .collect(Collectors.joining(COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));
        final Ltree naturalKey = Ltree.fromSql(naturalKeyAsString);
        recursionStrategy.getKnownId(naturalKey)
                .ifPresent(e::setId);
        final Ltree hierarchicalKey = recursionStrategy.getHierarchicalKey(naturalKey, referenceDatum);
        final Ltree hierarchicalReference = recursionStrategy.getHierarchicalReference(naturalKey);
        referenceDatum.putAll(InternationalizationDisplay.getDisplays(displayPattern, displayColumns, referenceDatum));

        /**
         * on remplace l'id par celle en base si elle existe
         * a noter que pour les references récursives on récupère l'id depuis  referenceLineChecker.getReferenceValues() ce qui revient au même
         */

        if (storedReferences.containsKey(hierarchicalKey)) {
            e.setId(storedReferences.get(hierarchicalKey));
        }
        e.setBinaryFile(fileId);
        e.setReferenceType(refType);
        e.setHierarchicalKey(hierarchicalKey);
        e.setHierarchicalReference(hierarchicalReference);
        e.setRefsLinkedTo(refsLinkedTo);
        e.setNaturalKey(naturalKey);
        e.setApplication(applicationId);
        e.setRefValues(referenceDatum);
        if (hierarchicalKeys.containsKey(e.getHierarchicalKey())) {
            ValidationCheckResult validationCheckResult = new DuplicationLineValidationCheckResult(DuplicationLineValidationCheckResult.FileType.REFERENCES, refType, ValidationLevel.ERROR, e.getHierarchicalKey(), rowWithReferenceDatum.getLineNumber(), hierarchicalKeys.get(e.getHierarchicalKey()));
            allErrors.add(new CsvRowValidationCheckResult(validationCheckResult, rowWithReferenceDatum.getLineNumber()));
            hierarchicalKeys.put(e.getHierarchicalKey(), rowWithReferenceDatum.getLineNumber());
            return null;
        } else {
            hierarchicalKeys.put(e.getHierarchicalKey(), rowWithReferenceDatum.getLineNumber());
            return e;
        }
    }

    private RowWithReferenceDatum csvRecordToLineAsMap(CSVRecord line) {
        Iterator<String> currentHeader = columns.iterator();
        ReferenceDatum referenceDatum = new ReferenceDatum();
        line.forEach(value -> {
            String header = currentHeader.next();
            ReferenceColumn referenceColumn = new ReferenceColumn(header);
            Multiplicity multiplicity = multiplicityPerColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
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

    private RecursionStrategy getRecursionStrategy() {
        final HierarchicalKeyFactory hierarchicalKeyFactory = HierarchicalKeyFactory.build(conf, refType);
        Optional<Configuration.CompositeReferenceComponentDescription> recursiveComponentDescription = conf.getCompositeReferences().values().stream()
                .map(compositeReferenceDescription -> compositeReferenceDescription.getComponents().stream().filter(compositeReferenceComponentDescription -> refType.equals(compositeReferenceComponentDescription.getReference()) && compositeReferenceComponentDescription.getParentRecursiveKey() != null).findFirst().orElse(null))
                .filter(e -> e != null)
                .findFirst();
        final Ltree refTypeAsLabel = Ltree.fromUnescapedString(refType);
        boolean isRecursive = recursiveComponentDescription.isPresent();
        final RecursionStrategy recursionStrategy;
        if (isRecursive) {
            Configuration.ReferenceDescription ref = conf.getReferences().get(refType);
            ImmutableList<String> keyColumns = ImmutableList.copyOf(ref.getKeyColumns());
            final ReferenceColumn columnToLookForParentKey = recursiveComponentDescription
                    .map(rcd -> rcd.getParentRecursiveKey())
                    .map(ReferenceColumn::new)
                    .orElseThrow(() -> new IllegalStateException("ne devrait jamais arriver (?)"));
            final ReferenceLineChecker referenceLineChecker = lineCheckers.stream()
                    .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker && ((ReferenceLineChecker) lineChecker).getRefType().equals(refType))
                    .map(lineChecker -> ((ReferenceLineChecker) lineChecker))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("pas de checker sur " + refType + " alors qu'on est sur un référentiel récursif"));
            recursionStrategy = new RecursionStrategy.WithRecursion(refType, refTypeAsLabel, hierarchicalKeyFactory, columnToLookForParentKey, keyColumns, referenceLineChecker);
        } else {
            recursionStrategy = new RecursionStrategy.WithoutRecursion(refType, refTypeAsLabel, hierarchicalKeyFactory);
        }
        return recursionStrategy;
    }

    abstract void storeAll(Stream<ReferenceValue> stream);

    @Value
    private static class RowWithReferenceDatum {
        int lineNumber;
        ReferenceDatum referenceDatum;
    }

    /**
     * Contrat permettant de créer pour chaque ligne de référentiel sa clé hiérarchique.
     * <p>
     * Comme la création de cette clé dépend de l'appartenance ou non à un référentiel hiérarchique, on gère
     * ça par héritage.
     */
    private static abstract class HierarchicalKeyFactory {

        static HierarchicalKeyFactory build(Configuration conf, String refType) {
            HierarchicalKeyFactory hierarchicalKeyFactory;
            Optional<Configuration.CompositeReferenceDescription> toUpdateCompositeReference = conf.getCompositeReferencesUsing(refType);
            if (toUpdateCompositeReference.isPresent()) {
                Configuration.CompositeReferenceDescription compositeReferenceDescription = toUpdateCompositeReference.get();
                boolean root = Iterables.get(compositeReferenceDescription.getComponents(), 0).getReference().equals(refType);
                if (root) {
                    hierarchicalKeyFactory = new HierarchicalKeyFactory.ForCompositeReferenceRoot();
                } else {
                    Configuration.CompositeReferenceComponentDescription referenceComponentDescription = compositeReferenceDescription.getComponents().stream()
                            .filter(compositeReferenceComponentDescription -> compositeReferenceComponentDescription.getReference().equals(refType))
                            .collect(MoreCollectors.onlyElement());
                    ReferenceColumn parentHierarchicalKeyColumn = new ReferenceColumn(referenceComponentDescription.getParentKeyColumn());
                    String parentHierarchicalParentReference = compositeReferenceDescription.getComponents().get(compositeReferenceDescription.getComponents().indexOf(referenceComponentDescription) - 1).getReference();
                    hierarchicalKeyFactory = new HierarchicalKeyFactory.ForCompositeReferenceChild(parentHierarchicalKeyColumn, parentHierarchicalParentReference);
                }
            } else {
                hierarchicalKeyFactory = new HierarchicalKeyFactory.ForNotCompositeReference();
            }
            return hierarchicalKeyFactory;
        }

        public abstract Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceValues);

        public abstract Ltree newHierarchicalReference(Ltree reference);

        /**
         * Pour un référentiel qui est la racine d'un référentiel hiérarchique
         */
        private static class ForCompositeReferenceRoot extends HierarchicalKeyFactory {

            /**
             * On est sur un référentiel qui est à la racine de la hiérarchie donc sa clé hiérarchique est simplement sa clé naturelle (pas de parent)
             */
            @Override
            public Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceValues) {
                return naturalKey;
            }

            @Override
            public Ltree newHierarchicalReference(Ltree reference) {
                return reference;
            }
        }

        /**
         * Pour un référentiel qui n'appartient pas à un référentiel hiérarchique
         */
        private static class ForNotCompositeReference extends HierarchicalKeyFactory {

            /**
             * On est sur un référentiel qui n'appartient pas à une hiérarchie donc la clé naturelle se suffit à elle-même
             */
            @Override
            public Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceValues) {
                return naturalKey;
            }

            @Override
            public Ltree newHierarchicalReference(Ltree reference) {
                return reference;
            }
        }

        /**
         * Pour un référentiel qui appartient à un référentiel hiérarchique mais qui est un enfant (= pas la racine).
         */
        private static class ForCompositeReferenceChild extends HierarchicalKeyFactory {

            /**
             * La colonne dans laquelle on va chercher la clé hiérachique du parent.
             */
            private final ReferenceColumn parentHierarchicalKeyColumn;

            private final String parentHierarchicalParentReference;

            public ForCompositeReferenceChild(ReferenceColumn parentHierarchicalKeyColumn, String parentHierarchicalParentReference) {
                this.parentHierarchicalKeyColumn = parentHierarchicalKeyColumn;
                this.parentHierarchicalParentReference = parentHierarchicalParentReference;
            }

            /**
             * On calcule la clé hiérachique en préfixant la clé naturelle avec la clé hiérarchique du parent.
             */
            @Override
            public Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum) {
                ReferenceColumnValue parentHierarchicalKeyColumnValue = referenceDatum.get(parentHierarchicalKeyColumn);
                Preconditions.checkState(parentHierarchicalKeyColumnValue instanceof ReferenceColumnSingleValue);
                String parentHierarchicalKeyAsString = ((ReferenceColumnSingleValue) parentHierarchicalKeyColumnValue).getValue();
                Ltree parentHierarchicalKey = Ltree.fromUnescapedString(parentHierarchicalKeyAsString);
                return Ltree.join(parentHierarchicalKey, naturalKey);
            }

            @Override
            public Ltree newHierarchicalReference(Ltree reference) {
                return Ltree.join(Ltree.fromUnescapedString(parentHierarchicalParentReference), reference);
            }
        }
    }

    private static abstract class RecursionStrategy {

        final String refType;

        final Ltree refTypeAsLabel;

        final HierarchicalKeyFactory hierarchicalKeyFactory;

        private RecursionStrategy(String refType, Ltree refTypeAsLabel, HierarchicalKeyFactory hierarchicalKeyFactory) {
            this.refType = refType;
            this.refTypeAsLabel = refTypeAsLabel;
            this.hierarchicalKeyFactory = hierarchicalKeyFactory;
        }

        public abstract Ltree getHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum);

        public abstract Ltree getHierarchicalReference(Ltree naturalKey);

        public abstract Optional<UUID> getKnownId(Ltree naturalKey);

        public abstract Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading);

        private static class WithRecursion extends RecursionStrategy {

            private final ReferenceColumn columnToLookForParentKey;

            private final ImmutableList<String> keyColumns;

            private final Map<Ltree, Ltree> parentReferenceMap = new LinkedHashMap<>();

            private final Map<Ltree, UUID> afterPreloadReferenceUuids = new LinkedHashMap<>();

            private final ReferenceLineChecker referenceLineChecker;

            private WithRecursion(String refType, Ltree refTypeAsLabel, HierarchicalKeyFactory hierarchicalKeyFactory, ReferenceColumn columnToLookForParentKey, ImmutableList<String> keyColumns, ReferenceLineChecker referenceLineChecker) {
                super(refType, refTypeAsLabel, hierarchicalKeyFactory);
                this.columnToLookForParentKey = columnToLookForParentKey;
                this.keyColumns = keyColumns;
                this.referenceLineChecker = referenceLineChecker;
            }

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
                final Ltree hierarchicalKey = hierarchicalKeyFactory.newHierarchicalKey(recursiveNaturalKey, referenceDatum);
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
                Ltree partialSelfHierarchicalReference = refTypeAsLabel;
                for (int i = 1; i < recursiveNaturalKey.getSql().split("\\.").length; i++) {
                    partialSelfHierarchicalReference = Ltree.fromSql(partialSelfHierarchicalReference.getSql() + ".".concat(refType));
                }
                final Ltree selfHierarchicalReference = partialSelfHierarchicalReference;
                final Ltree hierarchicalReference = hierarchicalKeyFactory.newHierarchicalReference(selfHierarchicalReference);
                return hierarchicalReference;
            }

            @Override
            public Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading) {
                final ImmutableMap<Ltree, UUID> beforePreloadReferenceUuids = referenceLineChecker.getReferenceValues();
                afterPreloadReferenceUuids.putAll(beforePreloadReferenceUuids);
                ListMultimap<Ltree, Integer> missingParentReferences = LinkedListMultimap.create();
                List<RowWithReferenceDatum> collect = streamBeforePreloading
                        .peek(rowWithReferenceDatum -> {
                            ReferenceDatum referenceDatum = rowWithReferenceDatum.getReferenceDatum();
                            String sAsString = ((ReferenceColumnSingleValue) referenceDatum.get(columnToLookForParentKey)).getValue();
                            String naturalKeyAsString = keyColumns.stream()
                                    .map(ReferenceColumn::new)
                                    .map(referenceDatum::get)
                                    .map(columnDansLaquellle -> {
                                        Preconditions.checkState(columnDansLaquellle instanceof ReferenceColumnSingleValue);
                                        String keyColumnValue = ((ReferenceColumnSingleValue) columnDansLaquellle).getValue();
                                        return keyColumnValue;
                                    })
                                    .filter(StringUtils::isNotEmpty)
                                    .map(Ltree::escapeToLabel)
                                    .collect(Collectors.joining(COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));
                            Ltree naturalKey = Ltree.fromSql(naturalKeyAsString);
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
                List<CsvRowValidationCheckResult> rowErrors = missingParentReferences.entries().stream()
                        .map(entry -> {
                            Ltree missingParentReference = entry.getKey();
                            Integer lineNumber = entry.getValue();
                            ValidationCheckResult validationCheckResult =
                                    new MissingParentLineValidationCheckResult(lineNumber, refType, missingParentReference, afterPreloadReferenceUuids.keySet());
                            return new CsvRowValidationCheckResult(validationCheckResult, lineNumber);
                        })
                        .collect(Collectors.toUnmodifiableList());
                InvalidDatasetContentException.checkErrorsIsEmpty(rowErrors);
                referenceLineChecker.setReferenceValues(ImmutableMap.copyOf(afterPreloadReferenceUuids));
                return collect.stream();
            }
        }

        private static class WithoutRecursion extends RecursionStrategy {

            private WithoutRecursion(String refType, Ltree refTypeAsLabel, HierarchicalKeyFactory hierarchicalKeyFactory) {
                super(refType, refTypeAsLabel, hierarchicalKeyFactory);
            }

            @Override
            public Optional<UUID> getKnownId(Ltree naturalKey) {
                return Optional.empty();
            }

            @Override
            public Ltree getHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum) {
                return hierarchicalKeyFactory.newHierarchicalKey(naturalKey, referenceDatum);
            }

            @Override
            public Ltree getHierarchicalReference(Ltree naturalKey) {
                return hierarchicalKeyFactory.newHierarchicalReference(refTypeAsLabel);
            }

            @Override
            public Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading) {
                return streamBeforePreloading;
            }
        }
    }
}
