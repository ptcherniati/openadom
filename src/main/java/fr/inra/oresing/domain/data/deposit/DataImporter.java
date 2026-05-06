package fr.inra.oresing.domain.data.deposit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.csvreader.CsvReader;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.recursion.WithRecursion;
import fr.inra.oresing.domain.data.deposit.recursion.WithoutRecursion;
import fr.inra.oresing.domain.data.deposit.transformation.DataTransformer;
import fr.inra.oresing.domain.data.deposit.transformation.DataValidator;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.workflow.cascade.progress.ImportProgressReporter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataImporter {
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    public static final DateTimeFormatter DISPLAY_DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");


    public static final String HIERARCHICALKEY_SEPARATOR = "K";

    /**
     * shared JsonRowMapper instance reused for the entire JVM lifetime.
     * The previous code did {@code new JsonRowMapper<>().toJson(...)} once
     * per CSV line ; on a 274 706-line import that allocated 274 k Jackson
     * ObjectMappers + 274 k JavaTimeModule + 274 k AfterburnerModule
     * registrations - the single biggest GC pressure observed in the audit.
     * JsonRowMapper is stateless and thread-safe ( wraps a configured
     * ObjectMapper that itself is thread-safe once configured ).
     */
    private static final JsonRowMapper<Object> SHARED_JSON_ROW_MAPPER = new JsonRowMapper<>();

    /** R-P2-3 : seuil min d'occurrences pour pré-calculer une valeur de référence. */
    private static final int PRECOMPUTE_CACHE_THRESHOLD = 2;

    private final AsynchroneFileImporterContext dataImporterContext;
    private final RecursionStrategy recursionStrategy;
    private final DataTransformer dataTransformer;
    private final DataValidator dataValidator;
    private final CsvReader csvReader;
    // R-P2-1/R-P2-3 : configurer le plafond du cache ReferenceType + pré-warmer dans prepareContextForDataTreatment.
    private final ImportProperties importProperties;

    public DataImporter(final AsynchroneFileImporterContext dataImporterContext) {
        this(dataImporterContext, null);
    }

    /**
     * Constructeur principal avec support du mode récursion ordonnée.
     *
     * @param dataImporterContext contexte de l'import
     * @param importProperties    configuration (peut être {@code null} → valeurs par défaut utilisées)
     */
    public DataImporter(final AsynchroneFileImporterContext dataImporterContext, final ImportProperties importProperties) {
        super();
        this.dataImporterContext = dataImporterContext;
        this.importProperties = importProperties;
        if (getDataImporterContext().isRecursive()) {
            boolean ordered = (importProperties != null && importProperties.isOrderedRecursionMode())
                    || getDataImporterContext().isOrderStrictTaggedOnRecursiveValidation();
            recursionStrategy = ordered
                    ? WithRecursion.ordered(dataImporterContext)
                    : new WithRecursion(dataImporterContext);
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

    public AsynchroneFileImporterContext getDataImporterContext() {
        return dataImporterContext;
    }

    /**
     * Backward-compatible overload : delegates to the 2-arg version with
     * {@code skipCsvReencoding=false} ( safe default - re-encodes via
     * CSVPrinter ) .
     */
    public Path prepareContextForDataTreatment(final FileBomResolver csv) throws IOException {
        return prepareContextForDataTreatment(csv, false);
    }

    /**
     * Configures the importer context from the CSV headers ( always
     * required ) and writes the data body to a temp file used by the
     * cascade pipeline as input .
     *
     * <p>Two modes of body writing :
     * <ul>
     *   <li>{@code skipCsvReencoding = false} ( default ) : re-encode every
     *       record via {@link CSVPrinter} . Handles cells containing the
     *       delimiter , double quotes , or embedded newlines correctly by
     *       quoting them on output . Slower but safe .</li>
     *   <li>{@code skipCsvReencoding = true} : assume the input CSV is
     *       already free of multi-line cells / unescaped quotes . Iterate
     *       records , join values with the delimiter , write the line .
     *       Faster ( 1-3 s saved on a 274k-line file ) but unsafe if the
     *       assumption breaks ; an importer using this flag MUST validate
     *       the source format upstream .</li>
     * </ul>
     *
     * <p>The header consumption + line-checker setup ( required by the
     * downstream {@link DataImporter#doDataTreatment} pipeline ) runs in
     * BOTH modes — the flag only affects the body-write phase .
     *
     * @param csv                input CSV ( header + data rows )
     * @param skipCsvReencoding  see above
     * @return path to the headerless data temp file consumed by cascade
     */
    public Path prepareContextForDataTreatment(final FileBomResolver csv, final boolean skipCsvReencoding) throws IOException {
        final String dataForChunkedTreatment = getDataImporterContext().isRecursive() ? "notSplitableDataForChunkedTreatment_" : "dataForChunkedTreatment_";
        Path tempFile = Files.createTempFile(dataForChunkedTreatment, ".tmp");
        tempFile.toFile().deleteOnExit();
        final CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(getDataImporterContext().contextConstants().dataConfiguration().separator())
                .setSkipHeaderRecord(true)
                .get();
        final CSVParser csvParser = CSVParser.parse(csv, StandardCharsets.UTF_8, csvFormat);
        final Iterator<CSVRecord> linesIterator = csvParser.iterator();

        // === Setup context ( ALWAYS executed ) ===
        getDataImporterContext().dataHeaderReader().readHeader(linesIterator);
        getDataImporterContext().withPatternColumn();
        getDataImporterContext().setTransformedLineCheckers(getRecursionStrategy(),
                csvReader.buildLineCheckers(getDataImporterContext().dataHeaderReader().constantValues().values()));

        // R-P2-1/R-P2-2 : configurer le plafond de cache sur tous les ReferenceType
        // et construire la liste (colName → ReferenceType) pour le pré-warmer.
        Map<String, ReferenceType> refTypeByColumnName = new HashMap<>();
        if (importProperties != null) {
            int maxCache = importProperties.getReferenceCacheMaxEntries();
            getDataImporterContext().transformedLineCheckers().stream()
                    .map(LineChecker::fieldTypeForOne)
                    .filter(ReferenceType.class::isInstance)
                    .map(ReferenceType.class::cast)
                    .forEach(rt -> rt.setMaxCacheEntries(maxCache));
        }
        getDataImporterContext().transformedLineCheckers().forEach(lc -> {
            if (lc.fieldTypeForOne() instanceof ReferenceType rt) {
                String col = lc.target().column();
                refTypeByColumnName.put(col, rt);
            }
        });

        // === Body write ===
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            if (skipCsvReencoding) {
                // FAST path : trust the input format , avoid CSVPrinter overhead .
                // Saves ~1-3 s on a 274k-line file . Hazardous if cells contain
                // the delimiter / quotes / newlines : the body lines would be
                // unparseable downstream . Use only when the source is known
                // clean ( machine-generated exports , validated upstream ) .
                final char sep = getDataImporterContext().contextConstants().dataConfiguration().separator();
                while (linesIterator.hasNext()) {
                    CSVRecord r = linesIterator.next();
                    int n = r.size();
                    for (int i = 0; i < n; i++) {
                        if (i > 0) writer.write(sep);
                        writer.write(r.get(i));
                    }
                    writer.newLine();
                }
            } else {
                // SAFE path : re-encode via CSVPrinter ( default ) .
                // #499 - quotes cells containing the delimiter , double
                // quotes , or embedded newlines so the chunker downstream
                // can split lines safely .
                try (CSVPrinter printer = new CSVPrinter(writer, csvFormat)) {
                    while (linesIterator.hasNext()) {
                        printer.printRecord(linesIterator.next());
                    }
                }
            }
        }
        // R-P2-3 : pré-warmer sélectif — compter les fréquences de chaque valeur
        // par colonne référence, puis pré-calculer les valeurs vues ≥ THRESHOLD fois.
        // Un seul passage sur le fichier temp (déjà en cache OS ou SSD).
        if (!refTypeByColumnName.isEmpty()) {
            prewarmReferenceCache(tempFile, refTypeByColumnName);
        }

        return tempFile;
    }

    /**
     * Traite un chunk CSV : valide chaque ligne, applique les transformations
     * metier et ecrit le CSV processé sur disque.
     *
     * <p>Phase 1e (#62) : signature affranchie des types file-processor.
     * Les anciennes dependances (SharedContext, ChunkInfo, WorkflowProperties,
     * WorkflowLifecycleManager) sont remplacees par des primitives + un
     * callback {@link ImportProgressReporter}. La logique metier interne
     * (validation, computeKeys, dedup, toEntity) est strictement inchangee.
     */
    public void doDataTreatment(
            final Path                    processedPath,
            final Path                    inputChunkPath,
            final String                  correlationId,
            final int                     chunkNumber,
            final int                     chunkSizeLines,
            final int                     progressBatchSize,
            final AtomicInteger           dataLinesProcessed,
            final AtomicInteger           successfulLinesBatch,
            final ImportProgressReporter  progressReporter) throws IOException {

        try (InputStream csv = Files.newInputStream(inputChunkPath);
             BufferedWriter writer = Files.newBufferedWriter(processedPath, StandardCharsets.UTF_8)) {
            final CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setDelimiter(getDataImporterContext().contextConstants().dataConfiguration().separator()).setSkipHeaderRecord(true).get();
            final CSVParser csvParser = CSVParser.parse(csv, StandardCharsets.UTF_8, csvFormat);
            final Stream<CSVRecord> csvRecordStream = Streams.stream(csvParser);
            final Integer firstRowLine = getDataImporterContext().contextConstants().dataConfiguration().firstRowLine();
            final Function<CSVRecord, Stream<RowWithReferenceDatum>> csvRecordToReferenceDatumFn = csvRecord -> csvReader.csvRecordToRowWithReferenceDatum((ImmutableList<String>) getDataImporterContext().publishContextBuilder().headerRow, csvRecord, firstRowLine, chunkNumber, chunkSizeLines);
            // Self-reference contract : addKnownIdToReferenceValues mutates
            // the LineChecker's ReferenceType.referenceValues map after each
            // parent row is processed. Subsequent child rows in the same
            // chunk look up parent UUIDs via that map. Any per-chunk clone
            // breaks this contract : children can't see UUIDs registered
            // on the original LineChecker , validation fails silently for
            // self-referencing references , rows are filtered at line 243
            // ( errors().isEmpty() ) , only parents land in DB . Use the
            // shared transformedLineCheckers() set ( also matches the
            // pre-cascade-1.8.0 behavior that was known-working ).
            csvRecordStream
                    .map(csvRecord -> {
                        dataLinesProcessed.getAndIncrement();
                        successfulLinesBatch.getAndIncrement();
                        final int delta = successfulLinesBatch.get();
                        if (delta >= progressBatchSize) {
                            progressReporter.onLinesProcessed(correlationId, delta);
                            successfulLinesBatch.set(0);
                        }
                        return csvRecord;
                    })
                    .flatMap(csvRecordToReferenceDatumFn)
                    .map(getDataImporterContext().dataHeaderReader()::addConstantsToRow)
                    .map(dataTransformer::computeComputedColumns)
                    .takeWhile(rowWithReferenceDatum -> getDataImporterContext().allErrors().canRegisterErrors())
                    .map(rowWithReferenceDatum -> dataValidator.check(
                                    dataTransformer::computeKeys,
                                    recursionStrategy, rowWithReferenceDatum,
                                    getDataImporterContext().transformedLineCheckers(),
                                    getDataImporterContext().publishContextBuilder()
                            )
                    ).flatMap(List::stream)
                    .map(referenceDatumAfterChecking -> {
                        getDataImporterContext().allErrors().addAll(referenceDatumAfterChecking.errors());
                        return referenceDatumAfterChecking;
                    })
                    .filter(referenceDatumAfterChecking -> referenceDatumAfterChecking.errors().isEmpty())
                    .map(dataTransformer::computeKeys)
                    .map(getDataImporterContext()::storeHierarchicalKeyForConflictDetection)
                    .filter(keysAndReferenceDatumAfterChecking -> {
                        final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
                        return getDataImporterContext().encounteredHierarchicalKeysForConflictDetection().get(hierarchicalKey).size() == 1;
                    })
                    .map(keysAndReferenceDatumAfterChecking -> dataTransformer.toEntity(keysAndReferenceDatumAfterChecking, getDataImporterContext().publishContextBuilder().fileOrUUID.fileid(), getDataImporterContext().allErrors()))
                    .map(this::convertToCSVLine)
                    .forEach(csvLine -> {
                        try {
                            writer.write(csvLine);
                            writer.newLine();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            writer.flush();
        }

    }

    public void treatErrors() {
        try {
            final Set<CsvRowValidationCheckResult> hierarchicalKeysConflictErrors = csvReader.getHierarchicalKeysConflictErrors(getDataImporterContext().encounteredHierarchicalKeysForConflictDetection());
            getDataImporterContext().allErrors().addAll(hierarchicalKeysConflictErrors);

            if (!recursionStrategy.dataImporterContext().missingParentLine().isEmpty()) {
                getDataImporterContext().transformedLineCheckers().stream()
                        .filter(lineChecker -> lineChecker.checkerDescription() instanceof ReferenceChecker referenceChecker && referenceChecker.isRecursive())
                        .map(LineChecker::fieldTypeForOne)
                        .filter(ReferenceType.class::isInstance)
                        .map(ReferenceType.class::cast)
                        .filter(rt -> rt.getRefType().equals(getDataImporterContext().contextConstants().refType()))
                        .findFirst()
                        .map(ReferenceType::target)
                        .ifPresent(target -> {
                            ReferenceValidationCheckResult error = ReferenceValidationCheckResult.error(target,//target,
                                    recursionStrategy.dataImporterContext().missingParentLine().keySet().toString(),//localRawValue,
                                    target.getInternationalizedKey("missingrecursiveParentReference"), ImmutableMap.of("target", target,//target.toHumanReadableString(),
                                            "referenceValues", recursionStrategy.getReferenceValuesForSelfType().keySet().stream().map(DataValue.LineIdentityColumnName::naturalKey).collect(Collectors.toSet()), "refType", recursionStrategy.dataImporterContext().contextConstants().refType(), "values", recursionStrategy.dataImporterContext().missingParentLine().keySet()), null);
                            getDataImporterContext().allErrors().add(new CsvRowValidationCheckResult(error, -1));
                        });
            }
            InvalidDatasetContentException.checkErrorsIsEmpty(getDataImporterContext().allErrors());
        } catch (SiOreIllegalArgumentException illegalArgumentException) {
            if (SiOreIllegalArgumentException.NO_RIGHT_ON_TABLE.equals(illegalArgumentException.getMessage())) {
                throw SiOreIllegalArgumentException.noRightOnTableForDeposit(illegalArgumentException);
            }
            throw illegalArgumentException;
        }
    }

    private String convertToCSVLine(DataValue dataValue) {
        // reuse the shared JsonRowMapper instead of allocating one per row.
        String json = SHARED_JSON_ROW_MAPPER.toJson(dataValue);
        return fixTimescopeFormat(json);
    }

    private String fixTimescopeFormat(String json) {
        final String marker = "\"timescope\":\"";
        int pos = json.indexOf(marker);
        if (pos < 0) return json;
        int start = pos + marker.length();
        // Trouver le " de fermeture (non précédé d'un \)
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            boolean escaped = end > start && json.charAt(end - 1) == '\\';
            if (c == '"' && !escaped) break;
            end++;
        }
        if (end >= json.length()) return json;
        // Supprimer les guillemets échappés dans la valeur timescope
        String cleaned = json.substring(start, end).replace("\\\"", "");
        return json.substring(0, start) + cleaned + json.substring(end);
    }

    /**
     * R-P2-3 — Pré-warmer sélectif par fréquence.
     *
     * <p>Scanne le fichier temp CSV une fois pour compter les occurrences de chaque
     * valeur brute dans les colonnes qui ont un {@link ReferenceType} associé. Toute
     * valeur vue ≥ {@link #PRECOMPUTE_CACHE_THRESHOLD} fois est pré-calculée en appelant
     * {@link ReferenceType#check} ce qui l'injecte dans {@code precomputedResults}
     * (via le chemin seenOnce → precomputedResults décrit dans R-P2-2).
     *
     * <p>Ce pre-calcul est effectué avant le démarrage des workers Cascade, de sorte
     * que le premier vrai appel à {@code check} pour ces valeurs trouve directement
     * le résultat dans le cache.
     *
     * @param tempFile           fichier CSV headerless écrit par prepareContextForDataTreatment
     * @param refTypeByColumnName mapping colonne → ReferenceType (déjà initialisé)
     */
    private void prewarmReferenceCache(Path tempFile,
                                        Map<String, ReferenceType> refTypeByColumnName) {
        if (refTypeByColumnName.isEmpty()) return;

        final char sep = getDataImporterContext().contextConstants().dataConfiguration().separator();
        final CSVFormat fmt = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(sep).setSkipHeaderRecord(false).get();

        // Récupérer les en-têtes de colonnes pour mapper index → colonne
        @SuppressWarnings("unchecked")
        ImmutableList<String> headerRow = (ImmutableList<String>)
                getDataImporterContext().publishContextBuilder().headerRow;
        if (headerRow == null || headerRow.isEmpty()) return;

        // Compter les occurrences : colIndex → valeur → compte
        Map<Integer, Map<String, Integer>> freq = new HashMap<>();
        Map<Integer, ReferenceType> refTypeByColIndex = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String col = headerRow.get(i);
            ReferenceType rt = refTypeByColumnName.get(col);
            if (rt != null) {
                freq.put(i, new HashMap<>());
                refTypeByColIndex.put(i, rt);
            }
        }
        if (refTypeByColIndex.isEmpty()) return;

        try (InputStream is = Files.newInputStream(tempFile)) {
            CSVParser parser = CSVParser.parse(is, StandardCharsets.UTF_8, fmt);
            for (CSVRecord record : parser) {
                for (Map.Entry<Integer, ReferenceType> entry : refTypeByColIndex.entrySet()) {
                    int colIdx = entry.getKey();
                    if (colIdx < record.size()) {
                        String val = record.get(colIdx);
                        // Ignorer les valeurs vides : Ltree.escapeToLabel("") lève nullLabel
                        if (val != null && !val.isBlank()) {
                            freq.get(colIdx).merge(val, 1, Integer::sum);
                        }
                    }
                }
            }
        } catch (IOException e) {
            // Non fatal : le pré-warmer est une optimisation, pas une exigence
            return;
        }

        // Pré-calculer les valeurs fréquentes : appeler check() 2 fois pour
        // déclencher la promotion seenOnce → precomputedResults (logique R-P2-2)
        for (Map.Entry<Integer, Map<String, Integer>> colEntry : freq.entrySet()) {
            ReferenceType rt = refTypeByColIndex.get(colEntry.getKey());
            LineChecker<?> lc = getDataImporterContext().transformedLineCheckers().stream()
                    .filter(checker -> checker.fieldTypeForOne() == rt)
                    .findFirst().orElse(null);
            if (lc == null) continue;

            for (Map.Entry<String, Integer> valEntry : colEntry.getValue().entrySet()) {
                if (valEntry.getValue() >= PRECOMPUTE_CACHE_THRESHOLD) {
                    try {
                        // Deux appels : 1er → seenOnce, 2e → precomputedResults
                        rt.check(valEntry.getKey(), lc);
                        rt.check(valEntry.getKey(), lc);
                    } catch (Exception e) {
                        // Non fatal : le pré-warmer est une optimisation, pas une exigence
                        // Ex. valeur invalide pour Ltree (ne produira qu'un miss de cache)
                    }
                }
            }
        }
    }

}