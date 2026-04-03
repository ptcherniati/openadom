package fr.inra.oresing.domain.fileprocessor;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.fileprocessor.domain.TransformationDomain;
import fr.inra.oresing.fileprocessor.workflow.config.WorkflowProperties;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowCancellationService;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowChunkCleanupService;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowLifecycleManager;
import fr.inra.oresing.fileprocessor.workflow.control.processing.ErrorThresholdExceededException;
import fr.inra.oresing.fileprocessor.workflow.control.processing.WorkerService;
import fr.inra.oresing.fileprocessor.workflow.entity.ChunkInfo;
import fr.inra.oresing.fileprocessor.workflow.entity.WorkflowStatus;
import fr.inra.oresing.fileprocessor.workflow.entity.context.HeaderContext;
import fr.inra.oresing.fileprocessor.workflow.entity.context.SharedContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel chunk processing service implementing fail-fast cancellation strategy.
 *
 * <p>This service orchestrates the parallel processing of file chunks using a configurable
 * {@link TransformationDomain} strategy. It implements a fail-fast cancellation mechanism
 * where the failure of a single chunk immediately cancels all other chunks in the same workflow.
 *
 * <p><b>Key Responsibilities:</b>
 * <ul>
 *   <li>Parallel execution of chunk transformations using worker thread pool</li>
 *   <li>Real-time progress tracking for monitoring and UI updates</li>
 *   <li>Fail-fast cancellation with immediate cleanup on errors</li>
 *   <li>Graceful handling of thread interruptions during cancellation</li>
 *   <li>Incremental disk space management by deleting processed chunks</li>
 * </ul>
 *
 * <p><b>Processing Strategy:</b>
 * The service delegates business logic to a {@link TransformationDomain} implementation
 * selected via configuration ({@code workflow.worker.domainType}). This allows different
 * transformation rules without modifying the processing pipeline.
 *
 * <p><b>Fail-Fast Behavior:</b>
 * When any chunk processing fails:
 * <ol>
 *   <li>All pending/running chunk futures are immediately cancelled with interruption</li>
 *   <li>Workflow status is marked as {@link WorkflowStatus#FAILED}</li>
 *   <li>All temporary files for the workflow are deleted via {@link WorkflowChunkCleanupService}</li>
 *   <li>Workflow is unregistered from cancellation tracking</li>
 *   <li>A {@link ProcessingException} is propagated to the caller</li>
 * </ol>
 *
 * <p><b>Thread Safety:</b>
 * This service is thread-safe and can process chunks from multiple workflows concurrently.
 * The worker thread pool is shared across all workflows but chunks are isolated by correlationId.
 *
 * <p><b>Progress Tracking:</b>
 * Progress is tracked at two levels:
 * <ul>
 *   <li><b>Chunk-level:</b> Incremented after each chunk completes processing</li>
 *   <li><b>Line-level:</b> Incremented in batches during chunk processing for real-time updates</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * List<ChunkInfo> chunks = chunkerService.splitFileIntoChunks(uploadedFile);
 *
 * CompletableFuture<List<ChunkInfo>> future = workerService.processChunksAsync(chunks);
 *
 * future.thenAccept(processedChunks -> {
 *     log.info("All {} chunks processed successfully", processedChunks.size());
 * }).exceptionally(ex -> {
 *     log.error("Processing failed, all chunks cancelled and cleaned up", ex);
 *     return null;
 * });
 * }</pre>
 *
 * <p><b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code workflow.worker.poolSize} - Number of parallel worker threads</li>
 *   <li>{@code workflow.worker.domainType} - Transformation strategy to use</li>
 *   <li>{@code workflow.worker.tempDirectory} - Directory for processed chunk files</li>
 *   <li>{@code workflow.worker.progressBatchSize} - Batch size for line-level progress updates</li>
 * </ul>
 *
 * @see TransformationDomain
 * @see WorkflowCancellationService
 * @see WorkflowChunkCleanupService
 * @see WorkflowLifecycleManager
 * @see ChunkInfo
 */
@Slf4j
public class OaImportWorkerService extends WorkerService {

    /**
     * Business logic implementation for transforming chunk data.
     * Selected at startup based on {@code workflow.worker.domainType} configuration.
     */
    private final WorkflowLifecycleManager lifecycleManager;
    private final DataImporter dataImporter;
    private final WorkflowCancellationService cancellationService;
    private final ExecutorService workerExecutor;
    private final WorkflowProperties workflowProperties;
    private final WorkflowChunkCleanupService cleanupService;

    /**
     * Constructs a WorkerService with required dependencies and selects transformation strategy.
     *
     * <p>The transformation domain is selected based on {@code workflow.worker.domainType}
     * configuration property. If the configured domain type is not found in the {@code domainRules}
     * map, the first available domain is used as a fallback.
     *
     * @param workflowProperties       configuration properties for all workflow stages
     * @param lifecycleManager         manager for workflow status and progress tracking
     * @param workerExecutor           dedicated thread pool for parallel chunk processing
     * @param domainRules              map of all available transformation domain implementations
     * @param cancellationService      service for cancelling running workflows
     * @param cleanupService           s
     * @see TransformationDomain
     * @see WorkflowProperties
     */
    public OaImportWorkerService(
            WorkflowProperties workflowProperties,
            WorkflowLifecycleManager lifecycleManager,
            @Qualifier("workerExecutor") ExecutorService workerExecutor,
            Map<String, TransformationDomain> domainRules,
            WorkflowCancellationService cancellationService,
            WorkflowChunkCleanupService cleanupService,
            DataImporter dataImporter) {
        super(workflowProperties,
                lifecycleManager,
                workerExecutor,
                domainRules,
                cancellationService,
                cleanupService);
        this.dataImporter = dataImporter;
        this.lifecycleManager = lifecycleManager;
        this.cancellationService = cancellationService;
        this.workerExecutor = workerExecutor;
        this.workflowProperties = workflowProperties;
        this.cleanupService = cleanupService;
    }

    /**
     * Processes multiple file chunks in parallel with fail-fast cancellation semantics.
     *
     * <p>All chunks are processed concurrently using the {@link #workerExecutor} thread pool.
     * Each chunk is transformed according to the configured {@link TransformationDomain} strategy.
     *
     * <p><b>Fail-Fast Guarantee:</b>
     * If ANY single chunk fails during processing, ALL other chunks for the same workflow
     * are immediately cancelled. This prevents partial processing and wasted resources.
     *
     * <p><b>Processing Flow:</b>
     * <ol>
     *   <li>Reset progress counters for fresh tracking</li>
     *   <li>Update workflow status to {@link WorkflowStatus#PROCESS}</li>
     *   <li>Submit all chunks to worker thread pool concurrently</li>
     *   <li>Register futures with {@link WorkflowCancellationService} for cancellation support</li>
     *   <li>Wait for all chunks to complete (or first failure)</li>
     *   <li>On success: update status to {@link WorkflowStatus#WAIT_FOR_MERGE}</li>
     *   <li>On failure: cancel all chunks, cleanup files, mark workflow as failed</li>
     * </ol>
     *
     * <p><b>Resource Management:</b>
     * Original chunk files are deleted immediately after processing to minimize disk usage.
     * On failure, all files (original and processed) are cleaned up via {@link WorkflowChunkCleanupService}.
     *
     * <p><b>Progress Tracking:</b>
     * <ul>
     *   <li>Chunk-level progress increments as each chunk completes</li>
     *   <li>Line-level progress increments during chunk processing (batch updates)</li>
     * </ul>
     *
     * @param chunks list of chunks to process (must all belong to the same workflow)
     * @param headerContext CSV headers extracted before chunking, available for column interpretation
     * @param sharedContext thread-safe context for error collection and line counting
     *
     * @return a {@link CompletableFuture} that completes with the list of processed chunks,
     *         each containing the path to its processed output file via
     *         {@link ChunkInfo#setProcessedChunkPath(Path)}. Returns immediately if chunks list is empty.
     *
     * @throws ProcessingException if any chunk fails to process, wrapped in the future's
     *                             exceptional completion. Original exception is available via
     *                             {@link Throwable#getCause()}.
     * @throws ErrorThresholdExceededException if error threshold is reached (wrapped in future)
     *
     * @see ErrorThresholdExceededException
     * @see SharedContext
     * @see HeaderContext
     */
    public CompletableFuture<List<ChunkInfo>> processChunksAsync(
            List<ChunkInfo> chunks,
            HeaderContext headerContext,
            SharedContext sharedContext) {
        if (chunks.isEmpty()) {
            return CompletableFuture.completedFuture(chunks);
        }

        String correlationId = chunks.get(0).getCorrelationId();
        String userId = chunks.get(0).getUserId();
        log.info("Starting parallel processing of {} chunks for correlationId: {}", chunks.size(), correlationId);

        // Reset processed chunks counter for this stage
        lifecycleManager.resetProcessedChunks(correlationId);

        // Update status to PROCESS (progress will be calculated from chunks)
        lifecycleManager.updateWorkflowStatus(correlationId, WorkflowStatus.PROCESS, null);

        log.info("Processing with HeaderContext ({}  headers), SharedContext (max {} errors)",
                headerContext.getHeaderCount(), sharedContext.getMaxErrorsThreshold());

        // Process all chunks in parallel
        List<CompletableFuture<ChunkInfo>> futures = new ArrayList<>();
        for (ChunkInfo chunk : chunks) {
            CompletableFuture<ChunkInfo> future = processChunkAsync(chunk, headerContext, sharedContext);
            futures.add(future);

            // Register each future for potential cancellation
            cancellationService.registerFuture(correlationId, future);
        }

        // Wait for all chunks to be processed
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .handle((v, throwable) -> {
                    if (throwable != null) {
                        // ONE CHUNK FAILED - CANCEL ALL OTHERS IMMEDIATELY
                        log.error("❌ Chunk processing error for correlationId: {}", correlationId, throwable);

                        // 1. Cancel ALL other futures for this correlationId (interrupts threads)
                        int cancelledCount = cancellationService.cancelWorkflow(
                                correlationId,
                                "Chunk failed: " + getRootCauseMessage(throwable)
                        );
                        log.warn("⛔ Cancelled {} remaining chunk futures for correlationId: {}",
                                cancelledCount, correlationId);

                        // 2. Mark workflow as FAILED in monitoring
                        // Check if it's an ErrorThresholdExceededException to include all error messages
                        Throwable cause = throwable.getCause();
                        if (cause instanceof ErrorThresholdExceededException) {
                            ErrorThresholdExceededException errorEx = (ErrorThresholdExceededException) cause;
                            lifecycleManager.markWorkflowAsFailed(correlationId, errorEx.getErrorMessages());
                            log.warn("⚠️ Workflow failed due to error threshold: {} errors collected",
                                    errorEx.getErrorCount());
                        } else {
                            lifecycleManager.markWorkflowAsFailed(
                                    correlationId,
                                    "Processing failed: " + getRootCauseMessage(throwable)
                            );
                        }

                        // 3. Cleanup ALL files for this correlationId
                        int deletedFiles = cleanupService.cleanupWorkflow(correlationId, userId);
                        log.info("🧹 Cleaned up {} files for failed workflow: {}", deletedFiles, correlationId);

                        // 4. Unregister from tracking
                        cancellationService.unregisterWorkflow(correlationId);

                        // 5. Propagate exception
                        if (throwable.getCause() instanceof ErrorThresholdExceededException) {
                            throw (CompletionException) throwable;
                        }
                        throw new ProcessingException(
                                "Failed to process chunks for " + correlationId + ": " + getRootCauseMessage(throwable),
                                throwable
                        );
                    }

                    // SUCCESS - All chunks processed
                    List<ChunkInfo> processedChunks = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();

                    log.info("✅ Completed processing {} chunks for correlationId: {}",
                            processedChunks.size(), correlationId);

                    // Set processed files count for stage tracking
                    lifecycleManager.setProcessedFiles(correlationId, processedChunks.size());

                    // Update status to WAIT_FOR_MERGE
                    lifecycleManager.updateWorkflowStatus(correlationId, WorkflowStatus.WAIT_FOR_MERGE, null);

                    // Unregister from tracking (success path)
                    cancellationService.unregisterWorkflow(correlationId);

                    return processedChunks;
                });
    }

    /**
     * Processes a single chunk asynchronously with interruption handling.
     *
     * <p>Submits the chunk processing task to the {@link #workerExecutor} thread pool.
     *
     * <p><b>Interruption Handling:</b>
     * When a workflow is cancelled (due to another chunk failing), the thread executing
     * this task is interrupted. The method catches {@link InterruptedException} and
     * {@link ClosedByInterruptException}, restores the interrupt status, and throws
     * {@link CancellationException} to signal graceful cancellation.
     *
     * <p><b>Resource Cleanup:</b>
     * Upon successful processing, the original chunk file is immediately deleted to
     * minimize disk usage. The processed chunk path is stored in {@link ChunkInfo#setProcessedChunkPath(Path)}.
     *
     * @param chunkInfo metadata about the chunk to process, including source file path
     * @param headerContext CSV headers extracted before chunking
     * @param sharedContext thread-safe context for error collection and line counting
     *
     * @return a {@link CompletableFuture} that completes with the updated {@link ChunkInfo}
     *         containing the processed chunk file path
     *
     * @throws ProcessingException wrapped in future if I/O error occurs during processing
     * @throws ErrorThresholdExceededException wrapped in future if error threshold is reached
     * @throws CancellationException wrapped in future if thread is interrupted (workflow cancelled)
     *
     */
    private CompletableFuture<ChunkInfo> processChunkAsync(
            ChunkInfo chunkInfo,
            HeaderContext headerContext,
            SharedContext sharedContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Processing chunk {} for correlationId: {}",
                        chunkInfo.getChunkNumber(), chunkInfo.getCorrelationId());

                // Process the chunk (will throw InterruptedException if cancelled or ErrorThresholdExceededException if threshold reached)
                Path processedPath = processChunk(chunkInfo, headerContext, sharedContext);
                chunkInfo.setProcessedChunkPath(processedPath);

                // Increment processed chunks counter after each chunk completion
                lifecycleManager.incrementProcessedChunks(chunkInfo.getCorrelationId());

                // Delete original chunk to save space
                Files.deleteIfExists(chunkInfo.getChunkPath());
                log.debug("Deleted original chunk: {}", chunkInfo.getChunkPath());

                return chunkInfo;

            } catch (InterruptedException | ClosedByInterruptException e) {
                // Thread was interrupted by cancel() - this is EXPECTED when another chunk fails
                log.debug("⛔ Chunk {} processing interrupted (workflow cancelled)",
                        chunkInfo.getChunkNumber());
                Thread.currentThread().interrupt(); // Restore interrupt status
                throw new CancellationException("Chunk processing cancelled");

            } catch (IOException e) {
                // Real I/O error - this will trigger cancellation of other chunks
                log.error("❌ Error processing chunk {} for correlationId: {}",
                        chunkInfo.getChunkNumber(), chunkInfo.getCorrelationId(), e);
                throw new ProcessingException("Failed to process chunk: " + e.getMessage(), e);
            }
        }, workerExecutor);
    }

    /**
     * Performs the actual chunk transformation using the configured business logic.
     *
     * <p>Reads the chunk CSV file, applies the {@link TransformationDomain} transformation
     * to each row, and writes the transformed data to a new processed chunk file.
     *
     * <p><b>Processing Steps:</b>
     * <ol>
     *   <li>Check for thread interruption before starting</li>
     *   <li>Create processed chunk directory if needed</li>
     *   <li>Open source chunk file for reading</li>
     *   <li>Create processed chunk file for writing</li>
     *   <li>Transform header lines using {@link TransformationDomain#getOutputHeaders(List)}</li>
     *   <li>Transform data lines using {@link TransformationDomain#transform(CSVRecord)}</li>
     *   <li>Update line-level progress in batches (configurable via {@code workflow.worker.progressBatchSize})</li>
     * </ol>
     *
     * <p><b>Interruption Safety:</b>
     * The method checks {@link Thread#isInterrupted()} before starting. During processing,
     * the CSV parser automatically detects thread interruption and throws an exception.
     * If I/O is interrupted ({@link ClosedByInterruptException}), the partial output file
     * is deleted before throwing {@link InterruptedException}.
     *
     * <p><b>Progress Tracking:</b>
     * Line-level progress is updated incrementally in batches to avoid excessive synchronization
     * overhead. The batch size is configurable via {@code workflow.worker.progressBatchSize}.
     *
     * <p><b>File Naming Convention:</b>
     * Processed files are named: {@code processed_chunk_<correlationId>_<chunkNumber>.csv}
     * and stored in: {@code <tempDirectory>/<correlationId>/}
     *
     * @param chunkInfo metadata containing chunk number, correlation ID, and source file path
     * @param headerContext CSV headers extracted before chunking
     * @param sharedContext thread-safe context for error collection and line counting
     *
     * @return path to the newly created processed chunk file
     *
     * @throws IOException if file I/O operations fail
     * @throws InterruptedException if thread is interrupted during processing (workflow cancelled)
     * @throws ErrorThresholdExceededException if error threshold is reached during processing
     *
     * @see TransformationDomain#transform(CSVRecord)
     * @see TransformationDomain#getOutputHeaders(List)
     * @see SharedContext#incrementProcessedLines(long)
     * @see SharedContext#addError(String)
     */
    private Path processChunk(
            ChunkInfo chunkInfo,
            HeaderContext headerContext,
            SharedContext sharedContext) throws IOException, InterruptedException {
        // Check if thread is interrupted before starting
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Thread interrupted before processing chunk");
        }
        // Create processed directory
        String processedDir = workflowProperties.getWorker().getTempDirectory() + "/" + chunkInfo.getCorrelationId();
        Files.createDirectories(Paths.get(processedDir));

        // Create processed chunk file path
        String processedFileName = String.format("processed_chunk_%s_%04d.csv",
                chunkInfo.getCorrelationId(),
                chunkInfo.getChunkNumber());
        Path processedPath = Paths.get(processedDir, processedFileName);
        try {
            final AtomicInteger dataLinesProcessed = new AtomicInteger();
            final AtomicInteger successfulLinesBatch = new AtomicInteger();
            dataImporter.doDataTreatment(
                    processedPath,
                    sharedContext,
                    chunkInfo,
                    dataLinesProcessed,
                    successfulLinesBatch,
                    workflowProperties,
                    lifecycleManager);

            // Increment remaining successful lines (if any)
            final int succesLineCount = successfulLinesBatch.get();
            if (succesLineCount > 0) {
                sharedContext.incrementProcessedLines(succesLineCount);
                lifecycleManager.incrementProcessedLines(chunkInfo.getCorrelationId(), succesLineCount);
            }

            log.debug("Chunk {} processed: {} successful, {} errors (total errors in workflow: {}/{})",
                    chunkInfo.getChunkNumber(),
                    dataLinesProcessed,
                    -1,
                    sharedContext.getCurrentErrorCount(),
                    sharedContext.getMaxErrorsThreshold());

            log.debug("Processed chunk saved to: {}", processedPath);
            return processedPath;

        } catch (ClosedByInterruptException e) {
            // I/O operation was interrupted - cleanup partial file
            try {
                Files.deleteIfExists(processedPath);
                log.debug("Deleted partial processed chunk: {}", processedPath);
            } catch (IOException cleanupEx) {
                log.warn("Failed to delete partial chunk: {}", processedPath, cleanupEx);
            }
            throw new InterruptedException("I/O interrupted while processing chunk");
        }
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}