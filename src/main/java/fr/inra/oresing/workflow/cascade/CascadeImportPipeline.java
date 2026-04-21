package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.fileprocessor.workflow.config.WorkflowProperties;
import fr.inra.oresing.fileprocessor.workflow.control.monitoring.MonitoringService;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowChunkCleanupService;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowLifecycleManager;
import fr.inra.oresing.fileprocessor.workflow.control.security.RateLimitingService;
import fr.inra.oresing.fileprocessor.workflow.entity.WorkflowStatus;
import fr.inra.oresing.fileprocessor.workflow.entity.context.SharedContext;
import fr.inra.oresing.fileprocessor.workflow.entity.monitoring.WorkflowMonitoring;
import fr.inra.oresing.persistence.DataRepository;
import fr.inrae.ore.cascade.api.workflow.builder.WorkflowBuilder;
import fr.inrae.ore.cascade.model.workflow.ProcessingStatus;
import fr.inrae.ore.cascade.model.workflow.Workflow;
import fr.inrae.ore.cascade.model.workflow.WorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Orchestration de l'import CSV → PostgreSQL via la bibliothèque cascade.
 *
 * <p>Remplace l'ancien pipeline file-processor
 * ({@code ChunkerService / WorkerService / MergerService / LoaderService})
 * par trois briques cascade :
 * <ul>
 *   <li>{@link FileChunkSource} — découpe le fichier en chunks CSV sur disque</li>
 *   <li>{@link DataImporterTransformation} — délègue le traitement métier
 *       au {@link DataImporter} existant (intact)</li>
 *   <li>{@link MergingFileSink} — concatène les chunks traités en un seul CSV</li>
 * </ul>
 *
 * <p>Le chargement final en base ({@link DataRepository#storeAll}) reste
 * inchangé : il est appelé en aval une fois que cascade a produit le fichier
 * mergé. Le bean {@link WorkflowLifecycleManager} est toujours utilisé pour
 * le suivi de progression et le statut — il sera remplacé ultérieurement par
 * un interceptor cascade dédié.
 *
 * <p>Cette implémentation fait partie de la phase 1a de l'issue #62.
 */
@Slf4j
@Service
public class CascadeImportPipeline {

    private final RateLimitingService         rateLimitingService;
    private final MonitoringService           monitoringService;
    private final WorkflowLifecycleManager    lifecycleManager;
    private final WorkflowChunkCleanupService cleanupService;
    private final WorkflowProperties          workflowProperties;

    public CascadeImportPipeline(
            RateLimitingService         rateLimitingService,
            MonitoringService           monitoringService,
            WorkflowLifecycleManager    lifecycleManager,
            WorkflowChunkCleanupService cleanupService,
            WorkflowProperties          workflowProperties) {
        this.rateLimitingService = rateLimitingService;
        this.monitoringService   = monitoringService;
        this.lifecycleManager    = lifecycleManager;
        this.cleanupService      = cleanupService;
        this.workflowProperties  = workflowProperties;
    }

    /**
     * Lance un import pour un fichier CSV sans en-tête déjà préparé par
     * {@link DataImporter#prepareContextForDataTreatment}.
     */
    public void execute(
            DataImporter   dataImporter,
            DataRepository referenceValueRepository,
            Path           headerlessCsv,
            String         userId) {

        if (!Files.exists(headerlessCsv)) {
            throw new IllegalArgumentException("Input file does not exist: " + headerlessCsv);
        }

        if (lifecycleManager.hasReachedMaxConcurrentWorkflows(userId)) {
            log.warn("User {} has reached maximum concurrent workflows", userId);
            throw new UnsupportedOperationException("Max concurrent workflows reached for user " + userId);
        }

        final WorkflowMonitoring monitoring;
        try {
            monitoring = initiateWorkflow(userId, headerlessCsv);
        } catch (IOException e) {
            log.error("Error preparing upload for user: {}", userId, e);
            throw new UnsupportedOperationException("Failed to prepare workflow", e);
        } catch (WorkflowLifecycleManager.RateLimitExceededException e) {
            log.warn("Rate limit exceeded for user: {}", userId);
            throw new UnsupportedOperationException("Rate limit exceeded", e);
        }

        final String correlationId = monitoring.getCorrelationId();
        final Path   originalPath  = Paths.get(monitoring.getOriginalFilePath());

        final int chunkSizeLines = workflowProperties.getChunker().getChunkSizeLines();
        final int workerThreads  = workflowProperties.getWorker().getThreads();
        final int maxErrors      = workflowProperties.getErrorHandling().getMaxErrorsThreshold();

        final Path chunksDir    = Paths.get(workflowProperties.getChunker().getTempDirectory(), userId, correlationId);
        final Path processedDir = Paths.get(workflowProperties.getWorker().getTempDirectory(), correlationId);
        final Path mergedPath   = Paths.get(System.getProperty("java.io.tmpdir"),
                                            "oresing-safe-" + correlationId + ".csv");

        SharedContext sharedContext = new SharedContext(correlationId, maxErrors);

        FileChunkSource source = new FileChunkSource(originalPath, chunkSizeLines, chunksDir);

        DataImporterTransformation transformation = new DataImporterTransformation(
                dataImporter,
                workflowProperties,
                lifecycleManager,
                sharedContext,
                processedDir,
                correlationId,
                userId);

        MergingFileSink sink = new MergingFileSink(mergedPath);

        lifecycleManager.updateWorkflowStatus(correlationId, WorkflowStatus.PROCESS, null);

        Workflow workflow = WorkflowBuilder.create()
                .forUser(userId)
                .from(source)
                .transform(transformation)
                .to(sink)
                .withCorrelationId(correlationId)
                .withParallelism(workerThreads)
                .withMaxErrors(maxErrors)
                .build();

        try {
            WorkflowResult result = workflow.execute();
            if (result.status() == ProcessingStatus.FAILED) {
                String firstError = result.errors().isEmpty()
                        ? result.fatalError().map(Throwable::getMessage).orElse("unknown error")
                        : result.errors().get(0);
                log.error("❌ Cascade workflow failed for {}: {}", correlationId, firstError);
                lifecycleManager.markWorkflowAsFailed(correlationId, firstError);
                cleanupService.cleanupWorkflow(correlationId, userId);
                throw new UnsupportedOperationException("Import workflow failed: " + firstError);
            }

            log.info("✅ Cascade workflow completed for {}: processed={}, chunks={}, duration={}",
                    correlationId,
                    result.recordsProcessed(),
                    result.chunksProcessed(),
                    result.duration());

            dataImporter.treatErrors();
            referenceValueRepository.storeAll(sink.getMergedPath());

            lifecycleManager.updateWorkflowStatus(correlationId, WorkflowStatus.COMPLETED, null);

            int deleted = cleanupService.cleanupWorkflow(correlationId, userId);
            if (deleted > 0) {
                log.info("🧹 Cleaned up {} remaining items for {}", deleted, correlationId);
            }

        } catch (RuntimeException e) {
            lifecycleManager.markWorkflowAsFailed(correlationId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            cleanupService.cleanupWorkflow(correlationId, userId);
            throw e;
        }
    }

    private WorkflowMonitoring initiateWorkflow(String userId, Path path) throws IOException {
        if (!rateLimitingService.tryConsume(userId)) {
            throw new WorkflowLifecycleManager.RateLimitExceededException(
                    "Rate limit exceeded. Please try again later.");
        }

        String correlationId = UUID.randomUUID().toString();

        Path uploadDirPath = Paths.get(
                workflowProperties.getChunker().getTempDirectory(), "uploads", userId);
        Files.createDirectories(uploadDirPath);

        File   file     = path.toFile().getAbsoluteFile();
        String fileName = file.getName();
        Path   originalFilePath = uploadDirPath.resolve(correlationId + "_" + fileName);
        Files.move(path, originalFilePath, REPLACE_EXISTING);

        log.info("File uploaded: {} for user: {} with correlationId: {}",
                fileName, userId, correlationId);

        WorkflowMonitoring monitoring = monitoringService.registerWorkflow(
                correlationId, userId, fileName, file.length());
        monitoring.setOriginalFilePath(originalFilePath.toString());

        log.info("Workflow monitoring registered for correlationId: {}", correlationId);
        return monitoring;
    }
}
