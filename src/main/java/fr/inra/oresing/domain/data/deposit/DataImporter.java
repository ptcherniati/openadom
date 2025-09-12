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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataImporter {
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    public static final DateTimeFormatter DISPLAY_DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");


    public static final String HIERARCHICALKEY_SEPARATOR = "K";


    private final DataImporterContext dataImporterContext;
    private final RecursionStrategy recursionStrategy;
    private final Consumer<Stream<DataValue>> storeAll;
    private final DataTransformer dataTransformer;
    private final DataValidator dataValidator;
    private final CsvReader csvReader;

    public DataImporter(final DataImporterContext dataImporterContext, final Consumer<Stream<DataValue>> storeAll) {
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
    public void doImport(final InputStream csv, final UUID fileId) throws IOException {
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
                        : baseStream.parallel(); //
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
                .sequential()
                .map(storeHierarchicalKeyForConflictDetection)
                .filter(keysAndReferenceDatumAfterChecking -> {
                    final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
                    return encounteredHierarchicalKeysForConflictDetection.get(hierarchicalKey).size() == 1;
                }).map(keysAndReferenceDatumAfterChecking -> dataTransformer.toEntity(keysAndReferenceDatumAfterChecking, fileId, allErrors));
        storeAll(dataValueStream);
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