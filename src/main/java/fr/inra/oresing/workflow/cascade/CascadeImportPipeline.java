package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.cleanup.WorkflowTempCleanup;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import fr.inra.oresing.workflow.cascade.progress.ImportProgressReporter;
import fr.inrae.ore.cascade.api.workflow.builder.WorkflowBuilder;
import fr.inrae.ore.cascade.model.workflow.ProcessingStatus;
import fr.inrae.ore.cascade.model.workflow.Workflow;
import fr.inrae.ore.cascade.model.workflow.WorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Orchestration de l'import CSV → PostgreSQL via la bibliothèque cascade.
 *
 * <p>Phase 1e (#62) : aucune dependance file-processor. Le pipeline est
 * compose de trois briques cascade :
 * <ul>
 *   <li>{@link FileChunkSource} - decoupe le fichier en chunks CSV sur disque</li>
 *   <li>{@link DataImporterTransformation} - delegue le traitement metier
 *       au {@link DataImporter} existant</li>
 *   <li>{@link MergingFileSink} - concatene les chunks traites en un seul CSV</li>
 * </ul>
 *
 * <p>Le chargement final en base reste assure par
 * {@link DataRepository#storeAll}. La progression est tracee via
 * {@link ImportProgressReporter}, le cleanup via {@link WorkflowTempCleanup}.
 * Le rate-limit n'est plus actif sur cet endpoint en phase 1e ; il sera
 * reintroduit via cascade {@code UserRateLimiter} si necessaire.
 */
@Slf4j
@Service
public class CascadeImportPipeline {

    private final ImportProperties        importProperties;
    private final ImportProgressReporter  progressReporter;
    private final WorkflowTempCleanup     tempCleanup;
    private final ImportRateLimiter       importRateLimiter;
    private final OpenadomMetrics         metrics;
    private final WorkflowLogWriter       logWriter;
    private final AuthenticationService   authenticationService;

    public CascadeImportPipeline(
            ImportProperties       importProperties,
            ImportProgressReporter progressReporter,
            WorkflowTempCleanup    tempCleanup,
            ImportRateLimiter      importRateLimiter,
            OpenadomMetrics        metrics,
            WorkflowLogWriter      logWriter,
            AuthenticationService  authenticationService) {
        this.importProperties      = importProperties;
        this.progressReporter      = progressReporter;
        this.tempCleanup           = tempCleanup;
        this.importRateLimiter     = importRateLimiter;
        this.metrics               = metrics;
        this.logWriter             = logWriter;
        this.authenticationService = authenticationService;
    }

    /**
     * Lance un import pour un fichier CSV sans en-tete deja prepare par
     * {@link DataImporter#prepareContextForDataTreatment}.
     *
     * @param applicationName nom de l'application ( tag metrics )
     * @param dataType        type de reference/data ( tag metrics )
     */
    public void execute(
            DataImporter   dataImporter,
            DataRepository referenceValueRepository,
            Path           headerlessCsv,
            String         userId,
            String         applicationName,
            String         dataType) {

        if (!Files.exists(headerlessCsv)) {
            throw new IllegalArgumentException("Input file does not exist: " + headerlessCsv);
        }

        // Quota par utilisateur : 429 Too Many Requests immediat si trop
        // d'imports simultanes. Le slot est libere dans le finally.
        importRateLimiter.acquireOrThrow(userId);

        // Resolu une seule fois ici ( thread HTTP ) pour etre disponible dans
        // tous les chemins de logImportEvent , y compris ceux rattrapant
        // une exception apres un basculement de contexte.
        final String userLogin = resolveCurrentLogin();

        final Instant startedAt = Instant.now();
        final String correlationId = UUID.randomUUID().toString();
        final String resourceName = headerlessCsv.getFileName().toString();
        long fileSizeBytes = 0L;
        try {
            fileSizeBytes = Files.size(headerlessCsv);
        } catch (IOException ignored) {
            // metrics best-effort uniquement
        }

        try {
            final Path uploadedPath;
            try {
                uploadedPath = uploadFile(headerlessCsv, userId, correlationId);
            } catch (IOException e) {
                log.error("Erreur lors de la preparation de l'upload pour {} : {}", userId, e.getMessage(), e);
                Duration failDuration = Duration.between(startedAt, Instant.now());
                metrics.recordImportCompleted(applicationName, dataType, WorkflowLogEntry.STATUS_FAILED,
                        failDuration, 0L, 0L, 0, fileSizeBytes);
                logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                        startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                        0L, 0L, 0, fileSizeBytes, List.of(), e.getMessage());
                throw new UnsupportedOperationException("Failed to prepare workflow", e);
            }

            final int chunkSizeLines = importProperties.getChunkSizeLines();
            final int parallelism    = importProperties.getParallelism();
            final int maxErrors      = importProperties.getMaxErrorsThreshold();

            final Path chunksDir    = Paths.get(importProperties.getChunksTempDir(), userId, correlationId);
            final Path processedDir = Paths.get(importProperties.getProcessedTempDir(), correlationId);
            final Path mergedPath   = Paths.get(System.getProperty("java.io.tmpdir"),
                                                "openadom-import-" + correlationId + ".csv");

            FileChunkSource source = new FileChunkSource(uploadedPath, chunkSizeLines, chunksDir);

            DataImporterTransformation transformation = new DataImporterTransformation(
                    dataImporter,
                    importProperties,
                    progressReporter,
                    processedDir,
                    correlationId);

            MergingFileSink sink = new MergingFileSink(mergedPath);

            log.info("[{}] Demarrage import : user={}, file={}, chunkSize={}, parallelism={}, maxErrors={}",
                    correlationId, userId, uploadedPath.getFileName(), chunkSizeLines, parallelism, maxErrors);

            Workflow workflow = WorkflowBuilder.create()
                    .forUser(userId)
                    .from(source)
                    .transform(transformation)
                    .to(sink)
                    .withCorrelationId(correlationId)
                    .withParallelism(parallelism)
                    .withMaxErrors(maxErrors)
                    .build();

            try {
                WorkflowResult result = workflow.execute();
                if (result.status() == ProcessingStatus.FAILED) {
                    String firstError = result.errors().isEmpty()
                            ? result.fatalError().map(Throwable::getMessage).orElse("unknown error")
                            : result.errors().get(0);
                    log.error("[{}] Workflow cascade en echec : {}", correlationId, firstError);
                    Duration failDuration = Duration.between(startedAt, Instant.now());
                    metrics.recordImportCompleted(applicationName, dataType, WorkflowLogEntry.STATUS_FAILED,
                            failDuration, result.recordsProcessed(), result.recordsFailed(),
                            result.chunksProcessed(), fileSizeBytes);
                    logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                            startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                            result.recordsProcessed(), result.recordsFailed(),
                            result.chunksProcessed(), fileSizeBytes,
                            result.errors(), firstError);
                    tempCleanup.cleanup(chunksDir, processedDir, mergedPath, uploadedPath);
                    throw new UnsupportedOperationException("Import workflow failed: " + firstError);
                }

                log.info("[{}] Workflow cascade termine : processed={}, chunks={}, duration={}",
                        correlationId, result.recordsProcessed(), result.chunksProcessed(), result.duration());

                dataImporter.treatErrors();
                referenceValueRepository.storeAll(sink.getMergedPath());

                Duration okDuration = Duration.between(startedAt, Instant.now());
                metrics.recordImportCompleted(applicationName, dataType, WorkflowLogEntry.STATUS_COMPLETED,
                        okDuration, result.recordsProcessed(), result.recordsFailed(),
                        result.chunksProcessed(), fileSizeBytes);
                logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                        startedAt, okDuration, WorkflowLogEntry.STATUS_COMPLETED,
                        result.recordsProcessed(), result.recordsFailed(),
                        result.chunksProcessed(), fileSizeBytes,
                        result.errors(), null);

                tempCleanup.cleanup(chunksDir, processedDir, mergedPath, uploadedPath);

            } catch (RuntimeException e) {
                // Evite un double-enregistrement quand l'exception vient du
                // bloc FAILED deja metric au-dessus.
                if (!(e instanceof UnsupportedOperationException
                        && e.getMessage() != null
                        && e.getMessage().startsWith("Import workflow failed"))) {
                    Duration failDuration = Duration.between(startedAt, Instant.now());
                    metrics.recordImportCompleted(applicationName, dataType, WorkflowLogEntry.STATUS_FAILED,
                            failDuration, 0L, 0L, 0, fileSizeBytes);
                    logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                            startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                            0L, 0L, 0, fileSizeBytes, List.of(), e.getMessage());
                }
                tempCleanup.cleanup(chunksDir, processedDir, mergedPath, uploadedPath);
                throw e;
            }
        } finally {
            importRateLimiter.release(userId);
        }
    }

    /**
     * Deplace le fichier source dans un repertoire dedie au user et au
     * correlationId, puis renvoie le chemin final.
     */
    /**
     * Helper de construction + submission asynchrone d'une
     * {@link WorkflowLogEntry} pour un import. Best-effort : en cas
     * d'erreur de parsing des IDs , on log un warning et on continue.
     */
    private void logImportEvent(
            String correlationId, String userId, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startedAt, Duration duration, String status,
            long recordsProcessed, long recordsFailed,
            int chunksProcessed, long fileSizeBytes,
            List<String> errors, String fatalError) {
        try {
            UUID corrUuid = UUID.fromString(correlationId);
            UUID userUuid = UUID.fromString(userId);
            logWriter.logAsync(new WorkflowLogEntry(
                    corrUuid,
                    WorkflowLogEntry.TYPE_IMPORT,
                    userUuid,
                    userLogin,
                    applicationName,
                    dataType,
                    resourceName,
                    startedAt,
                    startedAt.plus(duration),
                    duration,
                    status,
                    recordsProcessed,
                    recordsFailed,
                    chunksProcessed,
                    fileSizeBytes,
                    errors == null ? List.of() : errors,
                    fatalError));
        } catch (IllegalArgumentException e) {
            log.warn("Format UUID invalide , skip log entry [correlationId={} , userId={}]",
                    correlationId, userId);
        }
    }

    /**
     * Best-effort resolution of the caller login from the current request
     * context. Returns null if no user is bound to the thread , in which
     * case the dashboard will fall back to displaying only the UUID.
     */
    private String resolveCurrentLogin() {
        try {
            return authenticationService.getCurrentUserRoles().userLogin();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Path uploadFile(Path source, String userId, String correlationId) throws IOException {
        Path uploadDir = Paths.get(importProperties.getChunksTempDir(), "uploads", userId);
        Files.createDirectories(uploadDir);

        String fileName = source.getFileName().toString();
        Path target = uploadDir.resolve(correlationId + "_" + fileName);
        Files.move(source, target, REPLACE_EXISTING);

        log.debug("[{}] Fichier deplace : {} -> {}", correlationId, source, target);
        return target;
    }
}
