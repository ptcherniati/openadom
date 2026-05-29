package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.cancel.CancellationContext;
import fr.inra.oresing.domain.cancel.CancellationToken;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.workflow.cascade.BackendPidRegistry;
import fr.inra.oresing.workflow.cascade.history.HeartbeatService;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Phase 2 ( asynchrone , post-COMMIT ) du
 * {@link PublishLifecycleService} . Decoupe en handler distinct pour :
 *
 * <ul>
 *   <li>permettre au proxy Spring d'appliquer
 *       {@code @TransactionalEventListener + @Async} correctement ( si
 *       le listener etait dans le meme bean que le publisher , l'invocation
 *       traverserait quand meme le proxy ; mais la separation rend
 *       l'intention plus lisible et facilite les tests unitaires
 *       independants ) ;</li>
 *   <li>isoler les responsabilites : la phase 1 est purement metier
 *       transactionnel , la phase 2 est I/O lourde ( cascade pipeline ,
 *       DELETE SQL , recompute synthesis , mail END ) .</li>
 * </ul>
 *
 * <p>Le SecurityContext et MDC sont propages via le
 * {@code ContextPropagatingTaskDecorator} configure dans
 * {@code AsyncExecutorConfiguration} ; les services downstream
 * ( {@code DataService.addData} , {@code DataRepository.removeByFileId} )
 * peuvent donc utiliser l'identite du user d'origine .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class PublishLifecyclePhase2Handler {

    /**
     * Taille de chunk pour le DELETE chunked unpublish / delete-file .
     * 10_000 rows = compromis lock duration ( ~1s sur gros volumes ) vs
     * nombre d'iterations ( 100M / 10k = 10000 iterations , overhead
     * negligeable ) . Permet progression live + cancel cooperative +
     * WAL pressure bornee par chunk au lieu d'un DELETE atomique massif .
     */
    private static final int UNPUBLISH_DELETE_CHUNK_SIZE = 10_000;


    private final ServiceContainer              serviceContainer;
    private final OreSiRepository               repository;
    private final WorkflowLogWriter             logWriter;
    private final PublishLifecycleCoordinator   coordinator;
    private final fr.inra.oresing.workflow.cascade.config.PublishProperties publishProperties;
    private final ConfigHashService             configHashService;
    private final PublishFastPathDirectExecutor fastPathDirectExecutor;
    /**
     * Pour creer des {@link org.springframework.transaction.support.TransactionTemplate}
     * dans les operations atomiques ( DELETE_FILE , commit flag ) executees depuis
     * le @Async handler hors tx Spring native .
     */
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    /**
     * Registry des pg_backend_pid actifs , utilise pour register/deregister
     * autour des phases SQL longues ( snapshot string_agg , DELETE referencevalue )
     * afin que {@code pg_cancel_backend} puisse interrompre le statement en
     * cours quand l'utilisateur clique cancel . Cf {@link BackendPidRegistry} .
     */
    private final fr.inra.oresing.workflow.cascade.BackendPidRegistry backendPidRegistry;

    /**
     * Emetteur de heartbeats periodiques sur {@code oa_audit.workflow_log}
     * pour le workflow PARENT publish/unpublish/delete . Sans ce wiring ,
     * la row parent n'avait jamais de pulse ( seul le child IMPORT cascade
     * en avait un ) , et apres 10 min le {@code WorkflowZombieSweeper}
     * marquait le parent CANCELLED a tort - alors que Phase 2 continuait
     * son travail et finissait par commit {@code binaryfile.published=true} ,
     * produisant la divergence dangereuse {@code workflow_log=CANCELLED}
     * vs {@code binaryfile.published=true} observee en production .
     *
     * <p>Le scope du heartbeat couvre l'integralite de Phase 2 :
     * executeAction ( cascade pipeline ) + commitVisibleFlagAndSynthesis
     * + recordEnd . Pattern try-with-resources garantit l'arret meme en
     * cas d'exception .
     */
    private final HeartbeatService heartbeatService;

    /**
     * P0 cancel-divergence fix : utilise pour acquerir un lock {@code FOR UPDATE}
     * sur la row {@code workflow_log} avant le toggle de {@code binaryfile.published} .
     * Si la row est deja terminale ( CANCELLED par watchdog ou cancel utilisateur ) ,
     * le commit est aborte ( {@link WorkflowAlreadyTerminalException} ) pour
     * preserver l'invariant {@code binaryfile.published=true =>
     * workflow_log.status terminal-COMPLETED} .
     */
    private final WorkflowLogRepository logRepository;

    /**
     * Compensation saga ( audit P0-4 ) : forward-recovery de la suppression
     * de la binaryfile si la phase DELETE_FILE_ROW echoue alors que les rows
     * ont deja ete supprimees . Cf {@code DeleteFileRowCompensationHandler} .
     */
    private final fr.inra.oresing.monitoring.compensation.CompensationLogService compensationLogService;

    /**
     * P0 UX fix : registry in-memory des workflows actifs ( consume par
     * {@code DashboardService.listInProgress} ) . Le PARENT publish est
     * enregistre en Phase 1 ; on appelle {@code finish(parentCid)} ici en
     * Phase 2 finally pour cleanup ( evite fuite a long terme ) . Required=false
     * en cohrence avec PublishLifecycleService ( tests unitaires ) .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry workflowActiveRegistry;

    /**
     * Centralized phase tracker : single API for emitting workflow phase
     * transitions to both {@code workflow_log.metadata.phase} and the
     * in-memory {@link fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry}
     * ( resolves childCid automatically ) . Cf
     * {@link fr.inra.oresing.workflow.phase.WorkflowPhaseTracker} for
     * rationale - replaces the 3-line boilerplate previously inlined at
     * every transition site .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.phase.WorkflowPhaseTracker phaseTracker;

    /**
     * Centralized workflow progress reporter . Publishes the 3 symmetric
     * counters ( total / progress / completed ) so non-cascade workflows
     * ( unpublish , delete-file ) also drive the UI progress bar to
     * 100 % at completion - cf
     * {@link fr.inra.oresing.workflow.phase.WorkflowProgressReporter} .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.phase.WorkflowProgressReporter progressReporter;

    /**
     * Async post-commit cache capture service . Once Phase 2 has flipped
     * {@code binaryfile.published=true} and the workflow has reached
     * COMPLETED / DONE , this service is invoked to capture the binary
     * COPY of referencevalue rows into the binaryfile's processed_data
     * Large Object . Running it async lets the workflow row appear
     * terminated in oa-live ~30 s sooner ( gain on perceived publish
     * duration ; the FAST path remains armed for the next republish
     * once the async capture completes ) .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CacheCaptureService cacheCaptureService;

    /**
     * Factory : new {@code TransactionTemplate} configured with
     * {@code PROPAGATION_REQUIRES_NEW} . All atomic SQL sequences in this
     * handler run in a fresh tx ( spawned from {@code @Async} handler , so
     * outside Spring native tx scope ) ; deduplicating this in one place
     * avoids 4 copies of identical boilerplate and prevents drift if the
     * propagation strategy ever needs to change uniformly .
     */
    private org.springframework.transaction.support.TransactionTemplate newRequiresNewTx() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        tx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }

    public PublishLifecyclePhase2Handler(
            ServiceContainer            serviceContainer,
            OreSiRepository             repository,
            WorkflowLogWriter           logWriter,
            PublishLifecycleCoordinator coordinator,
            fr.inra.oresing.workflow.cascade.config.PublishProperties publishProperties,
            ConfigHashService           configHashService,
            PublishFastPathDirectExecutor fastPathDirectExecutor,
            org.springframework.transaction.PlatformTransactionManager txManager,
            fr.inra.oresing.workflow.cascade.BackendPidRegistry backendPidRegistry,
            HeartbeatService            heartbeatService,
            WorkflowLogRepository       logRepository,
            fr.inra.oresing.monitoring.compensation.CompensationLogService compensationLogService) {
        this.serviceContainer       = serviceContainer;
        this.repository             = repository;
        this.logWriter              = logWriter;
        this.coordinator            = coordinator;
        this.publishProperties      = publishProperties;
        this.configHashService      = configHashService;
        this.fastPathDirectExecutor = fastPathDirectExecutor;
        this.backendPidRegistry     = backendPidRegistry;
        this.txManager              = txManager;
        this.heartbeatService       = heartbeatService;
        this.logRepository          = logRepository;
        this.compensationLogService = compensationLogService;
    }

    /**
     * Entry point invoque par Spring sur publication d'un
     * {@link PublishLifecycleEvent} apres COMMIT de la phase 1 .
     *
     * <p>Cette methode ne propage jamais d'exception : toute erreur metier
     * est captee , persistee dans {@code workflow_log} ( status FAILED +
     * fatalError ) et notifiee a l'utilisateur via mail .
     *
     * <p><b>Publish atomicity - atomicite stricte</b> :
     * le flag {@code binaryfile.params.published} n'est <b>plus mute en phase 1</b>
     * mais directement par cette phase 2 , uniquement <b>apres succes</b> de
     * la cascade pipeline ( methode {@link #commitVisibleFlagAndComplete} ) .
     * Si la cascade echoue , le flag reste dans son etat anterieur , aucune
     * row partielle n'est visible , aucun rollback best-effort n'est requis .
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublishLifecycleEvent(PublishLifecycleEvent ev) {
        log.info("Phase 2 start : correlationId={} action={} fileId={}",
                ev.correlationId(), ev.action(), ev.fileId());
        Application application = serviceContainer.applicationService().getApplication(ev.applicationName());
        Instant     endTime;
        Duration    duration;
        long        recordsProcessed = 0L;
        String      fatalError       = null;
        String      finalStatus      = WorkflowLogEntry.STATUS_FAILED;
        // Note : ancien flag {@code committedAtomically} retire . Le recordEnd
        // est toujours appele dans le finally hors tx commit ( UPSERT idempotent
        // WHERE status='IN_PROGRESS' ; si la row est deja terminale , la clause
        // UPDATE filtre rowCount=0 et le statut existant est preserve ) .

        // P0-1 (cancel divergence fix) : heartbeat scope sur Phase 2 entiere .
        // Sans ce wiring , la row parent workflow_log restait sans pulse et
        // WorkflowZombieSweeper la marquait CANCELLED apres 10 min - en
        // parallele Phase 2 finissait et committait binaryfile.published=true ,
        // creant la divergence "presumed dead vs published" observee . Le
        // try-with-resources garantit l'arret du heartbeat meme sur exception
        // ; il englobe executeAction + commitVisibleFlagAndSynthesis + recordEnd .
        try (HeartbeatService.Heartbeat ignored = heartbeatService.start(ev.correlationId())) {
            try {
            // Early cancellation : la phase 1 d'un workflow plus recent
            // a deja marque celui-ci comme supersede ( reclick utilisateur ) .
            // On termine proprement sans toucher aux donnees .
            if (coordinator.isCancelled(ev.correlationId())) {
                log.info("Phase 2 cancelled before start : correlationId={}", ev.correlationId());
                finalStatus = WorkflowLogEntry.STATUS_CANCELLED;
                fatalError  = "Superseded by user request";
                return;
            }

            // ============================================================
            // STEP 1 : executeAction HORS lock => parallelisme fileIds
            // ============================================================
            // La cascade UPSERT ecrit dans referencevalue avec un discriminant
            // binaryfile=fileId . Deux fileIds differents ecrivent sur des rows
            // distincts ; PG gere les row-locks nativement . Pas de besoin de
            // serialisation a ce stade meme pour le meme datatype .
            //
            // NB : on n'utilise PAS de cascade Action Pattern ( Sources.single +
            // Sinks.action wrap ) malgre l'apparent benefice observability .
            // Raison : cascade execute le body de Sinks.action dans son pool de
            // threads dedie qui n'herite PAS du SecurityContext de l'appelant
            // @Async . Or les services downstream ( ApplicationService.getApplication
            // -> authenticationService.setRoleForClient -> SET LOCAL ROLE )
            // dependent du SecurityContextHolder thread-local . Sous cascade
            // thread , le role n'est pas pose -> RLS refuse l'acces ->
            // NoSuchApplicationException . Le @TransactionalEventListener +
            // @Async standard Spring utilise un pool decore par
            // ContextPropagatingTaskDecorator ( cf . AsyncExecutorConfiguration )
            // qui propage explicitement SecurityContext + RequestAttributes +
            // MDC . C'est ce qu'on garde ici .
            try {
                recordsProcessed = executeAction(application, ev);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ex) {
                // PhaseScope helpers throw checked Exception ; wrap any
                // non-runtime exception as unchecked to keep the existing
                // contract of this async dispatcher .
                throw new RuntimeException(ex);
            }

            // Fix P0-BACK-4 / RC-1 : checkpoint cancel APRES executeAction
            // mais AVANT commit flag . Si l'utilisateur a cancel pendant la
            // fenetre executeAction ( cascade pipeline long ) , on ne committe
            // PAS le flag visible -> rows committed mais flag reste pre-action
            // -> coherent avec le statut CANCELLED . Les rows sont rollback
            // par le pg_cancel_backend si l'UPSERT tournait encore au moment
            // du cancel ; sinon elles restent visibles mais flag inchange =>
            // l'utilisateur peut retry .
            if (coordinator.isCancelled(ev.correlationId())) {
                log.info("Phase 2 cancelled apres executeAction : skip commit flag pour {}",
                        ev.correlationId());
                finalStatus = WorkflowLogEntry.STATUS_CANCELLED;
                fatalError  = "Cancelled by user during executeAction";
                return;
            }

            // ============================================================
            // STEP 2 : narrow lock autour de commitVisibleFlagAndSynthesis
            // ============================================================
            // Serialisation par (app, datatype) UNIQUEMENT autour de la
            // section synthesis rebuild . oresisynthesis est rebuild en
            // DELETE-all + INSERT-from-scratch ; 2 rebuilds concurrents sur
            // le meme datatype = race SQL 55P03 ( cause historique du lock ) .
            //
            // Difference vs version anterieure : le lock englobait executeAction
            // ( 5 min ) , bloquant toute Phase 2 du datatype 5+ min . Resultat :
            // 3 publish back-to-back sur meme datatype = timeout 5 min sur
            // le 3eme . Avec lock narrow autour de commitVisibleFlagAndSynthesis
            // seul ( ~10s ) , les cascades tournent en parallele , seule la
            // section finale est serialisee . Different fileId meme datatype
            // = vrai parallelisme cascade ; meme fileId est deja bloque en
            // amont par advisory fileId + Reject 409 ( PublishLifecycleService ) .
            ReentrantLock lock = coordinator.synthesisLock(ev.applicationName(), ev.dataName());
            boolean acquired;
            try {
                acquired = lock.tryLock(publishProperties.getSynthesisLockTimeoutMinutes(), TimeUnit.MINUTES);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                fatalError = "Interrupted while waiting for synthesis lock";
                return;
            }
            if (!acquired) {
                fatalError = "Timeout waiting for synthesis lock after %d min".formatted(publishProperties.getSynthesisLockTimeoutMinutes());
                log.warn("Phase 2 synthesis lock timeout : correlationId={} app={} datatype={}",
                        ev.correlationId(), ev.applicationName(), ev.dataName());
                return;
            }
            try {
                // Re-check cancel apres acquisition du lock ( wait ~10s par
                // publish concurrent en queue ) : le workflow peut avoir ete
                // supersede entre temps .
                if (coordinator.isCancelled(ev.correlationId())) {
                    log.info("Phase 2 cancelled while waiting synthesis lock : correlationId={}", ev.correlationId());
                    finalStatus = WorkflowLogEntry.STATUS_CANCELLED;
                    fatalError  = "Superseded by user request";
                    return;
                }
                // SYNTHESIS_REBUILD = step dominant ( buildSynthesis 10s-2min )
                // dans commitVisibleFlagAndSynthesis . Emission persistante
                // + in-memory via phaseTracker ( resolve childCid auto pour
                // que le bloc "UPSERT staging -> table finale" rende le
                // spinner ) .
                if (phaseTracker != null) {
                    phaseTracker.transitionTo(ev.correlationId(),
                            fr.inra.oresing.workflow.WorkflowPhase.SYNTHESIS_REBUILD);
                } else {
                    logRepository.updatePhase(ev.correlationId(),
                            fr.inra.oresing.workflow.WorkflowPhase.SYNTHESIS_REBUILD);
                }
                // Capture endTime APRES buildSynthesis pour que la duree
                // affichee dans l'historique reflete le temps total perceptu
                // par l'utilisateur ( pas seulement la fin de la phase
                // cascade ) . Avant ce fix : endTime captured avant la tx
                // -> historique 6m02s vs live 7m36s , incoherence visible
                // par l'utilisateur le temps que buildSynthesis termine .
                commitVisibleFlagAndSynthesis(application, ev,
                        Instant.now(), Duration.between(ev.startTime(), Instant.now()),
                        recordsProcessed);
                endTime  = Instant.now();
                duration = Duration.between(ev.startTime(), endTime);
                invalidateReferencedFilesCacheSilently(application.getName());
                // Cache filterList : publish / unpublish / delete-file changent
                // l'ensemble des rows visibles de data_<dataType> ( ajout /
                // retrait via flag binaryfile.published ou DELETE physique ) ,
                // donc les valeurs distinctes par colonne du payload /filters
                // sont obsoletes . Refresh asynchrone ( Mono boundedElastic )
                // pour rester coherent avec les hooks deja en place sur
                // addData ( OreSiResources L965 ) , deleteData ( L1394 ) et
                // changement de config ( L757 ) . Sans ce hook , le bloc
                // filtre frontend afficherait des valeurs stales jusqu'a la
                // prochaine action mutante ( TTL filterListCache = 0 = pas
                // d'eviction auto ) . Granularite per-dataType : cle cache
                // app::dataType , aucune purge globale .
                refreshFilterListCacheSilently(application, ev.dataName());
                finalStatus = WorkflowLogEntry.STATUS_COMPLETED;
                logRepository.updatePhase(ev.correlationId(),
                        fr.inra.oresing.workflow.WorkflowPhase.DONE);
                // Cache capture async post-DONE : on dispatche la capture
                // binaire de referencevalue dans processed_data ( Large Object )
                // sur un thread separe APRES que le workflow soit visible
                // comme COMPLETED dans oa-live . L'utilisateur n'attend pas
                // les ~30 s de capture ; le FAST path s'arme pour le prochain
                // republish une fois la capture terminee en background .
                // No-op silencieux si {@code openadom.publish.capture-processed-enabled=false}
                // ou si le cache est deja a jour ( configHash match ) .
                if (ev.action() == PublishLifecycleAction.PUBLISH) {
                    if (cacheCaptureService != null) {
                        log.info("Post-DONE : dispatching cacheCaptureService.captureCacheAsync for fileId={} ( correlationId={} )",
                                ev.fileId(), ev.correlationId());
                        cacheCaptureService.captureCacheAsync(application, ev.fileId(), ev.dataName(), ev.correlationId());
                    } else {
                        log.warn("Post-DONE : cacheCaptureService is null - cache will NOT be built for fileId={} . "
                                + "Check Spring wiring ( @Service CacheCaptureService should be auto-injected ) .",
                                ev.fileId());
                    }
                }
            } finally {
                lock.unlock();
            }
        } catch (WorkflowAlreadyTerminalException terminal) {
            // P0-2 cancel-divergence fix : la row workflow_log etait deja
            // terminale ( watchdog ou cancel ) au moment du commit . La tx
            // a rollback , binaryfile.published n'a PAS ete togglee . Etat
            // coherent : workflow_log=CANCELLED + binaryfile.published reste
            // a son etat anterieur ( pas de divergence ) . Le recordEnd qui
            // suit dans le finally sera no-op ( WHERE status='IN_PROGRESS'
            // filtre ) , preservant le statut CANCELLED de la row .
            log.warn("Phase 2 commit aborted - workflow already terminal : correlationId={} : {}",
                    ev.correlationId(), terminal.getMessage());
            fatalError  = terminal.getMessage();
            finalStatus = WorkflowLogEntry.STATUS_CANCELLED;
        } catch (RuntimeException ex) {
            log.error("Phase 2 failed : correlationId={} action={} fileId={} : {}",
                    ev.correlationId(), ev.action(), ev.fileId(), ex.getMessage(), ex);
            fatalError = ex.getMessage();
            // Publish atomicity : pas de rollbackVisibleFlag - le flag n'a
            // jamais ete touche . Etat coherent garanti naturellement .
        } finally {
            endTime  = Instant.now();
            duration = Duration.between(ev.startTime(), endTime);
            if (shouldPersistRecordEnd(ev.correlationId(), finalStatus)) {
                try {
                    logWriter.recordEnd(buildEndEntry(ev, endTime, duration, finalStatus, recordsProcessed, fatalError));
                } catch (RuntimeException ex) {
                    log.error("Phase 2 recordEnd failed for {} : {}", ev.correlationId(), ex.getMessage());
                }
            } else {
                log.warn("Phase 2 recordEnd skipped for {} : cancel detected during commit "
                        + "( workflow_log keeps CANCELLED ; binaryfile.published reflects committed tx )",
                        ev.correlationId());
            }
            try {
                sendEndMail(application, ev,
                        WorkflowLogEntry.STATUS_COMPLETED.equals(finalStatus),
                        fatalError, recordsProcessed);
            } catch (RuntimeException ex) {
                log.warn("Phase 2 sendEndMail failed for {} : {}", ev.correlationId(), ex.getMessage());
            }
            coordinator.releaseCancellation(ev.correlationId());
            // P0 UX fix : cleanup le PARENT registre en Phase 1 dans
            // WorkflowActiveRegistry . Sans ce finish , la row reste en memoire
            // indefinement -> oa-live Live tab afficherait des operations
            // terminees ad infinitum + fuite memoire .
            if (workflowActiveRegistry != null) {
                workflowActiveRegistry.finish(ev.correlationId());
            }
            log.info("Phase 2 end : correlationId={} status={} durationMs={}",
                    ev.correlationId(), finalStatus, duration.toMillis());
        }
        }  // close try-with-resources ( heartbeatService.start ) - heartbeat stopped here
    }

    // ------------------------------------------------------------
    // Action dispatch
    // ------------------------------------------------------------

    private long executeAction(Application application, PublishLifecycleEvent ev) throws Exception {
        return switch (ev.action()) {
            case PUBLISH     -> doPublish(application, ev);
            case UNPUBLISH   -> doUnpublish(application, ev);
            case DELETE_FILE -> doDeleteFile(application, ev);
        };
    }

    /**
     * Routage republish 3-path ( Publish FAST path ) :
     *
     * <ol>
     *   <li><b>FAST</b> : hash de config inchange + {@code processed_data}
     *       cache disponible -> bypass complet {@link DataImporter} via
     *       {@link PublishFastPathDirectExecutor} ( SQL pur :
     *       {@code lo_get + regexp_split_to_table + INSERT direct} dans
     *       {@code referencevalue} ) , pas de staging table intermediaire .</li>
     *   <li><b>LITE</b> : hash inchange mais pas de cache ( fichier
     *       pre-feature ou capture desactivee ) -> cascade avec
     *       {@code lightweight=true} ( skip accumulation cross-chunks ) .
     *       Sert aussi de fallback si FAST echoue ( exception SQL ,
     *       cache corrompu , etc ) .</li>
     *   <li><b>FULL</b> : hash mismatch ( config evoluee ) -> cascade
     *       complete avec re-validation + regeneration cache si capture
     *       activee .</li>
     * </ol>
     *
     * <p>FAST + LITE/FULL convergent vers {@code referencevalue} via UPSERT
     * idempotent ( ON CONFLICT DO UPDATE ) , garantissant la coherence
     * finale independamment du path emprunte .
     */
    private long doPublish(Application application, PublishLifecycleEvent ev) throws IOException {
        // Scope cancellation : tout appel descendant ( DataService.addData ,
        // cascade prep , FAST path ) peut observer le cancel signal via
        // CancellationContext.checkpoint() . La cleanup ThreadLocal est
        // garantie par le finally en fin de methode .
        final java.util.UUID parentCid = ev.correlationId();
        final CancellationToken token = CancellationToken.of(() -> coordinator.isCancelled(parentCid));
        CancellationContext.set(parentCid, token);
        try {
            return doPublishWithinScope(application, ev, token);
        } finally {
            CancellationContext.clear();
            coordinator.unregisterChildImport(parentCid);
        }
    }

    private long doPublishWithinScope(Application application, PublishLifecycleEvent ev, CancellationToken token) throws IOException {
        token.throwIfCancelled("doPublish entry");
        BinaryFileRepository bfRepo = repository.getRepository(application).binaryFile();
        BinaryFile bf = bfRepo.tryFindById(ev.fileId()).orElseThrow(() ->
                new IllegalStateException("File disappeared during phase 2 : " + ev.fileId()));
        BinaryFileInfos params = bf.getParams();
        BinaryFileDataset dataset = params != null ? params.binaryFiledataset() : null;

        boolean hashKnown = configHashService != null
                && params != null && params.configHash() != null;
        boolean hashMatch = hashKnown
                && configHashService.configUnchangedSinceUpload(application, ev.dataName(), params.configHash());
        long processedSize = 0L;
        try { processedSize = bfRepo.findProcessedSize(ev.fileId()); }
        catch (RuntimeException ex) { log.warn("findProcessedSize failed for fileId={} : {}", ev.fileId(), ex.getMessage()); }

        boolean tryFast = publishProperties.isFastPathEnabled()
                && hashMatch
                && processedSize > 0;

        log.info("Phase 2 republish routing : fileId={} datatype={} hashKnown={} hashMatch={} processedSize={} tryFast={}",
                ev.fileId(), ev.dataName(), hashKnown, hashMatch, processedSize, tryFast);

        // ----- Idempotent skip : exact match no-op fast republish -----
        // Si l'utilisateur republie un fichier deja publie ( wasPublished=true )
        // alors que :
        //   - la config datatype est INCHANGEE depuis le dernier publish
        //     ( hashMatch=true ) ,
        //   - le cache binaire est present et aligne avec cette config
        //     ( processedSize > header ) ,
        // alors la table finale referencevalue + reference_reference contient
        // deja exactement les memes rows que ce que produirait un republish
        // complet . Aucun changement de donnees a appliquer : Phase 2 est un
        // no-op fonctionnel . On evite le DELETE + COPY binaire ( ~2-3 mn )
        // du FAST path et la cascade complete ( ~13-15 mn ) , en retournant
        // immediatement . Le caller flippera quand meme binaryfile.published
        // ( idempotent : deja true ) et persistera workflow_log COMPLETED .
        //
        // Garde-fou : on n'applique le skip QUE si la binaryfile etait deja
        // publiee ( wasPublished=true ) - sur un FIRST publish , wasPublished
        // est false meme si un cache pre-existait , et on doit faire le full
        // cycle pour pre-warmer la table finale et generer les rows .
        // Garde-fou : si une depublication / suppression anterieure a echoue
        // ( marquee FAILED par le sweeper zombie - cf migration V12 ) , les
        // donnees peuvent etre dans un etat partiel ( DELETE chunked
        // interrompu mid-flight ) . Dans ce cas , la branche SKIP iso-data
        // declarerait TERMINE sans rien faire alors que des lignes manquent
        // en referencevalue . Forcer une cascade FULL pour reconstruire la
        // coherence ( UPSERT ON CONFLICT recouvrera l'etat partiel ) .
        boolean recentFailedUnpublish = false;
        try {
            recentFailedUnpublish = logRepository.hasRecentFailedUnpublish(ev.fileId(), 24);
        } catch (RuntimeException ex) {
            log.warn("Phase 2 : hasRecentFailedUnpublish check failed ( non-critical , skip path autorisee ) : {}",
                    ex.getMessage());
        }
        if (ev.action() == PublishLifecycleAction.PUBLISH
                && ev.wasPublished()
                && hashMatch
                && processedSize > fr.inra.oresing.workflow.cascade.cache.ReferencevalueCacheFormat.HEADER_SIZE_BYTES
                && !recentFailedUnpublish) {
            log.info("Phase 2 SKIP republish iso-data : fileId={} ( no-op , data + cache + config deja alignes - economie ~2-15 min )",
                    ev.fileId());
            return 0L;
        }
        if (recentFailedUnpublish) {
            log.warn("Phase 2 : SKIP iso-data desactive pour fileId={} : depublication / suppression anterieure FAILED detectee ( etat potentiellement partiel ) - cascade FULL forcee",
                    ev.fileId());
        }

        // ----- FAST path attempt -----
        // INSERT direct depuis processed_data ( Large Object ) vers referencevalue
        // via lo_get + regexp_split_to_table + jsonb_populate_record en SQL pur .
        // Pas de staging table ( elimine pipeline Java text-mode trop lent sur
        // gros JSON cache , ~44x ratio vs CSV raw ) .
        if (tryFast) {
            // Fix P0-BACK-6 : clear processed_data DANS la tx FAST elle-meme
            // ( atomicite cache+rows ) - flag propage a l'executor .
            boolean clearCacheOnSuccess = publishProperties.getPublishMode()
                    == fr.inra.oresing.workflow.cascade.config.PublishProperties.PublishMode.CACHED_ROTATION;
            try {
                long copied = fastPathDirectExecutor.execute(application, ev.fileId(), ev.correlationId(), clearCacheOnSuccess);
                log.info("Phase 2 FAST path success : fileId={} upserted={} rows ( cache clear inline = {} )",
                        ev.fileId(), copied, clearCacheOnSuccess);
                return copied;
            } catch (Exception fastErr) {
                log.warn("Phase 2 FAST path failed for fileId={} - fallback cascade : {}",
                        ev.fileId(), fastErr.getMessage());
                // Continue to cascade below
            }
        }

        // Live phase tracking : cascade path active . On commence par
        // CASCADE_PREPARING pour couvrir la phase opaque ( ~1-3 min sur gros
        // datatypes ) qui se passe dans DataService.getAsynchroneImporterContext :
        // chargement des LineCheckers , resolution displayByNaturalKey ,
        // normalisation CSV , pre-warm reference cache . CASCADE_RUNNING sera
        // publiee plus tard par CascadeImportPipeline.execute quand les workers
        // demarrent reellement le traitement des chunks .
        logRepository.updatePhase(ev.correlationId(), fr.inra.oresing.workflow.WorkflowPhase.CASCADE_PREPARING);

        // ----- LITE / FULL cascade path -----
        FileOrUUID fou = new FileOrUUID(ev.fileId(), dataset, true);
        // Decision lite : Sprint A.5 + Sprint B logic .
        //   hashMatch=true  : config inchangee -> lite safe ( skip O(N) map ) ;
        //   hashKnown=false : fichier pre-feature -> lite safe ( meme raisonnement :
        //                     CSV deja valide au 1er upload , skip cross-row map ) ;
        //   hashKnown && !hashMatch : config evoluee -> FULL re-validation .
        boolean lite = hashMatch || !hashKnown;
        log.info("Phase 2 cascade path : fileId={} lite={} ( hashKnown={} hashMatch={} )",
                ev.fileId(), lite, hashKnown, hashMatch);

        // Scope CancellationContext deja actif ( cf doPublish ) :
        // DataService.addData -> cascade prep observent token via
        // CancellationContext.checkpoint() . parentCid relaye au
        // sub-IMPORT cascade pour le mapping registerChildImport .
        try (InputStream in = bfRepo.streamFileContent(ev.fileId())) {
            serviceContainer.dataService().addData(
                    application,
                    ev.dataName(),
                    new DataFile(fou, in),
                    publishProperties.toRuntimeOverride(),
                    lite);
        }
        // Post-addData : si Phase 2 termine sans throw mais cancel a ete signale ,
        // on ne committe PAS le cache .
        token.throwIfCancelled("post-addData capture");

        // Cache capture is dispatched ASYNC AFTER the workflow reaches
        // COMPLETED / DONE state ( see caller of doPublishWithinScope :
        // {@code cacheCaptureService.captureCacheAsync(...)} ) . Inlining
        // it here used to block the workflow from flipping to DONE for
        // ~30 s on large datasets ; running it post-commit lets oa-live
        // show the workflow terminated as soon as data is visible , while
        // the FAST path cache builds in background . The check for
        // {@code openadom.publish.capture-processed-enabled} and for cache
        // freshness is now inside {@link CacheCaptureService} .

        return 0L; // count authoritatif disponible apres synthesis recompute
    }

    /**
     * DELETE des rows referencevalue pour ce fileId . Le binaryfile reste
     * intact ( on garde la possibilite de republier ) .
     *
     * <p>Mode {@code CACHED_ROTATION} : strategie d'optimisation FAST path .
     * <ul>
     *   <li><b>Cas nominal</b> : le cache {@code processed_data} a deja ete
     *       capture au upload ( Sprint 2 B.2 ) ou au 1er publish ( capture
     *       path dans {@code doPublish} ) , et le {@code configHash} est
     *       enregistre . Le cache reste valide tant que {@code configHash}
     *       n'a pas change ( verification au republish via
     *       {@code tryFast = hashMatch && processedSize > 0} ) . Dans ce cas
     *       l'unpublish fait UNIQUEMENT le DELETE referencevalue - on
     *       PRESERVE le cache existant pour permettre un FAST path au
     *       republish suivant ( gain x100 vs cascade FULL sur 1M+ rows ) .</li>
     *   <li><b>Cas legacy / pre-feature</b> : cache absent ou configHash
     *       manquant ( fichier upload avant Sprint 2 B.2 ) . On tente un
     *       snapshot best-effort via {@code string_agg} server-side pour
     *       alimenter le cache + on capture le hash courant . Si le snapshot
     *       echoue ( typiquement {@code string buffer > 1 GB} sur gros
     *       datasets ) , on retombe sur DELETE seul - le republish fera
     *       cascade FULL ( comportement legacy , pas de regression ) .</li>
     * </ul>
     *
     * <p>Critique : on ne fait PLUS de {@code clearProcessedData} sur
     * snapshot failure - bug observe ou un cache pre-existant valide etait
     * detruit pour rien , forcant le republish a cascade FULL .
     */
    private long doUnpublish(Application application, PublishLifecycleEvent ev) throws Exception {
        DataRepository dataRepo = repository.getRepository(application).data();
        org.springframework.jdbc.core.JdbcTemplate localJdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataRepo.getDataSource());

        // Chaque etape DOIT etre annoncee a l'UI via le PhaseScope :
        //   1. COUNTING_ROWS   ( ~ms , publie recordsTotal )
        //   2. DELETE_ROWS     ( bulk DELETE , pput minutes sur gros volumes )
        // PhaseScope.close() emet la duree finale automatiquement .
        long rowCount;
        try (fr.inra.oresing.workflow.phase.PhaseScope scope =
                     fr.inra.oresing.workflow.phase.PhaseScope.open(phaseTracker, ev.correlationId())) {

            rowCount = scope.run(fr.inra.oresing.workflow.WorkflowPhase.COUNTING_ROWS, () -> {
                long n = dataRepo.countByFileId(ev.fileId());
                if (progressReporter != null) {
                    progressReporter.reportTotal(ev.correlationId(), n);
                }
                log.info("doUnpublish : {} row(s) to unpublish for fileId={}", n, ev.fileId());
                return n;
            });

            scope.run(fr.inra.oresing.workflow.WorkflowPhase.DELETE_ROWS, () -> {
                if (coordinator.isCancelled(ev.correlationId())) {
                    throw new java.util.concurrent.CancellationException("Cancelled before DELETE phase");
                }
                // DELETE chunked avec progression live + cancel cooperative
                // entre chunks . Resout :
                //  - statement_timeout sur fichiers > 1M rows ( ancien DELETE
                //    unique pouvait depasser 1h sur 100M rows ) ;
                //  - WAL pressure ( commit independant par chunk vs WAL massif
                //    en une seule tx ) ;
                //  - oa-live "EN ATTENTE" muet ( progressReporter.reportProgress
                //    fire toutes 10k rows -> bar UI bouge en temps reel ) ;
                //  - cancel SLA 5s ( coordinator.isCancelled consulte entre
                //    chunks au lieu du DELETE atomique non-interruptible ) .
                backendPidRegistry.runWithRegistration(localJdbc, ev.correlationId(), () ->
                        dataRepo.removeByFileIdChunked(
                                ev.fileId(),
                                UNPUBLISH_DELETE_CHUNK_SIZE,
                                deleted -> {
                                    if (progressReporter != null) {
                                        progressReporter.reportProgress(ev.correlationId(), deleted);
                                    }
                                },
                                () -> coordinator.isCancelled(ev.correlationId())));
            });
        }
        // Marque le workflow comme 100 % done dans le registry pour que
        // la bar UI passe de 0 % a 100 % a la completion ( DELETE etant
        // atomique , aucun progres incremental n'est emis pendant son
        // execution ) .
        if (progressReporter != null) {
            progressReporter.reportCompleted(ev.correlationId(), rowCount);
        }
        return rowCount;
    }


    /**
     * DELETE des rows referencevalue ( si le fichier etait publie ) + DELETE
     * de la row binaryfile , dans une seule transaction Spring atomique
     * pilotee par {@link org.springframework.transaction.support.TransactionTemplate} .
     *
     * <p><b>Atomicite garantie</b> : si la 2eme operation echoue ( ex :
     * permission denied sur binaryfile , FK violation , timeout ) , la 1ere
     * est rollback automatiquement par Spring/Postgres . Etat final : soit
     * les deux operations reussissent et commit ensemble , soit rien ne
     * change . Aucune incoherence possible ( rows orphelines sans parent
     * binaryfile , ou inversement ) .
     *
     * <p>{@link org.springframework.transaction.support.TransactionTemplate}
     * choisi plutot que {@code @Transactional} car l'appel se fait depuis
     * une methode privee du meme bean : le proxy Spring AOP serait by-pass
     * et l'annotation ignoree silencieusement . TransactionTemplate
     * fonctionne sans proxy , c'est l'approche recommandee pour les
     * contextes async/event-driven .
     *
     * <p>{@code REQUIRES_NEW} : ce code execute en @Async , donc hors tx
     * Spring native . REQUIRES_NEW garantit une tx fraiche dediee a
     * l'operation atomique , isolee des autres workflows concurrents .
     */
    private long doDeleteFile(Application application, PublishLifecycleEvent ev) throws Exception {
        // Fix P1-BACK-9 : re-check cancellation avant DELETE potentiellement long .
        if (coordinator.isCancelled(ev.correlationId())) {
            throw new java.util.concurrent.CancellationException("Cancelled before DELETE_FILE");
        }
        long rowCount;
        try (fr.inra.oresing.workflow.phase.PhaseScope scope =
                     fr.inra.oresing.workflow.phase.PhaseScope.open(phaseTracker, ev.correlationId())) {

            // Skip precount si le fichier n'etait pas publie ( referencevalue
            // rows absentes par definition , count toujours 0 ) .
            rowCount = ev.wasPublished()
                    ? scope.run(fr.inra.oresing.workflow.WorkflowPhase.COUNTING_ROWS, () -> {
                        long n = repository.getRepository(application).data().countByFileId(ev.fileId());
                        if (progressReporter != null) {
                            progressReporter.reportTotal(ev.correlationId(), n);
                        }
                        log.info("doDeleteFile : {} row(s) to delete for fileId={}", n, ev.fileId());
                        return n;
                    })
                    : 0L;

            scope.run(fr.inra.oresing.workflow.WorkflowPhase.DELETE_ROWS, () -> {
                if (ev.wasPublished()) {
                    // DELETE chunked avec progress live + cancel cooperative
                    // ( cf doUnpublish pour la motivation detaillee ) .
                    repository.getRepository(application).data().removeByFileIdChunked(
                            ev.fileId(),
                            UNPUBLISH_DELETE_CHUNK_SIZE,
                            deleted -> {
                                if (progressReporter != null) {
                                    progressReporter.reportProgress(ev.correlationId(), deleted);
                                }
                            },
                            () -> coordinator.isCancelled(ev.correlationId()));
                }
            });

            scope.run(fr.inra.oresing.workflow.WorkflowPhase.DELETE_FILE_ROW, () -> {
                // Audit P0-4 : forward-recovery compensee . Les rows sont deja
                // supprimees ( phase DELETE_ROWS committee ) ; si la suppression
                // de la binaryfile echoue/crash maintenant , on aurait une
                // binaryfile orpheline ( fichier visible sans donnees ) . On
                // enregistre donc une compensation AVANT ( tx propre committe ) :
                //  - succes -> confirm() ( supprime la row compensation ) ;
                //  - echec  -> compensateNow() ( retry synchrone immediat ) puis
                //    rethrow ; si ca echoue aussi , le CompensationSweeper
                //    terminera la suppression ( idempotent + smart-check
                //    anti-perte ) . Best-effort : un echec du record() ne doit
                //    pas bloquer la suppression nominale .
                java.util.UUID compId = null;
                try {
                    compId = compensationLogService.record(
                            fr.inra.oresing.monitoring.compensation.handlers.DeleteFileRowCompensationHandler.OP_TYPE,
                            application.getName(), "binaryfile", ev.fileId().toString(),
                            ev.correlationId(), ev.userId(), ev.userLogin(),
                            java.util.Map.of("dataName", ev.dataName() == null ? "" : ev.dataName()));
                } catch (RuntimeException recEx) {
                    log.warn("[{}] record compensation DELETE_FILE_ROW echoue ( best-effort , "
                            + "sweeper orphan TTL en filet ) : {}", ev.correlationId(), recEx.getMessage());
                }
                final java.util.UUID compIdFinal = compId;
                try {
                    newRequiresNewTx().executeWithoutResult(status ->
                            serviceContainer.binaryFileService().removeFile(application, ev.fileId()));
                    if (compIdFinal != null) {
                        compensationLogService.confirm(compIdFinal);
                    }
                } catch (RuntimeException delEx) {
                    if (compIdFinal != null) {
                        try {
                            compensationLogService.compensateNow(compIdFinal);
                        } catch (RuntimeException compEx) {
                            log.warn("[{}] compensateNow DELETE_FILE_ROW echoue ( sweeper prendra le relais ) : {}",
                                    ev.correlationId(), compEx.getMessage());
                        }
                    }
                    throw delEx;
                }
            });
        }
        if (progressReporter != null) {
            progressReporter.reportCompleted(ev.correlationId(), rowCount);
        }
        return rowCount;
    }

    // ------------------------------------------------------------
    // Cache / atomic-commit / audit / mail
    // ------------------------------------------------------------

    private void invalidateReferencedFilesCacheSilently(String applicationName) {
        try {
            if (serviceContainer.binaryFileService() instanceof fr.inra.oresing.rest.binaryFile.BinaryFileService bfs) {
                bfs.invalidateReferencedFilesCache(applicationName);
            }
        } catch (RuntimeException ex) {
            log.warn("invalidateReferencedFilesCache failed : {}", ex.getMessage());
        }
    }

    /**
     * Refresh asynchrone du cache filterList ( payload {@code /filters} )
     * pour le dataType cible , en best-effort .
     *
     * <p>Symetrique avec les hooks deja en place sur addData ( OreSiResources
     * L965 ) , deleteData ( L1394 ) et changement de config ( L757 ) .
     * Reutilise {@link fr.inra.oresing.rest.data.DataService#refreshFilterListCache}
     * qui : (i) recompute SQL dans Mono boundedElastic ( pas de blocage du
     * thread Phase 2 ) , (ii) garde l'ancien cache pendant le rebuild ( zero
     * downtime ) , (iii) invalide aussi authorizationScopes + checkedFormat
     * en cascade ( coherence checkers / RLS apres mutation ) .
     *
     * <p>Granularite : cle cache {@code application.name + "::" + dataType} ,
     * aucune purge globale .
     */
    // Package-private pour tests unitaires ( verification appel best-effort
    // + swallow RuntimeException + guards null/blank ) .
    void refreshFilterListCacheSilently(Application application, String dataName) {
        if (dataName == null || dataName.isBlank()) return;
        try {
            serviceContainer.dataService().refreshFilterListCache(application, dataName);
        } catch (RuntimeException ex) {
            log.warn("refreshFilterListCache failed for {}::{} : {}",
                    application.getName(), dataName, ex.getMessage());
        }
    }

    /**
     * Publish atomicity - commit atomique post-cascade .
     *
     * <p>Apres le succes de la cascade pipeline ( referencevalue rows
     * deja committed par cascade dans sa propre tx ) , cette mini-transaction
     * Spring effectue les operations finales :
     * <ul>
     *   <li>toggle du flag {@code binaryfile.params.published} a sa valeur
     *       cible ( PUBLISH -&gt; true , UNPUBLISH/DELETE -&gt; false ) ;</li>
     *   <li>recompute du compteur synthesis ( {@code oresisynthesis} ) .</li>
     * </ul>
     *
     * <p>Si cette mini-tx echoue ( ex lock contention sur synthesis ) ,
     * l'exception est remontee . Le flag reste a son etat anterieur ; la
     * cascade a deja committed ses rows mais le flag visible reste FALSE
     * donc l'utilisateur final ne les voit pas tant que phase 2 ne reussit
     * pas completement . Un retry ulterieur convergera l'etat ( ON CONFLICT
     * UPSERT garantit l'idempotence ) .
     *
     * @throws RuntimeException si toggle ou synthesis fail ( remontee au
     *                          catch global qui marquera workflow FAILED )
     *
     * <p><b>Self-call gotcha</b> : appelee depuis {@link #onPublishLifecycleEvent}
     * de la meme classe , donc proxy Spring AOP by-passe ; {@code @Transactional}
     * serait ignoree silencieusement ( bug P0-BACK-1 ) . On utilise
     * {@link #newRequiresNewTx} pour creer la tx explicitement .
     */
    /**
     * Commit atomique : {@code togglePublishedFlag} + {@code buildSynthesis}
     * en UNE seule tx Spring REQUIRES_NEW . Toute exception rollback les
     * deux operations en bloc .
     *
     * <p>Le {@code recordEnd} ( UPSERT workflow_log ) est volontairement
     * exclu de cette tx pour eviter toute contention avec
     * {@code beat_workflow} ( heartbeat scheduler ) ou
     * {@code mark_zombie_workflows} ( sweeper ) qui ecrivent aussi sur
     * workflow_log . Cf {@link #onPublishLifecycleEvent} bloc finally pour
     * le recordEnd hors tx .
     */
    public void commitVisibleFlagAndSynthesis(Application application, PublishLifecycleEvent ev,
                                              Instant endTime, Duration duration, long recordsProcessed) {
        // Tx UNIQUE pour togglePublished + buildSynthesis . Plus de
        // recordEnd dans cette tx : evite tout SQL touchant workflow_log
        // pendant que la tx tient des locks sur binaryfile / oresisynthesis ,
        // ce qui supprime toute fenetre de contention 55P03 avec
        // beat_workflow / mark_zombie_workflows / autres ecritures
        // concurrentes sur workflow_log .
        //
        // Le recordEnd ( COMPLETED ou FAILED ) est gere par le caller
        // {@link #onPublishLifecycleEvent} dans son bloc {@code finally}
        // via {@link WorkflowLogWriter#recordEnd} ( auto-commit hors tx ) .
        // Cancel-divergence : si un cancel concurrent passe la row a
        // CANCELLED entre le commit ici et le recordEnd post-tx , le
        // recordEnd UPSERT WHERE status='IN_PROGRESS' detecte rowCount=0
        // et log un warn ( WorkflowLogWriter#recordEnd policy P0-4 ) .
        // binaryfile.published reflete l'action effectuee ; workflow_log
        // reste CANCELLED . Fenetre race = quelques us entre 2 statements
        // Java consecutifs , acceptable .
        newRequiresNewTx().executeWithoutResult(status -> {
            boolean targetFlag = targetVisibleFlag(ev.action(), ev.wasPublished());
            switch (ev.action()) {
                case PUBLISH, UNPUBLISH -> repository.getRepository(application).binaryFile()
                        .togglePublishedFlag(ev.fileId(), targetFlag, ev.userId());
                case DELETE_FILE -> {
                    // binaryfile deja supprime par doDeleteFile - pas de flag toggle .
                }
            }
            if (ev.dataName() != null) {
                serviceContainer.synthesisService().buildSynthesis(application.getName(), ev.dataName(), null);
            }
        });
    }

    /**
     * Determine la valeur cible du flag {@code published} selon l'action .
     * Centralise la regle metier pour eviter divergence entre call sites .
     */
    /**
     * Cancel-divergence guard : decide si on doit persister recordEnd ou
     * laisser le statut CANCELLED ecrit par le cancel SQL .
     *
     * <p>Retourne {@code false} uniquement quand le caller s'apprete a
     * ecrire {@code COMPLETED} et qu'un cancel utilisateur a deja ete
     * notifie au coordinator . Dans ce cas le recordEnd est skippe pour
     * preserver le statut CANCELLED en base ( la branche UPDATE de
     * {@code oa_audit.record_workflow} filtrerait de toute facon , mais
     * skipper economise un round-trip + log explicite pour audit ) .
     *
     * <p>Pour FAILED / CANCELLED on retourne toujours {@code true} :
     *  - FAILED : on veut persister l'echec , la clause WHERE filtre
     *    naturellement si la row est deja terminale ( idempotence ) .
     *  - CANCELLED : symmetrie pour les cas de cancel detecte avant
     *    commit ( finalStatus deja mis a CANCELLED dans le catch ) .
     *
     * <p>Visible package-private pour testabilite unitaire ( cf
     * {@code PublishLifecyclePhase2HandlerTest.shouldPersistRecordEnd*} ) .
     *
     * @param cid         workflow correlation id
     * @param finalStatus statut final calcule par Phase 2
     * @return true si recordEnd doit etre persiste ; false si on skip
     */
    boolean shouldPersistRecordEnd(java.util.UUID cid, String finalStatus) {
        if (!WorkflowLogEntry.STATUS_COMPLETED.equals(finalStatus)) {
            return true;
        }
        return cid == null || !coordinator.isCancelled(cid);
    }

    private static boolean targetVisibleFlag(PublishLifecycleAction action, boolean wasPublished) {
        return switch (action) {
            case PUBLISH     -> true;
            case UNPUBLISH   -> false;
            case DELETE_FILE -> wasPublished;  // valeur transitoire ignoree , binaryfile DELETE complet
        };
    }

    private WorkflowLogEntry buildEndEntry(PublishLifecycleEvent ev, Instant endTime,
                                           Duration duration, String status,
                                           long recordsProcessed, String fatalError) {
        Map<String, Object> metadata = Map.of(
                "fileId",       ev.fileId().toString(),
                "wasPublished", ev.wasPublished());
        return new WorkflowLogEntry(
                ev.correlationId(), ev.action().workflowType().name(),
                ev.userId(), ev.userLogin(),
                ev.applicationName(), ev.dataName(), ev.fileName(),
                ev.startTime(), endTime, duration, status,
                recordsProcessed, 0L, 0, 0L, List.of(), fatalError,
                metadata, null, null);
    }

    /**
     * Mail END envoye DIRECTEMENT sans aucune transaction Spring .
     *
     * <p>Le user destinataire est reconstruit a partir des champs
     * captures dans la phase 1 ( {@link PublishLifecycleEvent#userEmail()}
     * + {@link PublishLifecycleEvent#userLogin()} ) - on EVITE
     * {@code authenticationService.getCurrentUser()} qui est
     * {@code @Transactional ( readOnly = true )} . Sous @Async , ce
     * Transactional ouvre une connexion qui , combinee a l'envoi SMTP
     * potentiellement lent ( 5-90 s ) , reste en " idle in transaction "
     * et bloque le pool ( RowExclusive sur workflow_log + oresisynthesis
     * -&gt; cause du lock_timeout 55P03 ) .
     *
     * <p>L'envoi mail lui-meme ne touche pas a la DB ( JavaMailSender
     * SMTP only ) , la connexion n'est jamais retenue meme si le SMTP
     * timeout .
     */
    private void sendEndMail(Application application, PublishLifecycleEvent ev,
                             boolean success, String fatalError, long recordsProcessed) {
        OreSiUser user = userFromEvent(ev);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("End mail skipped ( no recipient email captured in phase 1 ) for {}",
                    ev.correlationId());
            return;
        }
        if (success) {
            // Bug fix : auparavant on passait une liste vide de DataSynthesis ,
            // ce qui faisait que EmailService.sendUpoadSuccessMail ne trouvait
            // jamais d'entree pour ev.dataName() et tombait sur orElse ( 0 ) -
            // d'ou l'email " contains 0 record(s) " systematique apres un
            // publish reussi . On construit ici une entree DataSynthesis
            // minimale qui matche le filter ( referenceType == dataName )
            // et porte le count reel issu de executeAction .
            ApplicationResult.DataSynthesis synthesis = new ApplicationResult.DataSynthesis();
            synthesis.setReferenceType(ev.dataName());
            synthesis.setLineCount((int) Math.max(0L, recordsProcessed));
            DataVersioningResult dvr = DataVersioningResult.of(
                    ev.applicationName(), ev.dataName(), ev.fileId(),
                    List.of(synthesis),
                    ev.action().endMailState());
            try {
                serviceContainer.emailService().sendUpoadSuccessMail(
                        application, ev.dataName(), ev.fileName(),
                        ev.action().endMailState(), ev.locale(), dvr, user);
            } catch (RuntimeException ex) {
                log.warn("End success mail failed for {} : {}", ev.correlationId(), ex.getMessage());
            }
        } else {
            try {
                boolean isReference = !application.isData(ev.dataName());
                serviceContainer.emailService().sendUpoadErrorsMail(
                        ev.locale(),
                        application.getLocalizedLocalName(ev.locale()),
                        ev.dataName(),
                        ev.fileName(),
                        isReference,
                        user,
                        fatalError == null ? "" : fatalError);
            } catch (RuntimeException ex) {
                log.warn("End failure mail send failed for {} : {}", ev.correlationId(), ex.getMessage());
            }
        }
    }

    /**
     * Stub {@link OreSiUser} construit a partir des champs persistes
     * dans l'event ( login + email captures en phase 1 sous tx normale ) .
     * Pas de relecture DB en phase 2 -&gt; pas de tx leak SMTP-induit .
     */
    private OreSiUser userFromEvent(PublishLifecycleEvent ev) {
        if (ev.userEmail() == null) return null;
        OreSiUser user = new OreSiUser();
        user.setId(ev.userId());
        user.setLogin(ev.userLogin());
        user.setEmail(ev.userEmail());
        return user;
    }
}