package fr.inra.oresing.domain.fileprocessor;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.fileprocessor.workflow.config.WorkflowProperties;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowChunkCleanupService;
import fr.inra.oresing.fileprocessor.workflow.control.processing.ChunkerService;
import fr.inra.oresing.fileprocessor.workflow.control.processing.MergerService;
import fr.inra.oresing.fileprocessor.workflow.control.processing.WorkerService;
import fr.inra.oresing.fileprocessor.workflow.entity.context.HeaderContext;
import fr.inra.oresing.fileprocessor.workflow.entity.context.SharedContext;
import fr.inra.oresing.fileprocessor.workflow.entity.monitoring.WorkflowMonitoring;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
public class WorkflowOrchestratorImport {
    private final ChunkerService chunkerService;
    private final WorkflowProperties workflowProperties;
    private final WorkerService workerService;
    private final MergerService mergerService;
    private final WorkflowChunkCleanupService cleanupService;
    private final DataImporter dataImporter;

    public WorkflowOrchestratorImport(
            ChunkerService chunkerService,
            WorkflowProperties workflowProperties,
            MergerService mergerService,
            WorkerService workerService,
            WorkflowChunkCleanupService cleanupService,
            DataImporter dataImporter
    ) {
        this.workflowProperties = workflowProperties;
        this.dataImporter = dataImporter;
        this.workerService = workerService;
        this.chunkerService = chunkerService;
        this.mergerService = mergerService;
        this.cleanupService = cleanupService;
    }

    public CompletableFuture<Path> executeWorkflowAsync(WorkflowMonitoring monitoring) {
        return executeWorkflow(monitoring)
                .thenApply(mergedFile -> {
                    log.info("Workflow completed for correlationId: {}", monitoring.getCorrelationId());
                    return mergedFile;  // ← Retourner le Path
                })
                .exceptionally(throwable -> {
                    log.error("Workflow failed for correlationId: {}", monitoring.getCorrelationId(), throwable);
                    throw new CompletionException(throwable.getCause());  // ← Propager l'erreur
                });
    }


    public CompletableFuture<Path> executeWorkflow(WorkflowMonitoring monitoring) {
        String correlationId = monitoring.getCorrelationId();
        String userId = monitoring.getUserId();
        Path originalFilePath = Paths.get(monitoring.getOriginalFilePath());

        log.info("Starting workflow pipeline for correlationId: {}, user: {}, file: {}",
                correlationId, userId, monitoring.getFileName());

        try {
            // Stage 0a: Extract headers from original file
            log.info("Stage 0a: Extracting headers from file: {}", originalFilePath);
            HeaderContext headerContext = HeaderContext.builder()
                    .headers(List.of(""))
                    .correlationId(correlationId)
                    .extractedAt(Instant.now())
                    .build();
            log.info("Stage 0a complete");

            // Stage 0b: Create file without headers
            log.info("Stage 0b: Creating headerless file");
            Path headerlessFilePath = createHeaderlessFile(originalFilePath, correlationId, userId);
            log.info("Stage 0b complete: Created headerless file at {}", headerlessFilePath);

            // Stage 0c: Delete original file to save disk space
            log.info("Stage 0c: Deleting original file to save disk space: {}", originalFilePath);
            Files.deleteIfExists(originalFilePath);
            log.info("Stage 0c complete: Original file deleted");

            // Stage 0d: Create SharedContext for error tracking
            int maxErrorsThreshold = workflowProperties.getErrorHandling().getMaxErrorsThreshold();
            SharedContext sharedContext = new SharedContext(correlationId, maxErrorsThreshold);
            log.info("Stage 0d complete: Created SharedContext with maxErrorsThreshold: {}", maxErrorsThreshold);

            // Stage 1: Chunk the headerless file (false = file has no headers)
            return chunkerService
                    .chunkFileAsync(correlationId, userId, headerlessFilePath, false)
                    .thenCompose(chunks -> {
                        log.info("Stage 1 complete: Chunked into {} pieces", chunks.size());
                        // Stage 2: Process chunks in parallel with contexts
                        return workerService.processChunksAsync(chunks, headerContext, sharedContext);
                    })
                    .thenCompose(processedChunks -> {
                        log.info("Stage 2 complete: Processed {} chunks", processedChunks.size());
                        // Stage 3: Merge processed chunks
                        return mergerService.mergeChunksAsync(processedChunks);
                    })
                    .thenApply(mergedFile -> {
                        log.info("Stage 3 complete: Merged file at {}", mergedFile);
                        dataImporter.treatErrors();


                        Path safeCopy = Paths.get(System.getProperty("java.io.tmpdir"),
                                "oresing-safe-" + correlationId + ".csv");
                        try {
                            Files.copy(mergedFile, safeCopy);
                            log.info("Copied merged file to safe location: {}", safeCopy);
                            return safeCopy;
                        } catch (IOException e) {
                            throw new CompletionException("Failed to copy merged file", e);
                        }  // ← RETOURNER le Path
                    })
                    .whenComplete((mergedFile, throwable) -> {
                        if (throwable == null) {
                            log.info("✅ Workflow pipeline completed successfully for correlationId: {}", correlationId);
                            log.info("📊 Final stats: {} lines processed, {} errors encountered",
                                    sharedContext.getProcessedLinesCount(),
                                    sharedContext.getCurrentErrorCount());

                            int deletedFiles = cleanupService.cleanupWorkflow(correlationId, userId);
                            if (deletedFiles > 0) {
                                log.info("🧹 Cleaned up {} remaining items for completed workflow: {}", deletedFiles, correlationId);
                            }
                        } else {
                            log.error("❌ Workflow pipeline failed for correlationId: {}", correlationId, throwable);

                            if (!(throwable.getCause() instanceof WorkerService.ProcessingException)) {
                                log.info("Cleaning up failed workflow (non-processing error): {}", correlationId);
                                int deletedFiles = cleanupService.cleanupWorkflow(correlationId, userId);
                                log.info("🧹 Cleaned up {} files for failed workflow: {}", deletedFiles, correlationId);
                            }
                        }
                    });

        } catch (IOException e) {
            log.error("❌ Failed to prepare workflow (header extraction/file creation): {}", correlationId, e);
            // Cleanup and fail immediately
            cleanupService.cleanupWorkflow(correlationId, userId);
            return CompletableFuture.failedFuture(
                    new RuntimeException("Failed to prepare workflow: " + e.getMessage(), e)
            );
        }
    }

    private Path createHeaderlessFile(Path originalFilePath, String correlationId, String userId)
            throws IOException {

        // Determine output path: use chunker's temp directory structure
        Path headerlessPath = Paths.get(
                workflowProperties.getChunker().getTempDirectory(),
                userId,
                correlationId,
                "headerless_" + originalFilePath.getFileName()
        );

        // Create parent directories if they don't exist
        Files.createDirectories(headerlessPath.getParent());

        // Copy file content, skipping first line
        try (BufferedReader reader = Files.newBufferedReader(originalFilePath);
             BufferedWriter writer = Files.newBufferedWriter(headerlessPath)) {

            // Copy remaining lines
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }

        log.debug("Created headerless file: {} (size: {} bytes)",
                headerlessPath,
                Files.size(headerlessPath));

        return headerlessPath;
    }
}