package fr.inra.oresing.domain.data.deposit;

import com.google.common.collect.*;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Authorization;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.checker.GroovyExpressionChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.configuration.ConfigurationSi;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.csvreader.CsvReader;
import fr.inra.oresing.domain.data.deposit.csvreader.PatternValueForHeader;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.recursion.WithRecursion;
import fr.inra.oresing.domain.data.deposit.recursion.WithoutRecursion;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.transformation.DataTransformer;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.DefaultValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.PatternValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;
import fr.inra.oresing.domain.data.read.DataHeaderReader;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataImporter {
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    public static final DateTimeFormatter DISPLAY_DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");


    public static final String HIERARCHICALKEY_SEPARATOR = "K";

    public CsvReader getCsvReader() {
        return csvReader;
    }

    public ConfigurationSi getConfigurationSi() {
        return configurationSi;
    }

    private final CsvReader csvReader = new CsvReader(this);
    private final DataTransformer dataTransformer = new DataTransformer(this);

    public DataImporterContext getDataImporterContext() {
        return dataImporterContext;
    }

    private final DataImporterContext dataImporterContext;

    public RecursionStrategy getRecursionStrategy() {
        return recursionStrategy;
    }

    private final RecursionStrategy recursionStrategy;
    private final Consumer<Stream<DataValue>> storeAll;
    private final ConfigurationSi configurationSi = new ConfigurationSi(this);

    public DataImporter(final DataImporterContext dataImporterContext, final Consumer<Stream<DataValue>> storeAll) {
        super();
        this.dataImporterContext = dataImporterContext;
        this.storeAll = storeAll;
        if (dataImporterContext.isRecursive()) {
            recursionStrategy = new WithRecursion(dataImporterContext);
        } else {
            recursionStrategy = new WithoutRecursion(dataImporterContext);
        }
    }

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
    private static List<ReferenceDatumAfterChecking> check(
            Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
            RecursionStrategy recursionStrategy,
            final RowWithReferenceDatum rowWithReferenceDatum,
            final ImmutableSet<LineChecker<? extends FieldType>> transformedLineCheckers,
            PublishContext.PublishContextBuilder publishContextBuilder) {
        final DataDatum referenceDatumBeforeChecking = rowWithReferenceDatum.referenceDatum();
        final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo = new HashMap<>();
        final ImmutableList.Builder<CsvRowValidationCheckResult> allCheckerErrorsBuilder = ImmutableList.builder();
        final DataDatum referenceDatum = DataDatum.copyOf(referenceDatumBeforeChecking);
        for (final LineChecker lineChecker : transformedLineCheckers) {
            if (matchingTarget(rowWithReferenceDatum, lineChecker)) {
                continue;
            }
            if (lineChecker instanceof final LineChecker.ManyChecker manyChecker) {
                manyChecker.value().getValue().clear();
            }
            Map<String, Object> context = new HashMap<>();

            final CheckerValidationCheckResult validationCheckResults = testValues(rowWithReferenceDatum, publishContextBuilder, lineChecker, context, referenceDatumBeforeChecking);
            registerCheckedValues(lineChecker, validationCheckResults, referenceDatumBeforeChecking, refsLinkedTo, referenceDatum);

            if (validationCheckResults != null && !validationCheckResults.isSuccess()) {
                final List<ReferenceDatumAfterChecking> recursionStrategy1 = registerErrors(recursionStrategy, rowWithReferenceDatum, lineChecker, validationCheckResults, referenceDatumBeforeChecking, allCheckerErrorsBuilder);
                if (recursionStrategy1 != null) return recursionStrategy1;
            }
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

    private static List<ReferenceDatumAfterChecking> buildReferenceDataAfterChecking(Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey, RecursionStrategy recursionStrategy, ImmutableSet<LineChecker<? extends FieldType>> transformedLineCheckers, PublishContext.PublishContextBuilder publishContextBuilder, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
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

    private static void registerCheckedValues(LineChecker lineChecker, CheckerValidationCheckResult validationCheckResults, DataDatum referenceDatumBeforeChecking, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo, DataDatum referenceDatum) {
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

    private static List<ReferenceDatumAfterChecking> registerErrors(RecursionStrategy recursionStrategy, RowWithReferenceDatum rowWithReferenceDatum, LineChecker lineChecker, CheckerValidationCheckResult validationCheckResults, DataDatum referenceDatumBeforeChecking, ImmutableList.Builder<CsvRowValidationCheckResult> allCheckerErrorsBuilder) {
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

    private static List<ReferenceDatumAfterChecking> registerMissingLine(RecursionStrategy recursionStrategy, RowWithReferenceDatum rowWithReferenceDatum, LineChecker lineChecker, DataDatum referenceDatumBeforeChecking) {
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

    private static List<ReferenceDatumAfterChecking> testLinesRegardingRecursivity(
            Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
            RecursionStrategy recursionStrategy,
            ImmutableSet<LineChecker<? extends FieldType>> transformedLineCheckers,
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

    private static void addBuildedLineKeysToReferenceValues(
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

    private static CheckerValidationCheckResult testValues(RowWithReferenceDatum rowWithReferenceDatum, PublishContext.PublishContextBuilder publishContextBuilder, LineChecker<? extends FieldType> lineChecker, Map<String, Object> context, DataDatum referenceDatumBeforeChecking) {
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
        //}
        return lineChecker.checkReference(referenceDatumBeforeChecking, context);
    }

    private static boolean matchingTarget(RowWithReferenceDatum rowWithReferenceDatum, LineChecker<? extends FieldType> lineChecker) {
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

    /**
     *
     */
    public void doImport(final InputStream csv, final UUID fileId) throws IOException {
        final CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(dataImporterContext.getCsvSeparator())
                .setSkipHeaderRecord(true)
                .build();

        SetMultimap<Ltree, Long> encounteredHierarchicalKeysForConflictDetection = HashMultimap.create();
        Function<KeysAndReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> storeHierarchicalKeyForConflictDetection = keysAndReferenceDatumAfterChecking -> {
            final long lineNumber = keysAndReferenceDatumAfterChecking.getLineNumber();
            final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
            encounteredHierarchicalKeysForConflictDetection.put(hierarchicalKey, lineNumber);
            return keysAndReferenceDatumAfterChecking;
        };
        ReportErrors allErrors = new ReportErrors(dataImporterContext.getJsonRowMapper());

        final CSVParser csvParser = CSVParser.parse(csv, StandardCharsets.UTF_8, csvFormat);
        final Iterator<CSVRecord> linesIterator = csvParser.iterator();
        final DataHeaderReader dataHeaderReader = new DataHeaderReader(
                dataImporterContext,
                dataImporterContext.getPublishContextBuilder()
        );
        final ImmutableList<String> columns = dataHeaderReader.readHeader(linesIterator);
        dataImporterContext.withPatternColumn();
        final Function<CSVRecord, Stream<RowWithReferenceDatum>> csvRecordToReferenceDatumFn = csvRecord ->
                csvReader.csvRecordToRowWithReferenceDatum(
                        columns,
                        csvRecord
                );
        final Stream<CSVRecord> csvRecordsStream = Streams.stream(csvParser);
        dataImporterContext.setTransformedLineCheckers(csvReader.buildLineCheckers(dataHeaderReader.constantValues().values()));
        Stream<DataValue> referenceValuesStream = csvRecordsStream
                .flatMap(csvRecordToReferenceDatumFn)
                .map(dataHeaderReader::addConstantsToRow)
                .map(dataTransformer::computeComputedColumns)
                //.parallel()
                .filter(rowWithReferenceDatum -> allErrors.canRegisterErrors())
                .map(rowWithReferenceDatum -> check(
                                dataTransformer::computeKeys,
                                recursionStrategy,
                                rowWithReferenceDatum,
                                dataImporterContext.getTransformedLineCheckers(),
                                dataImporterContext.getPublishContextBuilder()
                        )
                )
                .flatMap(List::stream)
                .map(referenceDatumAfterChecking -> {
                    allErrors.addAll(referenceDatumAfterChecking.errors());
                    return referenceDatumAfterChecking;
                })
                .filter(referenceDatumAfterChecking -> referenceDatumAfterChecking.errors().isEmpty())
                .map(dataTransformer::computeKeys)
                .map(storeHierarchicalKeyForConflictDetection)
                .filter(keysAndReferenceDatumAfterChecking -> {
                    final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
                    return encounteredHierarchicalKeysForConflictDetection.get(hierarchicalKey).size() == 1;
                })
                .map(keysAndReferenceDatumAfterChecking -> dataTransformer.toEntity(keysAndReferenceDatumAfterChecking, fileId, allErrors));

        storeAll(referenceValuesStream);
        final Set<CsvRowValidationCheckResult> hierarchicalKeysConflictErrors = csvReader.getHierarchicalKeysConflictErrors(encounteredHierarchicalKeysForConflictDetection);
        allErrors.addAll(hierarchicalKeysConflictErrors);
        if (!recursionStrategy.dataImporterContext().getMissingLines().isEmpty()) {
            Optional<ReferenceType> referenceType = dataImporterContext.getTransformedLineCheckers().stream()
                    .filter(lineChecker -> lineChecker.checkerDescription() instanceof ReferenceChecker referenceChecker && referenceChecker.isRecursive())
                    .map(LineChecker::fieldTypeForOne)
                    .filter(ReferenceType.class::isInstance)
                    .map(ReferenceType.class::cast)
                    .filter(rt -> rt.getRefType().equals(dataImporterContext.getRefType()))
                    .findAny();
            CheckerTarget target = referenceType.get().target();
            ReferenceValidationCheckResult error = ReferenceValidationCheckResult.error(
                    target,//target,
                    recursionStrategy.dataImporterContext().getMissingLines().keySet().toString(),//localRawValue,
                    target.getInternationalizedKey("missingrecursiveParentReference"),
                    ImmutableMap.of(
                            "target", target,//target.toHumanReadableString(),
                            "referenceValues", recursionStrategy.dataImporterContext().getReferenceValuesForSelfType()
                                    .keySet()
                                    .stream()
                                    .map(DataValue.LineIdentityColumnName::naturalKey)
                                    .collect(Collectors.toSet()),
                            "refType", recursionStrategy.dataImporterContext().getRefType(),
                            "values", recursionStrategy.dataImporterContext().getMissingLines().keySet()),
                    null);
            allErrors.add(new CsvRowValidationCheckResult(error, -1));
        }
        InvalidDatasetContentException.checkErrorsIsEmpty(allErrors);
    }

    /**
     * Étant donné les clé hiérarchiques qu'on a rencontré (normalement une seule par ligne), vérifie s'il y a des doublons et calcul des erreurs le cas échéant
     */
    private Set<CsvRowValidationCheckResult> getHierarchicalKeysConflictErrors(final SetMultimap<Ltree, Long> hierarchicalKeys) {
        return csvReader.getHierarchicalKeysConflictErrors(hierarchicalKeys);
    }

    private Function<Map.Entry<Ltree, Collection<Long>>, Stream<? extends CsvRowValidationCheckResult>> buildCsvRowValidationCheckResult() {
        return csvReader.buildCsvRowValidationCheckResult();
    }

    private Function<Long, List<CsvRowValidationCheckResult>> buildValidationsCheckResults(ImmutableSortedSet<Long> lineNumbers, Ltree conflictingHierarchicalKey) {
        return csvReader.buildValidationsCheckResults(lineNumbers, conflictingHierarchicalKey);
    }

    /**
     * Transforme une ligne du fichier CSV ({@link CSVRecord}) en {@link DataDatum}, plus simple à lire et y associe un n° de ligne.
     */
    private Stream<RowWithReferenceDatum> csvRecordToRowWithReferenceDatum(
            final ImmutableList<String> columns,
            final CSVRecord csvRecord) {
        return csvReader.csvRecordToRowWithReferenceDatum(columns, csvRecord);
    }

    private Stream<RowWithReferenceDatum> buildRowsWithPattern(List<PatternValueForHeader> patternValueForHeaders, DataDatum referenceDatum, int lineNumber, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
        return csvReader.buildRowsWithPattern(patternValueForHeaders, referenceDatum, lineNumber, refsLinkedTo);
    }

    /**
     * Transforme une ligne de données en une entité prête à être sauvée en base de données.
     */
    private DataValue toEntity(final KeysAndReferenceDatumAfterChecking keysAndReferenceDatumAfterChecking, final UUID fileId, ReportErrors errors) {

        return dataTransformer.toEntity(keysAndReferenceDatumAfterChecking, fileId, errors);
    }

    private fr.inra.oresing.domain.Authorization getLineAuthorization(DataDatum referenceDatum, long lineNumber, ReportErrors errors) {


        return configurationSi.getLineAuthorization(referenceDatum, lineNumber, errors);
    }

    private Map<String, List<Ltree>> buildRequiredAuthorizations(Authorization authorization, DataDatum referenceDatum) {
        return configurationSi.buildRequiredAuthorizations(authorization, referenceDatum);
    }

    private List<Ltree> getHierarchyOfHierarchicalkeys(ReferenceType referenceType) {
        return configurationSi.getHierarchyOfHierarchicalkeys(referenceType);
    }

    public void checkTimescopRangeInDatasetRange(LocalDateTimeRange timeScope, List<CsvRowValidationCheckResult> errors, BinaryFileDataset binaryFileDataset, long rowNumber) {
        if (binaryFileDataset == null) {
            return;
        }

        LocalDateTimeRange dateTimeRange;
        if (binaryFileDataset.getFrom() == null && binaryFileDataset.getTo() == null) {
            return;
        }
        LocalDateTime from = binaryFileDataset.getFrom() == null ?
                null :
                LocalDate.from(ISO_DATE_TIME_FORMATTER.parse(binaryFileDataset.getFrom())).atStartOfDay();
        ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
        builder.put("from", DISPLAY_DATE_FORMATTER_DDMMYYYY.format(Objects.requireNonNull(from)));
        LocalDateTime lowerBound = timeScope.getRange().hasLowerBound() ? timeScope.getRange().lowerEndpoint() : LocalDateTime.MIN;
        builder.put("value", DISPLAY_DATE_FORMATTER_DDMMYYYY.format(lowerBound));
        LocalDateTime to = binaryFileDataset.getTo() == null ?
                null :
                LocalDate.from(ISO_DATE_TIME_FORMATTER.parse(binaryFileDataset.getTo())).plusDays(1).atStartOfDay();
        dateTimeRange = LocalDateTimeRange.between(from, to);
        assert to != null;
        builder.put("to", DISPLAY_DATE_FORMATTER_DDMMYYYY.format(to));
        if (!dateTimeRange.getRange().encloses(timeScope.getRange())) {
            errors.add(new CsvRowValidationCheckResult(DefaultValidationCheckResult.error("timeRangeOutOfInterval", builder.build(), null), rowNumber));
        }
    }


    void storeAll(final Stream<DataValue> referenceValueStream) {
        try {
            storeAll.accept(referenceValueStream);
        } catch (SiOreIllegalArgumentException illegalArgumentException) {
            if (SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE.equals(illegalArgumentException.getMessage())) {
                throw SiOreIllegalArgumentException.noRightOnTableForDeposit(illegalArgumentException);
            }
            throw illegalArgumentException;
        }
    }

}