package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.cleanup.WorkflowTempCleanup;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.history.WorkflowMetadataCollector;
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

    /**
     * Fallback per-stage pool size when no override is set via
     * {@code -Dcascade.pool.{stage}=N} or {@code CASCADE_POOL_{STAGE}=N} .
     * Mirrors the {@code WorkflowConfig.defaults().defaultParallelism()}
     * value of cascade ( 4 ) so the dashboard header is consistent with
     * what cascade actually allocates.
     */
    private static final int DEFAULT_PARALLELISM = 4;

    private final ImportProperties        importProperties;
    private final ImportProgressReporter  progressReporter;
    private final WorkflowTempCleanup     tempCleanup;
    private final ImportRateLimiter       importRateLimiter;
    private final OpenadomMetrics         metrics;
    private final WorkflowLogWriter       logWriter;
    private final AuthenticationService   authenticationService;
    private final WorkflowActiveRegistry  activeRegistry;
    private final WorkflowMetadataCollector metadataCollector;
    private final fr.inra.oresing.workflow.cascade.history.HeartbeatService heartbeatService;
    private final fr.inra.oresing.monitoring.compensation.CompensationLogService compensationLogService;

    /**
     * Optional : utilise pour mapping parent PUBLISH cid -> child IMPORT cid
     * ( fix BUG-1 cancel propagation ) . Field injection required=false pour
     * preserver les tests qui n'ont pas le coordinator dans leur context .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleCoordinator publishLifecycleCoordinator;

    /**
     * Optional : utilise pour publier la phase CASCADE_RUNNING sur le parent
     * PUBLISH quand le cascade pipeline demarre reellement les workers .
     * Permet a oa-live de distinguer la phase opaque {@code CASCADE_PREPARING}
     * ( pre-warm checkers + reference cache + CSV normalize , 1-3 min ) de
     * la phase {@code CASCADE_RUNNING} ( workers actifs , progress visible ) .
     * Field injection required=false pour preserver les tests sans repository .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository workflowLogRepository;

    /**
     * Pont SPI cascade 3.3.0 -> workflow_log . Quand un futur preparator
     * sera attache via {@code .withDataPreparator(...)} , ses emissions
     * de sous-phases ( {@code CSV_REENCODING} , {@code PREWARM_REFS} ,
     * etc . ) seront routees vers
     * {@code workflowLogRepository.updatePhase(cid, phase)} par ce listener .
     * En l'absence de preparator attache , l'interceptor reste silencieux
     * ( zero overhead ) . Voir
     * {@link fr.inra.oresing.workflow.cascade.preparation.PreparationPhaseListenerInterceptor} .
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.preparation.PreparationPhaseListenerInterceptor preparationPhaseListener;

    public CascadeImportPipeline(
            ImportProperties       importProperties,
            ImportProgressReporter progressReporter,
            WorkflowTempCleanup    tempCleanup,
            ImportRateLimiter      importRateLimiter,
            OpenadomMetrics        metrics,
            WorkflowLogWriter      logWriter,
            AuthenticationService  authenticationService,
            WorkflowActiveRegistry activeRegistry,
            WorkflowMetadataCollector metadataCollector,
            fr.inra.oresing.workflow.cascade.history.HeartbeatService heartbeatService,
            fr.inra.oresing.monitoring.compensation.CompensationLogService compensationLogService) {
        this.importProperties      = importProperties;
        this.progressReporter      = progressReporter;
        this.tempCleanup           = tempCleanup;
        this.importRateLimiter     = importRateLimiter;
        this.metrics               = metrics;
        this.logWriter             = logWriter;
        this.authenticationService = authenticationService;
        this.activeRegistry        = activeRegistry;
        this.metadataCollector     = metadataCollector;
        this.heartbeatService      = heartbeatService;
        this.compensationLogService = compensationLogService;
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
     * Format un Throwable en chaine "Type: message ; cause: Type: message ;
     * ..." pour ne pas perdre le diagnostic root quand fatalError est
     * persiste dans workflow_log . Sans ca , {@code SinkException(
     * "FinalizeHook failed", e)} masque la SQLException sous-jacente
     * ( FK violation , syntax error , etc. ) .
     */
    static String formatThrowable(Throwable t) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        java.util.Set<Throwable> seen = new java.util.HashSet<>();
        while (cur != null && seen.add(cur)) {
            if (sb.length() > 0) sb.append(" ; cause: ");
            sb.append(cur.getClass().getSimpleName()).append(": ");
            sb.append(cur.getMessage() == null ? "(no message)" : cur.getMessage());
            cur = cur.getCause();
        }
        return sb.toString();
    }

    /**
     * Lance un import pour un fichier CSV sans en-tete deja prepare par
     * {@link DataImporter#prepareContextForDataTreatment}.
     *
     * <p>Backward-compat : delegue a la version 8-params avec
     * {@code override = CascadeRuntimeOverride.EMPTY} ( comportement
     * identique au pre-refacto : tous les params lus depuis
     * {@link ImportProperties} ) .
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
            String         dataType,
            UUID           sourceBinaryFileId) {
        execute(dataImporter, referenceValueRepository, headerlessCsv, userId,
                applicationName, dataType, sourceBinaryFileId,
                fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride.EMPTY);
    }

    /**
     * Variante avec override per-call des 6 axes strategiques :
     * pipelineMode , sinkStrategy , stagingStrategy , parallelism ,
     * chunkSizeLines , maxErrorsThreshold ( cf
     * {@link fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride} ) .
     *
     * <p>Tout field non-null de l'override surcharge la valeur de
     * {@link ImportProperties} pour ce workflow uniquement . Les autres
     * fields ( temp dirs , collectorChunkSize , skipCsvReencoding , etc . )
     * restent globaux et lus depuis {@link ImportProperties} .
     *
     * <p>Cas d'usage : {@code PublishLifecyclePhase2Handler.doPublish}
     * passe un override construit depuis {@code PublishProperties}
     * ( profil memoire-friendly ) pendant que l'upload initial passe
     * {@link fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride#EMPTY}
     * ( profil throughput-friendly hérité d'ImportProperties ) .
     *
     * @since openadom phase B publish/unpublish refonte
     */
    public void execute(
            DataImporter   dataImporter,
            DataRepository referenceValueRepository,
            Path           headerlessCsv,
            String         userId,
            String         applicationName,
            String         dataType,
            UUID           sourceBinaryFileId,
            fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride override) {
        execute(dataImporter, referenceValueRepository, headerlessCsv, userId,
                applicationName, dataType, sourceBinaryFileId, override,
                /* preGeneratedCid */ null);
    }

    /**
     * Variante acceptant un correlation id pre-genere par le caller .
     * Cas d'usage : {@code DataService.addData} pre-cree la row workflow_log
     * pour visibilite du depot frais pendant prepareContext . Quand
     * {@code preGeneratedCid} est non null , reuse au lieu de generer un
     * nouveau . {@code logWriter.recordStart} interne reste appele mais
     * il est idempotent ( skip silencieusement sur duplicate primary key ) .
     *
     * @since openadom plan resilience Phase 2 cascade adoption
     */
    public void execute(
            DataImporter   dataImporter,
            DataRepository referenceValueRepository,
            Path           headerlessCsv,
            String         userId,
            String         applicationName,
            String         dataType,
            UUID           sourceBinaryFileId,
            fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride override,
            String         preGeneratedCid) {

        if (!Files.exists(headerlessCsv)) {
            throw new IllegalArgumentException("Input file does not exist: " + headerlessCsv);
        }

        // Resolu une seule fois ici . Toutes les references aux 6 fields
        // override-ables ( pipelineMode , sinkStrategy , stagingStrategy ,
        // parallelism , chunkSizeLines , maxErrorsThreshold ) doivent
        // utiliser ces variables locales et NON importProperties.getXxx() .
        final fr.inrae.ore.cascade.model.workflow.PipelineMode effPipelineMode =
                override.pipelineMode() != null ? override.pipelineMode() : importProperties.getPipelineMode();
        final ImportProperties.SinkStrategy effSinkStrategy =
                override.sinkStrategy() != null ? override.sinkStrategy() : importProperties.getSinkStrategy();
        final ImportProperties.StagingStrategy effStagingStrategy =
                override.stagingStrategy() != null ? override.stagingStrategy() : importProperties.getStagingStrategy();
        final int effParallelismRaw =
                override.parallelism() != null ? override.parallelism() : importProperties.getParallelism();
        final int effChunkSizeLinesRaw =
                override.chunkSizeLines() != null ? override.chunkSizeLines() : importProperties.getChunkSizeLines();
        final int effMaxErrorsThreshold =
                override.maxErrorsThreshold() != null ? override.maxErrorsThreshold() : importProperties.getMaxErrorsThreshold();

        // Quota par utilisateur : 429 Too Many Requests immediat si trop
        // d'imports simultanes. Le slot est libere dans le finally
        // ci-dessous . CR-2 audit : tout le code apres acquireOrThrow doit
        // etre dans le try qui contient le finally release ; sinon une
        // RuntimeException levee pendant l'init ( resolveCurrentLogin ,
        // registerWorkflowStart , ... ) ferait fuir le slot rate-limit .
        importRateLimiter.acquireOrThrow(userId);

        // Identifiants safe ( UUID + parsing local ) declares avant le
        // try afin d'etre visibles dans le finally cleanup .
        // Phase 2 adoption : reuse preGeneratedCid si fourni par caller
        // ( DataService.addData pre-creation row workflow_log ) , sinon
        // generation legacy . Comportement identique quand preGeneratedCid
        // est null .
        final String correlationId = preGeneratedCid != null
                ? preGeneratedCid
                : UUID.randomUUID().toString();
        final UUID   corrUuid      = safeUuid(correlationId);

        // Fix BUG-1 : si on tourne en sub-IMPORT d'un PUBLISH parent ,
        // enregistrer le mapping parent->child dans le coordinator publish
        // pour permettre la propagation du cancel utilisateur .
        // Le ThreadLocal {@link fr.inra.oresing.domain.cancel.CancellationContext}
        // est set par {@link PublishLifecyclePhase2Handler#doPublish} avant
        // l'appel a {@code dataService.addData} . Sans ce mapping , un cancel
        // sur la PUBLISH cid ne touche pas le sub-IMPORT en cours .
        UUID publishParent = fr.inra.oresing.domain.cancel.CancellationContext.currentParentCid();
        if (publishParent != null && corrUuid != null && publishLifecycleCoordinator != null) {
            publishLifecycleCoordinator.registerChildImport(publishParent, corrUuid);
            // Race protection : si le PUBLISH parent a deja ete cancelled
            // PENDANT la preparation du sub-IMPORT ( ex : lecture file , write
            // temp file -> ~10 s ) , le cancel propagation a tente un lookup
            // child empty et n'a rien fait . Maintenant que le sub est registered ,
            // on re-check le flag parent et on abort immediatement si cancelled .
            // Garantie : aucun chunk transform / sink ne demarre si user a annule
            // avant que le sub commence vraiment .
            if (publishLifecycleCoordinator.isCancelled(publishParent)) {
                log.warn("Sub-IMPORT {} : PUBLISH parent {} deja cancelled -> abort immediat , aucun chunk transform/sink ne demarre",
                        corrUuid, publishParent);
                throw new RuntimeException("Parent PUBLISH workflow " + publishParent + " was cancelled before sub-IMPORT could start");
            }
            log.info("Sub-IMPORT cascade {} enregistre comme child de PUBLISH parent {}",
                    corrUuid, publishParent);
            // Publie CASCADE_RUNNING sur le PUBLISH parent : la phase
            // CASCADE_PREPARING ( setup contexte cascade dans DataService ) est
            // termine , les workers cascade s'apprete a demarrer . Permet a
            // oa-live de basculer de "Preparation cascade" a "Cascade en cours" .
            if (workflowLogRepository != null) {
                workflowLogRepository.updatePhase(publishParent, fr.inra.oresing.workflow.WorkflowPhase.CASCADE_RUNNING);
            }
        }

        // Marqueur lu par le finally : a true des qu un runner deferred a
        // ete enregistre sur le TransactionSynchronizationManager Spring .
        // Le runner reprend la finalisation ( log COMPLETED , metrics ,
        // activeRegistry.finish ) en afterCommit / afterCompletion ; le
        // finally doit donc s abstenir de retirer le workflow du registry
        // sinon les consommateurs live le verraient disparaitre AVANT que
        // les donnees soient effectivement chargees en DB .
        final java.util.concurrent.atomic.AtomicBoolean runnerWillFinalize =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        // Heartbeat lifecycle au scope pipeline ( couvre tout le cycle de
        // vie openADOM : pre-cascade , cascade.execute() , post-cascade ,
        // afterCommit ) . Ferme le trou que CascadeHeartbeatBridge ne
        // couvre que pendant cascade.execute() et que HeartbeatService
        // dans la finalize hook ne reprend qu'au moment du UPSERT
        // staging -> table finale . Sans ce wrap , entre la sortie de
        // cascade.execute() et le declenchement de afterCommit ( mode
        // deferred Phase B ) le workflow peut rester sans heartbeat
        // plusieurs minutes -> le sweeper le marque "presumed dead"
        // alors qu il tourne legitimement . Le handle est ferme par
        // les lambdas markCompleted / markPostCommitFailure /
        // markTxRolledBack , avec safety net dans le finally .
        final java.util.concurrent.atomic.AtomicReference<fr.inra.oresing.workflow.cascade.history.HeartbeatService.Heartbeat> pipelineHeartbeat =
                new java.util.concurrent.atomic.AtomicReference<>(fr.inra.oresing.workflow.cascade.history.HeartbeatService.Heartbeat.NOOP);

        // Initialise hors du try uniquement quand l'expression est
        // garantie sans throw . userLogin est inside-try parce que
        // resolveCurrentLogin() peut lever une RuntimeException .
        try {
            // Resolu une seule fois ici ( thread HTTP ) pour etre disponible dans
            // tous les chemins de logImportEvent , y compris ceux rattrapant
            // une exception apres un basculement de contexte.
            final String userLogin = resolveCurrentLogin();

            final Instant startedAt   = Instant.now();
            final String  resourceName = headerlessCsv.getFileName().toString();
            long          fileSizeBytes = 0L;
            try {
                fileSizeBytes = Files.size(headerlessCsv);
            } catch (IOException ignored) {
                // metrics best-effort uniquement
            }

            // #62 - Publie la progression en temps réel dans WorkflowActiveRegistry
            // pour que /api/dashboard/workflows/in-progress puisse
            // afficher ce workflow dès son démarrage. Le finally garantit le
            // retrait du registry même en cas d'erreur ou d'annulation.
            final UUID userUuid = safeUuid(userId);
            registerWorkflowStart(corrUuid, userUuid, userLogin, applicationName, dataType,
                    resourceName, startedAt, fileSizeBytes);

            // Persist the parent linkage on the child IMPORT workflow_log row
            // now that recordStart has inserted it ( previous call site at line
            // 249 raced the INSERT and UPDATEd 0 rows ) . Reads the parentCid
            // from CancellationContext which is set by PublishLifecyclePhase2Handler
            // before invoking the cascade . Without this tag the history endpoint
            // shows two rows per publish ( parent PUBLISH + cascade IMPORT child ) .
            if (publishParent != null && workflowLogRepository != null) {
                workflowLogRepository.setParentCorrelationId(corrUuid, publishParent);
            }

            // Heartbeat lifecycle pipeline-wide ( cf doc pipelineHeartbeat
            // ci-dessus ) . Demarre apres recordStart pour que l UPDATE
            // beat_workflow trouve la row IN_PROGRESS deja persistee .
            if (corrUuid != null) {
                pipelineHeartbeat.set(heartbeatService.start(corrUuid));
            }

            // Publie le binaryfile source pour que WorkflowMetadataCollector
            // puisse l'inclure dans workflow_log.metadata.binaryFileId .
            // IntegrityService s'en sert pour compter les rows referencevalue
            // de ce workflow et detecter les imports incoherents .
            if (sourceBinaryFileId != null && corrUuid != null) {
                activeRegistry.setBinaryFileId(corrUuid, sourceBinaryFileId);
            }

            final Path uploadedPath;
            try {
                uploadedPath = uploadFile(headerlessCsv, userId, correlationId);
            } catch (IOException e) {
                log.error("Erreur lors de la preparation de l'upload pour {} : {}", userId, e.getMessage(), e);
                logErrors(userId, applicationName, userLogin, dataType, e, startedAt, fileSizeBytes, correlationId, resourceName);
                throw new UnsupportedOperationException("Failed to prepare workflow", e);
            }

            // #62 - Compte les lignes du fichier ( deja sans en-tete ) pour
            // permettre aux consommateurs live de basculer la barre de progression en
            // mode determine. Best-effort : si le comptage echoue , on
            // laisse recordsTotal a 0 ( fallback animation indeterminee ).
            long recordsTotal = countLines(uploadedPath);
            if (recordsTotal > 0 && corrUuid != null) {
                activeRegistry.setRecordsTotal(corrUuid, recordsTotal);
            }

            // Cascade-level pool sizes ( source / transform / sink ) sont
            // resolus via les env vars CASCADE_POOL_{STAGE} ( ou les
            // proprietes JVM cascade.pool.{stage} ) avec fallback sur le
            // default cascade ( 4 ) . openAdom n'expose plus de bouton
            // global parallelism : la source de verite est cascade.

            // Pour les referentiels recursifs, l'algorithme de resolution des parents
            // (missingParentLine / testLinesRegardingRecursivity dans DataValidator) suppose
            // un traitement sequentiel fichier entier : une ligne dont le parent n'est pas
            // encore connu est mise en attente, et re-traitee des que le parent est rencontre.
            // Ce mecanisme est brise si le fichier est decoupe en plusieurs chunks paralleles
            // (un chunk peut chercher un parent qui sera traite par un autre chunk non demarre).
            // On force donc chunkSizeLines=MAX_VALUE pour garantir 1 seul chunk (tout le fichier).
            // Consequence : meme avec plusieurs workers dans le pool, il n'y a qu'une seule
            // unite de travail => le traitement est de facto sequentiel.
            final boolean isRecursive = dataImporter.getDataImporterContext().isRecursive();
            final boolean isStrictOrdered = dataImporter.getDataImporterContext().isOrderStrictTaggedOnRecursiveValidation()
                    || importProperties.isOrderedRecursionMode();
            // Resolu inline depuis effChunkSizeLinesRaw / effParallelismRaw
            // calcules en tete d'execute() . Les helpers effectiveXxx
            // gardent la logique recursivite / ordering forcing par compat .
            final int chunkSizeLines  = isRecursive && !isStrictOrdered ? Integer.MAX_VALUE : effChunkSizeLinesRaw;
            final int parallelism     = isRecursive ? 1 : effParallelismRaw;
            final int sourcePoolSize       = resolvePoolSize("source",    parallelism);
            final int transformPoolSize    = resolvePoolSize("transform", parallelism);
            final int rawSinkPoolSize      = resolvePoolSize("sink",      DEFAULT_PARALLELISM);
            final int maxErrors            = effMaxErrorsThreshold;
            final int collectorChunkSize   = importProperties.getCollectorChunkSize();
            final boolean enableMetrics    = importProperties.isEnableMetrics();

            final Path chunksDir    = Paths.get(importProperties.getChunksTempDir(), userId, correlationId);
            final Path processedDir = Paths.get(importProperties.getProcessedTempDir(), correlationId);
            // mergedPath dans processedDir ( workflow-scoped , gere par
            // WorkflowTempCleanup ) au lieu de /tmp racine : evite que
            // systemd-tmpfiles / cron tmpwatch supprime le fichier pendant
            // un workflow long ( cause d'un NoSuchFileException vu en prod ) .
            final Path mergedPath   = processedDir.resolve("merged.csv");

            // FileChunkSource lit la taille de chunk depuis WorkflowConfig
            // ( hook Source.onWorkflowStart ) ; le constructeur ne reçoit
            // qu'une valeur de fallback , la valeur effective vient de
            // .sourceChunkSize() ci-dessous.
            FileChunkSource source = new FileChunkSource(uploadedPath, chunksDir, chunkSizeLines);

            // #62 - Compteurs intermediaires utilises uniquement pour pousser
            // la progression dans WorkflowActiveRegistry . La
            // valeur finale persistee est lue depuis WorkflowResult ( cascade
            // tient deja le compte correct grace a Chunk.recordCount() ).
            final java.util.concurrent.atomic.AtomicLong liveRecords  = new java.util.concurrent.atomic.AtomicLong();
            final java.util.concurrent.atomic.AtomicInteger liveChunks = new java.util.concurrent.atomic.AtomicInteger();

            // #62 - Décorateur autour du reporter existant : on tee les
            // appels onLinesProcessed vers le registry pour alimenter
            // recordsProcessed / chunksProcessed en temps réel.
            ImportProgressReporter teeingReporter = buildRegistryAwareReporter(
                    progressReporter, corrUuid, fileSizeBytes, liveRecords, liveChunks);

            // R-P1-4 — Notifie de suite le nombre total de lignes pour activer
            // la barre de progression ASCII dans LoggingImportProgressReporter.
            teeingReporter.onTotalLinesKnown(correlationId, recordsTotal);

            DataImporterTransformation transformation = new DataImporterTransformation(
                    dataImporter,
                    importProperties,
                    teeingReporter,
                    processedDir,
                    correlationId);

            // Strategy switch ( cascade 1.7.0 + ) :
            //   MERGE_FILE  : MergingFileSink + storeAll(merged.csv)  -- legacy , default
            //   DIRECT_COPY : StagingPostgresSink with FinalizeHook  -- production
            //   DISCARD     : Sinks.discard() no-op sink ( cascade 3.2.0 ) -- admin
            //                 pre-compute path : validators + transformers + chunk
            //                 emit run normally ( processed_data capture file
            //                 alimente par DataImporter.convertToCSVLine ) mais
            //                 le sink est un /dev/null . Aucune ecriture sur
            //                 referencevalue , aucune staging table requise .
            ImportProperties.SinkStrategy strategy = effSinkStrategy;
            boolean directCopy = strategy == ImportProperties.SinkStrategy.DIRECT_COPY;
            boolean discard    = strategy == ImportProperties.SinkStrategy.DISCARD;

            // PER_WORKFLOW_TABLE : CREATE UNLOGGED TABLE oa_staging.referencevalue_import_<corrid>
            // avant que cascade demarre . La table sera DROPpee apres
            // succes ( finally bloc de teardown plus bas ) ou par le
            // sweeper orphan apres TTL si le workflow crash en cours .
            fr.inra.oresing.workflow.cascade.staging.StagingMode stagingMode = directCopy
                    ? fr.inra.oresing.workflow.cascade.staging.StagingMode.of(
                            effStagingStrategy, corrUuid,
                            importProperties.getStagingSharedTableName(),
                            importProperties.getStagingSharedOrphanTtlMinutes())
                    : null;
            if (stagingMode != null && stagingMode.createTableSql() != null) {
                try (java.sql.Connection c = referenceValueRepository.getDataSource().getConnection();
                     java.sql.Statement st = c.createStatement()) {
                    st.execute(stagingMode.createTableSql());
                    log.info("[{}] PER_WORKFLOW_TABLE : table dediee {} creee",
                            correlationId, stagingMode.tableName());
                } catch (java.sql.SQLException e) {
                    log.error("[{}] CREATE staging table failed : {}", correlationId, e.getMessage());
                    throw new UnsupportedOperationException("Cannot create per-workflow staging table", e);
                }
            }

            // Enregistrement compensation_log STAGING_CLEANUP : garde-fou contre
            // les staging tables / rows orphelines ( JVM crash entre le CREATE
            // et la fin du finalize hook ) . Confirm en post-success ; sweeper
            // ramasse en cas de crash via {@link StagingCleanupHandler} qui
            // applique un smart-check ( refuse cleanup si workflow_log encore
            // IN_PROGRESS legitime ) . Skip pour PER_CONNECTION_TEMP : la TEMP
            // TABLE meurt avec la connexion , aucun cleanup necessaire .
            final java.util.concurrent.atomic.AtomicReference<java.util.UUID> stagingCompensationIdRef =
                    new java.util.concurrent.atomic.AtomicReference<>();
            if (directCopy
                    && effStagingStrategy != ImportProperties.StagingStrategy.PER_CONNECTION_TEMP
                    && corrUuid != null && stagingMode != null) {
                String fullTableName = stagingMode.tableName();
                String[] parts = fullTableName.split("\\.", 2);
                String compSchema = parts.length == 2 ? parts[0] : "oa_staging";
                String compTable  = parts.length == 2 ? parts[1] : fullTableName;
                try {
                    java.util.UUID compId = compensationLogService.record(
                            fr.inra.oresing.monitoring.compensation.handlers.StagingCleanupHandler.OP_TYPE,
                            compSchema,
                            compTable,
                            correlationId,
                            corrUuid, userUuid, userLogin,
                            java.util.Map.of(
                                    "stagingStrategy", effStagingStrategy.name(),
                                    "tableName",       fullTableName,
                                    "correlationId",   correlationId),
                            importProperties.getStagingSharedOrphanTtlMinutes());
                    stagingCompensationIdRef.set(compId);
                    log.info("[{}] STAGING_CLEANUP compensation enregistree : id={} table={}",
                            correlationId, compId, fullTableName);
                } catch (RuntimeException ex) {
                    // Best-effort : si la DB est down sur l'INSERT compensation ,
                    // on continue le workflow ( fallback : sweeper orphan TTL passif
                    // existant ) . Loggue warn pour signal .
                    log.warn("[{}] Echec record compensation STAGING_CLEANUP : {} ; fallback sweeper orphan TTL",
                            correlationId, ex.getMessage());
                }
            }

            // Sink + ( optionnel ) Collector selon strategy :
            //   DIRECT_COPY : 1 sink chunk-par-chunk vers staging DB ;
            //                 pas de collector .
            //   MERGE_FILE  : Collector qui accumule les chunk paths et les
            //                 concatene en merged.csv a finish() , puis Sink
            //                 qui prend ce 1 chunk merge et fait 1 COPY DB
            //                 massif via storeAll . UI cascade reflete
            //                 fidelement le travail reel ( cf rationale dans
            //                 javadoc MergedFileChunkCollector ) .
            // Cascade 3.0.0 : si on tourne dans une tx Spring outer ( cas
            // openADOM standard via @Transactional sur le controller ) , on
            // passe le sink en DEFERRED_TO_CALLER pour que le UPSERT
            // staging -> table finale soit execute apres la commit Spring
            // sur la connexion du caller ( evite le deadlock sink-thread vs
            // caller-thread sur les row-locks de la table finale ) . Hors
            // tx Spring ( tests directs , scripts ) on conserve
            // SYNCHRONOUS = comportement cascade 2.x .
            // S applique aux deux strategies : DIRECT_COPY ( deferred via
            // FinalizeMode.DEFERRED_TO_CALLER cascade 3.0.0 ) ET MERGE_FILE
            // ( deferred via StoreAllPathSink.deferToCaller phase B ) .
            final boolean outerTxActive = org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive();
            final fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeMode finalizeMode = (directCopy && outerTxActive)
                    ? fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeMode.DEFERRED_TO_CALLER
                    : fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeMode.SYNCHRONOUS;
            if (outerTxActive) {
                log.info("[{}] Outer Spring tx active : sink configure en mode deferred ( finalize execute en afterCommit ) - strategy={}",
                        correlationId, strategy);
            }

            // MERGE_FILE deferred : on garde une reference typee sur le sink
            // pour pouvoir ensuite recuperer le path capte via takeDeferredMergedPath .
            // DISCARD : ni mergeFileSink ni staging requis ; on injecte un sink no-op .
            StoreAllPathSink mergeFileSink = (directCopy || discard)
                    ? null
                    : new StoreAllPathSink(referenceValueRepository, activeRegistry, outerTxActive);
            fr.inrae.ore.cascade.model.core.Sink<java.nio.file.Path> sink;
            if (discard) {
                sink = fr.inrae.ore.cascade.api.Sinks.discard();
            } else if (directCopy) {
                sink = CascadeSinkFactory.directCopy(referenceValueRepository, importProperties, corrUuid, heartbeatService, activeRegistry, finalizeMode);
            } else {
                sink = mergeFileSink;
            }
            // DISCARD : pas de collector ( les chunks ne sont pas merges ) ,
            // PipelineMode peut etre STAGED ou PIPELINED indifferemment .
            MergedFileChunkCollector mergeCollector = (directCopy || discard)
                    ? null
                    : new MergedFileChunkCollector(mergedPath);

            // MERGE_FILE necessite un Collector ( pour concatener les
            // chunks ) ; cascade PIPELINED ne supporte pas les Collectors
            // ( bounded queue + transform/sink overlap incompatibles avec
            // un sync-point comme finish() ) . On force donc STAGED ici
            // ; PIPELINED n'apporterait rien de toute facon car le merge
            // est intrinsequement un sync-point . La config UI
            // ( ConfigEditForm ) desactive aussi le dropdown pipelineMode
            // pour MERGE_FILE pour que ca soit visible .
            fr.inrae.ore.cascade.model.workflow.PipelineMode effectivePipelineMode =
                    (directCopy || discard)
                            ? effPipelineMode
                            : fr.inrae.ore.cascade.model.workflow.PipelineMode.STAGED;
            if (!directCopy && !discard && effPipelineMode
                    != fr.inrae.ore.cascade.model.workflow.PipelineMode.STAGED) {
                log.info("[{}] MERGE_FILE force pipelineMode=STAGED ( cascade PIPELINED ne supporte pas les Collectors ) ; "
                        + "valeur configuree {} ignoree pour ce workflow",
                        correlationId, effPipelineMode);
            }

            log.info("[{}] Demarrage import : user={}, file={}, chunkSize={}, pools=[source={},transform={},sink={}], "
                    + "maxErrors={}, metrics={}, sinkStrategy={}, pipelineMode={}",
                    correlationId, userId, uploadedPath.getFileName(), chunkSizeLines,
                    sourcePoolSize, transformPoolSize, rawSinkPoolSize,
                    maxErrors, enableMetrics, strategy, effectivePipelineMode);

            // Construct workflow pipeline ; en MERGE_FILE on insere le
            // {@link MergedFileChunkCollector} entre transform et sink pour
            // que la concatenation chunk-files -> merged.csv passe par
            // l'API cascade Collector ( == accumulation + post-process )
            // au lieu d'etre cachee dans un faux Sink .
            @SuppressWarnings({ "rawtypes", "unchecked" })
            fr.inrae.ore.cascade.model.workflow.builder.WorkflowPipeline pipeline =
                    WorkflowBuilder.create()
                            .forUser(userId)
                            .from(source)
                            .transform(transformation);
            if (mergeCollector != null) {
                pipeline = pipeline.collect(mergeCollector);
            }
            @SuppressWarnings("unchecked")
            fr.inrae.ore.cascade.model.workflow.builder.WorkflowPipelineConfig builder =
                    pipeline
                            .to(sink)
                            .withCorrelationId(correlationId)
                            .withSourceChunkSize(chunkSizeLines)
                            .withCollectorChunkSize(collectorChunkSize)
                            .withMaxErrors(maxErrors)
                            .withPipelineMode(effectivePipelineMode)
                            .withPipelineQueueCapacity(importProperties.getPipelineQueueCapacity());

            // Cascade 3.3.0 : attache le listener PreparationInterceptor pour
            // router les sous-phases emises par un futur DataPreparator vers
            // workflow_log.metadata.phase . Sans preparator attache , aucune
            // emission n'a lieu et l'interceptor reste silencieux . Le wiring
            // ici est prospectif : il evite que chaque futur preparator ait
            // a se soucier d'enregistrer son propre listener observability .
            if (preparationPhaseListener != null) {
                builder = builder.withInterceptor(preparationPhaseListener);
            }

            // Sink parallelism wiring ( cascade 2.1.0 ) :
            //   - DIRECT_COPY + PER_CONNECTION_TEMP : sticky connection ,
            //     force sinkParallelism=1 ( PgConnection pas thread-safe et
            //     TEMP table portee par cette connexion uniquement ) .
            //   - DIRECT_COPY + SHARED_UNLOGGED / PER_WORKFLOW_TABLE :
            //     sinkParallelism=pool.sink ( workers paralleles , table
            //     UNLOGGED accessible cross-conn ) .
            //   - MERGE_FILE : sinkParallelism=1 ( agregateur fichier
            //     intrinsequement serie ; pool.sink ignore ) .
            // Cascade 2.1.0 default sinkParallelism=1 ; donc sans cet appel
            // explicite le sink reste sequentiel meme si pool.sink=4 .
            boolean stickyConnection = directCopy
                    && effStagingStrategy == ImportProperties.StagingStrategy.PER_CONNECTION_TEMP;
            int effectiveSinkPar;
            if (stickyConnection) {
                effectiveSinkPar = 1;
                if (rawSinkPoolSize > 1) {
                    log.warn("[{}] DIRECT_COPY + PER_CONNECTION_TEMP force sinkParallelism=1 ( connexion sticky unique ) ; "
                            + "CASCADE_POOL_SINK={} reste configure mais le sink est sériel",
                            correlationId, rawSinkPoolSize);
                }
            } else if (!directCopy) {
                effectiveSinkPar = 1;     // MERGE_FILE intrinsequement serie
            } else {
                effectiveSinkPar = Math.max(1, rawSinkPoolSize);
            }
            builder = builder.withSinkParallelism(effectiveSinkPar);

            if (enableMetrics) {
                builder = builder.enableMetrics();
            }
            Workflow workflow = builder.build();

            // Publie le parallélisme effectif dans le registry pour que
            // l'en-tête de la vue Workers live affiche le nombre de
            // threads par stage . Le sink est forcé à 1 quand
            // DIRECT_COPY + PER_CONNECTION_TEMP.
            int sinkSlots = stickyConnection ? 1 : rawSinkPoolSize;
            if (corrUuid != null) {
                activeRegistry.setParallelism(corrUuid,
                        new fr.inra.oresing.workflow.cascade.history.ParallelismSnapshot(
                                sourcePoolSize, transformPoolSize, sinkSlots));
                activeRegistry.setStrategy(corrUuid,
                        new fr.inra.oresing.workflow.cascade.history.StrategySnapshot(
                                strategy.name(),
                                directCopy ? effStagingStrategy.name() : null,
                                effectivePipelineMode.name(),
                                rawSinkPoolSize));
                activeRegistry.setImportConfig(corrUuid,
                        new fr.inra.oresing.workflow.cascade.history.ImportConfigSnapshot(
                                chunkSizeLines,
                                importProperties.getProgressBatchSize(),
                                maxErrors,
                                collectorChunkSize,
                                importProperties.getStagingSharedOrphanTtlMinutes(),
                                importProperties.getStagingSharedTableName(),
                                enableMetrics,
                                importProperties.isSkipCsvReencoding(),
                                sourcePoolSize,
                                transformPoolSize,
                                rawSinkPoolSize,
                                resolvePoolSize("ordering", 0)));
            }

            try {
                // Phase : traitement ( chunking + transformation + merge ).
                updateWorkflowPhase(corrUuid, WorkflowLogEntry.STATUS_PROCESSING, fileSizeBytes);
                // Hook bloc CHARGEMENT FINAL : capture cascadeStart .
                if (corrUuid != null) {
                    activeRegistry.initFinalizePhase(corrUuid, startedAt);
                }

                // TODO ( P1 #4 extension , cf TODO_LOCAL.md ) : integrer un
                // heartbeat sur les phases source / transform de cascade .
                // Aujourd'hui {@link HeartbeatService} ne tourne que pendant
                // la finalize hook ( cf {@link CascadeSinkFactory.directCopy} ) .
                // Si cascade source/transform/copy-staging hangue > N min sans
                // emettre de progress event , le sweeper utilisera le fallback
                // start_time -> threshold actuel ( app.workflow.zombie-threshold-minutes )
                // doit donc couvrir le plus long workflow legitime sans heartbeat .
                // Pour fermer ce trou : hook les ChunkProcessedEvent cascade pour
                // beat regulier ( debounce 30 sec ) ou wrapper workflow.execute()
                // dans HeartbeatService.start ( option simple mais beat sans
                // distinction de phase ) .
                WorkflowResult result = workflow.execute();
                // {@code cascadeFinishedAt} est marque dans le registry quand
                // recordsProcessed atteint recordsTotal ( = cascade emit 100 %
                // avant teardown / finalize hook ) , declenche par
                // {@link WorkflowActiveRegistry#update} . Pour DIRECT_COPY ce
                // marquage arrive AVANT le finalize hook qui tourne dans
                // workflow.execute() . Pour MERGE_FILE on s'en sert quand meme
                // comme delimiteur "fin de cascade emit" et la phase finalize
                // continue avec storeAll(merged.csv) ci-dessous .
                if (corrUuid != null) {
                    // Fallback : si recordsTotal etait 0 ( fichier non compte ) ,
                    // l'auto-detection 100% n'a pas pu se declencher . On le
                    // marque ici en post-execute pour avoir au moins T2 = 0 .
                    activeRegistry.findFinalizePhase(corrUuid).ifPresent(p -> {
                        if (p.cascadeFinishedAt() == null) {
                            activeRegistry.markCascadeFinished(corrUuid, Instant.now());
                        }
                    });
                }
                if (result.status() == ProcessingStatus.CANCELLED) {
                    // Cascade signale annulation utilisateur ( WorkflowEventBus.cancel
                    // observe par les chunks ) . Persiste CANCELLED dans workflow_log
                    // pour que l'onglet Historique l'affiche cote dashboard
                    // ( terminalOnly filter exclut IN_PROGRESS / UPLOADING / CHUNKING
                    // / PROCESSING / LOADING_DB mais pas CANCELLED ) .
                    Duration cancDuration = Duration.between(startedAt, Instant.now());
                    String cancelMessage = result.fatalError()
                            .map(CascadeImportPipeline::formatThrowable)
                            .orElseGet(() -> result.errors().isEmpty()
                                    ? "Cancelled by user"
                                    : result.errors().get(0));
                    log.info("[{}] Workflow cascade cancelled : processed={} chunks={} reason={}",
                            correlationId, result.recordsProcessed(),
                            result.chunksProcessed(), cancelMessage);
                    metrics.recordImportCompleted(applicationName, dataType,
                            WorkflowLogEntry.STATUS_CANCELLED,
                            cancDuration, result.recordsProcessed(), result.recordsFailed(),
                            result.chunksProcessed(), fileSizeBytes);
                    logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                            startedAt, cancDuration, WorkflowLogEntry.STATUS_CANCELLED,
                            result.recordsProcessed(), result.recordsFailed(),
                            result.chunksProcessed(), fileSizeBytes,
                            result.errors(), cancelMessage, null);
                    tempCleanup.cleanup(chunksDir, processedDir, uploadedPath);
                    if (corrUuid != null) {
                        activeRegistry.finish(corrUuid);
                    }
                    return;
                }
                if (result.status() == ProcessingStatus.FAILED) {
                    String firstError = result.errors().isEmpty()
                            ? result.fatalError().map(CascadeImportPipeline::formatThrowable).orElse("unknown error")
                            : result.errors().get(0);
                    // Stack trace complet logged cote serveur pour le diagnostic
                    // ( fatalError ne garde que le message dans workflow_log ) .
                    result.fatalError().ifPresent(t ->
                            log.error("[{}] Workflow cascade en echec ( stack ) :", correlationId, t));
                    // cascade 2.2.0 : recupere le stage attribuee par cascade
                    // ( SOURCE / TRANSFORM / COLLECTOR / SINK / TEARDOWN /
                    // UNKNOWN ) pour persistance + Prometheus tag .
                    String failedStage = result.failedStage()
                            .map(Enum::name)
                            .orElse(fr.inrae.ore.cascade.model.workflow.WorkflowStage.UNKNOWN.name());
                    log.error("[{}] Workflow cascade en echec ( stage={} ) : {}",
                            correlationId, failedStage, firstError);
                    Duration failDuration = Duration.between(startedAt, Instant.now());
                    metrics.recordImportFailed(applicationName, dataType, failedStage,
                            failDuration, result.recordsProcessed(), result.recordsFailed(),
                            result.chunksProcessed(), fileSizeBytes);
                    logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                            startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                            result.recordsProcessed(), result.recordsFailed(),
                            result.chunksProcessed(), fileSizeBytes,
                            result.errors(), firstError, failedStage);
                    tempCleanup.cleanup(chunksDir, processedDir, uploadedPath);
                    throw new UnsupportedOperationException("Import workflow failed at stage "
                            + failedStage + ": " + firstError);
                }

                log.info("[{}] Workflow cascade termine : processed={}, chunks={}, duration={}",
                        correlationId, result.recordsProcessed(), result.chunksProcessed(), result.duration());

                // ---- Lambdas de finalisation : invoquees inline en mode
                //      synchrone , passees au runner deferred sinon . Tout
                //      le post-load ( markFinalizeFinished , metrics ,
                //      logImportEvent , DROP staging , confirm compensation ,
                //      activeRegistry.finish ) est concentre ici pour qu il
                //      soit emis APRES que les donnees soient reellement
                //      visibles en DB ( afterCommit du caller ) , sinon
                //      les consommateurs live afficheraient COMPLETED avant
                //      que la table finale soit ecrite . ----
                final WorkflowResult finalResult = result;
                final long           finalFileSize = fileSizeBytes;
                final fr.inrae.ore.cascade.model.core.Sink<java.nio.file.Path> finalSink = sink;
                final java.nio.file.Path finalProcessedDir = processedDir;
                final fr.inra.oresing.workflow.cascade.staging.StagingMode finalStagingMode = stagingMode;
                final java.util.UUID finalStagingCompId = stagingCompensationIdRef.get();

                final String finalAppSchema = referenceValueRepository.getSchemaName();
                final java.util.UUID finalBinaryFileId = sourceBinaryFileId;
                final Runnable markCompleted = () -> {
                    // Stop heartbeat AVANT l UPDATE final pour eviter qu un
                    // beat tardif ( race condition entre cancel et tick )
                    // ne reouvre la row en IN_PROGRESS apres notre flip .
                    closeHeartbeatQuietly(pipelineHeartbeat);
                    if (corrUuid != null) {
                        activeRegistry.markFinalizeFinished(corrUuid, Instant.now());
                    }
                    long effective = effectiveRecordsProcessed(finalResult, finalSink);
                    Duration dur   = Duration.between(startedAt, Instant.now());

                    // AUDIT 06-05-26 #1 : COUNT(*) authoritatif post-afterCommit .
                    // Reajuste le registry AVANT finish pour que le dernier poll
                    // WorkflowFinalizeBadge voie la valeur exacte . Persiste
                    // dans workflow_log.final_count pour que les polls
                    // ulterieurs ( IntegrityView , dashboard ) lisent la
                    // colonne au lieu de refaire COUNT(*) systematiquement .
                    Long authoritativeCount = countReferencevaluePostCommit(
                            referenceValueRepository, finalAppSchema, finalBinaryFileId, correlationId);
                    if (authoritativeCount != null && corrUuid != null) {
                        activeRegistry.setFinalRowsAuthoritative(corrUuid, authoritativeCount);
                    }

                    metrics.recordImportCompleted(applicationName, dataType, WorkflowLogEntry.STATUS_COMPLETED,
                            dur, effective, finalResult.recordsFailed(),
                            finalResult.chunksProcessed(), finalFileSize);
                    logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                            startedAt, dur, WorkflowLogEntry.STATUS_COMPLETED,
                            effective, finalResult.recordsFailed(),
                            finalResult.chunksProcessed(), finalFileSize,
                            finalResult.errors(), null, null, authoritativeCount);
                    // PER_WORKFLOW_TABLE : DROP table dediee post-succes .
                    if (finalStagingMode != null && finalStagingMode.dropTableSql() != null) {
                        try (java.sql.Connection c = referenceValueRepository.getDataSource().getConnection();
                             java.sql.Statement st = c.createStatement()) {
                            st.execute(finalStagingMode.dropTableSql());
                            log.info("[{}] PER_WORKFLOW_TABLE : table {} droppee apres succes",
                                    correlationId, finalStagingMode.tableName());
                        } catch (java.sql.SQLException dropErr) {
                            log.warn("[{}] DROP staging table failed ( sweeper rattrapera ) : {}",
                                    correlationId, dropErr.getMessage());
                        }
                    }
                    if (finalStagingCompId != null) {
                        try { compensationLogService.confirm(finalStagingCompId); }
                        catch (RuntimeException ignore) {
                            log.warn("[{}] Echec confirm STAGING_CLEANUP {} ( sweeper rattrapera ) : {}",
                                    correlationId, finalStagingCompId, ignore.getMessage());
                        }
                    }
                    if (corrUuid != null) {
                        activeRegistry.finish(corrUuid);
                    }
                };

                final java.util.function.Consumer<Throwable> markPostCommitFailure = err -> {
                    // Spring tx outer DEJA committee . Le UPSERT differe a
                    // echoue : on persiste FAILED + metrics pour que les
                    // consommateurs live basculent en rouge ; le merged.csv / staging row reste
                    // pour replay manuel ( compensation_log + sweeper s en
                    // chargent en background ) .
                    closeHeartbeatQuietly(pipelineHeartbeat);
                    if (corrUuid != null) {
                        activeRegistry.markRollbackStarted(corrUuid, Instant.now(), 0L,
                                formatThrowable(err));
                        activeRegistry.markRollbackFinished(corrUuid, Instant.now());
                    }
                    Duration dur = Duration.between(startedAt, Instant.now());
                    String   stage = extractFailedStage(err);
                    metrics.recordImportFailed(applicationName, dataType, stage, dur,
                            finalResult.recordsProcessed(), finalResult.recordsFailed(),
                            finalResult.chunksProcessed(), finalFileSize);
                    logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                            startedAt, dur, WorkflowLogEntry.STATUS_FAILED,
                            finalResult.recordsProcessed(), finalResult.recordsFailed(),
                            finalResult.chunksProcessed(), finalFileSize,
                            finalResult.errors(), formatThrowable(err), stage);
                    if (corrUuid != null) {
                        activeRegistry.finish(corrUuid);
                    }
                };

                final Runnable markTxRolledBack = () -> {
                    // Le catch RuntimeException ci-dessous a deja emit
                    // markRollback + logErrors / FAILED . Le runner doit
                    // simplement retirer le workflow de l active registry
                    // ( car le finally a ete instruit de skip via
                    // runnerWillFinalize=true ) .
                    closeHeartbeatQuietly(pipelineHeartbeat);
                    if (corrUuid != null) {
                        activeRegistry.finish(corrUuid);
                    }
                };

                // ---- Cascade 3.0.0 DEFERRED_TO_CALLER ( DIRECT_COPY ) ou
                //      StoreAllPathSink.deferToCaller ( MERGE_FILE ) : le
                //      sink a capture la finalize SQL ( ou le path
                //      merged.csv ) au lieu de l executer inline . On
                //      accroche un runner sur la tx Spring : afterCommit
                //      execute le UPSERT puis markCompleted ; afterCommit
                //      en erreur appelle markPostCommitFailure ;
                //      afterCompletion(rolledBack) appelle markTxRolledBack . ----
                boolean deferredMergeFile = false;
                if (outerTxActive && directCopy) {
                    java.util.Optional<fr.inrae.ore.cascade.api.defaults.db.staging.DeferredFinalize> deferred =
                            result.deferredFinalize();
                    if (deferred.isPresent()) {
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .registerSynchronization(new TxAwareDeferredRunner(deferred.get(), corrUuid,
                                        markCompleted, markPostCommitFailure, markTxRolledBack));
                        runnerWillFinalize.set(true);
                        log.info("[{}] DIRECT_COPY deferred finalize bind sur la Spring tx courante ( afterCommit )",
                                correlationId);
                    }
                } else if (outerTxActive && mergeFileSink != null) {
                    java.util.Optional<java.nio.file.Path> mergedDeferred = mergeFileSink.takeDeferredMergedPath();
                    if (mergedDeferred.isPresent()) {
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .registerSynchronization(new MergeFileDeferredRunner(
                                        referenceValueRepository, tempCleanup, mergeFileSink,
                                        mergedDeferred.get(), finalProcessedDir, corrUuid, activeRegistry,
                                        markCompleted, markPostCommitFailure, markTxRolledBack));
                        runnerWillFinalize.set(true);
                        deferredMergeFile = true;
                        log.info("[{}] MERGE_FILE deferred storeAll bind sur la Spring tx courante ( afterCommit )",
                                correlationId);
                    }
                }
                final boolean processedDirDeferred = deferredMergeFile;

                updateWorkflowPhase(corrUuid, WorkflowLogEntry.STATUS_LOADING_DB, finalFileSize);
                dataImporter.treatErrors();

                if (runnerWillFinalize.get()) {
                    // Mode deferred : le runner emettra COMPLETED en afterCommit .
                    // En attendant le workflow reste en phase LOADING_DB pour
                    // que les consommateurs live affichent la barre finalize jusqu a la
                    // visibilite reelle des donnees en DB . On ne nettoie pas
                    // processedDir en MERGE_FILE ( le runner en a besoin ) .
                    if (processedDirDeferred) {
                        tempCleanup.cleanup(chunksDir, uploadedPath);
                    } else {
                        tempCleanup.cleanup(chunksDir, finalProcessedDir, uploadedPath);
                    }
                } else {
                    // Mode synchrone : on emet COMPLETED inline + cleanup complet .
                    markCompleted.run();
                    tempCleanup.cleanup(chunksDir, finalProcessedDir, uploadedPath);
                }

            } catch (RuntimeException e) {
                // Bloc CHARGEMENT FINAL : la transaction Postgres rollback
                // automatiquement quand l'exception bubble out du sink /
                // finalize hook . On marque la phase ROLLBACK pour que
                // les consommateurs live affichent un badge rouge . Postgres ne donne
                // pas de feedback granulaire sur le rollback ( atomique ) ,
                // on capture juste le timestamp + le delta de rows pour
                // post-mortem .
                closeHeartbeatQuietly(pipelineHeartbeat);
                if (corrUuid != null) {
                    activeRegistry.markRollbackStarted(corrUuid, Instant.now(), 0L,
                            formatThrowable(e));
                    // Marquer ROLLBACK_DONE quasi-immediatement : Postgres
                    // a deja fait le rollback au moment ou l'exception
                    // sort de la stack ( implicite tx commit / rollback
                    // sur close de la conn ) .
                    activeRegistry.markRollbackFinished(corrUuid, Instant.now());
                }
                // Evite un double-enregistrement quand l'exception vient du
                // bloc FAILED deja metric au-dessus.
                if (!(e instanceof UnsupportedOperationException
                        && e.getMessage() != null
                        && e.getMessage().startsWith("Import workflow failed"))) {

                    logErrors(userId, userLogin, applicationName, dataType, e, startedAt, fileSizeBytes, correlationId, resourceName);
                }
                tempCleanup.cleanup(chunksDir, processedDir, uploadedPath);
                throw e;
            }
        } finally {
            // Safety net heartbeat : si une exception precoce ( avant
            // markCompleted / catch ) sort de la pipeline , le close
            // ici garantit que le scheduler s arrete et qu il ne
            // continue pas a beat indefiniment . Idempotent .
            closeHeartbeatQuietly(pipelineHeartbeat);
            importRateLimiter.release(userId);
            // #62 - Toujours retirer le snapshot du registry , quel que soit
            // le chemin de sortie ( succès , erreur , annulation ). Sans ce
            // finally , un workflow planté laisserait un fantôme indéfiniment
            // visible cote consommateurs live . En mode deferred ( cascade 3.0.0 ) le
            // runner enregistre sur le TransactionSynchronizationManager
            // appelle activeRegistry.finish au moment de afterCommit /
            // afterCompletion , donc on s abstient ici pour que le
            // workflow reste visible jusqu a ce que les donnees soient
            // effectivement en DB .
            if (corrUuid != null && !runnerWillFinalize.get()) {
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
    //  Helpers de configuration — visibles pour les tests unitaires   //
    // ---------------------------------------------------------------- //

    /**
     * Taille de chunk effective selon la strategie recursive :
     * <ul>
     *   <li>{@code isRecursive=false} → valeur configuree dans {@link ImportProperties}</li>
     *   <li>{@code isRecursive=true, isStrictOrdered=true} → valeur configuree (chunks normaux ;
     *       le mode &laquo; recursion ordonnee &raquo; n'a pas besoin du fichier entier en RAM)</li>
     *   <li>{@code isRecursive=true, isStrictOrdered=false} → {@link Integer#MAX_VALUE} (fichier
     *       entier en un seul chunk, necessaire pour le lazy-loading des parents)</li>
     * </ul>
     *
     * @see fr.inra.oresing.domain.data.deposit.transformation.DataValidator#testLinesRegardingRecursivity
     * @see fr.inra.oresing.domain.application.configuration.Tag.OrderStrictTag
     */
    static int effectiveChunkSizeLines(ImportProperties props, boolean isRecursive, boolean isStrictOrdered) {
        if (!isRecursive) return props.getChunkSizeLines();
        return isStrictOrdered ? props.getChunkSizeLines() : Integer.MAX_VALUE;
    }

    /**
     * Surcharge de compatibilité : {@code isStrictOrdered = false} (comportement legacy).
     *
     * @see #effectiveChunkSizeLines(ImportProperties, boolean, boolean)
     */
    static int effectiveChunkSizeLines(ImportProperties props, boolean isRecursive) {
        return effectiveChunkSizeLines(props, isRecursive, false);
    }

    /**
     * Parallelisme effectif : {@code 1} pour les imports recursifs, valeur configuree sinon.
     * <p>Meme en mode &laquo; recursion ordonnee &raquo;, le parallelisme est force a 1 pour
     * eviter les race conditions sur {@code missingParentLine}.
     */
    static int effectiveParallelism(ImportProperties props, boolean isRecursive) {
        return isRecursive ? 1 : props.getParallelism();
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
        // Pre-persist workflow_log.status='IN_PROGRESS' synchrone . Ferme le
        // trou d'observabilite SIGKILL : sans cet appel , un docker kill /
        // OOM JVM / panne machine entre le start et le flush async des
        // terminaisons laissait le workflow sans aucune trace en base
        // ( workflow fantome non detectable par le dashboard ) . Idempotent .
        logWriter.recordStart(WorkflowLogEntry.startMarker(
                corrUuid, WorkflowLogEntry.TYPE_IMPORT, userUuid, userLogin,
                applicationName, dataType, resourceName, startedAt, fileSizeBytes));
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
                    List.of(),     // chunks ( injectes par le registry au read-time )
                    List.of(),     // workers ( injectes par le registry au read-time )
                    null,          // parallelism      ( renseigne par setParallelism plus tard )
                    null,          // strategy         ( renseigne par setStrategy plus tard )
                    List.of(),     // sinkChunks       ( injectes par le registry au read-time )
                    null,          // importConfig     ( renseigne par setImportConfig plus tard )
                    null));        // lastHeartbeatAt  ( renseigne par HeartbeatService pendant la finalize )
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
                                List.of(),
                                List.of(),
                                snapshot.parallelism(),
                                snapshot.strategy(),
                                List.of(),
                                snapshot.importConfig(),
                                snapshot.lastHeartbeatAt()));
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
     * que les consommateurs live voient progresser recordsProcessed et chunksProcessed en
     * temps réel.
     */
    private ImportProgressReporter buildRegistryAwareReporter(
            ImportProgressReporter delegate, UUID corrUuid, long fileSizeBytes,
            java.util.concurrent.atomic.AtomicLong liveRecords,
            java.util.concurrent.atomic.AtomicInteger liveChunks) {
        return new ImportProgressReporter() {
            @Override
            public void onTotalLinesKnown(String cid, long totalLines) {
                delegate.onTotalLinesKnown(cid, totalLines);
            }

            @Override
            public void onLinesProcessed(String cid, int delta) {
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
            }
        };
    }

    /**
     * Compte les lignes du fichier ( deja sans en-tete ) pour alimenter
     * recordsTotal. Best-effort : en cas d'erreur I/O on retourne 0L et
     * les consommateurs live retombent sur la barre indeterminee.
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
    /**
     * Resolves the effective pool size for a cascade stage , honoring the
     * same precedence cascade itself uses :
     *   1. -Dcascade.pool.{stage}=N  ( JVM system property )
     *   2. CASCADE_POOL_{STAGE}=N    ( environment variable )
     *   3. fallback                  ( workflow-level parallelism )
     * Mirrors {@code WorkflowPoolRegistry#resolveParallelism} so the
     * dashboard header reflects the real pool size when an override is
     * set , instead of the workflow default.
     */
    private static int resolvePoolSize(String stage, int fallback) {
        String prop = System.getProperty("cascade.pool." + stage);
        Integer v = parsePositiveInt(prop);
        if (v != null) return v;
        String env = System.getenv("CASCADE_POOL_" + stage.toUpperCase());
        v = parsePositiveInt(env);
        if (v != null) return v;
        return fallback;
    }

    private static Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int n = Integer.parseInt(raw.trim());
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Execute UN SELECT COUNT(*) FROM &lt;schema&gt;.referencevalue WHERE binaryfile=?
     * post-afterCommit pour capturer le rowcount authoritatif . Best-effort :
     * tout echec ( DB transient down , schema invalide ) est avale et le
     * caller persiste {@code null} dans workflow_log.final_count ;
     * IntegrityService refera un COUNT a la demande dans ce cas ( cache TTL ) .
     *
     * @return count ou {@code null} si echec / parametres invalides
     */
    private static final java.util.regex.Pattern SAFE_SCHEMA_IDENT =
            java.util.regex.Pattern.compile("^[a-z_][a-z0-9_]*$");

    /**
     * Ferme idempotemment et silencieusement le handle heartbeat
     * pipeline . Remplace le contenu de la reference par {@code NOOP}
     * pour eviter qu un appel ulterieur ( safety net finally ) tente
     * une 2eme fermeture sur le meme future deja annule . Tout echec
     * de close est avale ( best-effort ) .
     */
    private static void closeHeartbeatQuietly(
            java.util.concurrent.atomic.AtomicReference<fr.inra.oresing.workflow.cascade.history.HeartbeatService.Heartbeat> ref) {
        fr.inra.oresing.workflow.cascade.history.HeartbeatService.Heartbeat hb =
                ref.getAndSet(fr.inra.oresing.workflow.cascade.history.HeartbeatService.Heartbeat.NOOP);
        if (hb != null) {
            try { hb.close(); } catch (RuntimeException ignore) { /* best-effort */ }
        }
    }

    private static Long countReferencevaluePostCommit(
            fr.inra.oresing.persistence.DataRepository repo,
            String schema, UUID binaryFileId, String correlationId) {
        if (schema == null || binaryFileId == null) {
            return null;
        }
        if (!SAFE_SCHEMA_IDENT.matcher(schema).matches()) {
            log.warn("[{}] schema invalide '{}' , skip COUNT authoritatif", correlationId, schema);
            return null;
        }
        try (java.sql.Connection c = repo.getDataSource().getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM \"" + schema + "\".referencevalue WHERE binaryfile = ?")) {
            ps.setObject(1, binaryFileId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (java.sql.SQLException ex) {
            log.warn("[{}] COUNT authoritatif post-afterCommit a echoue ( IntegrityService refera la query a la demande ) : {}",
                    correlationId, ex.getMessage());
        }
        return null;
    }

    private static UUID safeUuid(String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void logErrors(String userId, String userLogin, String applicationName, String dataType, IOException e, Instant startedAt, long fileSizeBytes, String correlationId, String resourceName) {
        log.error("[{}] IO error during import ( stack ) :", correlationId, e);
        Duration failDuration = Duration.between(startedAt, Instant.now());
        // IOException = upload / disk error -> attribute to UNKNOWN ( pre-cascade ) .
        String failedStage = extractFailedStage(e);
        metrics.recordImportFailed(applicationName, dataType, failedStage,
                failDuration, 0L, 0L, 0, fileSizeBytes);
        logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                0L, 0L, 0, fileSizeBytes, List.of(), formatThrowable(e), failedStage);
    }

    // Surcharge pour gérer les exceptions non-IOException
    private void logErrors(String userId, String userLogin, String applicationName, String dataType, Exception e, Instant startedAt, long fileSizeBytes, String correlationId, String resourceName) {
        log.error("[{}] Runtime error during import ( stack ) :", correlationId, e);
        Duration failDuration = Duration.between(startedAt, Instant.now());
        String failedStage = extractFailedStage(e);
        metrics.recordImportFailed(applicationName, dataType, failedStage,
                failDuration, 0L, 0L, 0, fileSizeBytes);
        logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                startedAt, failDuration, WorkflowLogEntry.STATUS_FAILED,
                0L, 0L, 0, fileSizeBytes, List.of(), formatThrowable(e), failedStage);
    }

    /**
     * Extracts the cascade {@link fr.inrae.ore.cascade.model.workflow.WorkflowStage}
     * from a throwable . Walks the cause chain looking for a
     * {@link fr.inrae.ore.cascade.model.workflow.CascadeStageException} ;
     * falls back to {@code UNKNOWN} for non-cascade failures ( upload IO ,
     * pre-cascade validation , unexpected bugs ) .
     *
     * @return the stage name ( SOURCE / TRANSFORM / COLLECTOR / SINK /
     *         TEARDOWN / UNKNOWN ) , never null
     */
    private static String extractFailedStage(Throwable t) {
        Throwable cur = t;
        // Cap the walk to avoid infinite loops on cyclic cause chains
        // ( extremely rare , but cheap to guard against ) .
        for (int i = 0; cur != null && i < 16; i++) {
            if (cur instanceof fr.inrae.ore.cascade.model.workflow.CascadeStageException cse) {
                return cse.stage().name();
            }
            cur = cur.getCause();
        }
        return fr.inrae.ore.cascade.model.workflow.WorkflowStage.UNKNOWN.name();
    }

    /**
     * Resout le nombre de rows reellement traitees par l'import . Cascade
     * cumule la taille des chunks emis par le sink ( {@code Chunk.recordCount()} )
     * pour produire {@code WorkflowResult.recordsProcessed()} . Pour les
     * sinks qui ecrivent une unite opaque ( typiquement
     * {@link StoreAllPathSink} qui recoit 1 path = N rows ) cette valeur
     * reflete le nombre d'unites , pas le nombre reel de rows en finale .
     *
     * <p>Resolution polymorphique via {@link RowCountingSink} : tout sink
     * qui implemente le marker expose un compteur cumulatif fiable .
     * Pas de couplage a une classe concrete ; ajouter un nouveau sink
     * avec rowcount divergent ne touche pas ce helper .
     *
     * @return {@link RowCountingSink#getRowsWritten()} si le sink l'expose
     *         et que le compteur est strictement positif ;
     *         {@code result.recordsProcessed()} sinon . Le seuil
     *         {@code > 0} est defensif : un compteur a zero apres une
     *         execution declaree COMPLETED indique un cas degrade
     *         ( cascade pretend OK , sink pretend rien ecrit ) -> on
     *         retombe sur la valeur cascade plutot que de mentir avec 0 .
     */
    private static long effectiveRecordsProcessed(
            fr.inrae.ore.cascade.model.workflow.WorkflowResult result,
            fr.inrae.ore.cascade.model.core.Sink<?> sink) {
        if (sink instanceof RowCountingSink countingSink) {
            long actual = countingSink.getRowsWritten();
            if (actual > 0L) {
                return actual;
            }
        }
        return result.recordsProcessed();
    }

    /**
     * Helper enrichi avec le {@code failedStage} ( cascade 2.2.0
     * {@code WorkflowResult.failedStage} ) . Persiste le stage dans
     * {@code oa_audit.workflow_log.failed_stage} pour le dashboard
     * Integrite et la metrique Prometheus
     * {@code openadom_imports_failed_total{stage=...}} .
     */
    private void logImportEvent(
            String correlationId, String userId, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startedAt, Duration duration, String status,
            long recordsProcessed, long recordsFailed,
            int chunksProcessed, long fileSizeBytes,
            List<String> errors, String fatalError, String failedStage) {
        logImportEvent(correlationId, userId, userLogin, applicationName, dataType, resourceName,
                startedAt, duration, status, recordsProcessed, recordsFailed,
                chunksProcessed, fileSizeBytes, errors, fatalError, failedStage, null);
    }

    /**
     * Variante avec {@code finalCount} : compteur authoritatif COUNT(*) capture
     * au markCompleted ( cf AUDIT 06-05-26 #1 ) . Persiste dans
     * {@code workflow_log.final_count} pour que IntegrityService et
     * DashboardService.finalizeProgress puissent eviter de refaire COUNT(*) au poll .
     */
    private void logImportEvent(
            String correlationId, String userId, String userLogin,
            String applicationName, String dataType, String resourceName,
            Instant startedAt, Duration duration, String status,
            long recordsProcessed, long recordsFailed,
            int chunksProcessed, long fileSizeBytes,
            List<String> errors, String fatalError, String failedStage,
            Long finalCount) {
        try {
            UUID corrUuid = UUID.fromString(correlationId);
            UUID userUuid = UUID.fromString(userId);
            java.util.Map<String, Object> metadata = metadataCollector.collect(corrUuid);
            logWriter.recordEnd(new WorkflowLogEntry(
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
                    fatalError,
                    metadata,
                    failedStage,
                    finalCount));
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