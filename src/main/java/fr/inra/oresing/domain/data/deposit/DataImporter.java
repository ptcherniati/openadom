package fr.inra.oresing.domain.data.deposit;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.primitives.Ints;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.GroovyExpressionChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.context.column.OneValueStaticPatternColumn;
import fr.inra.oresing.domain.data.deposit.validation.*;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.PatternValidationCheckResult;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.DataHeaderReader;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.internationalization.InternationalizationDisplay;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
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
    private final DataImporterContext dataImporterContext;
    private final RecursionStrategy recursionStrategy;
    private final Consumer<Stream<DataValue>> storeAll;

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

    public static Ltree getHierarchicalNodeFromNatural(final String naturalKey, final String refType) {
        final Ltree escapedNaturalKey = Ltree.fromUnescapedString(naturalKey);
        final Ltree type = Ltree.fromUnescapedString(refType);
        final String naturalKeyToString = "%1$s%3$s%2$s".formatted(type, escapedNaturalKey, HIERARCHICALKEY_SEPARATOR);
        return Ltree.fromSql(naturalKeyToString);
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
            final ImmutableSet<LineChecker> transformedLineCheckers,
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

            if (validationCheckResults != null && !validationCheckResults.isSuccess()) {
                boolean isLineCheckerRecusrsiveReference = Optional.ofNullable(lineChecker.checkerDescription())
                        .filter(ReferenceChecker.class::isInstance)
                        .map(ReferenceChecker.class::cast)
                        .stream().anyMatch(ReferenceChecker::isRecursive);
                boolean isErrorInvalidReferenceWithComponent = validationCheckResults.getValidations().stream()
                        .map(ValidationCheckResult::message)
                        .anyMatch("invalidReferenceWithComponent"::equals);
                if(isLineCheckerRecusrsiveReference && isErrorInvalidReferenceWithComponent){
                    return List.of();
                }
                List<ValidationCheckResult> vcrs = validationCheckResults.getValidations().stream().filter(ValidationCheckResult::isError).toList();
                vcrs.stream()
                        .map(vcr -> new CsvRowValidationCheckResult(vcr, rowWithReferenceDatum.lineNumber()))
                        .forEach(element -> {
                            allCheckerErrorsBuilder.add(element);
                        });
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
        return recursionStrategy.testHasParent(buildKey, recursionStrategy, referenceDatumAfterChecking);
    }

    private static CheckerValidationCheckResult testValues(RowWithReferenceDatum rowWithReferenceDatum, PublishContext.PublishContextBuilder publishContextBuilder, LineChecker lineChecker, Map<String, Object> context, DataDatum referenceDatumBeforeChecking) {
        switch (lineChecker.transformer()) {
            case LineChecker.LineTransformer.ChainTransformersLineTransformer transformers -> {
                for (LineChecker.LineTransformer transformer : transformers.transformers()) {
                    switch (transformer) {
                        case LineChecker.LineTransformer.TransformOneLineElementTransformer.GroovyExpressionOnOneLineElementTransformer groovyExpressionOnOneLineElementTransformer -> {
                            Set<String> groovyReferences = groovyExpressionOnOneLineElementTransformer.references();
                            context = publishContextBuilder.getGroovyContextForReferences(
                                    groovyReferences,
                                    new PublishContext.RowInfos(
                                            rowWithReferenceDatum.referenceDatum.values().values().stream()
                                                    .map(DataColumnValue::getValuesToCheck)
                                                    .map(FieldType::toString).toList(),
                                            rowWithReferenceDatum.lineNumber
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
                                    rowWithReferenceDatum.referenceDatum.values().values().stream().map(DataColumnValue::getValuesToCheck).map(FieldType::toString).toList(),
                                    rowWithReferenceDatum.lineNumber
                            )
                    );
                }
            }
        }
        //}
        final CheckerValidationCheckResult validationCheckResults = lineChecker.checkReference(referenceDatumBeforeChecking, context);
        return validationCheckResults;
    }

    private static boolean matchingTarget(RowWithReferenceDatum rowWithReferenceDatum, LineChecker lineChecker) {
        boolean matchingTarget = rowWithReferenceDatum.referenceDatum().values()
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
        if (matchingTarget) {
            return true;
        }
        return false;
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
        Consumer<KeysAndReferenceDatumAfterChecking> storeHierarchicalKeyForConflictDetection = keysAndReferenceDatumAfterChecking -> {
            final long lineNumber = keysAndReferenceDatumAfterChecking.getLineNumber();
            final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
            encounteredHierarchicalKeysForConflictDetection.put(hierarchicalKey, lineNumber);
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
                csvRecordToRowWithReferenceDatum(
                        columns,
                        csvRecord
                );
        final Stream<CSVRecord> csvRecordsStream = Streams.stream(csvParser);
        final Stream<RowWithReferenceDatum> recordStreamBeforePreloading =
                csvRecordsStream
                        .flatMap(csvRecordToReferenceDatumFn)
                        .map(dataHeaderReader::addConstantsToRow)
                        .map(this::computeComputedColumns);
        Stream<RowWithReferenceDatum> recordStream = recordStreamBeforePreloading;//recursionStrategy.firstPass(recordStreamBeforePreloading);
        dataImporterContext.setTransformedLineCheckers(buildLineCheckers(dataHeaderReader.constantValues().values()));
        Stream<DataValue> referenceValuesStream = recordStream
                //.parallel()
                .filter(rowWithReferenceDatum -> allErrors.canRegisterErrors())
                .map(rowWithReferenceDatum -> check(
                                this::computeKeys,
                                recursionStrategy,
                                rowWithReferenceDatum,
                                dataImporterContext.getTransformedLineCheckers(),
                                dataImporterContext.getPublishContextBuilder()
                        )
                )
                .flatMap(List::stream)
                .peek(referenceDatumAfterChecking -> allErrors.addAll(referenceDatumAfterChecking.errors()))
                .filter(referenceDatumAfterChecking -> referenceDatumAfterChecking.errors().isEmpty())
                .map(this::computeKeys)
                .peek(storeHierarchicalKeyForConflictDetection)
                .filter(keysAndReferenceDatumAfterChecking -> {
                    final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
                    return encounteredHierarchicalKeysForConflictDetection.get(hierarchicalKey).size() == 1;
                })
                .map(keysAndReferenceDatumAfterChecking -> toEntity(keysAndReferenceDatumAfterChecking, fileId, allErrors));
        /*if (dataImporterContext.isRecursive()) {
            referenceValuesStream = referenceValuesStream.sorted(Comparator.comparing(a -> a.getHierarchicalKey().getSql()));
        }*/
        //referenceValuesStream.sequential();
        storeAll(referenceValuesStream);

        final Set<CsvRowValidationCheckResult> hierarchicalKeysConflictErrors = getHierarchicalKeysConflictErrors(encounteredHierarchicalKeysForConflictDetection);
        allErrors.addAll(hierarchicalKeysConflictErrors);
        InvalidDatasetContentException.checkErrorsIsEmpty(allErrors);
    }


    private ImmutableSet<LineChecker> buildLineCheckers(Map<DataColumn, DataColumnValue> constantColumnsValues) {
        final ImmutableSet.Builder<LineChecker> linecheckersBuilder = ImmutableSet.builder();
        for (final LineChecker lineChecker : dataImporterContext.getLineCheckers()) {
            if (!dataImporterContext.existsColumn(lineChecker.target(), constantColumnsValues)) {
                continue;
            }
            if (recursionStrategy instanceof WithRecursion) {
                if (lineChecker.underlyingType() instanceof final ReferenceType referenceType) {
                    final Map<DataValue.LineIdentityColumnName, UUID> map2 = ((WithRecursion) recursionStrategy).afterPreloadReferenceUuids;
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
            }
            linecheckersBuilder.add(lineChecker);
        }
        return linecheckersBuilder.build();
    }

    private RowWithReferenceDatum computeComputedColumns(final RowWithReferenceDatum rowWithReferenceDatum) {
        final DataDatum rowWithDefaults = new DataDatum();
        final DataDatum rowWithValues = DataDatum.copyOf(rowWithReferenceDatum.referenceDatum());
        dataImporterContext.getColumns().stream()
                .filter(column -> column.getComputedValueUsage() != ComputedValueUsage.NOT_COMPUTED)
                .forEach(column -> {
                    final DataColumn referenceColumn = column.getReferenceColumn();
                    final Optional<DataColumnValue> evaluate = column.computeValue(rowWithReferenceDatum.referenceDatum());
                    evaluate.ifPresent(presentEvaluate -> {
                        if (column.getComputedValueUsage() == ComputedValueUsage.USE_COMPUTED_VALUE) {
                            rowWithValues.put(referenceColumn, presentEvaluate);
                        } else if (column.getComputedValueUsage() == ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE) {
                            rowWithDefaults.put(referenceColumn, presentEvaluate);
                        } else {
                            throw ComputedValueUsage.getError(column.getComputedValueUsage());
                        }
                    });
                });
        rowWithDefaults.putAll(rowWithValues);
        return new RowWithReferenceDatum(rowWithReferenceDatum.lineNumber(), rowWithReferenceDatum.patternColumnName(), rowWithDefaults, rowWithReferenceDatum.refsLinkedTo());
    }

    /**
     * Étant donné les clé hiérarchiques qu'on a rencontré (normalement une seule par ligne), vérifie s'il y a des doublons et calcul des erreurs le cas échéant
     */
    private Set<CsvRowValidationCheckResult> getHierarchicalKeysConflictErrors(final SetMultimap<Ltree, Long> hierarchicalKeys) {
        return hierarchicalKeys.asMap().entrySet().stream()
                .filter(entry -> {
                    final Collection<Long> lineNumbers = entry.getValue();
                    return lineNumbers.size() > 1;
                })
                .flatMap(entry -> {
                    final Ltree conflictingHierarchicalKey = entry.getKey();
                    final ImmutableSortedSet<Long> lineNumbers = ImmutableSortedSet.copyOf(entry.getValue());
                    final SortedSet<Long> conflictingLineNumbers = new TreeSet<>(lineNumbers);
                    final Long firstLineNumberToIgnore = conflictingLineNumbers.first();
                    conflictingLineNumbers.remove(firstLineNumberToIgnore);
                    return conflictingLineNumbers.stream()
                            .map(conflictingLineNumber -> {
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
                                        .collect(Collectors.toList());
                            })
                            .flatMap(Collection::stream);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Transforme une ligne du fichier CSV ({@link CSVRecord}) en {@link DataDatum}, plus simple à lire et y associe un n° de ligne.
     */
    private Stream<RowWithReferenceDatum> csvRecordToRowWithReferenceDatum(
            final ImmutableList<String> columns,
            final CSVRecord csvRecord) {
        final Iterator<String> currentHeader = columns.iterator();
        final DataDatum referenceDatum = new DataDatum();
        final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo = new HashMap<>();
        final List<PatternValueForHeader> patternValueForHeaders = new LinkedList<>();
        final int lineNumber = Ints.checkedCast(csvRecord.getRecordNumber());
        for (int i = 0; i < columns.size(); i++) {
            String[] values = csvRecord.values();
            final String cellContent = values.length > i ? values[i] : "";
            final String patternComponentName = currentHeader.next();
            if (dataImporterContext.pushValue(referenceDatum, patternComponentName, cellContent.trim(), refsLinkedTo)) {
                OneValueStaticPatternColumn expectedPatternColumn1 = dataImporterContext.getPatternColumnFactory().getExpectedPatternColumn(patternComponentName);
                List<String> adjacentValues = new LinkedList<>();
                for (int j = 0; j < (expectedPatternColumn1 == null ? 0 : expectedPatternColumn1.getAdjacentColumnsSize()); j++) {
                    i++;
                    adjacentValues.add(values[i]);
                    currentHeader.next();
                }
                patternValueForHeaders.add(new PatternValueForHeader(patternComponentName, cellContent.trim(), adjacentValues, refsLinkedTo));
            }
        }
        if (patternValueForHeaders.isEmpty()) {
            return Stream.of(new RowWithReferenceDatum(lineNumber, "", referenceDatum, ImmutableMap.copyOf(refsLinkedTo)));
        }
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

    /**
     * Associe à chaque ligne une clé naturelle (peut-être composite ?) et une clé hiérarchique.
     */
    private KeysAndReferenceDatumAfterChecking computeKeys(final ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        DataDatum referenceDatum = referenceDatumAfterChecking.referenceDatumAfterChecking();
        Ltree naturalKey = recursionStrategy.computeNaturalKey(referenceDatumAfterChecking);
        DataDatum datumForColumnsInKey = new DataDatum(
                referenceDatum.values().entrySet()
                        .stream().filter(entry ->
                                dataImporterContext.getNaturalKeyColumns().contains(entry.getKey().column())
                        )
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        Ltree hierarchicalKey = recursionStrategy.getHierarchicalKey(naturalKey, datumForColumnsInKey, referenceDatumAfterChecking);
        return new KeysAndReferenceDatumAfterChecking(
                referenceDatumAfterChecking,
                naturalKey,
                hierarchicalKey);
    }

    /**
     * Transforme une ligne de données en une entité prête à être sauvée en base de données.
     */
    private DataValue toEntity(final KeysAndReferenceDatumAfterChecking keysAndReferenceDatumAfterChecking, final UUID fileId, ReportErrors errors) {
        ReferenceDatumAfterChecking referenceDatumAfterChecking = keysAndReferenceDatumAfterChecking.referenceDatumAfterChecking();
        DataDatum referenceDatum = referenceDatumAfterChecking.referenceDatumAfterChecking();
        Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();

        DataValue e = new DataValue();
        Ltree naturalKey = keysAndReferenceDatumAfterChecking.naturalKey();
        recursionStrategy.getKnownId(naturalKey)
                .ifPresent(e::setId);
        referenceDatum.putAll(InternationalizationDisplay.getDisplaysName(dataImporterContext, referenceDatum));
        referenceDatum.putAll(InternationalizationDisplay.getDisplaysDescription(dataImporterContext, referenceDatum));

        final String patternColumnName = referenceDatumAfterChecking.patternColumnName();
        dataImporterContext.getIdForSameHierarchicalKeyInDatabase(hierarchicalKey)
                .ifPresent(e::setId);

        fr.inra.oresing.domain.Authorization lineAuthorization = getLineAuthorization(referenceDatum, referenceDatumAfterChecking.lineNumber(), errors);

        e.setPatternColumnName(patternColumnName);
        e.setBinaryFile(fileId);
        e.setReferenceType(dataImporterContext.getRefType());
        e.setHierarchicalKey(hierarchicalKey);
        e.setRefsLinkedTo(referenceDatumAfterChecking.refsLinkedTo());
        e.setAuthorization(lineAuthorization);
        e.setNaturalKey(naturalKey);
        e.setApplication(dataImporterContext.getApplication().getId());
        e.setRefValues(referenceDatum);
        return e;
    }

    private fr.inra.oresing.domain.Authorization getLineAuthorization(DataDatum referenceDatum, long lineNumber, ReportErrors errors) {
        final Authorization authorization = dataImporterContext.getAuthorization();
        if (authorization == null) {
            return new fr.inra.oresing.domain.Authorization();
        }

        BinaryFileDataset binaryFileDataset = Optional.ofNullable(dataImporterContext.getPublishContextBuilder())
                .map(PublishContext.PublishContextBuilder::build)
                .map(PublishContext::fileOrUUID)
                .map(FileOrUUID::binaryfiledataset)
                .orElse(null);

        Map<String, List<Ltree>> requiredAuthorizations = new LinkedHashMap<>();
        authorization.authorizationScope().stream()
                .map(AuthorizationScopeComponentData::component)
                .map(DataColumn::new)
                .map(referenceDatum::get)
                .map(DataColumnValue::toJsonForDatabase)
                .filter(ReferenceType.class::isInstance)
                .map(ReferenceType.class::cast)
                .forEach(referenceType -> {
                            List<Ltree> hierarchyOfHierarchicalkeys = getHierarchyOfHierarchicalkeys(referenceType);
                            requiredAuthorizations.put(referenceType.getRefType(), hierarchyOfHierarchicalkeys);
                        }
                );
        LocalDateTimeRange timeScope;
        DateType timeScopeDateLineChecker = authorization.timeScope() != null ?
                dataImporterContext.getLineCheckers().stream()
                        .filter(dateType -> dateType.target().column().equals(authorization.timeScope()))
                        .map(LineChecker::underlyingType)
                        .filter(DateType.class::isInstance)
                        .map(DateType.class::cast)
                        .collect(MoreCollectors.onlyElement()) :
                null;


        if (timeScopeDateLineChecker != null) {
            LocalDateTime value = ((DateType) referenceDatum.get(new DataColumn(authorization.timeScope())).getValuesToCheck()).getValue();
            timeScope = LocalDateTimeRange.parse(value, timeScopeDateLineChecker);
        } else {
            timeScope = LocalDateTimeRange.always();
        }
        checkTimescopRangeInDatasetRange(timeScope, errors, binaryFileDataset, lineNumber);
        return new fr.inra.oresing.domain.Authorization(
                requiredAuthorizations,
                timeScope
        );
    }

    private List<Ltree> getHierarchyOfHierarchicalkeys(ReferenceType referenceType) {
        List<ReferenceScope.NodeDescription> nodesForMenu = dataImporterContext.getNodesForMenu();
        List<Ltree> hierarchicalKeys = new LinkedList<>();
        ReferenceScope.NodeDescription referenceNode = nodesForMenu.stream()
                .filter(node -> node.node_type().equals(referenceType.getRefType()))
                .filter(node -> node.node_key().equals(referenceType.getHierarchicalKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("reference of type %s and hirarchicalkey %s doesn't exist".formatted(referenceType.getRefType(), referenceType.getHierarchicalKey())));
        hierarchicalKeys.add(referenceNode.node().node_key());
        while (referenceNode.parent_nk() != null) {
            ReferenceScope.NodeDescription finalReferenceNode = referenceNode;
            referenceNode = nodesForMenu.stream()
                    .filter(node -> node.node_type().equals(finalReferenceNode.parent_type()))
                    .filter(node -> node.node_nk().equals(finalReferenceNode.parent_nk()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("reference of type %s and hirarchicalkey %s doesn't exist".formatted(referenceType.getRefType(), referenceType.getHierarchicalKey())));
            hierarchicalKeys.add(referenceNode.node().node_key());
        }
        return hierarchicalKeys;
    }

    private void checkTimescopRangeInDatasetRange(LocalDateTimeRange timeScope, List<CsvRowValidationCheckResult> errors, BinaryFileDataset binaryFileDataset, long rowNumber) {
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

    /**
     * Représente les variations de l'algorithme d'import selon que le référentiel soit récursif ou non.
     */
    private interface RecursionStrategy {

        Ltree getHierarchicalKey(Ltree naturalKey, DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking);

        DataImporterContext dataImporterContext();

        default Ltree getParentNaturalKey(final String parentReference, final DataDatum referenceDatum) {
            if (Strings.isNullOrEmpty(parentReference)) {
                return null;
            }
            final Optional<HierarchicalNode> parentNode = dataImporterContext().getApplication().getConfiguration().findCompositeReferencesUsing(parentReference);
            if (parentNode.isPresent()) {
                final String parentColumn = parentNode.get().node().componentKey();
                if (referenceDatum.contains(new DataColumn(parentColumn))) {
                    final DataColumnValue referenceParentColumn = referenceDatum.get(new DataColumn(parentColumn));
                    String hierarchicParentNode = ((DataColumnSingleValue) referenceParentColumn).getValue().getValue().toString();
                    return Strings.isNullOrEmpty(hierarchicParentNode)?null:getHierarchicalNodeFromNatural(hierarchicParentNode, parentReference);
                }
            }
            return null;
        }

        Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking);

        Optional<UUID> getKnownId(Ltree naturalKey);

        Stream<RowWithReferenceDatum> firstPass(Stream<RowWithReferenceDatum> streamBeforePreloading);

        List<ReferenceDatumAfterChecking> testHasParent(Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey, RecursionStrategy recursionStrategy, ReferenceDatumAfterChecking referenceDatumAfterChecking);
    }

    public record PatternValueForHeader(String header, String cellContent, List<String> adjacentCellContent,
                                        Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
    }

    public record RowWithReferenceDatum(long lineNumber, String patternColumnName, DataDatum referenceDatum,
                                        Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
    }

    private record ReferenceDatumAfterChecking(long lineNumber, String patternColumnName,
                                               DataDatum referenceDatumBeforeChecking,
                                               DataDatum referenceDatumAfterChecking,
                                               Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo,
                                               ImmutableList<CsvRowValidationCheckResult> errors) {
    }

    private record KeysAndReferenceDatumAfterChecking(ReferenceDatumAfterChecking referenceDatumAfterChecking,
                                                      Ltree naturalKey, Ltree hierarchicalKey) {
        public long getLineNumber() {
            return referenceDatumAfterChecking.lineNumber();
        }
    }

    public record WithRecursion(DataImporterContext dataImporterContext,
                                Map<DataValue.LineIdentityColumnName, UUID> afterPreloadReferenceUuids,
                                Map<DataValue.LineIdentityColumnName, Ltree> parentReferenceMap) implements RecursionStrategy {
        /**
         * When we have the hierarchical key, we can recover the natural key as the leaf of the ltree.
         * In this case you must remove the reference to the data type "[^\\.][a-z][a-z]*K"
         */
        //public static final Function<Ltree, Ltree> fromNaturalKey = nk -> Ltree.fromSql(nk.getSql().replaceAll("[^\\.][a-z][a-z]*K", ""));

        public WithRecursion(final DataImporterContext dataImporterContext) {
            this(dataImporterContext, new HashMap<>(), new HashMap<>());
        }

        public static Function<Ltree, Ltree> toNaturalKey(String dataname) {
            return nk -> Ltree.fromSql("%sK%s".formatted(dataname, nk.getSql()));
        }

        /*
             to construct the natural key we concatenate the values of the key columns.
             - if the column is a repository type column, its value is an ltree. We take the last element of this ltree as part of the key
             - otherwise we escape the value
             - date values receive special treatment
          */
        @Override
        public Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
            Function<String, String> nullOrEmptyToNull = partialKey -> Strings.isNullOrEmpty(partialKey) ? Ltree.NULL_KEY : partialKey;
            Function<DataColumn, String> toEscapedValueFromColumnRegardingColumnIsReferenceType = dataColumn -> getEscapedValueFromColumnRegardingColumnIsReferenceType(dataColumn, referenceDatumAfterChecking.referenceDatumAfterChecking());
            String naturalKey = dataImporterContext().getNaturalKeyColumns().stream()
                    .map(DataColumn::new)
                    .map(toEscapedValueFromColumnRegardingColumnIsReferenceType)
                    .map(nullOrEmptyToNull)
                    .collect(Collectors.joining(DataImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));
            Preconditions.checkState(!naturalKey.isEmpty(), ExceptionMessage.NULL_NATURAL_KEY.toMessage(), referenceDatumAfterChecking.lineNumber(), String.join(" - ", dataImporterContext().getNaturalKeyColumnsImportHeaders()));
            return Ltree.fromSql(naturalKey/*.replaceAll("^%s__".formatted(Ltree.NULL_KEY) "")*/);
        }

        String getEscapedValueFromColumnRegardingColumnIsReferenceType(DataColumn dataColumn, DataDatum referenceDatum) {
            boolean isReferenceColumn = dataImporterContext().getLineCheckers().stream()
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
        public Optional<UUID> getKnownId(final Ltree naturalKey) {
            return afterPreloadReferenceUuids().entrySet().stream()
                    .filter(entry -> entry.getKey().naturalKey().equals(naturalKey))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }

        @Override
        public Ltree getHierarchicalKey(final Ltree naturalKey, final DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
            Optional<DataValue.LineIdentityColumnName> registerId = afterPreloadReferenceUuids().keySet()
                    .stream()
                    .filter(lineIdentityColumnName -> lineIdentityColumnName.naturalKey().equals(naturalKey))
                    .findFirst();
            if(registerId.isPresent()) {
                return registerId.get().hierarchicalKey();
            }
            final Ltree recursiveNodeHierarchicalKey = recursiveNodeHierarchicalKey(naturalKey);
            String parentType = dataImporterContext()
                    .getDataDescription()
                    .findParentDescription(dataImporterContext().getRefType())
                    .map(ComponentDescription::checker)
                    .map(ReferenceChecker.class::cast)
                    .map(ReferenceChecker::refType)
                    .orElse(null);
            Optional<Ltree> parentValue = dataImporterContext()
                    .getDataDescription()
                    .findParentDescription(dataImporterContext().getRefType())
                    .map(ComponentDescription::componentKey)
                    .map(DataColumn::new)
                    .map(referenceDatumAfterChecking.referenceDatumAfterChecking::get)
                    .map(DataColumnValue::toJsonForDatabase)
                    .map(Object::toString)
                    .map(Ltree::fromSql)
                    .map(toNaturalKey(parentType));
            Ltree parentRecursiveValue =
                    getParentNaturalKey(dataImporterContext().getRefType(), referenceDatumAfterChecking.referenceDatumBeforeChecking());
            Ltree hierarchicalKey = recursiveNodeHierarchicalKey;
            if(parentRecursiveValue!=null){
                hierarchicalKey = Ltree.join(parentRecursiveValue, hierarchicalKey);
            }
            if (parentValue.isPresent()) {
                hierarchicalKey = Ltree.join(parentValue.get(), hierarchicalKey);
            }
            return hierarchicalKey;
        }

        private Ltree recursiveNodeHierarchicalKey(final Ltree naturalKey) {
            return Ltree.fromSql("%sK%s".formatted(dataImporterContext().getRefType(), naturalKey));
        }

        @Override
        public Stream<RowWithReferenceDatum> firstPass(final Stream<RowWithReferenceDatum> streamBeforePreloading) {
            DataColumn columnToLookForParentKey = dataImporterContext().getColumnToLookForParentKey();
            final LineChecker lineChecker = dataImporterContext().getReferenceLineChecker();
            final ReferenceType referenceType = (ReferenceType) lineChecker.fieldTypeForOne();
            ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> beforePreloadReferenceUuids = referenceType.getReferenceValues();
            beforePreloadReferenceUuids.forEach((key, value) -> {
                final UUID uuid = value.stream().findFirst().orElse(null);
                afterPreloadReferenceUuids().putIfAbsent(key, uuid);
            });
            final ListMultimap<Ltree, Long> missingParentReferences = LinkedListMultimap.create();
            final List<RowWithReferenceDatum> collect = streamBeforePreloading
                    .map(rowWithReferenceDatum -> {
                        final DataDatum referenceDatum = rowWithReferenceDatum.referenceDatum();
                        final DataValue.LineIdentityColumnName naturalKey = computeIdentityKey(referenceDatum);
                        if (afterPreloadReferenceUuids().keySet().stream()
                                .map(DataValue.LineIdentityColumnName::naturalKey)
                                .noneMatch(nk -> naturalKey.naturalKey().equals(nk))) {
                            afterPreloadReferenceUuids().putIfAbsent(naturalKey, UUID.randomUUID());
                        }
                        DataColumnValue parentDataColumnValue = referenceDatum.get(columnToLookForParentKey);
                        switch (parentDataColumnValue) {
                            case DataColumnMultipleValue dataColumnMultipleValue ->
                                    ((Collection<? extends FieldType>) dataColumnMultipleValue.getValues().getValue())
                                            .stream()
                                            .map(FieldType::getValue)
                                            .map(Object::toString)
                                            .flatMap(multi -> Arrays.stream(multi.split(",")))
                                            .forEach(parentKey -> testIfMissingParentKey(rowWithReferenceDatum, parentKey, naturalKey, missingParentReferences));
                            case DataColumnSingleValue dataColumnSingleValue -> {
                                final String parentKey = dataColumnSingleValue.getValue().toString();
                                testIfMissingParentKey(rowWithReferenceDatum, parentKey, naturalKey, missingParentReferences);
                            }
                            default -> throw new IllegalStateException("Unexpected value: " + parentDataColumnValue);
                        }
                        missingParentReferences.removeAll(naturalKey.naturalKey());
                        return rowWithReferenceDatum;
                    })
                    .toList();
            Map<DataValue.LineIdentityColumnName, UUID> resolvedDuringPreloadReferenceUuids = afterPreloadReferenceUuids().entrySet().stream()
                    .map(entry -> buildEntryWithHierarchicalKey(entry))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            afterPreloadReferenceUuids().clear();
            afterPreloadReferenceUuids().putAll(resolvedDuringPreloadReferenceUuids);
            checkMissingParentReferencesIsEmpty(missingParentReferences);
            final ImmutableMap.Builder<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> builder = ImmutableMap.builder();
            afterPreloadReferenceUuids()
                    .forEach((ltree, uuid) -> builder.put(ltree, ImmutableSet.of(uuid)));
            referenceType.setReferenceValues(ImmutableMap.copyOf(builder.build()));

            return collect.stream();
        }

        @Override
        public List<ReferenceDatumAfterChecking> testHasParent(
                Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
                RecursionStrategy recursionStrategy,
                ReferenceDatumAfterChecking referenceDatumAfterChecking) {
            KeysAndReferenceDatumAfterChecking keys = buildKey.apply(referenceDatumAfterChecking);
            Optional<UUID> knownId = recursionStrategy.getKnownId(keys.naturalKey());
            DataValue.LineIdentityColumnName key = new DataValue.LineIdentityColumnName(keys.naturalKey(), keys.hierarchicalKey());
            if (knownId.isEmpty()) {
                afterPreloadReferenceUuids().put(key, UUID.randomUUID());
                knownId = recursionStrategy.getKnownId(keys.naturalKey());
            }
            final UUID uuid = knownId.orElse(null);
            ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues =
                    dataImporterContext().getTransformedLineCheckers().stream()
                    .filter(lineChecker -> {
                        if(lineChecker.checkerDescription() instanceof ReferenceChecker referenceChecker){
                            return referenceChecker.isRecursive();
                        }
                        return false;
                    })
                    .map(LineChecker::fieldTypeForOne)
                    .filter(ReferenceType.class::isInstance)
                    .map(ReferenceType.class::cast)
                    .findFirst()
                    .map(referenceType ->
                            ImmutableMap.<DataValue.LineIdentityColumnName, ImmutableSet<UUID>>builder()
                                    .putAll(referenceType.getReferenceValues())
                                    .put(key, ImmutableSet.of(uuid))
                                    .build()
                    )
                    .orElse(null);
            for (LineChecker lineChecker : dataImporterContext().getTransformedLineCheckers()){
                if(lineChecker.checkerDescription() instanceof ReferenceChecker referenceChecker && referenceChecker.isRecursive()){
                    ReferenceType fieldType = (ReferenceType) lineChecker.fieldTypeForOne();
                    fieldType.setReferenceValues(referenceValues);
                }
            }
            return List.of(referenceDatumAfterChecking);
        }

        private Map.Entry<DataValue.LineIdentityColumnName, UUID> buildEntryWithHierarchicalKey(Map.Entry<DataValue.LineIdentityColumnName, UUID> lineIdentityColumnNameUUIDEntry) {
            Ltree child = lineIdentityColumnNameUUIDEntry.getKey().naturalKey();
            Ltree currentChild = toNaturalKey(dataImporterContext().getRefType()).apply(child);
            Ltree hierarchicalKey = currentChild;
            Function<Ltree, Ltree> getParent = achild -> parentReferenceMap().entrySet().stream()
                    .filter(entry -> entry.getKey().naturalKey().equals(achild))
                    .map(Map.Entry::getValue)
                    .map(toNaturalKey(dataImporterContext().getRefType()))
                    .findFirst()
                    .orElse(null);
            Ltree parent = getParent.apply(child);
            while (parent != null) {
                hierarchicalKey = Ltree.join(parent, hierarchicalKey);
                currentChild = parent;
                parent = getParent.apply(currentChild);
            }
            DataValue.LineIdentityColumnName lineIdentityColumnName = new DataValue.LineIdentityColumnName(child, hierarchicalKey);
            return new AbstractMap.SimpleEntry<>(lineIdentityColumnName, lineIdentityColumnNameUUIDEntry.getValue());
        }

        private void testIfMissingParentKey(RowWithReferenceDatum rowWithReferenceDatum, String parentKeyAsString, DataValue.LineIdentityColumnName naturalKey, ListMultimap<Ltree, Long> missingParentReferences) {
            if (!Strings.isNullOrEmpty(parentKeyAsString)) {
                final Ltree parentKey = Ltree.fromUnescapedString(parentKeyAsString);
                parentReferenceMap().putIfAbsent(naturalKey, parentKey);
                if (afterPreloadReferenceUuids().keySet().stream()
                        .map(DataValue.LineIdentityColumnName::naturalKey)
                        .noneMatch(nk -> nk.equals(parentKey))) {
                    UUID uuid = UUID.randomUUID();
                    DataValue.LineIdentityColumnName key = new DataValue.LineIdentityColumnName(parentKey, parentKey);
                    if (afterPreloadReferenceUuids().keySet().stream()
                            .map(DataValue.LineIdentityColumnName::naturalKey)
                            .noneMatch(nk -> naturalKey.naturalKey().equals(key.naturalKey()))) {
                        afterPreloadReferenceUuids().putIfAbsent(key, uuid);//TODO
                    }
                    missingParentReferences.put(parentKey, rowWithReferenceDatum.lineNumber());
                }
            }
        }

        /**
         * Pour une ligne passée, calcule la clé naturelle composite de cette ligne.
         * <p>
         * Il s'agit d'aller lire les différentes colonnes qui composent la clé, de joindre le tout et de gérer
         * l'échappement.
         */
        private DataValue.LineIdentityColumnName computeIdentityKey(final DataDatum referenceDatum) {
            final String naturalKeyAsString = dataImporterContext.getKeyColumns().stream()
                    .map(referenceColumn -> {
                        final DataColumnValue referenceColumnValue = referenceDatum.get(referenceColumn);
                        Preconditions.checkState(referenceColumnValue instanceof DataColumnSingleValue, "dans le référentiel " + dataImporterContext.getRefType() + " la colonne " + referenceColumn + " est utilisée comme clé. Par conséquent, il ne peut pas y avoir une valeur multiple.");
                        return referenceColumnValue;
                    })
                    .map(DataColumnSingleValue.class::cast)
                    .map(DataColumnSingleValue::getValue)
                    .map(Object::toString)
                    .map(s -> Strings.isNullOrEmpty(s) ? Ltree.NULL_KEY : s)
                    .map(Ltree::escapeToLabel)
                    .collect(Collectors.joining(DataImporterContext.getCompositeNaturalKeyComponentsSeparator()));
            Ltree naturalKey = Ltree.fromSql(naturalKeyAsString);
            return new DataValue.LineIdentityColumnName(naturalKey, naturalKey); //TODO
        }

        /**
         * Si on a détecté des lignes qui font référence à un parent mais que celui-ci n'existe pas, on lève une exception
         *
         * @param missingParentReferences pour chaque parent manquant, les lignes du CSV où il est mentionné
         */
        private void checkMissingParentReferencesIsEmpty(final ListMultimap<Ltree, Long> missingParentReferences) {
            final ReportErrors reportErrors = new ReportErrors(dataImporterContext.getJsonRowMapper());
            missingParentReferences.entries().stream()
                    .map(entry -> {
                        final Ltree missingParentReference = entry.getKey();
                        final Long lineNumber = entry.getValue();
                        final ValidationCheckResult validationCheckResult =
                                new MissingParentLineValidationCheckResult(lineNumber, dataImporterContext.getRefType(), missingParentReference, afterPreloadReferenceUuids.keySet());
                        return validationCheckResult.getValidations().stream()
                                .map(validationCheckResult1 -> new CsvRowValidationCheckResult(validationCheckResult1, lineNumber))
                                .collect(Collectors.toList());
                    })
                    .flatMap(List::stream)
                    .forEach(reportErrors::add);
            InvalidDatasetContentException.checkErrorsIsEmpty(reportErrors);
        }
    }

    public record WithoutRecursion(DataImporterContext dataImporterContext) implements RecursionStrategy {

        @Override
        public Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
            Function<String, String> nullOrEmptyToNull = partialKey -> Strings.isNullOrEmpty(partialKey) ? Ltree.NULL_KEY : partialKey;

            final String naturalKeyAsString = dataImporterContext.getKeyColumns().stream()
                    .map(referenceColumn -> {
                        final DataColumnValue referenceColumnValue = referenceDatumAfterChecking.referenceDatumAfterChecking.get(referenceColumn);
                        Preconditions.checkState(referenceColumnValue instanceof DataColumnSingleValue, "dans le référentiel " + dataImporterContext.getRefType() + " la colonne " + referenceColumn + " est utilisée comme clé. Par conséquent, il ne peut pas y avoir une valeur multiple.");
                        return referenceColumnValue;
                    })
                    .map(DataColumnSingleValue.class::cast)
                    .map(DataColumnSingleValue::getValue)
                    .map(Object::toString)
                    .map(nullOrEmptyToNull)
                    .map(label -> label.matches(DateType.PATTERN_DATE_REGEXP_FIND_DATE) ? DateType.sorteableDateToFormattedDate(label).replaceAll("/", "_") : label)
                    .map(Ltree::escapeToLabel)
                    .collect(Collectors.joining(DataImporterContext.getCompositeNaturalKeyComponentsSeparator()));
            Preconditions.checkState(!naturalKeyAsString.isEmpty(), ExceptionMessage.NULL_NATURAL_KEY.toMessage(), referenceDatumAfterChecking.lineNumber(), String.join(" - ", dataImporterContext().getNaturalKeyColumnsImportHeaders()));
            return Ltree.fromSql(naturalKeyAsString);
        }

        @Override
        public Optional<UUID> getKnownId(final Ltree naturalKey) {
            return Optional.empty();
        }

        @Override
        public Ltree getHierarchicalKey(Ltree naturalKey, final DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
            Ltree naturalKey1 = getHierarchicalNodeFromNatural(naturalKey.getSql(), dataImporterContext.getRefType());
            return dataImporterContext.newHierarchicalKey(naturalKey1, referenceDatumAfterChecking.referenceDatumAfterChecking());
        }

        @Override
        public Stream<RowWithReferenceDatum> firstPass(final Stream<RowWithReferenceDatum> streamBeforePreloading) {
            return streamBeforePreloading;
        }

        @Override
        public List<ReferenceDatumAfterChecking> testHasParent(Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey, RecursionStrategy recursionStrategy, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
            return List.of(referenceDatumAfterChecking);
        }
    }
}