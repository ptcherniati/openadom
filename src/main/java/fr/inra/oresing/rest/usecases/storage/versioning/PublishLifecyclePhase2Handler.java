package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.cancel.CancellationContext;
import fr.inra.oresing.domain.cancel.CancellationToken;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
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
     * P0 UX fix : registry in-memory des workflows actifs ( consume par
     * {@code DashboardService.listInProgress} ) . Le PARENT publish est
     * enregistre en Phase 1 ; on appelle {@code finish(parentCid)} ici en
     * Phase 2 finally pour cleanup ( evite fuite a long terme ) . Required=false
     * en cohrence avec PublishLifecycleService ( tests unitaires ) .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry workflowActiveRegistry;

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
            WorkflowLogRepository       logRepository) {
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
        // P0 cancel-divergence fix : si le commit a deja persiste status=COMPLETED
        // dans la meme tx que le toggle binaryfile.published , le finally bloc ne
        // doit PAS rappeler recordEnd ( ce serait un double-write potentiellement
        // ecrase par un cancel race - exactement le bug qu'on corrige ) .
        boolean     committedAtomically = false;

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
                // Publish atomicity : cascade pipeline OK -> commit atomique
                // flag + synthesis + recordEnd(COMPLETED) dans UNE seule mini-tx
                // Spring . On capture endTime/duration AVANT la tx pour qu'ils
                // refletent le moment effectif de fin Phase 2 ( pas le moment
                // du commit qui peut s'etaler sur buildSynthesis 10+ sec ) .
                endTime  = Instant.now();
                duration = Duration.between(ev.startTime(), endTime);
                commitVisibleFlagAndSynthesis(application, ev, endTime, duration, recordsProcessed);
                invalidateReferencedFilesCacheSilently(application.getName());
                finalStatus = WorkflowLogEntry.STATUS_COMPLETED;
                committedAtomically = true;
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
            // P0 cancel-divergence fix : skip recordEnd si COMPLETED a deja ete
            // persiste dans la tx atomique de commitVisibleFlagAndSynthesis .
            // Un appel redondant ici ouvrirait la fenetre de race ( cancel
            // arrivant entre tx commit et ce recordEnd marquerait CANCELLED
            // tandis que binaryfile.published serait deja true ) - precisement
            // le bug qu'on corrige . Pour FAILED / CANCELLED , la row n'a pas
            // ete touchee par la tx atomique , il faut bien la persister ici .
            if (!committedAtomically) {
                try {
                    logWriter.recordEnd(buildEndEntry(ev, endTime, duration, finalStatus, recordsProcessed, fatalError));
                } catch (RuntimeException ex) {
                    log.error("Phase 2 recordEnd failed for {} : {}", ev.correlationId(), ex.getMessage());
                }
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

    private long executeAction(Application application, PublishLifecycleEvent ev) throws IOException {
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

        // Capture path : on capture le JSON processed pendant la cascade pour
        // alimenter le cache processed_data , ce qui permettra le FAST path
        // au prochain republish ( meme si hash match mais cache vide actuellement ) .
        java.nio.file.Path capturePath = null;
        boolean shouldCapture = publishProperties.isCaptureProcessedEnabled();
        if (shouldCapture) {
            try {
                capturePath = java.nio.file.Files.createTempFile("oa_litev2_capture_" + ev.correlationId() + "_", ".jsonl");
                capturePath.toFile().deleteOnExit();
                log.debug("Phase 2 cascade capture file : {}", capturePath);
            } catch (IOException ce) {
                log.warn("Phase 2 capture file create failed : {} ( non-critical , continuing without capture )",
                        ce.getMessage());
                capturePath = null;
            }
        }

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
                    lite,
                    capturePath);
        }
        // Post-addData : si Phase 2 termine sans throw mais cancel a ete signale ,
        // on ne committe PAS le capture file ni le flag .
        token.throwIfCancelled("post-addData capture");

        // Cascade reussi : persiste le capture file dans binaryfile.processed_data
        // pour activer le FAST path au prochain republish .
        if (capturePath != null && java.nio.file.Files.exists(capturePath)
                && java.nio.file.Files.size(capturePath) > 0) {
            try (InputStream pin = java.nio.file.Files.newInputStream(capturePath)) {
                long sizeBytes = java.nio.file.Files.size(capturePath);
                bfRepo.storeProcessedData(ev.fileId(), pin, sizeBytes);
                log.info("Phase 2 capture persisted : fileId={} processed_size={} bytes ( FAST path armed for next republish )",
                        ev.fileId(), sizeBytes);
                // Aussi : capture le hash courant pour signifier que cache est aligne avec config actuelle .
                configHashService.computeHash(application, ev.dataName()).ifPresent(currentHash -> {
                    if (params == null || !currentHash.equals(params.configHash())) {
                        bfRepo.updateConfigHash(ev.fileId(), currentHash);
                        log.info("Phase 2 capture configHash updated for fileId={}", ev.fileId());
                    }
                });
            } catch (RuntimeException | IOException persistErr) {
                log.warn("Phase 2 capture persist failed for fileId={} : {} ( non-critical )",
                        ev.fileId(), persistErr.getMessage());
            } finally {
                try { java.nio.file.Files.deleteIfExists(capturePath); } catch (IOException ignored) { /* best-effort */ }
            }
        }

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
    private long doUnpublish(Application application, PublishLifecycleEvent ev) {
        boolean cachedRotation = publishProperties.getPublishMode()
                == fr.inra.oresing.workflow.cascade.config.PublishProperties.PublishMode.CACHED_ROTATION;
        DataRepository dataRepo = repository.getRepository(application).data();
        org.springframework.jdbc.core.JdbcTemplate localJdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataRepo.getDataSource());
        if (!cachedRotation) {
            // Legacy CASCADE_ALWAYS : DELETE simple sans snapshot
            repository.getRepository(application).data().removeByFileId(ev.fileId());
            return 0L;
        }
        // CACHED_ROTATION : fast path optimization for republish .
        BinaryFileRepository bfRepo = repository.getRepository(application).binaryFile();
        long existingCacheSize = 0L;
        try { existingCacheSize = bfRepo.findProcessedSize(ev.fileId()); }
        catch (RuntimeException ex) { log.warn("findProcessedSize failed fileId={} : {}", ev.fileId(), ex.getMessage()); }
        boolean configHashKnown = bfRepo.tryFindById(ev.fileId())
                .map(BinaryFile::getParams)
                .map(BinaryFileInfos::configHash)
                .filter(h -> !h.isBlank())
                .isPresent();
        long snapshotRows = 0L;
        if (existingCacheSize > 0 && configHashKnown) {
            // Cache + hash deja captures ( upload ou 1er publish ) . Cache
            // valide tant que configHash inchange - garde-fou applique au
            // republish via tryFast = hashMatch && processedSize > 0 . On
            // skip le snapshot redondant + couteux ( evite >1GB string_agg
            // qui plantait + clearProcessedData destructeur ) .
            log.info("CACHED_ROTATION unpublish : skip snapshot ( cache deja present , size={} , configHash known ) fileId={}",
                    existingCacheSize, ev.fileId());
        } else {
            // Cache absent OU configHash manquant : fichier pre-feature ou
            // cache jamais capture . Snapshot best-effort pour alimenter
            // cache + configHash . En cas d'echec : log warn , pas de
            // clearProcessedData ( la branch garantit qu'il n'y a rien
            // a perdre = cache absent au depart ) .
            var snapshotTx = newRequiresNewTx();
            try {
                long[] holder = new long[]{0L};
                snapshotTx.executeWithoutResult(status ->
                        backendPidRegistry.runWithRegistration(localJdbc, ev.correlationId(), () -> {
                            holder[0] = bfRepo.snapshotProcessedDataFromReferenceValue(
                                    ev.fileId(), dataRepo.getSchemaName());
                            configHashService.computeHash(application, ev.dataName()).ifPresent(currentHash ->
                                    bfRepo.updateConfigHash(ev.fileId(), currentHash));
                        }));
                snapshotRows = holder[0];
                log.info("CACHED_ROTATION unpublish : snapshot {} rows -> processed_data ( fileId={} , cache was missing )",
                        snapshotRows, ev.fileId());
            } catch (RuntimeException ex) {
                log.warn("CACHED_ROTATION snapshot failed for fileId={} : {} ( fallback : DELETE only , republish ira en cascade )",
                        ev.fileId(), ex.getMessage());
                // Pas de clearProcessedData : on est dans la branche cache-absent ,
                // rien a clear . Suppression du clearProcessedData destructeur qui
                // existait dans la version precedente et detruisait des caches
                // valides pre-existants .
            }
        }
        // Checkpoint cancel avant DELETE potentiellement long .
        if (coordinator.isCancelled(ev.correlationId())) {
            throw new java.util.concurrent.CancellationException("Cancelled before DELETE phase");
        }
        // DELETE referencevalue dans une tx neuve ( evite poison Postgres 25P02
        // si le snapshot precedent a abort ) . pg_cancel_backend possible via
        // backendPidRegistry pour interrompre le DELETE long sur gros datasets .
        var deleteTx = newRequiresNewTx();
        deleteTx.executeWithoutResult(status ->
                backendPidRegistry.runWithRegistration(localJdbc, ev.correlationId(),
                        () -> repository.getRepository(application).data().removeByFileId(ev.fileId())));
        return snapshotRows;
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
    private long doDeleteFile(Application application, PublishLifecycleEvent ev) {
        // Fix P1-BACK-9 : re-check cancellation avant DELETE potentiellement long .
        if (coordinator.isCancelled(ev.correlationId())) {
            throw new java.util.concurrent.CancellationException("Cancelled before DELETE_FILE");
        }
        newRequiresNewTx().executeWithoutResult(status -> {
            if (ev.wasPublished()) {
                repository.getRepository(application).data().removeByFileId(ev.fileId());
            }
            serviceContainer.binaryFileService().removeFile(application, ev.fileId());
        });
        return 0L;
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
    public void commitVisibleFlagAndSynthesis(Application application, PublishLifecycleEvent ev,
                                              Instant endTime, Duration duration, long recordsProcessed) {
        newRequiresNewTx().executeWithoutResult(status -> {
            // P0 cancel-divergence fix - sequence atomique en UNE tx :
            // 1. SELECT FOR UPDATE workflow_log WHERE status='IN_PROGRESS'
            //    Si la row n'est plus IN_PROGRESS ( WorkflowZombieSweeper ou
            //    cancel utilisateur entre executeAction et ici ) , abort -
            //    le toggle visible ne se fera PAS .
            // 2. UPDATE binaryfile.params.published .
            // 3. buildSynthesis ( UPSERT oresisynthesis ) .
            // 4. UPDATE workflow_log SET status='COMPLETED' DANS LA MEME TX .
            //    Critique : recordEnd doit etre dans cette tx , pas dans le
            //    finally du caller . Sinon un cancel arrivant entre le COMMIT
            //    de la tx ici et le recordEnd outer peut marquer la row
            //    CANCELLED alors que binaryfile.published est deja true
            //    ( divergence "Cancelled by admin vs publie le ..." observee
            //    en prod ) . Le lock FOR UPDATE de l'etape 1 bloque le
            //    cancel SQL ( cancel_workflow ) jusqu'au COMMIT ; le cancel
            //    voit alors status='COMPLETED' et son WHERE NOT IN (terminal)
            //    rejette - donc le cancel devient no-op idempotent .
            // Cooperation par lock DB , garantie atomique par construction .
            if (!logRepository.tryLockInProgress(ev.correlationId())) {
                throw new WorkflowAlreadyTerminalException(ev.correlationId());
            }
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
            // Etape 4 : recordEnd COMPLETED dans MEME tx ( recordEndInCurrentTx
            // utilise jdbcTemplate Spring-managed qui partage la connexion ) .
            boolean recorded = logRepository.recordEndInCurrentTx(
                    buildEndEntry(ev, endTime, duration,
                            WorkflowLogEntry.STATUS_COMPLETED, recordsProcessed, null));
            if (!recorded) {
                // Defense en profondeur : le tryLockInProgress devrait deja
                // avoir abort si la row n'etait pas IN_PROGRESS . Si on arrive
                // ici avec recorded=false , c'est qu'un tiers a modifie la
                // row apres notre FOR UPDATE - regression d'invariant , on
                // raise pour rollback le toggle visible .
                throw new WorkflowAlreadyTerminalException(ev.correlationId());
            }
        });
    }

    /**
     * Determine la valeur cible du flag {@code published} selon l'action .
     * Centralise la regle metier pour eviter divergence entre call sites .
     */
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
            DataVersioningResult dvr = DataVersioningResult.of(
                    ev.applicationName(), ev.dataName(), ev.fileId(),
                    List.<ApplicationResult.DataSynthesis>of(),
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
