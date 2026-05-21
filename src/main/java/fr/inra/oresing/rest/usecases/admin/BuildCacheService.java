package fr.inra.oresing.rest.usecases.admin;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.cancel.CancellationContext;
import fr.inra.oresing.domain.cancel.CancellationToken;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.usecases.storage.versioning.ConfigHashService;
import fr.inra.oresing.workflow.OreSiWorkflowType;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * Admin pre-compute du cache {@code binaryfile.processed_data} : execute
 * la pipeline cascade en mode {@code SinkStrategy.DISCARD} ( cascade 3.2.0 )
 * sur un fichier binaire deja uploade et persiste le JSON valide+
 * transforme cumule par {@code DataImporter.convertToCSVLine} dans la
 * colonne {@code processed_data} + actualise le {@code configHash} .
 *
 * <p><b>Effet</b> : armer le Publish FAST path pour le prochain republish .
 * Si la config du datatype n'a pas change ( hashMatch ) , le publish
 * suivant COPY direct {@code processed_data} -&gt; {@code referencevalue}
 * en quelques secondes au lieu de rejouer la pipeline cascade complete
 * ( 30-60 s sur 1M lignes ) .
 *
 * <p><b>Invariants</b> :
 * <ul>
 *   <li>{@code referencevalue} jamais modifie ( sink no-op cascade 3.2.0 ) ;</li>
 *   <li>{@code binaryfile.params.published} jamais touche ( BUILD est
 *       orthogonal a publish/unpublish ) ;</li>
 *   <li>{@code oresisynthesis} jamais recompute ( pas de nouveaux rows ) ;</li>
 *   <li>workflow_log entry type {@code BUILD_CACHE} permet observabilite
 *       complete dans oa-live history ( IN_PROGRESS -&gt; COMPLETED / FAILED ) .</li>
 * </ul>
 *
 * <p><b>Cancel</b> : reutilise {@link CancellationContext} + checkpoints
 * dans {@code DataImporter.prepareContextForDataTreatment} ( deja en
 * place pour publish ) -&gt; SLA cancel 5s identique au publish .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Service
public class BuildCacheService {

    private final ServiceContainer        serviceContainer;
    private final OreSiRepository         repository;
    private final WorkflowLogWriter       logWriter;
    private final ConfigHashService       configHashService;
    private final fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleCoordinator coordinator;
    private final fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository workflowLogRepository;

    /**
     * Heartbeat scheduler partage : un seul thread daemon emet
     * {@code beat_workflow(cid)} toutes les 30 s pendant les BUILD_CACHE
     * actifs . Sans cela , le WorkflowZombieSweeper marque les BUILD_CACHE
     * IN_PROGRESS comme CANCELLED apres {@code app.workflow.zombie-threshold-minutes}
     * ( 10 min defaut ) puisque la pipeline cascade interne ne propage pas
     * son heartbeat au workflow_log parent BUILD_CACHE .
     */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT_POOL =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "BuildCacheService-heartbeat");
                t.setDaemon(true);
                return t;
            });

    private static final String KEY_DATA_NAME = "dataName";
    /**
     * Self-injection lazy : permet d'appeler {@link #runBuildAsync} via le
     * proxy Spring ( contournement self-invocation @Async gotcha ) . Sans
     * cette indirection , l'appel direct {@code this.runBuildAsync(...)}
     * bypass le proxy et la methode tourne sync sur le thread caller .
     */
    private final org.springframework.beans.factory.ObjectProvider<BuildCacheService> selfProvider;

    public BuildCacheService(ServiceContainer serviceContainer,
                              OreSiRepository repository,
                              WorkflowLogWriter logWriter,
                              ConfigHashService configHashService,
                              fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleCoordinator coordinator,
                              fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository workflowLogRepository,
                              org.springframework.beans.factory.ObjectProvider<BuildCacheService> selfProvider) {
        this.serviceContainer      = serviceContainer;
        this.repository            = repository;
        this.logWriter             = logWriter;
        this.configHashService     = configHashService;
        this.coordinator           = coordinator;
        this.workflowLogRepository = workflowLogRepository;
        this.selfProvider          = selfProvider;
    }

    /**
     * Demarre un BUILD_CACHE async . Phase 1 synchrone : recordStart
     * workflow_log + retour cid pour polling . Phase 2 async : cascade
     * DISCARD + persist capture + recordEnd .
     *
     * <p>L'audit invariant ( workflow_log start emis avant tout travail
     * et end emis dans le finally ) garantit visibilite oa-live history
     * pour toute demande , meme echec precoce ( fichier introuvable ,
     * application invalide , etc ) .
     */
    public UUID startBuildCache(String applicationName, UUID fileId) {
        UUID correlationId = UUID.randomUUID();
        Instant startTime  = Instant.now();
        UUID    userId     = OreSiApiRequestContext.getRequestUserId();
        String  userLogin  = null;
        String  dataName   = null;
        String  fileName   = null;

        try {
            OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
            userLogin = currentUser != null ? currentUser.getLogin() : null;

            // Fetch minimal metadata via raw JDBC ( avoids triggering the typed
            // BinaryFile deserializer which has cross-field application lookup
            // failing in admin context for legacy rows with null app refs ) .
            Application application = serviceContainer.applicationService().getApplication(applicationName);
            String schemaIdent = "\"" + application.getName() + "\".\"binaryfile\"";
            org.springframework.jdbc.core.JdbcTemplate metaJdbc =
                    new org.springframework.jdbc.core.JdbcTemplate(
                            repository.getRepository(application).data().getDataSource());
            final Map<String, String> meta;
            try {
                meta = metaJdbc.queryForObject(
                    "SELECT name AS file_name , "
                  + "       (params -> 'binaryfiledataset' ->> 'datatype') AS data_name "
                  + "  FROM " + schemaIdent + " WHERE id = ?::uuid",
                    (rs, n) -> Map.of(
                            "fileName", rs.getString("file_name") == null ? "" : rs.getString("file_name"),
                            KEY_DATA_NAME, rs.getString("data_name") == null ? "" : rs.getString("data_name")),
                    fileId.toString());
            } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
                throw new IllegalArgumentException(
                        "Binary file %s not found in application %s".formatted(fileId, applicationName), ex);
            }
            fileName = meta.get("fileName");
            dataName = meta.get(KEY_DATA_NAME).isEmpty() ? null : meta.get(KEY_DATA_NAME);
            if (dataName == null) {
                throw new IllegalArgumentException(
                        "Binary file %s in application %s has no datatype ( params.binaryfiledataset.datatype null ) , cannot build cache"
                                .formatted(fileId, applicationName));
            }

            logWriter.recordStart(buildStartEntry(
                    correlationId, userId, userLogin, applicationName, dataName, fileName,
                    startTime, fileId));

            // Call via Spring proxy ( @Async takes effect only through proxy ;
            // direct this.runBuildAsync(...) bypasses AOP -> sync run ) .
            selfProvider.getObject().runBuildAsync(applicationName, fileId, correlationId,
                    userId, userLogin, dataName, fileName, startTime);
            return correlationId;
        } catch (RuntimeException ex) {
            safeRecordFailure(correlationId, userId, userLogin, applicationName, dataName,
                              fileName, fileId, startTime, ex);
            throw ex;
        }
    }

    /**
     * Phase 2 async : cascade DISCARD + persist capture . Tourne sur le pool
     * @Async configure ( {@code ContextPropagatingTaskDecorator} propage
     * SecurityContext + RequestAttributes + MDC ) .
     */
    @Async
    public void runBuildAsync(String applicationName, UUID fileId, UUID correlationId,
                               UUID userId, String userLogin,
                               String dataName, String fileName, Instant startTime) {
        String finalStatus = WorkflowLogEntry.STATUS_FAILED;
        String fatalError  = null;
        long   processed   = 0L;

        // synthesisLock (app, datatype) : memes locks que publish/unpublish .
        // Empeche un BUILD concurrent avec un publish sur le meme datatype
        // d'ecrire 2 LOs concurrents -> overwrite + orphan . Si lock pris ,
        // on retourne FAILED rapidement plutot que d'attendre indefiniment .
        java.util.concurrent.locks.ReentrantLock lock = coordinator.synthesisLock(applicationName, dataName);
        final long LOCK_TIMEOUT_SEC = 5L;
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            fatalError = "BUILD_CACHE rejected : (app, datatype) lock busy ( publish/unpublish in progress ? )";
            log.warn(fatalError + " : app={} dataName={}", applicationName, dataName);
            Instant endTime = Instant.now();
            try {
                logWriter.recordEnd(buildEndEntry(correlationId, userId, userLogin, applicationName,
                        dataName, fileName, startTime, endTime,
                        java.time.Duration.between(startTime, endTime),
                        finalStatus, 0L, fatalError, fileId));
            } catch (RuntimeException ignored) { /* best-effort */ }
            return;
        }

        // Heartbeat scheduler : tick beat_workflow ( cid ) every 30 s
        // so WorkflowZombieSweeper does NOT mark this BUILD_CACHE as zombie
        // ( cascade pipeline beats its own IMPORT cid , not the parent ) .
        java.util.concurrent.ScheduledFuture<?> heartbeatHandle = HEARTBEAT_POOL.scheduleAtFixedRate(
                () -> {
                    try { workflowLogRepository.beat(correlationId); }
                    catch (RuntimeException ex) {
                        log.debug("BUILD_CACHE heartbeat beat failed for {} : {}", correlationId, ex.getMessage());
                    }
                },
                15, 30, java.util.concurrent.TimeUnit.SECONDS);

        try {
            Application application = serviceContainer.applicationService().getApplication(applicationName);
            BinaryFileRepository bfRepo = repository.getRepository(application).binaryFile();

            final CancellationToken token = CancellationToken.of(() -> coordinator.isCancelled(correlationId));
            CancellationContext.set(correlationId, token);
            try {
                token.throwIfCancelled("BUILD_CACHE entry");

                // Direct COPY capture from referencevalue : the file must have
                // been published at least once for rows to exist . Skips the
                // cascade pipeline entirely - this is a pure SQL dump bound
                // by the disk write bandwidth .
                final String schemaName = repository.getRepository(application).data().getSchemaName();
                bfRepo.storeProcessedDataDirectCopy(fileId, (captureConn, out) -> {
                    out.write(fr.inra.oresing.workflow.cascade.cache.ReferencevalueCacheFormat.buildHeader());
                    fr.inra.oresing.workflow.cascade.cache.ReferencevalueCacheWriter
                            .writeCache(captureConn, schemaName, fileId, out);
                });
                token.throwIfCancelled("post-capture BUILD_CACHE");

                long sizeBytes = bfRepo.findProcessedSize(fileId);
                if (sizeBytes > fr.inra.oresing.workflow.cascade.cache.ReferencevalueCacheFormat.HEADER_SIZE_BYTES) {
                    configHashService.computeHash(application, dataName)
                            .ifPresent(hash -> bfRepo.updateConfigHash(fileId, hash));
                    log.info("BUILD_CACHE persisted : fileId={} processed_size={} bytes ( direct COPY )",
                            fileId, sizeBytes);
                    processed = sizeBytes;
                    finalStatus = WorkflowLogEntry.STATUS_COMPLETED;
                } else {
                    // No rows in referencevalue ( file never published ) - cache cannot be built .
                    log.warn("BUILD_CACHE produced empty cache for fileId={} ( file probably never published )", fileId);
                    finalStatus = WorkflowLogEntry.STATUS_FAILED;
                    fatalError  = "No referencevalue rows for this file ; publish it first .";
                }
            } catch (CancellationException ce) {
                finalStatus = WorkflowLogEntry.STATUS_CANCELLED;
                fatalError  = ce.getMessage() != null ? ce.getMessage() : "Cancelled by admin";
                log.info("BUILD_CACHE cancelled : correlationId={}", correlationId);
            } finally {
                CancellationContext.clear();
            }
        } catch (RuntimeException ex) {
            fatalError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.error("BUILD_CACHE failed : correlationId={} : {}", correlationId, fatalError, ex);
        } finally {
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            try {
                logWriter.recordEnd(buildEndEntry(
                        correlationId, userId, userLogin, applicationName, dataName, fileName,
                        startTime, endTime, duration, finalStatus, processed, fatalError, fileId));
            } catch (RuntimeException ex) {
                log.error("BUILD_CACHE recordEnd failed for {} : {}", correlationId, ex.getMessage());
            }
            coordinator.releaseCancellation(correlationId);
            heartbeatHandle.cancel(false);
            if (lock.isHeldByCurrentThread()) { lock.unlock(); }
            log.info("BUILD_CACHE end : correlationId={} status={} durationMs={}",
                    correlationId, finalStatus, duration.toMillis());
        }
    }

    private WorkflowLogEntry buildStartEntry(UUID cid, UUID userId, String userLogin,
                                              String app, String dataName, String fileName,
                                              Instant startTime, UUID fileId) {
        return new WorkflowLogEntry(
                cid, OreSiWorkflowType.BUILD_CACHE.name(),
                userId, userLogin, app, dataName, fileName,
                startTime, null, null, WorkflowLogEntry.STATUS_IN_PROGRESS,
                0L, 0L, 0, 0L, List.of(), null,
                Map.of("fileId", fileId.toString(), "phase", "BUILD_CACHE"),
                null, null);
    }

    private WorkflowLogEntry buildEndEntry(UUID cid, UUID userId, String userLogin,
                                            String app, String dataName, String fileName,
                                            Instant startTime, Instant endTime, Duration duration,
                                            String status, long processed, String fatalError,
                                            UUID fileId) {
        return new WorkflowLogEntry(
                cid, OreSiWorkflowType.BUILD_CACHE.name(),
                userId, userLogin, app, dataName, fileName,
                startTime, endTime, duration, status,
                processed, 0L, 0, processed, List.of(), fatalError,
                Map.of("fileId", fileId.toString(), "phase", "BUILD_CACHE"),
                null, null);
    }

    private void safeRecordFailure(UUID cid, UUID userId, String userLogin,
                                    String app, String dataName, String fileName,
                                    UUID fileId, Instant startTime, RuntimeException ex) {
        try {
            logWriter.recordStart(buildStartEntry(cid, userId, userLogin, app, dataName, fileName, startTime, fileId));
        } catch (RuntimeException ignored) { /* best-effort */ }
        Instant endTime = Instant.now();
        try {
            logWriter.recordEnd(buildEndEntry(cid, userId, userLogin, app, dataName, fileName,
                    startTime, endTime, Duration.between(startTime, endTime),
                    WorkflowLogEntry.STATUS_FAILED, 0L,
                    "BUILD_CACHE rejected : " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                    fileId));
        } catch (RuntimeException ignored) { /* best-effort */ }
    }
}