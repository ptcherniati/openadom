package fr.inra.oresing.domain.data.deposit;

import com.google.common.collect.*;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.csvreader.CsvReader;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.recursion.WithRecursion;
import fr.inra.oresing.domain.data.deposit.recursion.WithoutRecursion;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.transformation.DataTransformer;
import fr.inra.oresing.domain.data.deposit.transformation.DataValidator;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;
import fr.inra.oresing.domain.data.read.DataHeaderReader;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.persistence.JsonRowMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataImporter {
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    public static final DateTimeFormatter DISPLAY_DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");


    public static final String HIERARCHICALKEY_SEPARATOR = "K";


    private final DataImporterContext dataImporterContext;
    private final RecursionStrategy recursionStrategy;
    private final Consumer<Path> storeAll;
    private final DataTransformer dataTransformer;
    private final DataValidator dataValidator;
    private final CsvReader csvReader;

    public DataImporter(final DataImporterContext dataImporterContext, final Consumer<Path> storeAll) {
        super();
        this.dataImporterContext = dataImporterContext;
        this.storeAll = storeAll;
        if (dataImporterContext.isRecursive()) {
            recursionStrategy = new WithRecursion(dataImporterContext);
        } else {
            recursionStrategy = new WithoutRecursion(dataImporterContext);
        }
        this.dataTransformer = new DataTransformer(getDataImporterContext(), getRecursionStrategy());
        this.csvReader = new CsvReader(getDataImporterContext(), getRecursionStrategy());
        this.dataValidator = new DataValidator();
    }

    public RecursionStrategy getRecursionStrategy() {
        return recursionStrategy;
    }

    public DataImporterContext getDataImporterContext() {
        return dataImporterContext;
    }

    /**
     *
     */
    public void doImport(final FileBomResolver csv, final UUID fileId) throws IOException {
        final CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(dataImporterContext.getCsvSeparator()).setSkipHeaderRecord(true).get();

        SetMultimap<Ltree, Long> encounteredHierarchicalKeysForConflictDetection = HashMultimap.create();
        UnaryOperator<KeysAndReferenceDatumAfterChecking> storeHierarchicalKeyForConflictDetection = keysAndReferenceDatumAfterChecking -> {
            final long lineNumber = keysAndReferenceDatumAfterChecking.getLineNumber();
            final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
            encounteredHierarchicalKeysForConflictDetection.put(hierarchicalKey, lineNumber);
            return keysAndReferenceDatumAfterChecking;
        };
        ReportErrors allErrors = new ReportErrors(dataImporterContext.getJsonRowMapper());

        final CSVParser csvParser = CSVParser.parse(csv, StandardCharsets.UTF_8, csvFormat);
        final Iterator<CSVRecord> linesIterator = csvParser.iterator();
        final DataHeaderReader dataHeaderReader = new DataHeaderReader(dataImporterContext, dataImporterContext.getPublishContextBuilder());
        final ImmutableList<String> columns = dataHeaderReader.readHeader(linesIterator);
        dataImporterContext.withPatternColumn();
        final Function<CSVRecord, Stream<RowWithReferenceDatum>> csvRecordToReferenceDatumFn = csvRecord -> csvReader.csvRecordToRowWithReferenceDatum(columns, csvRecord);
        final Stream<CSVRecord> csvRecordsStream = Streams.stream(csvParser);
        dataImporterContext.setTransformedLineCheckers(csvReader.buildLineCheckers(dataHeaderReader.constantValues().values()));
        final Stream<RowWithReferenceDatum> baseStream = csvRecordsStream
                .flatMap(csvRecordToReferenceDatumFn)
                .map(dataHeaderReader::addConstantsToRow);

        Stream<RowWithReferenceDatum> checkStream =
                (dataImporterContext.isRecursive())
                        ? baseStream // séquentiel si recursion
                        : baseStream; //
        final Stream<DataValue> dataValueStream = baseStream.map(dataTransformer::computeComputedColumns)
                //.parallel()
                .filter(rowWithReferenceDatum -> allErrors.canRegisterErrors())
                .map(rowWithReferenceDatum -> dataValidator.check(dataTransformer::computeKeys, recursionStrategy, rowWithReferenceDatum, dataImporterContext.getTransformedLineCheckers(), dataImporterContext.getPublishContextBuilder())).flatMap(List::stream)
                .map(referenceDatumAfterChecking -> {
                    allErrors.addAll(referenceDatumAfterChecking.errors());
                    return referenceDatumAfterChecking;
                })
                .filter(referenceDatumAfterChecking -> referenceDatumAfterChecking.errors().isEmpty())
                .map(dataTransformer::computeKeys)
                //.sequential()
                .map(storeHierarchicalKeyForConflictDetection)
                .filter(keysAndReferenceDatumAfterChecking -> {
                    final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
                    return encounteredHierarchicalKeysForConflictDetection.get(hierarchicalKey).size() == 1;
                })
                .map(keysAndReferenceDatumAfterChecking -> dataTransformer.toEntity(keysAndReferenceDatumAfterChecking, fileId, allErrors));

        storeAll(dataValueStream, allErrors, encounteredHierarchicalKeysForConflictDetection);
    }

    void storeAll(final Stream<DataValue> referenceValueStream, ReportErrors allErrors, SetMultimap<Ltree, Long> encounteredHierarchicalKeysForConflictDetection) {
        Path csvFile;
        try {
            csvFile = Files.createTempFile("data_import_", ".csv");
            csvFile.toFile().deleteOnExit();

            BlockingQueue<String> lineQueue = new LinkedBlockingQueue<>(10000);
            final String POISON_PILL = "###END###";

            // 1. ÉCRITURE ASYNCHRONE (consommateur)
            CompletableFuture<Void> writerFuture = CompletableFuture.runAsync(() -> {
                try (BufferedWriter writer = Files.newBufferedWriter(csvFile, StandardCharsets.UTF_8)) {
                    while (true) {
                        String line = lineQueue.take();
                        if (POISON_PILL.equals(line)) {
                            break;
                        }
                        writer.write(line);
                        writer.newLine();
                    }
                    writer.flush();
                } catch (IOException | InterruptedException e) {
                    throw new CompletionException("Erreur d'écriture asynchrone", e);
                }
            });

            // 2. PRODUCTION SYNCHRONE (producteur)
            try {
                referenceValueStream.forEach(dataValue -> {
                    try {
                        String line = convertToCSVLine(dataValue);
                        if (!lineQueue.offer(line, 10, TimeUnit.SECONDS)) {
                            throw new RuntimeException("Timeout lors de l'écriture dans la queue");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interruption lors de l'écriture", e);
                    }
                });

                // 3. Signal de fin de production
                lineQueue.put(POISON_PILL);

                // 4. Attendre la fin de l'écriture
                writerFuture.join();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writerFuture.cancel(true);
                throw new RuntimeException("Interruption du traitement", e);
            }

            final Set<CsvRowValidationCheckResult> hierarchicalKeysConflictErrors = csvReader.getHierarchicalKeysConflictErrors(encounteredHierarchicalKeysForConflictDetection);
            allErrors.addAll(hierarchicalKeysConflictErrors);
            if (!recursionStrategy.dataImporterContext().getMissingLines().isEmpty()) {
                dataImporterContext.getTransformedLineCheckers().stream()
                        .filter(lineChecker -> lineChecker.checkerDescription() instanceof ReferenceChecker referenceChecker && referenceChecker.isRecursive())
                        .map(LineChecker::fieldTypeForOne)
                        .filter(ReferenceType.class::isInstance)
                        .map(ReferenceType.class::cast)
                        .filter(rt -> rt.getRefType().equals(dataImporterContext.getRefType()))
                        .findFirst()
                        .map(ReferenceType::target)
                        .ifPresent(target -> {
                            ReferenceValidationCheckResult error = ReferenceValidationCheckResult.error(target,//target,
                                    recursionStrategy.dataImporterContext().getMissingLines().keySet().toString(),//localRawValue,
                                    target.getInternationalizedKey("missingrecursiveParentReference"), ImmutableMap.of("target", target,//target.toHumanReadableString(),
                                            "referenceValues", recursionStrategy.dataImporterContext().getReferenceValuesForSelfType().keySet().stream().map(DataValue.LineIdentityColumnName::naturalKey).collect(Collectors.toSet()), "refType", recursionStrategy.dataImporterContext().getRefType(), "values", recursionStrategy.dataImporterContext().getMissingLines().keySet()), null);
                            allErrors.add(new CsvRowValidationCheckResult(error, -1));
                        });
            }
            InvalidDatasetContentException.checkErrorsIsEmpty(allErrors);
            storeAll.accept(csvFile);
        } catch (SiOreIllegalArgumentException illegalArgumentException) {
            if (SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE.equals(illegalArgumentException.getMessage())) {
                throw SiOreIllegalArgumentException.noRightOnTableForDeposit(illegalArgumentException);
            }
            throw illegalArgumentException;
        }  catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String convertToCSVLine(DataValue dataValue) {
        String json = new JsonRowMapper<>().toJson(dataValue);
        return fixTimescopeFormat(json);
    }

    private String fixTimescopeFormat(String json) {
        Pattern pattern = Pattern.compile(
                "\"timescope\":\"([\\[\\(])(\\\\\"(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\\\\")?,(\\\\\"(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\\\\")?([\\]\\)])\""
        );

        Matcher matcher = pattern.matcher(json);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String openBracket = matcher.group(1);   // [ ou (
            String date1 = matcher.group(3);         // date1 (sans les \")
            String date2 = matcher.group(5);         // date2 (sans les \")
            String closeBracket = matcher.group(6);  // ] ou )

            String cleanDate1 = (date1 != null) ? date1 : "";
            String cleanDate2 = (date2 != null) ? date2 : "";

            String replacement = "\"timescope\":\"" + openBracket +
                                 cleanDate1 + "," + cleanDate2 +
                                 closeBracket + "\"";

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

}