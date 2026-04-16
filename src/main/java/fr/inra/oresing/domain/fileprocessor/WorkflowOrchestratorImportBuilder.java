package fr.inra.oresing.domain.fileprocessor;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.fileprocessor.domain.TransformationDomain;
import fr.inra.oresing.fileprocessor.workflow.config.WorkflowProperties;
import fr.inra.oresing.fileprocessor.workflow.control.monitoring.MonitoringService;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowCancellationService;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowChunkCleanupService;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowLifecycleManager;
import fr.inra.oresing.fileprocessor.workflow.control.processing.ChunkerService;
import fr.inra.oresing.fileprocessor.workflow.control.processing.LoaderService;
import fr.inra.oresing.fileprocessor.workflow.control.processing.MergerService;
import fr.inra.oresing.fileprocessor.workflow.control.processing.WorkerService;
import fr.inra.oresing.fileprocessor.workflow.control.security.RateLimitingService;
import fr.inra.oresing.fileprocessor.workflow.entity.monitoring.WorkflowMonitoring;
import fr.inra.oresing.persistence.DataRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Slf4j
@Service
public class WorkflowOrchestratorImportBuilder {
    private final WorkflowLifecycleManager workflowLifecycleManager;
    private final RateLimitingService rateLimitingService;
    private final WorkflowProperties workflowProperties;
    private final MonitoringService monitoringService;
    private final WorkflowChunkCleanupService cleanupService;
    private final ChunkerService chunkerService;
    private final MergerService mergerService;
    private final LoaderService loaderService;
    private final WorkflowLifecycleManager lifecycleManager;
    private final ExecutorService workerExecutor;
    private final WorkflowCancellationService cancellationService;

    public WorkflowOrchestratorImportBuilder(
            WorkflowLifecycleManager workflowLifecycleManager,
            RateLimitingService rateLimitingService,
            WorkflowProperties workflowProperties,
            MonitoringService monitoringService,
            WorkflowChunkCleanupService cleanupService,
            ChunkerService chunkerService,
            MergerService mergerService,
            LoaderService loaderService,
            WorkflowLifecycleManager lifecycleManager,
            @Qualifier("workerExecutor")
            ExecutorService workerExecutor, WorkflowCancellationService cancellationService
    ) {
        this.workflowLifecycleManager = workflowLifecycleManager;
        this.rateLimitingService = rateLimitingService;
        this.workflowProperties = workflowProperties;
        this.monitoringService = monitoringService;
        this.cleanupService = cleanupService;
        this.chunkerService = chunkerService;
        this.mergerService = mergerService;
        this.loaderService = loaderService;
        this.lifecycleManager = lifecycleManager;
        this.workerExecutor = workerExecutor;
        this.cancellationService = cancellationService;
    }

    private WorkflowMonitoring initiateWorkflow(String userId, Path path, boolean isRecursive) throws IOException {
        // Check rate limit
        if (!rateLimitingService.tryConsume(userId)) {
            throw new WorkflowLifecycleManager.RateLimitExceededException("Rate limit exceeded. Please try again later.");
        }

        // Generate correlation ID
        String correlationId = UUID.randomUUID().toString();

        // Create user-specific directory
        Path uploadDirPath = Paths.get(workflowProperties.getChunker().getTempDirectory(), "uploads", userId);
        Files.createDirectories(uploadDirPath);

        // Save uploaded file
        final File file = path.toFile().getAbsoluteFile();
        final String fileName = isRecursive ? file.getName() + "1234" : file.getName();
        Path originalFilePath = uploadDirPath.resolve(correlationId + "_" + fileName);
        Files.move(path, originalFilePath, REPLACE_EXISTING);

        log.info("File uploaded: {} for user: {} with correlationId: {}",
                fileName, userId, correlationId);

        // Register workflow in monitoring (in-memory)
        WorkflowMonitoring monitoring = monitoringService.registerWorkflow(
                correlationId,
                userId,
                fileName,
                file.length()
        );

        // Set original file path
        monitoring.setOriginalFilePath(originalFilePath.toString());

        log.info("Workflow monitoring registered for correlationId: {}", correlationId);

        return monitoring;
    }

    public void execute(DataImporter dataImporter, DataRepository referenceValueRepository, Path path, String userId) {
        if (!path.toFile().exists()) {
            throw new UnsupportedOperationException();
        }
        try {
            // Check if user has reached max concurrent workflows
            if (workflowLifecycleManager.hasReachedMaxConcurrentWorkflows(userId)) {
                log.warn("User {} has reached maximum concurrent workflows", userId);
                throw new UnsupportedOperationException();

            }

            // Initiate workflow (in-memory monitoring)
            final boolean isRecursive = dataImporter.getRecursionStrategy() instanceof RecursionStrategy;
            WorkflowMonitoring monitoring = initiateWorkflow(userId, path, isRecursive);

            Map<String, TransformationDomain> domainRules = Map.of(
                    "OA",
                    new TransformationDomain(
                    ) {
                        @Override
                        public List<String> transform(CSVRecord csvRecord) {
                            return List.of();
                        }

                        @Override
                        public String[] getOutputHeaders(List<String> inputHeaders) {
                            return new String[0];
                        }

                        @Override
                        public String getDomainName() {
                            return "OA";
                        }
                    }
            );
            WorkerService workerService = new OaImportWorkerService(
                    workflowProperties,
                    lifecycleManager,
                    workerExecutor,
                    domainRules,
                    cancellationService,
                    cleanupService,
                    dataImporter
            );
            final WorkflowOrchestratorImport workflowOrchestrator = new WorkflowOrchestratorImport(
                    chunkerService,
                    workflowProperties,
                    mergerService,
                    workerService,
                    cleanupService,
                    dataImporter
            );

            // Start the pipeline asynchronously
            final Path mergedPath = workflowOrchestrator.executeWorkflowAsync(monitoring)
                    .join();
            referenceValueRepository.storeAll(mergedPath);


            log.info("Successfully initiated workflow with correlationId: {}", monitoring.getCorrelationId());

        } catch (WorkflowLifecycleManager.RateLimitExceededException e) {
            log.warn("Rate limit exceeded for user: {}", userId);
            throw new UnsupportedOperationException();
        } catch (IOException e) {
            log.error("Error processing upload for user: {}", userId, e);
            throw new UnsupportedOperationException();
        }
    }
}