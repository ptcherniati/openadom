package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.workflow.WorkflowPhase;
import fr.inra.oresing.workflow.cascade.cache.ReferencevalueCacheFormat;
import fr.inra.oresing.workflow.cascade.cache.ReferencevalueCacheWriter;
import fr.inra.oresing.workflow.cascade.config.PublishProperties;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Post-commit cache capture service .
 *
 * <h2>Why</h2>
 *
 * <p>The cache capture step ( binary COPY of {@code referencevalue} rows
 * into the binaryfile's processed_data Large Object ) runs after every
 * successful publish so the next republish can take the FAST path
 * ( ~1-2 min vs ~13-15 min via cascade ) . On large datatypes the capture
 * itself takes ~30 s of pure IO . Embedded inline in
 * {@link PublishLifecyclePhase2Handler#doPublishWithinScope} it blocks
 * the workflow from reaching DONE state until capture is finished , so
 * the user sees the workflow as still running for an extra ~30 s after
 * the visible flag has been flipped and synthesis recomputed .
 *
 * <p>Running the capture asynchronously after the publish has flipped to
 * COMPLETED + DONE makes the workflow appear terminated to the user as
 * soon as the data is actually visible ; the cache builds in background .
 *
 * <h2>Safety</h2>
 *
 * <p>The cache is a perf-optim , never a correctness invariant : if the
 * capture fails ( DB hiccup , Large Object full , JVM crash between DONE
 * and capture completion ) , the next republish will simply fall back to
 * the cascade path . The {@code processedSize} +  {@code configHash}
 * check in {@code doPublishWithinScope} naturally re-routes to cascade
 * whenever the cache is missing or stale .
 *
 * <p>The bean uses {@code @Async} via the application's default executor
 * ( wired by {@code AsyncExecutorConfiguration} ) . Each call runs on a
 * daemon thread independently of the Phase 2 thread that scheduled it .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Service
public class CacheCaptureService {

    private final OreSiRepository repository;
    private final ConfigHashService configHashService;
    private final PublishProperties publishProperties;

    @Autowired(required = false)
    private WorkflowLogRepository workflowLogRepository;

    public CacheCaptureService(OreSiRepository repository,
                                ConfigHashService configHashService,
                                PublishProperties publishProperties) {
        this.repository = repository;
        this.configHashService = configHashService;
        this.publishProperties = publishProperties;
    }

    /**
     * Captures the binary COPY of {@code referencevalue} rows belonging
     * to {@code fileId} into the binaryfile's processed_data Large Object .
     *
     * <p>Skips silently when :
     * <ul>
     *   <li>{@code openadom.publish.capture-processed-enabled=false} ; or</li>
     *   <li>The existing cache is non-empty and the stored configHash
     *       matches the current configHash ( cache is already up-to-date ,
     *       FAST path will use it on next republish without rebuild ) .</li>
     * </ul>
     *
     * <p>On non-skip path , streams the COPY via {@code DirectCopyWriter}
     * into the LO and updates {@code binaryfile.configHash} so the
     * next publish can detect cache freshness . Failures are logged
     * warn and swallowed ( best-effort ) .
     */
    @Async
    public void captureCacheAsync(Application application, UUID fileId, String dataName, UUID correlationId) {
        log.info("[cache-capture-async] entry : fileId={} dataName={} correlationId={} captureEnabled={}",
                fileId, dataName, correlationId, publishProperties.isCaptureProcessedEnabled());
        if (!publishProperties.isCaptureProcessedEnabled()) {
            log.warn("[cache-capture-async] skip - captureProcessedEnabled=false for fileId={}", fileId);
            return;
        }
        try {
            BinaryFileRepository bfRepo = repository.getRepository(application).binaryFile();
            String currentHash = configHashService.computeHash(application, dataName).orElse(null);
            long existingCacheSize;
            try {
                existingCacheSize = bfRepo.findProcessedSize(fileId);
            } catch (RuntimeException ex) {
                log.debug("[cache-capture-async] findProcessedSize failed for {} : {}", fileId, ex.getMessage());
                existingCacheSize = 0L;
            }
            String storedHash = bfRepo.tryFindById(fileId)
                    .map(bf -> bf.getParams())
                    .map(p -> p.configHash())
                    .orElse(null);
            boolean cacheUpToDate = existingCacheSize > ReferencevalueCacheFormat.HEADER_SIZE_BYTES
                    && currentHash != null
                    && currentHash.equals(storedHash);
            if (cacheUpToDate) {
                log.info("[cache-capture-async] skip - cache up to date for fileId={}", fileId);
                return;
            }
            if (workflowLogRepository != null && correlationId != null) {
                workflowLogRepository.updatePhase(correlationId, WorkflowPhase.CACHE_CAPTURE);
            }
            final String schemaName = repository.getRepository(application).data().getSchemaName();
            bfRepo.storeProcessedDataDirectCopy(fileId, (captureConn, out) -> {
                out.write(ReferencevalueCacheFormat.buildHeader());
                ReferencevalueCacheWriter.writeCache(captureConn, schemaName, fileId, out);
            });
            log.info("[cache-capture-async] cache captured for fileId={} ( FAST path armed for next republish )", fileId);
            if (currentHash != null && !currentHash.equals(storedHash)) {
                bfRepo.updateConfigHash(fileId, currentHash);
                log.info("[cache-capture-async] configHash updated for fileId={}", fileId);
            }
        } catch (RuntimeException ex) {
            log.warn("[cache-capture-async] failed for fileId={} : {} ( non-critical , FAST path will fall back to cascade )",
                    fileId, ex.getMessage());
        }
    }
}
