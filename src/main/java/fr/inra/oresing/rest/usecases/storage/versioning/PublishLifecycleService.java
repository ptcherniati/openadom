package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.workflow.OreSiWorkflowType;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrateur unique de la publication / depublication / suppression
 * d'un fichier binaire , en deux phases :
 *
 * <ol>
 *   <li><b>Phase 1 ( synchrone , transactionnelle )</b> :
 *     <ul>
 *       <li>supersedure : annule un eventuel workflow IN_PROGRESS sur le
 *           meme {@code fileId} ( reclick utilisateur ) ;</li>
 *       <li>insertion {@code workflow_log} statut IN_PROGRESS ;</li>
 *       <li>envoi mail START best-effort ( etat dedie :
 *           {@code PUBLISH_STARTED} / {@code UNPUBLISH_STARTED} /
 *           {@code DELETE_STARTED} ) ;</li>
 *       <li>publication d'un {@link PublishLifecycleEvent} qui declenchera
 *           la phase 2 apres COMMIT .</li>
 *     </ul>
 *     <p><b>Note Publish atomicity</b> : le flag {@code binaryfile.params.published}
 *     <b>n'est plus mute en phase 1</b> . Il est toggle dans une mini-transaction
 *     Spring de phase 2 , <b>uniquement apres succes</b> de la cascade pipeline .
 *     Cela garantit la coherence stricte : si phase 2 echoue , le flag reste dans
 *     son etat anterieur , aucune row partielle n'est visible , et aucun rollback
 *     best-effort n'est requis . Le frontend voit l'ancien etat jusqu'a completion
 *     reussie ( UX : spinner / progress dans oa-live ) .
 *   </li>
 *   <li><b>Phase 2 ( asynchrone , post-commit )</b> :
 *     gere par {@code PublishLifecyclePhase2Handler} sur evenement Spring .
 *     Toggle le flag + mark workflow COMPLETED dans une transaction Spring
 *     dediee apres succes cascade .
 *   </li>
 * </ol>
 *
 * <p>Semantique stricte des actions :
 *
 * <ul>
 *   <li>{@link PublishLifecycleAction#PUBLISH} : cascade pipeline INSERT
 *       depuis le blob binaryfile , puis flag -&gt; true ( atomique phase 2 ) ;</li>
 *   <li>{@link PublishLifecycleAction#UNPUBLISH} :
 *       {@code DELETE FROM referencevalue WHERE binaryfile = fileId} , puis
 *       flag -&gt; false ( atomique phase 2 ) ; le binaryfile reste en place ;</li>
 *   <li>{@link PublishLifecycleAction#DELETE_FILE} : si publie , DELETE rows ,
 *       puis DELETE binaryfile + flag -&gt; false ( phase 2 ) ; si non publie ,
 *       DELETE binaryfile seul .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Service
public class PublishLifecycleService {

    /**
     * Types workflow_log eligibles a la supersedure ( un seul actif a la
     * fois sur un meme fileId ) . On exclut IMPORT car le cascade pipeline
     * lance par {@link PublishLifecycleAction#PUBLISH} cree sa propre row
     * IMPORT distincte ( workflow technique ) qui ne doit pas interferer .
     */
    private static final List<String> ACTIVE_WORKFLOW_TYPES = List.of(
            OreSiWorkflowType.PUBLISH.name(),
            OreSiWorkflowType.UNPUBLISH.name(),
            OreSiWorkflowType.DELETE_FILE.name());

    private final ServiceContainer              serviceContainer;
    private final OreSiRepository               repository;
    private final WorkflowLogWriter             logWriter;
    private final WorkflowLogRepository         logRepository;
    private final ApplicationEventPublisher     events;
    private final PublishLifecycleCoordinator   coordinator;
    private final ConfigHashService             configHashService;

    /**
     * Optional : utilise par {@link #rejectIfWorkflowAlreadyInProgress} pour interroger workflow_log
     * sur le workflow en cours afin d'interrompre son SQL ( fix P1-DB-2 ) .
     * Required=false pour preserver les tests unitaires existants .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.BackendPidRegistry backendPidRegistry;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Optional : registry in-memory des workflows actifs ( cascade + lifecycle
     * metier ) consume par {@code DashboardService.listInProgress} pour le
     * Live tab oa-live . Sans inscription du PARENT publish/unpublish/delete
     * ici en Phase 1 , la liste UI ne voit que les childs cascade IMPORT et
     * notre filter ( isKnownChild ) les cacherait sans rien afficher . Cette
     * registration garantit "1 operation utilisateur = 1 row visible" .
     * Required=false pour preserver tests unitaires .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry workflowActiveRegistry;

    /**
     * Optional : garde-fou heap JVM . Si la pression heap depasse le
     * seuil configure ( defaut 80% ) , les nouvelles demandes
     * publish / unpublish / delete-file sont refusees avec un
     * {@link fr.inra.oresing.workflow.guard.BackendOverloadedException}
     * ( mappable HTTP 503 par le handler global ) pour eviter d'aggraver
     * un OOM imminent .
     * Required=false pour preserver tests unitaires + permettre la
     * desactivation via {@code app.workflow.heap-guard.enabled=false} .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.guard.HeapGuardService heapGuard;

    public PublishLifecycleService(
            ServiceContainer            serviceContainer,
            OreSiRepository             repository,
            WorkflowLogWriter           logWriter,
            WorkflowLogRepository       logRepository,
            ApplicationEventPublisher   events,
            PublishLifecycleCoordinator coordinator,
            ConfigHashService           configHashService) {
        this.serviceContainer  = serviceContainer;
        this.repository        = repository;
        this.logWriter         = logWriter;
        this.logRepository     = logRepository;
        this.events            = events;
        this.coordinator       = coordinator;
        this.configHashService = configHashService;
    }

    // ------------------------------------------------------------
    // Phase 1 - entry points REST
    // ------------------------------------------------------------

    @Transactional
    public UUID startPublish(String applicationName, UUID fileId, Locale locale) {
        return startPhase1(applicationName, fileId, locale, PublishLifecycleAction.PUBLISH);
    }

    @Transactional
    public UUID startUnpublish(String applicationName, UUID fileId, Locale locale) {
        return startPhase1(applicationName, fileId, locale, PublishLifecycleAction.UNPUBLISH);
    }

    @Transactional
    public UUID startDeleteFile(String applicationName, UUID fileId, Locale locale) {
        return startPhase1(applicationName, fileId, locale, PublishLifecycleAction.DELETE_FILE);
    }

    // ------------------------------------------------------------
    // Phase 1 - implementation commune
    // ------------------------------------------------------------

    private UUID startPhase1(String applicationName, UUID fileId, Locale locale,
                             PublishLifecycleAction action) {
        // ----------------------------------------------------------------
        // Garde-fou heap : refuse toute nouvelle demande publish / unpublish /
        // delete-file si le heap JVM est au-dessus du seuil configure
        // ( defaut 80% ) . Cette decision arrive AVANT meme l'audit log car
        // l'objectif est de proteger le backend d'un OOM imminent ; loguer
        // une tentative supplementaire dans workflow_log ne ferait
        // qu'aggraver la pression . Le client retry apres ~1 min , le temps
        // que les workflows en cours liberent du heap .
        if (heapGuard != null && heapGuard.isUnderPressure()) {
            fr.inra.oresing.workflow.guard.HeapGuardService.HeapStats stats =
                    heapGuard.currentStats();
            log.warn("Phase 1 REFUSE : heap pressure ({}%/{}%) for {} on file {} ( application={} )",
                    String.format("%.1f", stats.smoothedUsagePct()),
                    stats.refusePublishThresholdPct(),
                    action, fileId, applicationName);
            throw new fr.inra.oresing.workflow.guard.BackendOverloadedException(
                    stats.smoothedUsagePct(),
                    stats.refusePublishThresholdPct());
        }
        // ----------------------------------------------------------------
        // Audit invariant ( oa-live history visibility ) : TOUTE demande
        // publish / unpublish / delete-file doit apparaitre dans workflow_log ,
        // y compris si Phase 1 echoue precocement ( binaryfile introuvable ,
        // user inconnu , supersede broke , etc ) . On record le START avant
        // toute operation susceptible de throw + on intercepte RuntimeException
        // pour fermer en FAILED .
        //
        // Sequence : ( 1 ) capter le minimum d'info disponible
        //            ( 2 ) recordStart asap ( meme avec dataName / fileName null
        //                  si echec en amont ; on enrichit si on a deja resolu )
        //            ( 3 ) executer logique metier
        //            ( 4 ) sur catch : recordEnd ( FAILED ) + rethrow
        //
        // Le WorkflowLogWriter etant asynchrone ( queue non-bloquante ) , la
        // tx @Transactional courante peut rollback sans annuler l'audit .
        // ----------------------------------------------------------------
        final UUID    correlationId = UUID.randomUUID();
        final Instant startTime     = Instant.now();

        UUID    userId       = OreSiApiRequestContext.getRequestUserId();
        String  userLogin    = null;
        String  userEmail    = null;
        String  dataName     = null;
        String  fileName     = null;
        boolean wasPublished = false;
        boolean startRecorded = false;

        try {
            OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
            userLogin = currentUser != null ? currentUser.getLogin() : null;
            userEmail = currentUser != null ? currentUser.getEmail() : null;

            Application application = serviceContainer.applicationService().getApplication(applicationName);
            BinaryFile binaryFile = repository.getRepository(application).binaryFile().tryFindById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Binary file %s not found in application %s".formatted(fileId, application.getName())));

            dataName     = resolveDataName(binaryFile);
            fileName     = binaryFile.getName();
            wasPublished = isPublished(binaryFile);

            // Fix R1 concurrence : advisory lock per fileId pour serialiser
            // toute Phase 1 entry concurrente sur le meme fichier . Sans cet
            // appel , 2 startPublish quasi-simultanes ( ms apart ) peuvent
            // tous deux lire workflow_log via findActiveByFileId AVANT le
            // INSERT de l'autre ( isolation READ COMMITTED ) et creer 2 rows
            // IN_PROGRESS coexistantes -> supersedure inefficace , 2 Phase 2
            // @Async concurrentes ( risque corruption cache LO orphelin +
            // mails contradictoires + audit invalide ) .
            //
            // pg_advisory_xact_lock : auto-released au COMMIT/ROLLBACK , aucun
            // cleanup manuel requis . hashtext(fileId::text) -> int4 partage
            // l'espace lockspace avec d'autres advisory locks PG mais collision
            // domain-distinct ( probabilite ~10^-9 ) , best-effort acceptable .
            acquireFileIdAdvisoryLock(fileId);

            rejectIfWorkflowAlreadyInProgress(applicationName, fileId);
            // Publish atomicity : flag toggle moved to phase 2 atomic block .
            // Phase 1 limited to supersedure + workflow_log + event emit .

            logWriter.recordStart(buildStartEntry(
                    correlationId, action.workflowType(), userId, userLogin,
                    applicationName, dataName, fileName, startTime, fileId, wasPublished));
            startRecorded = true;

            // P0 UX fix : enregistre le PARENT publish/unpublish/delete dans
            // WorkflowActiveRegistry pour le rendre visible dans le Live tab
            // oa-live ( DashboardService.listInProgress lit cette source ) .
            // Le child cascade IMPORT s'inscrit lui-meme plus tard ; mon filter
            // dans listInProgress ( isKnownChild ) cache le child et garde le
            // parent comme seule row UI - facade pattern "1 operation = 1 row" .
            // Finish appele dans Phase 2 finally pour garantir cleanup .
            if (workflowActiveRegistry != null) {
                workflowActiveRegistry.start(
                        fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot.minimal(
                                correlationId, action.workflowType().name(), userId, userLogin,
                                applicationName, dataName, fileName, startTime,
                                WorkflowLogEntry.STATUS_IN_PROGRESS,
                                0L, 0L, 0, null, 0L, 0L,
                                java.util.List.of(), java.util.List.of()));
            }

            safeSendStartMail(application, dataName, fileName, fileId,
                    action.startMailState(), locale, currentUser);

            events.publishEvent(new PublishLifecycleEvent(
                    correlationId, applicationName, fileId, dataName, fileName,
                    action, wasPublished, locale, userId, userLogin, userEmail, startTime));

            return correlationId;
        } catch (WorkflowAlreadyInProgressException reject) {
            // Reject 409 deliberé : pas un echec , pas de recordStart/End
            // ( pas de pollution workflow_log avec des FAILED parasites ) .
            // Le workflow actif existant garde son statut IN_PROGRESS intact .
            throw reject;
        } catch (RuntimeException ex) {
            // Audit close : paire ( recordStart , recordEnd ) toujours respectee
            // pour faire apparaitre l'echec precoce dans oa-live history .
            safeRecordPhase1Failure(correlationId, action, userId, userLogin,
                    applicationName, dataName, fileName, fileId, wasPublished,
                    startTime, startRecorded, ex);
            throw ex;
        }
    }

    /**
     * Ferme le scope d'audit Phase 1 sur echec precoce :
     * <ul>
     *   <li>si {@code recordStart} n'a pas encore ete emis ( echec dans la
     *       resolution app / binaryfile ou supersedure ) , on l'emet ici pour
     *       respecter l'invariant paire start / end ;</li>
     *   <li>on emet {@code recordEnd} avec status {@code FAILED} + raison
     *       extraite de l'exception ( visible dans oa-live history ) .</li>
     * </ul>
     * Best-effort : si le writer asynchrone echoue ( queue saturee ) , on log
     * sans rethrow pour ne pas masquer l'exception metier originale .
     */
    private void safeRecordPhase1Failure(UUID correlationId, PublishLifecycleAction action,
                                          UUID userId, String userLogin,
                                          String applicationName, String dataName, String fileName,
                                          UUID fileId, boolean wasPublished,
                                          Instant startTime, boolean startAlreadyRecorded,
                                          RuntimeException ex) {
        if (!startAlreadyRecorded) {
            try {
                logWriter.recordStart(buildStartEntry(
                        correlationId, action.workflowType(), userId, userLogin,
                        applicationName, dataName, fileName, startTime, fileId, wasPublished));
            } catch (RuntimeException startEx) {
                log.error("Phase 1 audit recordStart failed for {} ( early error path ) : {}",
                        correlationId, startEx.getMessage());
            }
        }
        Instant endTime = Instant.now();
        java.time.Duration duration = java.time.Duration.between(startTime, endTime);
        try {
            logWriter.recordEnd(new WorkflowLogEntry(
                    correlationId, action.workflowType().name(),
                    userId, userLogin,
                    applicationName, dataName, fileName,
                    startTime, endTime, duration, WorkflowLogEntry.STATUS_FAILED,
                    0L, 0L, 0, 0L, List.of(),
                    "Phase 1 rejected : " + safeMessage(ex),
                    Map.of("fileId", fileId.toString(), "wasPublished", wasPublished, "phase", "PHASE_1"),
                    null, null));
        } catch (RuntimeException endEx) {
            log.error("Phase 1 audit recordEnd failed for {} : {}", correlationId, endEx.getMessage());
        }
    }

    private static String safeMessage(Throwable ex) {
        String msg = ex.getMessage();
        return msg != null && !msg.isBlank() ? msg : ex.getClass().getSimpleName();
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private String resolveDataName(BinaryFile binaryFile) {
        BinaryFileInfos params = binaryFile.getParams();
        if (params == null || params.binaryFiledataset() == null) {
            return null;
        }
        return params.binaryFiledataset().getDatatype();
    }

    private boolean isPublished(BinaryFile binaryFile) {
        BinaryFileInfos params = binaryFile.getParams();
        return params != null && Boolean.TRUE.equals(params.published());
    }

    /**
     * Acquiert un Postgres advisory lock transactionnel scopé sur le fileId
     * courant . Force la serialisation des Phase 1 entries concurrentes pour
     * le meme fichier ( cf javadoc dans startPhase1 ) . No-op si jdbcTemplate
     * n'est pas injecte ( tests unitaires hors Spring context ) .
     *
     * <p>Le lock vit le temps de la {@code @Transactional} courante ; PG le
     * libere automatiquement au COMMIT ou ROLLBACK sans intervention Java .
     * Pas de risque de leak meme si la suite leve une exception .
     */
    private void acquireFileIdAdvisoryLock(UUID fileId) {
        if (jdbcTemplate == null || fileId == null) return;
        try {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_xact_lock(hashtext(?))",
                    Object.class,
                    fileId.toString());
        } catch (RuntimeException ex) {
            // Best-effort : si le SELECT echoue ( PG unavailable , wrong role ) ,
            // on log et on procede ; l'UNIQUE partial index workflow_log_uniq_active_per_file_idx
            // ( Flyway V10 sur oa_audit.workflow_log ) reste la defense-in-depth
            // qui leve une violation SQLSTATE 23505 si 2 INSERT IN_PROGRESS
            // passent malgre tout .
            log.warn("Phase 1 advisory lock failed for fileId={} ( best-effort , falling back on UNIQUE index ) : {}",
                    fileId, ex.getMessage());
        }
    }

    /**
     * Si un workflow lifecycle ( PUBLISH / UNPUBLISH / DELETE_FILE ) est deja
     * IN_PROGRESS sur ce {@code fileId} , leve une {@link WorkflowAlreadyInProgressException}
     * mappee en HTTP 409 Conflict par le {@code OreExceptionHandler} . Le
     * frontend affiche alors une popup explicite a l'utilisateur ( type d'action
     * deja en cours + login de l'auteur ) plutot que de canceler la victime ou
     * d'attendre silencieusement .
     *
     * <p>Combine avec le {@code pg_advisory_xact_lock} pris juste avant + le
     * UNIQUE partial index workflow_log V10 , cette verification garantit qu'au
     * plus UN workflow lifecycle est IN_PROGRESS par fileId a tout instant .
     */
    private void rejectIfWorkflowAlreadyInProgress(String applicationName, UUID fileId) {
        var active = logRepository.findActiveDetailsByFileId(
                applicationName, fileId, ACTIVE_WORKFLOW_TYPES);
        if (active.isEmpty()) {
            return;
        }
        var details = active.get();
        log.info("Phase 1 reject : workflow {} ({}) already in progress on fileId={} ( by {} )",
                details.correlationId(), details.workflowType(), fileId, details.userLogin());
        throw new WorkflowAlreadyInProgressException(
                details.correlationId(), fileId, details.workflowType(), details.userLogin());
    }

    private WorkflowLogEntry buildStartEntry(
            UUID correlationId, OreSiWorkflowType type, UUID userId, String userLogin,
            String applicationName, String dataName, String fileName, Instant startTime,
            UUID fileId, boolean wasPublished) {
        // Audit : flag indiquant si le republish PEUT emprunter le chemin
        // " lite " ( bypass validators ) . Calcul base sur la comparaison
        // entre le hash actuel de la config du datatype et celui qui
        // sera ( ou a ete ) stocke dans binaryfile.params.configHash au
        // moment de l'upload .
        // V1 : on calcule le hash courant et on le persiste dans metadata
        // pour observabilite ( pas encore de skip reel des validators ;
        // cf PUBLISH_UNPUBLISH.md Etape 2 ) .
        String currentConfigHash = serviceContainer.applicationService() != null
                ? configHashService.computeHash(
                        serviceContainer.applicationService().getApplication(applicationName), dataName)
                        .orElse(null)
                : null;
        Map<String, Object> metadata = currentConfigHash != null
                ? Map.of(
                        "fileId",            fileId.toString(),
                        "wasPublished",      wasPublished,
                        "currentConfigHash", currentConfigHash)
                : Map.of(
                        "fileId",       fileId.toString(),
                        "wasPublished", wasPublished);
        return new WorkflowLogEntry(
                correlationId, type.name(), userId, userLogin,
                applicationName, dataName, fileName,
                startTime, null, null, WorkflowLogEntry.STATUS_IN_PROGRESS,
                0L, 0L, 0, 0L, List.of(), null,
                metadata, null, null);
    }

    /**
     * Mail START best-effort : si SMTP / templates indisponibles , on log
     * et on continue . La phase 1 ne doit pas echouer a cause du mail .
     *
     * <p>Le {@code dataSynthesis} est vide ici : on n'a pas encore appele
     * {@code buildSynthesis} ( phase 2 ) , et le compteur affiche dans le
     * mail START sera donc 0 . Comportement assume : le mail START informe
     * que l'operation demarre , le mail END donnera le compteur final .
     */
    private void safeSendStartMail(Application application, String dataName, String fileName,
                                   UUID fileId, EmailService.UPLOAD_STATE state,
                                   Locale locale, OreSiUser user) {
        if (user == null) {
            return;
        }
        try {
            DataVersioningResult dvr = DataVersioningResult.of(
                    application.getName(), dataName, fileId,
                    List.<ApplicationResult.DataSynthesis>of(), state);
            EmailService.class.cast(serviceContainer.emailService()).sendUpoadSuccessMail(
                    application, dataName, fileName, state, locale, dvr, user);
        } catch (RuntimeException ex) {
            log.warn("Start mail failed for fileName={} state={} : {}",
                    fileName, state, ex.getMessage());
        }
    }
}
