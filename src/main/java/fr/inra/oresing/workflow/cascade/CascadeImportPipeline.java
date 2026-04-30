package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.cleanup.WorkflowTempCleanup;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
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
    private final WorkflowActiveRegistry  activeRegistry;

    public CascadeImportPipeline(
            ImportProperties       importProperties,
            ImportProgressReporter progressReporter,
            WorkflowTempCleanup    tempCleanup,
            ImportRateLimiter      importRateLimiter,
            OpenadomMetrics        metrics,
            WorkflowLogWriter      logWriter,
            AuthenticationService  authenticationService,
            WorkflowActiveRegistry activeRegistry) {
        this.importProperties      = importProperties;
        this.progressReporter      = progressReporter;
        this.tempCleanup           = tempCleanup;
        this.importRateLimiter     = importRateLimiter;
        this.metrics               = metrics;
        this.logWriter             = logWriter;
        this.authenticationService = authenticationService;
        this.activeRegistry        = activeRegistry;
    }

    /**
     * Expose the resolved {@link ImportProperties} so callers ( e.g.
     * {@link fr.inra.oresing.rest.data.DataService} ) can read flags they
     * need before delegating to {@link #execute} ( typically the
     * {@code skipCsvReencoding} flag honoured by
     * {@link fr.inra.oresing.domain.data.deposit.DataImporter#prepareContextForDataTreatment} ) .
     */
    public ImportProperties getImportProperties() {
        return importProperties;
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

        // #62 - Publie la progression en temps réel dans WorkflowActiveRegistry
        // pour que /api/dashboard/workflows/in-progress ( oa-live ) puisse
        // afficher ce workflow dès son démarrage. Le finally garantit le
        // retrait du registry même en cas d'erreur ou d'annulation.
        final UUID corrUuid = safeUuid(correlationId);
        final UUID userUuid = safeUuid(userId);
        registerWorkflowStart(corrUuid, userUuid, userLogin, applicationName, dataType,
                resourceName, startedAt, fileSizeBytes);
        try {
            final Path uploadedPath;
            try {
                uploadedPath = uploadFile(headerlessCsv, userId, correlationId);
            } catch (IOException e) {
                log.error("Erreur lors de la preparation de l'upload pour {} : {}", userId, e.getMessage(), e);
                logErrors(userId, applicationName, userLogin, dataType, e, startedAt, fileSizeBytes, correlationId, resourceName);
                throw new UnsupportedOperationException("Failed to prepare workflow", e);
            }

            // #62 - Compte les lignes du fichier ( deja sans en-tete ) pour
            // permettre a oa-live de basculer la barre de progression en
            // mode determine. Best-effort : si le comptage echoue , on
            // laisse recordsTotal a 0 ( fallback animation indeterminee ).
            long recordsTotal = countLines(uploadedPath);
            if (recordsTotal > 0 && corrUuid != null) {
                activeRegistry.setRecordsTotal(corrUuid, recordsTotal);
            }

            final int chunkSizeLines       = importProperties.getChunkSizeLines();
            final int parallelism          = importProperties.getParallelism();
            final int maxErrors            = importProperties.getMaxErrorsThreshold();
            final int collectorChunkSize   = importProperties.getCollectorChunkSize();
            final boolean enableMetrics    = importProperties.isEnableMetrics();

            final Path chunksDir    = Paths.get(importProperties.getChunksTempDir(), userId, correlationId);
            final Path processedDir = Paths.get(importProperties.getProcessedTempDir(), correlationId);
            final Path mergedPath   = Paths.get(System.getProperty("java.io.tmpdir"),
                                                "openadom-import-" + correlationId + ".csv");

            // FileChunkSource lit la taille de chunk depuis WorkflowConfig
            // ( hook Source.onWorkflowStart ) ; le constructeur ne reçoit
            // qu'une valeur de fallback , la valeur effective vient de
            // .sourceChunkSize() ci-dessous.
            FileChunkSource source = new FileChunkSource(uploadedPath, chunksDir, chunkSizeLines);

            // #62 - Compteurs intermediaires utilises uniquement pour pousser
            // la progression dans WorkflowActiveRegistry ( oa-live ). La
            // valeur finale persistee est lue depuis WorkflowResult ( cascade
            // tient deja le compte correct grace a Chunk.recordCount() ).
            final java.util.concurrent.atomic.AtomicLong liveRecords  = new java.util.concurrent.atomic.AtomicLong();
            final java.util.concurrent.atomic.AtomicInteger liveChunks = new java.util.concurrent.atomic.AtomicInteger();

            // #62 - Décorateur autour du reporter existant : on tee les
            // appels onLinesProcessed vers le registry pour alimenter
            // recordsProcessed / chunksProcessed en temps réel.
            ImportProgressReporter teeingReporter = buildRegistryAwareReporter(
                    progressReporter, corrUuid, fileSizeBytes, liveRecords, liveChunks);

            DataImporterTransformation transformation = new DataImporterTransformation(
                    dataImporter,
                    importProperties,
                    teeingReporter,
                    processedDir,
                    correlationId);

            // Strategy switch ( cascade 1.7.0 ) :
            //   MERGE_FILE  : MergingFileSink + storeAll(merged.csv)  -- legacy , default
            //   DIRECT_COPY : StagingPostgresSink with FinalizeHook  -- new , skips merge
            ImportProperties.SinkStrategy strategy = importProperties.getSinkStrategy();
            boolean directCopy = strategy == ImportProperties.SinkStrategy.DIRECT_COPY;

            fr.inrae.ore.cascade.model.core.Sink<java.nio.file.Path> sink = directCopy
                    ? CascadeSinkFactory.directCopy(referenceValueRepository, importProperties)
                    : new MergingFileSink(mergedPath);

            log.info("[{}] Demarrage import : user={}, file={}, chunkSize={}, parallelism={}, maxErrors={}, metrics={}, "
                    + "sinkStrategy={}, executionMode={}, directWriteParallel={}, streamingMode={}",
                    correlationId, userId, uploadedPath.getFileName(), chunkSizeLines, parallelism, maxErrors, enableMetrics,
                    strategy, importProperties.getExecutionMode(),
                    importProperties.isDirectWriteParallel(), importProperties.getStreamingMode());

            fr.inrae.ore.cascade.model.workflow.builder.WorkflowPipelineConfig builder =
                    WorkflowBuilder.create()
                            .forUser(userId)
                            .from(source)
                            .transform(transformation)
                            .to(sink)
                            .withCorrelationId(correlationId)
                            .withParallelism(parallelism)
                            .withSourceChunkSize(chunkSizeLines)
                            .withCollectorChunkSize(collectorChunkSize)
                            .withMaxErrors(maxErrors);
            if (enableMetrics) {
                builder = builder.enableMetrics();
            }
            Workflow workflow = builder.build();

            // Apply 1.7.0 cascade flags via the surrounding WorkflowConfig
            // so the executor can dispatch on executionMode and honour
            // directWriteParallel / streamingMode / sinkParallelism.
            //
            // Sticky-connection note : when sinkStrategy = DIRECT_COPY with
            // PER_CONNECTION_TEMP staging , sinkParallelism MUST be 1
            // ( PgConnection is not thread-safe ) ; we force it here .
            if (directCopy && importProperties.getStagingStrategy() == ImportProperties.StagingStrategy.PER_CONNECTION_TEMP) {
                if (parallelism > 1) {
                    log.warn("[{}] DIRECT_COPY + PER_CONNECTION_TEMP forces sinkParallelism=1 ( single sticky connection ) ; "
                            + "configured parallelism={} is honoured for transform but sink is serial",
                            correlationId, parallelism);
                }
            }

            try {
                // Phase : traitement ( chunking + transformation + merge ).
                updateWorkflowPhase(corrUuid, WorkflowLogEntry.STATUS_PROCESSING, fileSizeBytes);

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

                // Phase : chargement effectif en base . Branchement selon strategy :
                //   MERGE_FILE  : storeAll(merged.csv) -- legacy
                //   DIRECT_COPY : noop , le StagingPostgresSink a deja invoque
                //                 la finalize hook ( COPY + UPSERT ) pendant teardown()
                updateWorkflowPhase(corrUuid, WorkflowLogEntry.STATUS_LOADING_DB, fileSizeBytes);
                dataImporter.treatErrors();
                if (!directCopy) {
                    referenceValueRepository.storeAll(((MergingFileSink) sink).getMergedPath());
                }

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

                    logErrors(userId, userLogin, applicationName, dataType, e, startedAt, fileSizeBytes, correlationId, resourceName);
                }
                tempCleanup.cleanup(chunksDir, processedDir, mergedPath, uploadedPath);
                throw e;
            }
        } finally {
            importRateLimiter.release(userId);
            // #62 - Toujours retirer le snapshot du registry , quel que soit
            // le chemin de sortie ( succès , erreur , annulation ). Sans ce
            // finally , un workflow planté laisserait un fantôme indéfiniment
            // visible dans oa-live.
            if (corrUuid != null) {
                activeRegistry.finish(corrUuid);
            }
            // release the per-correlationId counter held by the
            // default LoggingImportProgressReporter so its internal map
            // does not grow unbounded across the JVM lifetime ( one of
            // the contributors to the 1st->2nd deposit degradation ).
            // No-op when the injected reporter is not the logging one.
            if (progressReporter instanceof fr.inra.oresing.workflow.cascade.progress.LoggingImportProgressReporter logging) {
                try {
                    logging.release(correlationId);
                } catch (RuntimeException releaseError) {
                    log.warn("[{}] Failed to release progress reporter slot : {}",
                            correlationId, releaseError.getMessage());
                }
            }
        }
    }

    // ---------------------------------------------------------------- //
    //  WorkflowActiveRegistry : helpers de publication temps réel       //
    // ---------------------------------------------------------------- //

    private void registerWorkflowStart(
            UUID corrUuid, UUID userUuid, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startedAt, long fileSizeBytes) {
        if (corrUuid == null) {
            return;
        }
        try {
            activeRegistry.start(new WorkflowSnapshot(
                    corrUuid,
                    WorkflowLogEntry.TYPE_IMPORT,
                    userUuid,
                    userLogin,
                    applicationName,
                    dataType,
                    resourceName,
                    startedAt,
                    WorkflowLogEntry.STATUS_UPLOADING,
                    0L,            // recordsProcessed
                    0L,            // recordsFailed
                    0,             // chunksProcessed
                    0.0,           // progressPercentage
                    fileSizeBytes,
                    0L,            // recordsTotal ( inconnu tant que le fichier n'est pas compté )
                    List.of(),     // errors ( aucune au démarrage )
                    List.of()));   // chunks ( injectes par le registry au read-time )
        } catch (RuntimeException e) {
            // Best-effort : un échec de publication ne doit pas casser l'import.
            log.warn("[{}] WorkflowActiveRegistry.start a échoué : {}", corrUuid, e.getMessage());
        }
    }

    /**
     * Met à jour la phase courante ( UPLOADING / CHUNKING / PROCESSING /
     * LOADING_DB ) sans toucher aux compteurs de records , maintenus à jour
     * par {@link #buildRegistryAwareReporter} via les events de progression.
     *
     * <p>Note : {@link WorkflowActiveRegistry#start} utilise putIfAbsent et
     * ne remplace pas une entrée existante. On retire donc l'ancienne
     * entrée puis on en réinsère une avec la nouvelle phase.
     */
    private void updateWorkflowPhase(UUID corrUuid, String phase, long fileSizeBytes) {
        if (corrUuid == null) {
            return;
        }
        try {
            activeRegistry.find(corrUuid)
                    .filter(s -> !phase.equals(s.status()))
                    .ifPresent(snapshot -> {
                        // Remplacement in-place ; les chunks ne sont pas
                        // dans le snapshot ( injectes au read-time par le
                        // registry ) , donc on n'a pas besoin de les
                        // recopier ici.
                        activeRegistry.replace(new WorkflowSnapshot(
                                snapshot.correlationId(),
                                snapshot.workflowType(),
                                snapshot.userId(),
                                snapshot.userLogin(),
                                snapshot.applicationName(),
                                snapshot.dataType(),
                                snapshot.resourceName(),
                                snapshot.startTime(),
                                phase,
                                snapshot.recordsProcessed(),
                                snapshot.recordsFailed(),
                                snapshot.chunksProcessed(),
                                snapshot.progressPercentage(),
                                fileSizeBytes,
                                snapshot.recordsTotal(),
                                snapshot.errors(),
                                List.of()));
                    });
        } catch (RuntimeException e) {
            log.warn("[{}] WorkflowActiveRegistry phase update échouée : {}", corrUuid, e.getMessage());
        }
    }

    /**
     * Décorateur autour du {@link ImportProgressReporter} existant : forwarde
     * les appels au reporter d'origine ( logs , métriques ) , publie le
     * delta sur {@link fr.inrae.ore.cascade.model.progress.ProgressContext}
     * pour que les interceptors cascade voient la progression intra-chunk ,
     * puis met à jour le snapshot dans {@link WorkflowActiveRegistry} pour
     * que oa-live voie progresser recordsProcessed et chunksProcessed en
     * temps réel.
     */
    private ImportProgressReporter buildRegistryAwareReporter(
            ImportProgressReporter delegate, UUID corrUuid, long fileSizeBytes,
            java.util.concurrent.atomic.AtomicLong liveRecords,
            java.util.concurrent.atomic.AtomicInteger liveChunks) {
        return (cid, delta) -> {
            try {
                delegate.onLinesProcessed(cid, delta);
            } finally {
                // Emet vers les interceptors cascade ( best effort : si
                // l'orchestrator n'a pas bind d'emetteur , c'est un NOOP ).
                fr.inrae.ore.cascade.model.progress.ProgressContext.current().emit(delta);

                if (corrUuid != null) {
                    long records = liveRecords.addAndGet(delta);
                    int chunks = liveChunks.incrementAndGet();
                    activeRegistry.update(corrUuid, records, 0L, chunks, null, fileSizeBytes);
                }
            }
        };
    }

    /**
     * Compte les lignes du fichier ( deja sans en-tete ) pour alimenter
     * recordsTotal. Best-effort : en cas d'erreur I/O on retourne 0L et
     * oa-live retombe sur la barre indeterminee.
     */
    private static long countLines(Path path) {
        try (java.util.stream.Stream<String> lines = Files.lines(path)) {
            return lines.count();
        } catch (IOException e) {
            log.warn("Comptage des lignes impossible pour {} : {}", path, e.getMessage());
            return 0L;
        }
    }

    /** Convertit en UUID en silence , null si format invalide. */
    private static UUID safeUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void logErrors(String userId, String userLogin, String applicationName, String dataType, IOException e, Instant startedAt, long fileSizeBytes, String correlationId, String resourceName) {
        Duration failDuration = Duration.between(startedAt, Instant.now());
        metrics.recordImportCompleted(applicationName, dataType, WorkflowLogEntry.STATUS_FAILED,
                failDuration, 0L, 0L, 0, fileSizeBytes);
        logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                0L, 0L, 0, fileSizeBytes, List.of(), e.getMessage());
    }

    // Surcharge pour gérer les exceptions non-IOException
    private void logErrors(String userId, String userLogin, String applicationName, String dataType, Exception e, Instant startedAt, long fileSizeBytes, String correlationId, String resourceName) {
        Duration failDuration = Duration.between(startedAt, Instant.now());
        metrics.recordImportCompleted(applicationName, dataType, WorkflowLogEntry.STATUS_FAILED,
                failDuration, 0L, 0L, 0, fileSizeBytes);
        logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                0L, 0L, 0, fileSizeBytes, List.of(), e.getMessage());
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