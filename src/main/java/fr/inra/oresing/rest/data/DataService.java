package fr.inra.oresing.rest.data;

import fr.inra.oresing.domain.data.DataRows;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.cancel.CancellationContext;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.CheckerFactory;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.context.ContextConstants;
import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.domain.data.rapport.Manifest;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.workflow.cascade.CascadeImportPipeline;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForBuildBundleReport;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForDownloadDatasetQuery;
import fr.inra.oresing.domain.data.deposit.bundle.BundleFileContent;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.domain.data.deposit.bundle.BundleFileContent;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import fr.inra.oresing.rest.HierarchicalReferenceAsTree;
import fr.inra.oresing.rest.data.extraction.DataCsvBuilder;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import fr.inra.oresing.domain.filesenderclient.BuildBundleReport;
import fr.inra.oresing.domain.filesenderclient.MessageInformations;
import fr.inra.oresing.rest.filesenderclient.FileInfos;
import fr.inra.oresing.rest.filesenderclient.FileRepository;
import fr.inra.oresing.rest.filesenderclient.FileSenderRepository;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.data.DefaultLineCheckerResult;
import fr.inra.oresing.rest.model.data.LineCheckerResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.workflow.guard.BackendOverloadedException;
import fr.inra.oresing.workflow.guard.HeapGuardService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class DataService {
    public static final String README_FILE_NAME = "README";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String REFERENCES_JSON = "references.json";
    public static final String CONFIGURATION_FILE = "configuration.yaml";
    private static final String CSV_FILENAME_PATTERN = "%s.csv";
    private static final String REFERENCES_CSV_FILENAME_PATTERN = "references/%s.csv";


    /**
     * Nombre de datatypes / référentiels traités <b>en parallèle</b> lors de
     * la construction d'un bundle via {@link #writeUploadBundle} ( endpoint
     * {@code GET /applications/{nameOrId}/upload-bundle} ).
     *
     * <p>Le bundle produit un ZIP contenant un CSV par datatype, ensuite
     * uploadé sur FileSender Renater pour générer un lien partagé envoyé
     * par mail au demandeur. Chaque traitement de datatype consomme :
     * 1 transaction DB ( pool main Hikari ), 1 thread du virtualScheduler,
     * et la mémoire temporaire pour le streaming CSV. Sans borne, Reactor
     * {@code Flux.flatMap} lancerait les N datatypes simultanément et
     * saturerait le pool Hikari + heap.</p>
     *
     * <p>Configurable via {@code openadom.bundle.upload.parallelism}
     * ( défaut : 6 ). À ajuster selon le sizing du pool main Hikari et
     * la mémoire conteneur backend disponible.</p>
     */
    @Value("${openadom.bundle.upload.parallelism:6}")
    private int bundleUploadParallelism;

    /**
     * Flag d'activation du cache des listes de filtres ( {@link #filterListCache} ).
     * Configurable via {@code openadom.cache.filter-list.enabled} ; défaut {@code true}.
     * Quand désactivé, chaque appel à {@code /filters} recalcule + sérialise sans
     * passer par le cache et sans y stocker le résultat ( utile pour debug
     * stale-data ou diagnostic comparatif des temps SQL ).
     */
    @Value("${openadom.cache.filter-list.enabled:true}")
    private boolean filterListCacheEnabled;

    @Setter
    ServiceContainer serviceContainer;
    private final OreSiRepository repo;
    private final JsonRowMapper jsonRowMapper;
    private final OreSiRepository repository;
    private final FileRepository fileRepository;
    private final PlatformTransactionManager transactionManager;
    private final CascadeImportPipeline cascadeImportPipeline;
    Executor fastExecutor;
    Executor normalExecutor;
    Executor heavyExecutor;
    Executor backupExecutor;

    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate streamingNamedJdbcTemplate;
    private final org.springframework.jdbc.core.JdbcTemplate streamingJdbcTemplate;

    /**
     * Garde-fou heap JVM : refuse les nouveaux depots quand la pression
     * memoire approche la limite . Symetrique a {@code PublishLifecycleService.startPhase1} .
     *
     * <p>Pourquoi sur le depot : le chargement CSV + referentiels en RAM
     * pour checkers + parsing typage + staging est le plus gros consommateur
     * RAM du backend . Refuser avant d'allouer evite le crash OOM brutal .
     *
     * <p>Optionnel ( {@code @Autowired(required=false)} ) : si le bean est
     * desactive via {@code app.workflow.heap-guard.enabled=false} , aucun
     * check n'est effectue . Voir {@link HeapGuardService} .
     *
     * @since openadom v25.05.17 - garde-fou depot symetrique publish
     */
    @Autowired(required = false)
    private HeapGuardService heapGuard;

    /**
     * Repository workflow_log pour publier les sous-phases du dépôt
     * ( {@code CSV_REENCODING} , {@code PREWARM_REFS} ) sur le workflow
     * parent quand un cid est disponible via {@link CancellationContext} .
     *
     * <p>Pour le flux republish ( {@code PublishLifecyclePhase2Handler}
     * appelle {@code addData} avec un parentCid actif ) , l'UI passe de
     * "CASCADE_PREPARING" opaque a une vraie progression CSV_REENCODING
     * -> PREWARM_REFS -> CASCADE_RUNNING . Pour le dépôt frais
     * ( pas de parentCid avant cascade.execute ) , no-op .
     *
     * @since openadom v25.05.17 - sous-phases visibles dépôt
     */
    @Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository workflowLogRepository;

    /**
     * Writer workflow_log pour pre-creer la row IMPORT en cas de depot
     * frais ( Phase 2 cascade adoption ) . Permet d'emettre des
     * sous-phases pendant prepareContext visibles immediatement dans
     * le dashboard sans attendre que cascade demarre ( 1-3 min sur
     * gros fichiers ) .
     */
    @Autowired(required = false)
    private fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter workflowLogWriter;

    /**
     * Prescan service injected as Spring bean ( cascade 3.3.0+ , Axe B ) .
     * Avant : instancie via {@code new} a chaque appel addData - tests
     * impossibles a mocker , dependency hidden , reuse impossible . Bean
     * Spring resout ces 3 problemes en 1 .
     */
    @Autowired
    private fr.inra.oresing.domain.data.deposit.prescan.NaturalKeyPreScanService naturalKeyPreScanService;

    public DataService(
            OreSiRepository repo,
            JsonRowMapper jsonRowMapper,
            OreSiRepository repository,
            FileRepository fileRepository,
            ServiceContainer serviceContainer,
            PlatformTransactionManager transactionManager, CascadeImportPipeline cascadeImportPipeline,
            @Qualifier("fastServiceExecutor") Executor fastExecutor,      // ✅ Fast executor
            @Qualifier("normalServiceExecutor") Executor normalExecutor,  // ✅ Normal executor
            @Qualifier("heavyServiceExecutor") Executor heavyExecutor,    // ✅ Heavy executor
            @Qualifier("backupExecutor") Executor backupExecutor,
            @Qualifier("streamingNamedJdbcTemplate") org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate streamingNamedJdbcTemplate,
            @Qualifier("streamingJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate streamingJdbcTemplate
    ) {
        this.repo = repo;
        this.jsonRowMapper = jsonRowMapper;
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.serviceContainer = serviceContainer;
        this.transactionManager = transactionManager;
        this.cascadeImportPipeline = cascadeImportPipeline;
        this.fastExecutor = fastExecutor;
        this.normalExecutor = normalExecutor;
        this.heavyExecutor = heavyExecutor;
        this.backupExecutor = backupExecutor;
        this.streamingNamedJdbcTemplate = streamingNamedJdbcTemplate;
        this.streamingJdbcTemplate = streamingJdbcTemplate;
    }

    /** Templates Hikari pool dedie aux endpoints de telechargement long-held . */
    public org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate streamingNamedJdbcTemplate() {
        return streamingNamedJdbcTemplate;
    }

    public org.springframework.jdbc.core.JdbcTemplate streamingJdbcTemplate() {
        return streamingJdbcTemplate;
    }

    /**
     * Plus de {@code @Transactional} ici : l'import passe par le pipeline
     * cascade ( 90+ s sur gros fichiers ) suivi de
     * {@code DataRepository.storeAll(...)} qui gere sa propre transaction
     * via {@link org.springframework.jdbc.core.ConnectionCallback} .
     *
     * <p>Avant ce changement , Spring AOP ouvrait une connexion Hikari au
     * debut d'addData et la maintenait inutilisee pendant toute la phase
     * cascade ( ~90 s ) avant de la passer a storeAll. Hikari leak detection
     * triggait a 30 s ( faux positif , long-running ) , et chaque import
     * concurrent gelait 1 connexion du pool pour rien.
     *
     * <p>Atomicite : storeAll est transactionnel ( COPY -> INSERT … ON
     * CONFLICT -> COMMIT ) ; setRoleForClient ouvre sa propre tx courte
     * via la methode chainee. Plus besoin d'enveloppe globale.
     */
    public UUID addData(final Application application,
                        final String dataName,
                        final DataFile file) throws IOException {
        // Garde-fou heap : refuse les nouveaux depots entrants quand la
        // JVM est sous pression . Symetrique au check fait par
        // PublishLifecycleService.startPhase1 sur le flux publish .
        // Ne s'applique QU'a cette variante ( appelee par VersioningService
        // au depot utilisateur ) - les variantes avec override ( appelees
        // depuis Phase2Handler ) sont deja protegees en amont .
        if (heapGuard != null && heapGuard.isUnderPressure()) {
            HeapGuardService.HeapStats stats = heapGuard.currentStats();
            log.warn("Depot REFUSE : heap pressure ( {} % >= seuil {} % ) - application={} datatype={}",
                    String.format("%.1f", stats.smoothedUsagePct()),
                    stats.refusePublishThresholdPct(),
                    application != null ? application.getName() : null,
                    dataName);
            throw new BackendOverloadedException(
                    stats.smoothedUsagePct(),
                    stats.refusePublishThresholdPct());
        }
        return addData(application, dataName, file,
                fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride.EMPTY);
    }

    /**
     * Variante avec override per-call des axes strategiques du pipeline
     * cascade ( cf
     * {@link fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride} ) .
     * Utilise par
     * {@code PublishLifecyclePhase2Handler.doPublish} pour appliquer le
     * profil memoire-friendly de {@code PublishProperties} sans impacter
     * les flux upload qui passent par {@code CascadeRuntimeOverride.EMPTY}
     * et heritent integralement de {@code ImportProperties} .
     *
     * @since openadom phase B publish/unpublish refonte
     */
    public UUID addData(final Application application,
                        final String dataName,
                        final DataFile file,
                        final fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride override) throws IOException {
        return addData(application, dataName, file, override, false);
    }

    /**
     * Variante <strong>lite</strong> : pour un republish ou la data a deja
     * ete validee anterieurement ( hash de config inchange ) , on saute
     * dans le {@link DataImporter} :
     * <ul>
     *   <li>le pre-warm du ReferenceCache ;</li>
     *   <li>l'accumulation cross-chunks
     *       {@code encounteredHierarchicalKeysForConflictDetection}
     *       ( principal coupable d'OOM sur gros datasets ) .</li>
     * </ul>
     * La transformation typee ( String -&gt; UUID , parse date , etc . ) reste
     * appliquee : indispensable pour produire le CSV staging que les sinks
     * ingerent .
     *
     * <p>Le routage lite vs FULL est de la responsabilite du caller
     * ( typiquement {@code PublishLifecyclePhase2Handler} ) qui doit
     * verifier l'invariant via {@link fr.inra.oresing.rest.usecases.storage.versioning.ConfigHashService} .
     *
     * @since openadom phase B LiteImporter
     */
    public UUID addData(final Application application,
                        final String dataName,
                        final DataFile file,
                        final fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride override,
                        final boolean lightweight) throws IOException {
        serviceContainer.authenticationService().setRoleForClient();
        addData(application, dataName, file.inputData(), file.params(), override, lightweight);
        return file.params().fileid();
    }

    private void addData(final Application application,
                         final String refType,
                         final InputStream file,
                         final FileOrUUID fileOrUUID) throws IOException {
        addData(application, refType, file, fileOrUUID,
                fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride.EMPTY,
                false);
    }

    private void addData(final Application application,
                         final String refType,
                         final InputStream file,
                         final FileOrUUID fileOrUUID,
                         final fr.inra.oresing.workflow.cascade.config.CascadeRuntimeOverride override,
                         final boolean lightweight) throws IOException {
        CancellationContext.checkpoint("addData entry");
        final DataRepository referenceValueRepository = getReferenceValueRepository(application);

        // Axe B plan resilience : sur un refType recursif , on pre-scanne
        // le CSV pour extraire l'ensemble des naturalkeys composites
        // distinctes effectivement referencees . Ce hint permet ensuite
        // de charger lazy uniquement ces rows depuis referencevalue via
        // getDataIdPerKeysByNaturalKeys ( O(|hint|) RAM ) au lieu du full
        // preload getDataIdPerKeys ( O(N_ref_size) RAM ) . Sur refs
        // recursifs 10M+ rows c'est la difference entre un import qui
        // passe et un OOM .
        //
        // Pre-requis : on doit lire le CSV deux fois ( prescan + body
        // write par prepareContextForDataTreatment ) ; le buffer en
        // temp file est libere en finally meme en cas d'erreur .
        //
        // Fallback graceful : si le prescan echoue ( CSV malforme ,
        // colonnes manquantes , I/O ) , on retombe sur le chemin
        // legacy ( hint=null = full preload ) - aucune regression .
        //
        // Non-recursif : skip , refacto B precedent a deja deplace ce
        // chargement en SQL JOIN post-UPSERT cote storeAll .
        final boolean isRecursive = application.getConfiguration()
                .findCompositeReferencesUsing(refType)
                .filter(HierarchicalNode::isRecursive)
                .isPresent();

        java.nio.file.Path csvBufferFile = null;
        java.util.Set<String> naturalKeysHint = null;
        InputStream effectiveInputStream = file;
        if (isRecursive) {
            try {
                csvBufferFile = Files.createTempFile("axe-b-prescan-", ".csv");
                csvBufferFile.toFile().deleteOnExit();
                try (InputStream src = file) {
                    Files.copy(src, csvBufferFile, StandardCopyOption.REPLACE_EXISTING);
                }
                // Extraction des columns naturalKey + separator depuis la config
                fr.inra.oresing.domain.application.configuration.StandardDataDescription dataDescription =
                        application.getConfiguration().dataDescription().get(refType);
                if (dataDescription != null
                        && dataDescription.naturalKey() != null
                        && !dataDescription.naturalKey().isEmpty()) {
                    java.util.List<String> nkColumns = new java.util.ArrayList<>(dataDescription.naturalKey());
                    char sep = dataDescription.separator();
                    // Header / first-data line numbers ( 1-indexed ) extraits de la
                    // config YAML ( OA_dataHeaderLine / OA_dataFirstLine ) . Si
                    // null = defaut legacy ( header ligne 1 ) ; sinon le prescan
                    // skippe les lignes de metadata humain en tete et lit le
                    // vrai header technique a la ligne configuree . Fix
                    // ISSUE_GITLAB_PRESCAN_AXE_B_IGNORE_DATAHEADERLINE_2026-05-18
                    // ( prescan plantait sur les datatypes Excel-style ACBB
                    // recursifs car parsait la ligne 1 - metadata humain - comme
                    // header CSV ) .
                    Integer dataHeaderLine = dataDescription.headerLine();
                    Integer dataFirstLine  = dataDescription.firstRowLine();
                    org.apache.commons.csv.CSVFormat fmt = org.apache.commons.csv.CSVFormat.Builder
                            .create(org.apache.commons.csv.CSVFormat.DEFAULT)
                            .setDelimiter(sep)
                            .get();
                    try (java.io.Reader r = Files.newBufferedReader(csvBufferFile, StandardCharsets.UTF_8)) {
                        naturalKeysHint = naturalKeyPreScanService.extractCompositeNaturalKeys(
                                        r, fmt, nkColumns,
                                        fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext
                                                .COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR,
                                        dataHeaderLine, dataFirstLine);
                    }
                    log.debug("[Axe B] prescan refType={} columns={} headerLine={} firstLine={} naturalkeys_distinctes={}",
                            refType, nkColumns, dataHeaderLine, dataFirstLine, naturalKeysHint.size());
                } else {
                    log.debug("[Axe B] prescan skip refType={} : config naturalKey absente/vide ( fallback legacy )", refType);
                }
                effectiveInputStream = Files.newInputStream(csvBufferFile);
            } catch (RuntimeException | IOException ex) {
                // Fallback graceful : retombe sur le chemin legacy ( hint=null ) .
                // CRUCIAL : on conserve le csvBufferFile pour fournir un
                // InputStream FRAIS au cascade ; l'original {@code file} a deja
                // ete consume par Files.copy(...) ci-dessus , l'utiliser
                // provoquerait NoSuchElementException dans CSVParser downstream .
                // Fix ISSUE_GITLAB_PRESCAN_AXE_B_IGNORE_DATAHEADERLINE_2026-05-18
                // ( cascade au 500 apres prescan failed parce que le legacy
                // preload heritait d'un InputStream vide ) . Le file est
                // supprime dans le finally en fin de methode .
                log.warn("[Axe B] prescan failed for refType={} ( fallback legacy full preload ) : {}",
                        refType, ex.getMessage());
                naturalKeysHint = null;
                if (csvBufferFile != null) {
                    effectiveInputStream = Files.newInputStream(csvBufferFile);
                } else {
                    effectiveInputStream = file;
                }
            }
        }

        try {
        AsynchroneFileImporterContext referenceImporterContext = getAsynchroneImporterContext(
                application,
                refType,
                fileOrUUID,
                naturalKeysHint
        );
        CancellationContext.checkpoint("addData importer context built");

        final DataImporter referenceImporter = new DataImporter(
                referenceImporterContext,
                cascadeImportPipeline.getImportProperties(),
                lightweight);
        // Honour cascade.import.skip-csv-reencoding ( default false ) .
        boolean skipReencoding = cascadeImportPipeline.getImportProperties().isSkipCsvReencoding();
        final String userId = serviceContainer.authenticationService().getCurrentUser().getId().toString();
        // Phase 2 cascade adoption :
        // - republish ( parentCid actif via PublishLifecyclePhase2Handler ) :
        //   phaseEmitter ecrit sur la row PUBLISH parent ( comportement
        //   existant Phase 1 preserve ) , cascade genere son propre cid IMPORT
        // - depot frais ( pas de parentCid ) : pre-genere cid IMPORT +
        //   pre-cree row workflow_log dans tx isolee REQUIRES_NEW ; le
        //   phaseEmitter ecrit sur ce cid ; cascade reuse ce cid ( idempotent
        //   via WorkflowLogWriter.recordStart skip silencieux sur duplicate )
        //
        // L'idempotence + l'isolation REQUIRES_NEW protegent la tx outer
        // ( CreateDataUseCase.@Transactional ) d'un eventuel echec de
        // pre-creation . En cas d'echec , phaseEmitter retombe sur null
        // et le depot continue sans visibilite sous-phase pendant
        // prepareContext ( comportement pre-Phase 2 preserve ) .
        final java.util.UUID parentCid = CancellationContext.currentParentCid();
        final java.util.UUID phaseEmitterTargetCid;
        final String preGeneratedCidForCascade;
        if (parentCid != null) {
            phaseEmitterTargetCid     = parentCid;
            preGeneratedCidForCascade = null;
        } else {
            java.util.UUID freshCid   = java.util.UUID.randomUUID();
            boolean preCreated        = preCreateImportWorkflowLog(freshCid, application, refType, userId);
            phaseEmitterTargetCid     = preCreated ? freshCid : null;
            preGeneratedCidForCascade = preCreated ? freshCid.toString() : null;
        }
        final java.util.function.Consumer<String> phaseEmitter =
                (phaseEmitterTargetCid != null && workflowLogRepository != null)
                        ? subPhase -> {
                            try { workflowLogRepository.updatePhase(phaseEmitterTargetCid, subPhase); }
                            catch (RuntimeException ex) {
                                log.debug("updatePhase {} on cid {} failed ( best effort ) : {}",
                                        subPhase, phaseEmitterTargetCid, ex.getMessage());
                            }
                        }
                        : null;
        Path path = referenceImporter.prepareContextForDataTreatment(FileBomResolver.of(effectiveInputStream), skipReencoding, phaseEmitter);
        CancellationContext.checkpoint("addData prepareContext done");
        cascadeImportPipeline.execute(
                referenceImporter,
                referenceValueRepository,
                path,
                userId,
                application.getName(),
                refType,
                fileOrUUID == null ? null : fileOrUUID.fileid(),
                override,
                preGeneratedCidForCascade
        );
        } finally {
            // Cleanup du temp file de prescan ( Axe B ) . Le file est
            // garde ouvert par effectiveInputStream qui a ete consume
            // par prepareContextForDataTreatment ; ici on le supprime
            // proprement . En cas d'exception en cours d'addData ,
            // deleteOnExit garantit le cleanup au shutdown JVM .
            if (csvBufferFile != null) {
                try { Files.deleteIfExists(csvBufferFile); }
                catch (IOException ignored) { /* best-effort cleanup */ }
            }
        }
    }

    /**
     * Pre-cree la row workflow_log IMPORT pour un depot frais ( Phase 2
     * cascade adoption ) . Insertion dans une transaction isolee
     * ( PROPAGATION_REQUIRES_NEW ) pour proteger la tx outer
     * ( {@code CreateDataUseCase.@Transactional} ) en cas d'echec :
     * si l'INSERT echoue ( BDD indispo , user non resolu , collision
     * unique index , bean absent en contexte test ) , la tx outer reste
     * intacte et le depot continue normalement sans visibilite
     * sous-phase pendant prepareContext .
     *
     * @return {@code true} si la row a ete effectivement creee
     *         ( phaseEmitter peut ecrire dessus + cascade reusera le
     *         cid ) , {@code false} sinon ( fallback comportement
     *         pre-Phase 2 : phaseEmitter null , cascade genere son
     *         propre cid )
     * @since openadom plan resilience Phase 2 cascade adoption
     */
    private boolean preCreateImportWorkflowLog(java.util.UUID cid,
                                                Application application,
                                                String refType,
                                                String userId) {
        if (workflowLogWriter == null) {
            log.debug("WorkflowLogWriter absent ( contexte test ?) , pre-creation row workflow_log skip pour cid {}", cid);
            return false;
        }
        // user_id est NOT NULL dans workflow_log ( cf V2 schema ) . Si on
        // ne peut pas resoudre un userId valide on SKIP la pre-creation
        // pour eviter une violation de contrainte qui marquerait la tx
        // outer ( @Transactional CreateDataUseCase ) rollback-only .
        // Le userId nous est deja fourni par le caller ( deja resolu via
        // serviceContainer.authenticationService().getCurrentUser().getId() ) ,
        // on le parse en UUID ; en cas d'echec on retombe sur le chemin
        // legacy ( pas de visibilite sous-phase mais pas de regression ) .
        final java.util.UUID userUuid;
        try {
            userUuid = java.util.UUID.fromString(userId);
        } catch (RuntimeException ex) {
            log.debug("[{}] userId non parseable en UUID , pre-creation row workflow_log skip ( fallback legacy ) : {}",
                    cid, userId);
            return false;
        }
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setName("preCreateImportWorkflowLog");
        TransactionStatus newTx = null;
        try {
            newTx = transactionManager.getTransaction(def);
            String userLogin = null;
            try {
                fr.inra.oresing.domain.OreSiUser current =
                        serviceContainer.authenticationService().getCurrentUser();
                if (current != null) {
                    userLogin = current.getLogin();
                }
            } catch (RuntimeException ex) {
                log.debug("Cannot resolve current user login for pre-create workflow_log : {}", ex.getMessage());
            }
            String appName = application != null ? application.getName() : null;
            String resourceName = "deferred-csv:" + cid;
            workflowLogWriter.recordStart(
                    fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry.startMarker(
                            cid,
                            fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry.TYPE_IMPORT,
                            userUuid, userLogin, appName, refType,
                            resourceName,
                            java.time.Instant.now(),
                            /* bytesTotal unknown at this point */ 0L));
            transactionManager.commit(newTx);
            log.debug("[{}] Pre-creation row workflow_log IMPORT pour depot frais ok", cid);
            return true;
        } catch (RuntimeException ex) {
            if (newTx != null) {
                try {
                    if (!newTx.isCompleted()) {
                        transactionManager.rollback(newTx);
                    }
                } catch (RuntimeException rbEx) {
                    log.debug("[{}] Rollback pre-create tx failed : {}", cid, rbEx.getMessage());
                }
            }
            log.warn("[{}] Pre-creation row workflow_log echec ( best-effort , cascade gerera son propre cid ) : {}",
                    cid, ex.getMessage());
            return false;
        }
    }

    public HierarchicalReferenceAsTree getHierarchicalReferenceAsTree(final Application application, final String lowestLevelReference) {
        HierarchicalNode compositeReferenceDescription = application.findNode(lowestLevelReference)
                .map(HierarchicalNode::new)
                .orElseThrow(() -> new OreSiTechnicalException("Can't find "));
        BiMap<Ltree, DataValue> indexedByHierarchicalKeyReferenceValues = HashBiMap.create();
        Map<DataValue, Ltree> parentHierarchicalKeys = new LinkedHashMap<>();
        ImmutableList<String> referenceTypes = compositeReferenceDescription.node().children().stream()
                .map(Node::nodeName)
                .collect(ImmutableList.toImmutableList());
        ImmutableSortedSet<String> sortedReferenceTypes = ImmutableSortedSet.copyOf(Ordering.explicit(referenceTypes), referenceTypes);
        ImmutableSortedSet<String> includedReferences = sortedReferenceTypes.headSet(lowestLevelReference, true);
        Optional.of(compositeReferenceDescription.node())
                //.filter(node -> includedReferences.contains(node.nodeName()))
                .ifPresent(compositeReferenceComponentDescription -> {
                    String reference = compositeReferenceComponentDescription.nodeName();
                    Optional<DataColumn> parentKeyColumn = Optional.ofNullable(compositeReferenceComponentDescription.componentKey())
                            .map(DataColumn::new);
                    try (Stream<DataValue> referenceStream = getReferenceValueRepository(application).findAllByReferenceTypeStream(reference)) {
                        referenceStream.forEach(referenceValue -> {
                            indexedByHierarchicalKeyReferenceValues.put(referenceValue.getNaturalKey(), referenceValue);
                            parentKeyColumn.ifPresent(presentParentKeyColumn -> {
                                DataDatum referenceDatum = referenceValue.getRefValues();
                                DataColumnValue referenceColumnValue = referenceDatum.get(presentParentKeyColumn);
                                Preconditions.checkState(referenceColumnValue instanceof DataColumnSingleValue);
                                String parentHierarchicalKeyAsString = ((DataColumnSingleValue) referenceColumnValue).getValue().toString();
                                if (!parentHierarchicalKeyAsString.isEmpty()) {
                                    Ltree parentHierarchicalKey = Ltree.fromSql(parentHierarchicalKeyAsString);
                                    parentHierarchicalKeys.put(referenceValue, parentHierarchicalKey);
                                }
                            });
                        });
                    }
                });
        Map<DataValue, DataValue> childToParents = Maps.transformValues(parentHierarchicalKeys, indexedByHierarchicalKeyReferenceValues::get);
        SetMultimap<DataValue, DataValue> tree = HashMultimap.create();
        childToParents.forEach((child, parent) -> tree.put(parent, child));
        ImmutableSet<DataValue> roots = Sets.difference(indexedByHierarchicalKeyReferenceValues.values(), parentHierarchicalKeys.keySet()).immutableCopy();
        return new HierarchicalReferenceAsTree(ImmutableSetMultimap.copyOf(tree), roots);
    }

    public AsynchroneFileImporterContext getAsynchroneImporterContext(final Application application, final String dataName, final FileOrUUID fileOrUUID) {
        return getAsynchroneImporterContext(application, dataName, fileOrUUID, /* naturalKeysHint */ null);
    }

    /**
     * Variante du getAsynchroneImporterContext acceptant un hint
     * pre-scanne des naturalkeys reellement referencees par le CSV
     * en cours d'import . Sur un refType recursif , declenche le
     * chargement lazy des refs via
     * {@link AsynchroneFileImporterContext#ofWithNaturalKeysHint} au
     * lieu du full preload via {@code getDataIdPerKeys} ( Axe B plan
     * resilience ) . Bornee a O ( |hint| ) RAM au lieu de
     * O ( N_ref_size ) . Sur refs recursifs 10M+ rows , evite l'OOM .
     *
     * <p>Fallback : si {@code naturalKeysHint} est {@code null} ou
     * vide , behaviour identique a la variante legacy ( full preload
     * pour recursif , {@code ImmutableMap.of()} pour non-recursif ) .
     *
     * @param naturalKeysHint set des naturalkeys composites pre-scannees
     *                        depuis le CSV ; {@code null} -> fallback
     *                        legacy
     * @since openadom plan resilience Axe B
     */
    public AsynchroneFileImporterContext getAsynchroneImporterContext(final Application application,
                                                                       final String dataName,
                                                                       final FileOrUUID fileOrUUID,
                                                                       final java.util.Set<String> naturalKeysHint) {
        final DataRepository referenceValueRepository = getReferenceValueRepository(application);
        final Configuration configuration = application.getConfiguration();
        final ContextConstants contextConstants = ContextConstants.with(
                application,
                dataName);
        final CheckerFactory checkerFactory = new CheckerFactory(referenceValueRepository);
        Function<String, List<DataValue>> getDatavaluesByReference = reference -> referenceValueRepository.findAllByReferenceType(reference);
        PublishContext.PublishContextBuilder publishContextBuilder = new PublishContext.PublishContextBuilder(application, dataName, fileOrUUID, getDatavaluesByReference);
        final ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers = checkerFactory.getCheckers(application, dataName,
                publishContextBuilder);
        final Set<String> patternColumnsNames = Optional.ofNullable(contextConstants.displayPattern())
                .map(InternationalizationTitle::getTitle)
                .map(Map::values)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        final Set<String> patternColumnsDescription = Optional.ofNullable(contextConstants.displayPattern())
                .map(InternationalizationTitle::getDescription)
                .map(Map::values)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        Map<String, List<String>> referenceToColumnName = lineCheckers.stream()
                .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                .collect(Collectors.groupingBy(
                                lc -> ((ReferenceType) lc.underlyingType()).getRefType(),
                                Collectors.mapping(ReferenceType -> ReferenceType.target().column(), Collectors.toList())
                        )
                );
        // Lazy view : on resolve les keys de reference UNIQUEMENT depuis la
        // config datatype ( pas de SELECT * ici ) , puis on differe l'appel a
        // findDisplayByNaturalKey au premier lookup via LazyDisplayNamesMap .
        // Avant : ~1-3 min de pre-load eager bloquant ; maintenant : ~10 ms
        // pour le scan config + ~50-200 ms par reference effectivement
        // referencee dans le CSV ( typique 30-60 % des references declarees ) .
        java.util.Set<String> displayReferenceKeys = lineCheckers.stream()
                .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                .map(lc -> ((ReferenceType) lc.underlyingType()).getRefType())
                .filter(patternColumnsNames::contains)
                .map(ref -> Optional.ofNullable(referenceToColumnName.getOrDefault(ref, null))
                        .map(List::getFirst)
                        .orElse(ref))
                .collect(Collectors.toSet());
        java.util.function.Function<String, Map<String, Map<String, String>>> displayLoader = ref ->
                getReferenceValueRepository(application).findDisplayByNaturalKey(ref);
        Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey =
                new fr.inra.oresing.rest.data.LazyDisplayNamesMap(displayReferenceKeys, displayLoader);
        if (naturalKeysHint != null && !naturalKeysHint.isEmpty()) {
            return AsynchroneFileImporterContext.ofWithNaturalKeysHint(
                    contextConstants,
                    new PublishContext.PublishContextBuilder(application, dataName, fileOrUUID, getDatavaluesByReference),
                    lineCheckers,
                    displayNamesByReferenceAndNaturalKey,
                    jsonRowMapper,
                    referenceValueRepository,
                    naturalKeysHint
            );
        }
        return AsynchroneFileImporterContext.of(
                contextConstants,
                new PublishContext.PublishContextBuilder(application, dataName, fileOrUUID, getDatavaluesByReference),
                lineCheckers,
                displayNamesByReferenceAndNaturalKey,
                jsonRowMapper,
                referenceValueRepository
        );
    }

    public List<DataValue> findReference(final String nameOrId, final String refType, final MultiValueMap<String, String> params) {
        Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);
        return serviceContainer.dataService()
                .findReferenceAccordingToRights(application, refType, params);
    }

    public List<DataValue> findReferenceAccordingToRights(final Application application, final String refType, final MultiValueMap<String, String> params) {
        if (application.getConfiguration().getHiddenData().contains(refType)) {
            return List.of();
        }
        final Set<String> hiddenComponents = application.getConfiguration().getHiddenComponentsForData(refType);
        serviceContainer.authenticationService().setRoleForClient();
        try (Stream<DataValue> referenceStream = getReferenceValueRepository(application)
                .findAllByReferenceTypeWithReferencingReferencesStream(refType, params)) {
            return referenceStream
                    .map(referenceValue -> {
                        referenceValue.setRefValues(referenceValue.getRefValues().filterHidden(hiddenComponents));
                        return referenceValue;
                    })
                    .toList();
        }
    }

    private DataRepository getReferenceValueRepository(Application application) {
        return repo.getRepository(application).data();
    }

    public List<UUID> deleteDataAccordingToRights(final Application application, final String refType, final MultiValueMap<String, String> params) {
        serviceContainer.authenticationService().setRoleForClient();
        return getReferenceValueRepository(application).deleteReferenceType(refType, params);
    }


    public Flux<DataRow> findDataFlux(final DownloadDatasetQuery downloadDatasetQuery) {
        return findDataFlux(downloadDatasetQuery, null);
    }

    /**
     * Variante streaming acceptant un {@link org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate}
     * explicite . Si {@code template} est non-null , le cursor est ouvert
     * sur le pool dedie aux downloads ( {@code streamingDataSource} ) ,
     * sinon retombe sur le pool main ( comportement historique ) .
     *
     * @since AUDIT 06-05-26 streaming pool isolation phase 2
     */
    public Flux<DataRow> findDataFlux(final DownloadDatasetQuery downloadDatasetQuery,
                                      final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate template) {
        final Application application = downloadDatasetQuery.application();
        if (application.findData(downloadDatasetQuery.dataName())
                .map(StandardDataDescription::tags)
                .filter(Tag.HiddenTag.HAS_HIDDEN_TAG_PREDICATE)
                .isPresent()) {
            return Flux.empty();
        }
        DataRepository dataRepository = getDataRepository(downloadDatasetQuery);
        serviceContainer.authenticationService().setRoleForClient();
        Flux<DataRows> rows = (template != null)
                ? dataRepository.findAllByDataTypeFlux(downloadDatasetQuery, template)
                : dataRepository.findAllByDataTypeFlux(downloadDatasetQuery);
        return rows.map(dataRows -> DataRow
                .of(downloadDatasetQuery.application()
                                .findData(downloadDatasetQuery.dataName()
                                ).orElse(null)
                        , dataRows));
    }

    private DataRepository getDataRepository(DownloadDatasetQuery downloadDatasetQuery) {
        return Optional.ofNullable(downloadDatasetQuery)
                .map(DownloadDatasetQuery::application)
                .map(repo::getRepository)
                .map(OreSiRepository.RepositoryForApplication::data)
                .orElseThrow(() -> new IllegalArgumentException("no data repository"));
    }

    public void getDataCsvStream(
            final OutputStream outputStream,
            final String applicationNameOrId,
            final String dataName,
            Locale language,
            boolean horizontalDisplay) {
        final Application application = serviceContainer.applicationService().getApplication(applicationNameOrId);
        if (application.getConfiguration().getHiddenData().contains(dataName)) {
            return;
        }
        DownloadDatasetQueryNoFilter downloadDatasetQuery = new DownloadDatasetQueryNoFilter(
                application,
                dataName,
                new OutPut(
                        Optional.of(language)
                                .orElse(application.getConfiguration().applicationDescription().defaultLanguage()),
                        0L,
                        -1L
                ),
                Set.of(),
                Set.of(),
                horizontalDisplay
        );
        // Streaming pool : cursor JDBC ouvert sur streamingDataSource pour
        // ne pas retenir une connexion du pool main pendant la duree du
        // download ( peut atteindre plusieurs heures sur gros referentiels ) .
        final Flux<DataRow> datas = findDataFlux(downloadDatasetQuery, streamingNamedJdbcTemplate);
        Optional<StandardDataDescription> data = downloadDatasetQuery.application()
                .findData(downloadDatasetQuery.dataName());
        final StandardDataDescription dataDescription = data
                .orElseThrow(() -> new IllegalStateException("can't find application %s".formatted(downloadDatasetQuery.dataName())));
        final AtomicLong counter = new AtomicLong();
        DataCsvBuilder
                .getDataCsvBuilder((appOrName, referenceType) -> getAsynchroneImporterContext(application, referenceType, null))
                .withDownloadDatasetQuery(downloadDatasetQuery)
                .withReferenceService(this)
                .onRepositories(getDataRepository(application), null)
                .addDatas(datas)
                .buildDataCsv(outputStream, downloadDatasetQuery.getLanguage(), dataDescription, downloadDatasetQuery.horizontalDisplay());
    }

    public List<ApplicationResult.DataSynthesis> getReferenceSynthesis(final Application application) {
        return getReferenceValueRepository(application).buildReferenceSynthesis();
    }

    /**
     * Délègue à {@link DataRepository#findLastReferencevalueCountStatsUpdate()}.
     * Utilisé par {@code ApplicationResources.getReferencevalueCountStatsInfo}
     * pour afficher la date du dernier recompute côté frontend.
     */
    public java.util.Optional<java.time.Instant> findLastReferencevalueCountStatsUpdate(final Application application) {
        return getReferenceValueRepository(application).findLastReferencevalueCountStatsUpdate();
    }

    /**
     * Délègue à {@link DataRepository#recomputeReferencevalueCountStats()}.
     * Utilisé par l'endpoint admin
     * {@code POST /applications/{name}/admin/recompute-referencevalue-count-stats}.
     */
    public java.time.Instant recomputeReferencevalueCountStats(final Application application) {
        return getReferenceValueRepository(application).recomputeReferencevalueCountStats();
    }

    public Boolean getDataFromStoredCsvStream(
            Manifest manifest,
            Path tempZipDirectory,
            String name,
            String reference,
            Application application,
            Locale locale) {

        log.info("getDataFromStoredCsvStream {}", reference);

        DataRepository dataRepository = repo.getRepository(application).data();
        Flux<BundleFileContent> storedData = Flux.fromStream(dataRepository.getStoredData(application, reference));

        try {
            Boolean result = storedData
                    .map(fileContent -> {
                        log.info("reference is loading {} - file {}", reference, fileContent.fileName());
                        try {
                            String relativePath = String.format("%s/%s", reference, fileContent.fileName());
                            manifest.add(reference, fileContent);
                            Path targetFile = tempZipDirectory.resolve(relativePath);
                            Files.createDirectories(targetFile.getParent());
                            try (InputStream is = fileContent.fileContent()) {
                                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                return true;
                            }
                        } catch (Exception e) {
                            log.error("Erreur traitement fichier {} pour référence {}", fileContent.fileName(), reference, e);
                            manifest.addError(reference, fileContent);
                            return false;
                        }
                    })
                    .reduce(false, (acc, current) -> acc || current) // Force la consommation et accumule
                    .block();

            log.info("Reference {} finished - hasData: {}", reference, result);
            return result != null ? result : false;

        } catch (Exception e) {
            log.error("Erreur lors du traitement des données stockées pour {}", reference, e);
            return false;
        }
    }


    public DataRepository getDataRepository(Application application) {
        return repository.getRepository(application).data();
    }

    public Flux<DownloadDatasetQueryByRowId> getDownloadDatasetQueriesAsync(
            long patternDefinitionCount,
            Application application,
            Locale locale,
            DataRepository dataRepository,
            Set<UUID> uuidsFromData,
            boolean horizontalDisplay) {
        return Flux.fromStream(dataRepository.getLinkedReferenceValuesStream(uuidsFromData))
                .map(dataValuesByDataType -> {
                    String dataType = dataValuesByDataType.dataType();
                    Set<DataRowIds> ids = dataValuesByDataType.ids();
                    return new DownloadDatasetQueryByRowId(
                            application,
                            dataType,
                            new OutPut(locale, 0L, null),
                            new HashSet<>(),
                            new HashSet<>(),
                            ids,
                            horizontalDisplay
                    );
                });
    }

    /**
     * Construit l'export ZIP : un CSV principal + N CSVs de references.
     *
     * <p>Phase 1c-bis ( issue #62 ) : le {@code @Transactional(readOnly=true)}
     * global a ete retire au profit d'une transaction par CSV ( cf. {@link
     * #addDatacsv} ). Cela permet de fermer le curseur JDBC entre chaque
     * fichier , ce qui evite de tenir une transaction longue ouverte sur le
     * pool {@code workflowDataSource} pendant toute la generation des N+1
     * CSVs ( risque OOM / saturation pool sur gros volumes ).
     */
    public void buildDataZip(
            Path zipOutputStream,
            DownloadDatasetQuery downloadDatasetQuery) {
        Application application = downloadDatasetQuery.application();
        DataRepository dataRepository = repository.getRepository(downloadDatasetQuery.application()).data();

        serviceContainer.authenticationService().setRoleForClient();

        // Passer par le proxy Spring ( serviceContainer.dataService() ) pour
        // que le @Transactional(readOnly=true) declare sur addDatacsv soit
        // effectivement applique - les appels intra-classe bypassent le proxy
        // et n'ouvrent aucune transaction.
        DataService self = serviceContainer.dataService();

        UUIDsfromData uuiDsfromData = self.addDatacsv(zipOutputStream, dataRepository, downloadDatasetQuery, "%s.csv");


        getDownloadDatasetQueriesAsync(
                downloadDatasetQuery.patternDefinitionCount(),
                application,
                downloadDatasetQuery.outPut().locale(),
                dataRepository,
                uuiDsfromData.uuidsfromData(),
                downloadDatasetQuery.horizontalDisplay()
        )
                .flatMap(downloadDatasetQueryByRowId -> Mono.fromCallable(() -> {
                    try {
                        return self.addDatacsv(zipOutputStream, dataRepository, downloadDatasetQueryByRowId, "references/%s.csv");
                    } catch (Exception e) {
                        throw new SiOreIllegalArgumentException("IOException", Map.of("message", Optional.ofNullable(e).map(Exception::getLocalizedMessage).orElse(OreSiTechnicalException.NO_MESSAGE)));
                    }
                }))
                .blockLast();
               /*  .subscribe(downloadDatasetQueries -> {
           for (DownloadDatasetQueryByRowId downloadDatasetQueryByRowId : downloadDatasetQueries) {
                try {
                    addDatacsv(zipOutputStream, dataRepository, downloadDatasetQueryByRowId, "references/%s.csv");
                } catch (Exception e) {
                    throw new SiOreIllegalArgumentException("IOException", Map.of("message", Optional.ofNullable(e).map(Exception::getLocalizedMessage).orElse(OreSiTechnicalException.NO_MESSAGE)));
                }
            }*/
        //TODO add additionalFiles

        /*Flux.fromStream(dataRepository.getLinkedReferenceValuesStream(uuiDsfromData.uuidsfromData()))
                //.filter(dataValuesByDataType -> "tr_metadata_agri_magri".equals(dataValuesByDataType.getDataType()))
                //.take(10)
                .doOnNext(dataValuesByDataType -> {
                    try {
                        addReferenceEntry(
                                downloadDatasetQuery.application(),
                                language,
                                separator,
                                dataValuesByDataType,
                                dataRepositoryWithBuffer,
                                zipOutputStream);
                    } catch (Exception e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    }
                })
                .doOnError(e -> {
                    // Gestion des erreurs
                    throw new SiOreIllegalArgumentException("IOException", Map.of("message", e.getLocalizedMessage()));
                })
                .doOnComplete(() -> {
                    try {
                        zipOutputStream.close();
                    } catch (IOException e) {
                        throw new SiOreIllegalArgumentException("IOException", Map.of("message", e.getLocalizedMessage()));
                    }
                })
                .subscribe();*/

        // 3. Construire la liste des fichiers additionnels
        //AdditionalFileRepository additionalFileRepository = repository.getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
        /*for (String additionalFileType : downloadDatasetQuery.application().getConfiguration().additionalFiles()) {
            if (!additionalFileType.isEmpty()) {
                additionalFileRepository.getAssociatedAdditionalFilesStream(uuiDsfromData.getDatasIds())
                        .forEach(additionalFile -> {
                            try {
                                new AdditionalFileSearchHelper().addAdditionalFilesToZip(additionalFile, zipOutputStream, "additionalFiles/");
                            } catch (final IOException e) {
                                throw new OreSiTechnicalException("Erreur lors de l'ajout des fichiers additionnels", e);
                            }
                        });
            }
        }*/
    }

    /**
     * Variante streaming de {@link #buildDataZip} : ecrit toutes les entrees
     * CSV ( principal + references ) directement dans le {@link ZipOutputStream}
     * fourni. Aucun fichier intermediaire sur disque. Phase 1c-full ( issue #62 ).
     *
     * <p>Le {@code zipOutputStream} n'est pas ferme par cette methode :
     * l'appelant garde le controle ( typiquement via try-with-resources ).
     *
     * <p>L'ordre d'ecriture est sequentiel ( {@code concatMap} au lieu de
     * {@code flatMap} ) car {@link ZipOutputStream} n'est pas thread-safe.
     */
    public void streamDataZipTo(
            java.util.zip.ZipOutputStream zipOutputStream,
            DownloadDatasetQuery          downloadDatasetQuery) {
        Application application = downloadDatasetQuery.application();
        DataRepository dataRepository = repository.getRepository(application).data();

        serviceContainer.authenticationService().setRoleForClient();

        DataService self = serviceContainer.dataService();

        UUIDsfromData uuiDsfromData = self.addDatacsvEntry(
                zipOutputStream, dataRepository, downloadDatasetQuery, CSV_FILENAME_PATTERN);

        getDownloadDatasetQueriesAsync(
                downloadDatasetQuery.patternDefinitionCount(),
                application,
                downloadDatasetQuery.outPut().locale(),
                dataRepository,
                uuiDsfromData.uuidsfromData(),
                downloadDatasetQuery.horizontalDisplay()
        )
                .concatMap(subQuery -> Mono.fromCallable(() -> {
                    try {
                        return self.addDatacsvEntry(
                                zipOutputStream, dataRepository, subQuery, REFERENCES_CSV_FILENAME_PATTERN);
                    } catch (Exception e) {
                        throw new SiOreIllegalArgumentException("IOException",
                                Map.of("message", Optional.ofNullable(e)
                                        .map(Exception::getLocalizedMessage)
                                        .orElse(OreSiTechnicalException.NO_MESSAGE)));
                    }
                }))
                .blockLast();
    }

    /**
     * Variante streaming de {@link #addDatacsv} : ecrit le CSV dans une entree
     * du zip fourni au lieu d'un fichier dans un repertoire temporaire.
     */
    @Transactional(readOnly = true)
    public UUIDsfromData addDatacsvEntry(
            final java.util.zip.ZipOutputStream zipOutputStream,
            DataRepository                      dataRepository,
            final DownloadDatasetQuery          downloadDatasetQuery,
            String                              fileNamePattern) {
        final Flux<DataRow> datas = serviceContainer.dataService().findDataFlux(downloadDatasetQuery);
        try {
            AdditionalFileRepository additionalFileRepository = repository
                    .getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
            return DataCsvBuilder.getDataCsvBuilder(
                            (appOrName, refType) -> serviceContainer.dataService()
                                    .getAsynchroneImporterContext(
                                            downloadDatasetQuery.application(), refType, null))
                    .withDownloadDatasetQuery(downloadDatasetQuery)
                    .withReferenceService(serviceContainer.dataService())
                    .onRepositories(dataRepository, additionalFileRepository)
                    .addDatas(datas)
                    .buildToZipEntry(zipOutputStream, fileNamePattern);
        } catch (IOException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public UUIDsfromData addDatacsv(
            final Path zipRepository,
            DataRepository dataRepository,
            final DownloadDatasetQuery downloadDatasetQuery,
            String fileNamePattern) {
        final Flux<DataRow> datas = serviceContainer.dataService().findDataFlux(downloadDatasetQuery);
        try {
            AdditionalFileRepository additionalFileRepository = repository.getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
            return DataCsvBuilder.getDataCsvBuilder((applicationNameOrId, referenceType) -> serviceContainer.dataService().getAsynchroneImporterContext(downloadDatasetQuery.application(), referenceType, null))
                    .withDownloadDatasetQuery(downloadDatasetQuery)
                    .withReferenceService(serviceContainer.dataService())
                    .withZipRepository(zipRepository)
                    .onRepositories(dataRepository, additionalFileRepository)
                    .addDatas(datas)
                    .build(fileNamePattern);
        } catch (IOException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
        }

    }

    public List<DataRow> findData(final DownloadDatasetQuery downloadDatasetQuery) {
        return serviceContainer.dataService().findDataFlux(downloadDatasetQuery).collectList().block();
    }

    public void sendZipLinkByMail(Path filePath, MessageInformations messageInformations, OreSiUser currentUser) {
        switch (messageInformations) {
            case DownloadDatasetQuery downloadDatasetQuery -> {
                try {
                    FileSenderInternationalisation fileSenderInternationalisation = new FileSenderInternationalisationForDownloadDatasetQuery(downloadDatasetQuery);
                    Locale locale = downloadDatasetQuery.outPut().locale();
                    String applicationName = Optional.ofNullable(
                                    fileSenderInternationalisation.getInternationnalizedApplication(locale)
                            )
                            .orElseGet(() -> Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedApplication(fileSenderInternationalisation.getDefaultLanguage()))
                                    .orElse(downloadDatasetQuery.application().getName()));
                    String dataName = Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedDataName(locale, downloadDatasetQuery.dataName()))
                            .orElseGet(() -> Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedDataName(fileSenderInternationalisation.getDefaultLanguage(), downloadDatasetQuery.dataName()))
                                    .orElse(downloadDatasetQuery.dataName()));
                    String subject = fileSenderInternationalisation.subjectPattern();
                    String message = fileSenderInternationalisation.messagePattern();
                    String internationnalizedDataName = fileSenderInternationalisation.getInternationnalizedDataName(
                            Locale.of(downloadDatasetQuery.getLanguage()),
                            dataName
                    );

                    String messageWithReport = fileSenderInternationalisation.
                            mailMessagefor(message.formatted(internationnalizedDataName),
                                    FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID);
                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            dataName,
                            filePath,
                            currentUser.getEmail(),
                            subject.formatted(applicationName),
                            messageWithReport);
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement : %s".formatted(downloadUrl));
                    /*sendUploadZipEmail(
                            currentUser.getEmail(),
                            subject.formatted(applicationName),
                            message.formatted(dataName),
                            downloadUrl,
                            fileSenderInternationalisation,
                            internationnalizedDataName
                    );*/
                } catch (Exception e) {
                    throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
                }
            }
            case BuildBundleReport buildBundleReport -> {
                try {
                    FileSenderInternationalisation fileSenderInternationalisation = new FileSenderInternationalisationForBuildBundleReport(buildBundleReport);
                    Locale locale = buildBundleReport.locale();

                    String applicationName = Optional.ofNullable(
                            fileSenderInternationalisation.getInternationnalizedApplication(locale)
                    ).orElseGet(() -> Optional.ofNullable(
                            fileSenderInternationalisation.getInternationnalizedApplication(fileSenderInternationalisation.getDefaultLanguage())
                    ).orElse(buildBundleReport.application().getName()));

                    String subject = fileSenderInternationalisation.subjectPattern().formatted(applicationName);
                    String message = fileSenderInternationalisation.messagePattern().formatted(applicationName);


                    String emailMessage = fileSenderInternationalisation.mailMessagefor(message, FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID);

                    /*sendUploadZipEmail(@Autowired
private PlatformTransactionManager transactionManager;

                            currentUser.getEmail(),
                            subject,
                            emailMessage,
                            FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID,
                            fileSenderInternationalisation,
                            applicationName
                    );*/
                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            "BulkUploadZIP",
                            filePath,
                            currentUser.getEmail(),
                            subject,
                            message
                    );
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement du ZIP pour dépôt en masse : %s".formatted(downloadUrl));

                } catch (Exception e) {
                    log.error("Erreur lors de la création ou de l'envoi du ZIP pour dépôt en masse", e);
                    throw new OreSiTechnicalException("Erreur lors de la création ou de l'envoi du ZIP pour dépôt en masse", e);
                }
            }
            case BundleReport bundleReport -> {
                try {
                    Locale locale = bundleReport.locale();

                    String applicationName = bundleReport.application().getName();

                    String subject = bundleReport.title();
                    String emailMessage = bundleReport.message();

                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            "BulkUploadZIP",
                            filePath,
                            currentUser.getEmail(),
                            subject,
                            emailMessage
                    );
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement du ZIP pour dépôt en masse : %s".formatted(downloadUrl));

                } catch (Exception e) {
                    log.error("Erreur lors de la création ou de l'envoi du rapport pour dépôt en masse", e);
                    throw new OreSiTechnicalException("Erreur lors de la création ou de l'envoi du rapport pour dépôt en masse", e);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + messageInformations);
        }

    }

    @Transactional(readOnly = true)
    public BuildBundleReport writeUploadBundle(String instanceUrl, String nameOrId, boolean withData,
                                               Locale locale, Path tempZipDirectory) throws IOException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        Scheduler virtualScheduler = Schedulers.fromExecutor(heavyExecutor);
        List<String> referentielsAvecDonnees = Collections.synchronizedList(new ArrayList<>());
        List<String> referentielsAvecDonneesExemple = Collections.synchronizedList(new ArrayList<>());
        List<String> referentielsEnErreur = Collections.synchronizedList(new ArrayList<>());

        Manifest manifest = new Manifest();

        // Capture le gestionnaire de transactions
        PlatformTransactionManager txManager = transactionManager;
        TransactionDefinition txDef = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Flux.fromIterable(application.getConfiguration().dataDescription().keySet())
                .flatMap(reference ->
                                Mono.fromCallable(() -> {
                                            // Crée une NOUVELLE transaction pour ce thread
                                            TransactionStatus txStatus = txManager.getTransaction(txDef);
                                            try {
                                                Boolean dataFromStoredCsvStream = serviceContainer.dataService()
                                                        .getDataFromStoredCsvStream(
                                                                manifest,
                                                                tempZipDirectory,
                                                                application.getName(),
                                                                reference,
                                                                application,
                                                                locale);

                                                if (withData && dataFromStoredCsvStream) {
                                                    referentielsAvecDonnees.add(reference);
                                                } else {
                                                    String fileName = application.getConfiguration().findData(reference)
                                                            .map(StandardDataDescription::submission)
                                                            .map(Submission::fileNameParsing)
                                                            .map(Submission.SubmissionFileNameParsing::createExampleSubmissionFileName)
                                                            .orElse(CSV_FILENAME_PATTERN.formatted(reference));
                                                    String dataCsvFilePath = "%1$s/%2$s".formatted(reference, fileName);
                                                    Path filePath = tempZipDirectory.resolve(dataCsvFilePath);
                                                    Files.createDirectories(filePath.getParent());
                                                    Files.createFile(filePath);
                                                    referentielsAvecDonneesExemple.add(reference);
                                                }

                                                txManager.commit(txStatus);
                                                return reference;
                                            } catch (Exception e) {
                                                txManager.rollback(txStatus);
                                                throw e;
                                            }
                                        })
                                        .subscribeOn(virtualScheduler)
                                        .onErrorResume(e -> {
                                            log.error("Erreur lors du traitement du référentiel {}", reference, e);
                                            referentielsEnErreur.add(reference);
                                            return Mono.empty();
                                        }),
                        bundleUploadParallelism)
                .collectList()
                .block();

        addManifest(tempZipDirectory, manifest);
        return new BuildBundleReport(application, referentielsAvecDonnees, referentielsAvecDonneesExemple,
                referentielsEnErreur, locale);
    }


    private static void addManifest(Path directory, Manifest manifest) throws IOException {

        final Map<String, List<String>> orderedDependancies = addReferencesFile(directory, manifest);
        final LinkedHashMap<String, List<String>> orderedManifest = orderedDependancies.keySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                Function.identity(),
                                referenceName -> manifest.referenceTypeFiles()
                                        .get(referenceName).stream()
                                        .map(BundleFileContent::fileName)
                                        .toList(),
                                (v1, v2) -> v1,
                                LinkedHashMap::new
                        )
                );
        String manifestJson = new ObjectMapper().writeValueAsString(orderedManifest);
        Path manifestFile = directory.resolve(MANIFEST_JSON);
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, manifestJson, StandardCharsets.UTF_8);
    }

    private static Map<String, List<String>> addReferencesFile(Path directory, Manifest manifest) throws IOException {
        Map<String, List<String>> referenceDeps = manifest.orderedReferenceTypes();
        String referencesJson = new ObjectMapper().writeValueAsString(referenceDeps);
        Path referencesFile = directory.resolve(REFERENCES_JSON);
        Files.createDirectories(referencesFile.getParent());
        Files.writeString(referencesFile, referencesJson, StandardCharsets.UTF_8);
        return referenceDeps;
    }

    @Transactional()
    public List<UUID> deleteData(final DownloadDatasetQuery downloadDatasetQuery) {
        serviceContainer.authenticationService().setRoleForClient();
        final Application application = downloadDatasetQuery.application();
        return repository.getRepository(application).data().delete(downloadDatasetQuery);
    }

    public Map<Ltree, List<DataValue>> getReferenceDisplaysById(final Application application, final Set<String> listOfDataIds) {
        return repository.getRepository(application).data().getReferenceDisplaysById(listOfDataIds);
    }

    /**
     * Flag d'activation du cache des CheckedFormatComponents ( cf.
     * {@link #checkedFormatComponentsCache} ). Quand désactivé , chaque
     * appel reconstruit l'arbre des checkers via {@code CheckerFactory}
     * ( ~600 ms par datatype riche en colonnes ). Mode dégradé conservé
     * pour debug / benchmarking.
     */
    @org.springframework.beans.factory.annotation.Value("${openadom.cache.checked-format-components.enabled:true}")
    private boolean checkedFormatComponentsCacheEnabled;

    // ─── Cache mémoire des CheckedFormatComponents ────────────────────────────
    //
    // Audit OA_FULL_REVIEW (8/5/26) : sur si_acbb , l'appel à
    // CheckerFactory.getCheckers + filtre + groupingBy coûte ~600 ms par
    // /data/json. Le résultat ne dépend QUE de ( application , dataName ) -
    // pas de l'utilisateur ni des paramètres de la requête. Stable jusqu'à
    // un import / delete ( change le contenu des référentiels que les
    // ReferenceChecker préchargent ) ou un YAML edit ( change la déclaration
    // des checkers ).
    //
    // Politique d'invalidation :
    //  - explicite via invalidateCheckedFormatComponentsForApplication ,
    //    appelée aux mêmes points que le cache filterList ( import , delete ,
    //    refresh manuel ) ;
    //  - filet TTL 5 min ;
    //  - LRU 200 entrées.
    //
    // Pas d'userId dans la clé : le résultat est purement déclaratif , les
    // permissions RLS s'appliquent au niveau des SELECT exécutés par les
    // checkers ( pas au niveau de la structure des checkers ).
    @org.springframework.beans.factory.annotation.Value("${openadom.cache.checked-format-components.ttl-minutes:120}")
    private long checkedFormatComponentsCacheTtlMinutes;

    @org.springframework.beans.factory.annotation.Value("${openadom.cache.checked-format-components.max-entries:200}")
    private int checkedFormatComponentsCacheMaxEntries;

    /**
     * Cache mémoire des CheckedFormatComponents ; clé {@code appName::dataName}.
     * Initialisé via {@link #initCheckedFormatComponentsCache} après injection
     * des @Value. Type stocké : Map<String, Map<String, LineCheckerResult>>.
     */
    private fr.inra.oresing.cache.MemoryCache<String, Map<String, Map<String, LineCheckerResult>>> checkedFormatComponentsCache;

    @jakarta.annotation.PostConstruct
    void initCheckedFormatComponentsCache() {
        this.checkedFormatComponentsCache = new fr.inra.oresing.cache.MemoryCache<>(
                "checkedFormatComponents",
                checkedFormatComponentsCacheMaxEntries,
                checkedFormatComponentsCacheTtlMinutes);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private fr.inra.oresing.workflow.cascade.metrics.OpenadomCacheMetrics cacheMetrics;

    public Map<String, Map<String, LineCheckerResult>> getCheckedFormatComponents(final String nameOrId, final String dataName) {
        if (!checkedFormatComponentsCacheEnabled) {
            return computeCheckedFormatComponents(nameOrId, dataName);
        }

        final String cacheKey = nameOrId + "::" + dataName;
        Map<String, Map<String, LineCheckerResult>> cached = checkedFormatComponentsCache.get(cacheKey);
        if (cached != null) {
            log.debug("checkedFormatComponents cache hit for {}", cacheKey);
            if (cacheMetrics != null) cacheMetrics.recordCheckedFormatHit();
            return cached;
        }

        log.info("checkedFormatComponents cache miss for {} , rebuilding via CheckerFactory", cacheKey);
        if (cacheMetrics != null) cacheMetrics.recordCheckedFormatMiss();
        Map<String, Map<String, LineCheckerResult>> result = computeCheckedFormatComponents(nameOrId, dataName);
        checkedFormatComponentsCache.put(cacheKey, result);
        return result;
    }

    /**
     * Calcul effectif via {@code CheckerFactory.getCheckers} ( bypass de cache ).
     */
    private Map<String, Map<String, LineCheckerResult>> computeCheckedFormatComponents(final String nameOrId, final String dataName) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        return new CheckerFactory(repository.getRepository(application).data()).getCheckers(application, dataName, new PublishContext.PublishContextBuilder(application, dataName, null, r -> List.of())).stream()
                .filter(c -> (c.underlyingType() instanceof DateType) || (c.underlyingType() instanceof IntegerType) || (c.underlyingType() instanceof FloatType) || (c.underlyingType() instanceof ReferenceType)).collect(Collectors
                        .groupingBy(
                                c -> c.underlyingType().getClass().getSimpleName(),
                                Collectors.toMap(c -> {
                                            final DataColumn dataColumn = c.target();
                                            return dataColumn.toHumanReadableString();
                                        },
                                        DefaultLineCheckerResult::fromLineChecker)
                        )
                );
    }

    /**
     * Invalide les entrées de cache pour une application. À appeler après
     * tout événement modifiant le résultat ( import / delete data , YAML
     * config update ).
     */
    public void invalidateCheckedFormatComponentsForApplication(String appName) {
        if (appName == null || checkedFormatComponentsCache == null) return;
        final String prefix = appName + "::";
        int removed = checkedFormatComponentsCache.invalidateMatching(k -> k.startsWith(prefix));
        log.info("checkedFormatComponents cache invalidated for app {} ( {} entries )", appName, removed);
        if (cacheMetrics != null) cacheMetrics.recordCheckedFormatInvalidate();
    }

    public void invalidateAllCheckedFormatComponents() {
        if (checkedFormatComponentsCache != null) checkedFormatComponentsCache.invalidateAll();
        log.info("All checkedFormatComponents caches invalidated");
        if (cacheMetrics != null) cacheMetrics.recordCheckedFormatInvalidate();
    }

    /** Observabilité : taille courante du cache filterList. */
    public int getFilterListCacheSize() {
        return filterListCache == null ? 0 : filterListCache.size();
    }

    /** Observabilité : taille courante du cache checkedFormatComponents. */
    public int getCheckedFormatComponentsCacheSize() {
        return checkedFormatComponentsCache == null ? 0 : checkedFormatComponentsCache.size();
    }

    /**
     * Observabilité : timestamp du dernier remplissage du cache filterList ,
     * ou {@code null} si jamais ecrit / invalidateAll . Affiche par l'UI
     * admin pour la colonne "Derniere mise a jour" .
     */
    public java.time.Instant getFilterListCacheLastWriteAt() {
        return filterListCache == null ? null : filterListCache.lastWriteAt();
    }

    /**
     * Observabilité : timestamp du dernier remplissage du cache
     * checkedFormatComponents , ou {@code null} si jamais ecrit /
     * invalidateAll .
     */
    public java.time.Instant getCheckedFormatComponentsCacheLastWriteAt() {
        return checkedFormatComponentsCache == null ? null : checkedFormatComponentsCache.lastWriteAt();
    }

    /**
     * Observabilité : taille mémoire approximative du cache filterList
     * via sérialisation Jackson . À appeler uniquement depuis un
     * endpoint admin ( CacheSizeEstimator ) , pas en hot path .
     */
    public long estimateFilterListCacheSizeBytes(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return filterListCache == null ? 0L : filterListCache.estimateSizeBytes(mapper);
    }

    /**
     * Observabilité : taille mémoire approximative du cache
     * checkedFormatComponents via sérialisation Jackson .
     */
    public long estimateCheckedFormatComponentsCacheSizeBytes(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return checkedFormatComponentsCache == null ? 0L : checkedFormatComponentsCache.estimateSizeBytes(mapper);
    }

    /** Observabilité : flag + caps des caches ( endpoint admin ). */
    public boolean isFilterListCacheEnabled() {
        return filterListCacheEnabled;
    }

    public int getFilterListCacheMaxEntries() {
        return filterListCache == null ? filterListCacheMaxEntries : filterListCache.maxEntries();
    }

    public boolean isCheckedFormatComponentsCacheEnabled() {
        return checkedFormatComponentsCacheEnabled;
    }

    public int getCheckedFormatComponentsCacheMaxEntries() {
        return checkedFormatComponentsCache == null ? checkedFormatComponentsCacheMaxEntries : checkedFormatComponentsCache.maxEntries();
    }

    public long getCheckedFormatComponentsCacheTtlMinutes() {
        return checkedFormatComponentsCache == null ? checkedFormatComponentsCacheTtlMinutes : checkedFormatComponentsCache.ttlMinutes();
    }

    @org.springframework.beans.factory.annotation.Value("${openadom.cache.front.etag.max-entries:50}")
    private int frontEtagCacheMaxEntries;

    @org.springframework.beans.factory.annotation.Value("${openadom.cache.front.etag.max-bytes-mb:20}")
    private int frontEtagCacheMaxBytesMb;

    public int getFrontEtagCacheMaxEntries() {
        return frontEtagCacheMaxEntries;
    }

    public int getFrontEtagCacheMaxBytesMb() {
        return frontEtagCacheMaxBytesMb;
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, LineChecker>> getFormatChecked(final String nameOrId, final String references) {
        final DataRepository dataRepository = repository.getRepository(serviceContainer.applicationService().getApplication(nameOrId)).data();
        return new CheckerFactory(dataRepository)
                .getCheckers(
                        serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId),
                        references,
                        null
                ).stream()
                .filter(c -> (c.underlyingType() instanceof DateType) || (c.underlyingType() instanceof IntegerType) || (c.underlyingType() instanceof FloatType) || (c.underlyingType() instanceof ReferenceType)).collect(Collectors
                        .groupingBy(
                                c -> c.fieldTypeForOne().getClass().getSimpleName(),
                                Collectors.toMap(
                                        c -> {
                                            final DataColumn vc = c.target();
                                            return vc.asString();
                                        },
                                        c -> c)
                        )
                );
    }

    public List<List<String>> getDataColumn(final Application application, final String refType, final String column) {
        if (application.findData(refType)
                .map(StandardDataDescription::tags)
                .filter(Tag.HiddenTag.HAS_HIDDEN_TAG_PREDICATE)
                .isEmpty()) {
            return List.of();
        }
        // Cache materialise V8 ( table <app>.data_versioning_scope_cache ) :
        // lookup par ( application , refType , column , userId ) , miss = compute
        // SQL findDataColumn + INSERT lazy . Bypass complet si flag desactive .
        // L'invalidation est assuree par les triggers SQL statement-level sur
        // referencevalue ( import / delete data ) + hooks Java grant-revoke
        // + YAML edit ( cf. DataVersioningScopeCacheService ).
        java.util.UUID userId = fr.inra.oresing.rest.OreSiApiRequestContext.getRequestUserId();
        return serviceContainer.dataVersioningScopeCacheService().getOrCompute(
                application, refType, column, userId,
                () -> repository.getRepository(application).data().findDataColumn(refType, column));
    }

    // PERF #465 - Cache en mémoire pour les résultats de la requête filterList.
    //
    // Raison ?
    //   La requête SQL getFilterList() dans DataRepository.java est très coûteuse ( ~54 secondes
    //   pour 100K lignes ). Le résultat ne change QUE lors d'un import/suppression de données.
    //   On cache le résultat en mémoire Java pour éviter de re-exécuter la requête.
    //
    // Fonctionnement :
    //   - Clé du cache : "nomApplication::nomDataType" (ex: "bmks_sandbox::t_soil_analysis_sana")
    //   - Valeur : le JSON sérialisé des FilterList + le timestamp ( pour l'éviction LRU )
    //     On stocke le JSON sérialisé (String) au lieu des objets Java pour éviter la re-sérialisation
    //     Jackson (~700ms) à chaque appel GET /filters. Le endpoint retourne le JSON directement.
    //   - Pas de TTL : le cache n'expire jamais automatiquement
    //   - Reconstruction : après un dépôt/suppression de données réussi, le cache est reconstruit
    //     en asynchrone via refreshFilterListCache(). L'ancien cache reste lisible pendant la
    //     reconstruction — il n'est jamais supprimé, seulement remplacé par le nouveau résultat.
    //   - Taille max : 50 entrées — au-delà, l'entrée la plus ancienne est supprimée (LRU)
    //   - Thread-safety : ConcurrentHashMap pour supporter les accès multi-utilisateurs
    //   - Rechargement manuel : GET /filters?refresh=true
    //
    // Mémoire utilisée :
    //   - Pour un jeu de 105K lignes : le résultat fait ~1.7 MB de JSON
    //   - 50 entrées max = ~85 MB worst case ( en pratique beaucoup moins )
    /**
     * Valeur stockée dans le cache filterList : JSON sérialisé + ETag.
     * Le timestamp ( pour LRU eviction ) est désormais porté par le
     * wrapper {@link fr.inra.oresing.cache.MemoryCache.Entry} , ce
     * record ne porte que la donnée métier.
     */
    private record FilterListValue(String json, String etag) {}

    /**
     * Résultat public exposé par {@link #getFilterListResult} : JSON sérialisé
     * + ETag stable ( hash SHA-256 du JSON , tronqué 64 bits ). L'ETag est
     * recalculé une seule fois à l'écriture cache et réutilisé tel quel à
     * chaque hit , de sorte que le coût HTTP/304 côté serveur soit borné à
     * une comparaison de String.
     */
    public record FilterListResult(String json, String etag) {}

    @org.springframework.beans.factory.annotation.Value("${openadom.cache.filter-list.max-entries:50}")
    private int filterListCacheMaxEntries;

    /**
     * Cache mémoire des FilterList. Audit OA_FULL_REVIEW (8/5/26) -
     * historiquement {@link java.util.concurrent.ConcurrentHashMap} avec
     * record {@code (json , etag , timestamp)} et LRU eviction manuelle ;
     * refactoré sur {@link fr.inra.oresing.cache.MemoryCache} pour mutualiser
     * le pattern avec scopesCache et checkedFormatComponentsCache. Pas de
     * TTL ( historique ; le hook d'invalidation explicite suffit ).
     */
    private fr.inra.oresing.cache.MemoryCache<String, FilterListValue> filterListCache;

    /**
     * Singleflight pattern : empeche le cache stampede sur cache miss
     * concurrent . Lorsque N requetes /filters arrivent en parallele
     * pour la meme cle sur un cache froid ( boot backend , apres
     * refresh ) , seule la PREMIERE execute le
     * {@code computeFilterListEntries} couteux ( EXISTS + DISTINCT par
     * colonne FK + serialize JSON ; 1-5s typique ) . Les N-1 autres
     * attendent la Future deja en cours et recoivent le meme resultat
     * ( ~0ms en plus de l'attente ) . Economise N-1 requetes SQL
     * identiques + connexions Hikari simultanees sur la meme cle .
     *
     * <p>Implementation deleguee a {@link fr.inra.oresing.cache.SingleflightCache} ,
     * helper generique unit-teste isolement . Slot cleanup post-compute
     * garantissant le retry sur prochain miss ( cf piege historique
     * {@code computeIfAbsent + remove inside lambda} documente dans la
     * classe ) .
     */
    private final fr.inra.oresing.cache.SingleflightCache<String, FilterListValue> filterListInFlight =
            new fr.inra.oresing.cache.SingleflightCache<>();

    @jakarta.annotation.PostConstruct
    void initFilterListCache() {
        this.filterListCache = new fr.inra.oresing.cache.MemoryCache<>(
                "filterList", filterListCacheMaxEntries, 0);
    }

    private static final ObjectMapper cacheObjectMapper = new ObjectMapper();

    /**
     * Retourne le JSON sérialisé des filtres + son ETag stable , depuis le
     * cache si disponible. Si le cache est vide ( premier appel ou après
     * refresh=true ) , exécute la requête SQL , sérialise le résultat en
     * JSON , calcule l'ETag une fois pour toutes et stocke en cache.
     *
     * <p>L'ETag permet au endpoint d'honorer {@code If-None-Match} et de
     * répondre {@code 304 Not Modified} quand le payload n'a pas changé
     * depuis la dernière réponse vue par le browser ( évite le retransfert
     * de ~1.7 MB pour les datasets volumineux ).
     *
     * @return le JSON + ETag prêts à être retournés directement par le
     *         endpoint
     */
    public FilterListResult getFilterListResult(final Application application, final String refType) {
        final String cacheKey = application.getName() + "::" + refType;

        // Cache desactive via openadom.cache.filter-list.enabled : on bypass
        // integralement ( pas de lecture cache , pas d'ecriture cache ) .
        // Utile pour debug stale-data ou benchmarking comparatif des temps SQL .
        if (!filterListCacheEnabled) {
            log.debug("filterList cache disabled , computing directly for {}", cacheKey);
            return toFilterListResult(serializeOnly(
                    computeFilterListEntries(application, refType).entries()));
        }

        // Cache hit : retourner le JSON deja serialise + ETag stocke ( ~0ms ) .
        FilterListValue cached = filterListCache.get(cacheKey);
        if (cached != null) {
            log.debug("filterList cache hit for {}", cacheKey);
            if (cacheMetrics != null) cacheMetrics.recordFilterListHit();
            return toFilterListResult(cached);
        }

        // Cache miss : singleflight pour empecher le cache stampede .
        log.info("filterList cache miss for {} , loading from database ( singleflight )", cacheKey);
        if (cacheMetrics != null) cacheMetrics.recordFilterListMiss();
        FilterListValue value = filterListInFlight.load(cacheKey,
                () -> computeAndMaybeCache(application, refType, cacheKey));
        return toFilterListResult(value);
    }

    /**
     * Compute + serialise le payload + ecrit le cache .
     *
     * <p><b>Historique</b> ( important pour comprendre les decisions ) :
     * <ol>
     *   <li>v1 ( bug ) : compute partial cache + singleflight slot figee
     *       a vie -> filtres masques pour des heures . Le coupable etait
     *       la slot figee , pas le cache du payload partial ;</li>
     *   <li>v2 : ajout slot cleanup correct ( commit ffad4a8d ) - resout
     *       le bug principal . En complement , skip-cache-si-partial
     *       ajoute pour belt-and-suspenders ;</li>
     *   <li>v3 ( ce code ) : revert du skip-cache-si-partial car il
     *       rendait le cache impossible a remplir des qu'UN composant
     *       SQL echouait silencieusement ( RLS deny , pool sature ... )
     *       sur n'importe quel datatype - resulting in 0/50 entries
     *       en prod malgre des preheats reussis . Le slot cleanup seul
     *       suffit a eviter la regression v1 .</li>
     * </ol>
     *
     * <p>Comportement actuel : cache TOUJOURS le payload ( meme partial ) .
     * Un compute partial est loggue en WARN pour visibilite ops mais
     * sert correctement les filtres disponibles . A la prochaine
     * invalidation explicite ( upload / publish / admin refresh ) le
     * cache sera recompute - cette fois potentiellement complet .
     */
    FilterListValue computeAndMaybeCache(Application application, String refType, String cacheKey) {
        FilterListComputeResult computed = computeFilterListEntries(application, refType);
        if (!computed.isComplete()) {
            log.warn("filterList compute partial for {} ( {} component(s) failed ) - caching partial result anyway ; refresh manually if needed",
                    cacheKey, computed.failedComponents());
        }
        return serializeAndCache(cacheKey, computed.entries());
    }

    /** Mapping homogene cache value -&gt; resultat public . */
    private static FilterListResult toFilterListResult(FilterListValue value) {
        return new FilterListResult(value.json(), value.etag());
    }

    /**
     * Resultat brut du compute pour un (app, refType) . Capture le nombre
     * de composants ayant silencieusement echoue ( SQL timeout , pool
     * saturation , RLS deny ) pour permettre au caller de decider du
     * caching ( cf {@link #computeAndMaybeCache} ) .
     *
     * <p>Package-private pour permettre les tests unitaires sans exposer
     * inutilement le detail interne au reste du code .
     */
    record FilterListComputeResult(List<FilterListEntry> entries, int failedComponents) {
        boolean isComplete() {
            return failedComponents == 0;
        }
    }

    /**
     * Calcule un ETag stable à partir du JSON cached. SHA-256 tronqué 64 bits
     * ( 16 hex chars ) suffit largement pour distinguer les variantes de
     * payload sans collision pratique ; ETag faible ( {@code W/"…"} ) , la
     * comparaison byte-à-byte n'étant pas requise pour notre besoin.
     */
    private static String computeEtag(String json) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(20).append("W/\"");
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.append('"').toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 fait partie du JDK standard ; cette branche est
            // morte sur toute JVM HotSpot / OpenJDK.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /**
     * Calcule la liste complète des entrées du payload {@code /filters} pour un
     * couple {@code (application, refType)} :
     * <ul>
     *   <li>les {@link FilterList} produits par la requête historique
     *       ( valeurs de {@code ReferenceChecker} liées au datatype ) ;
     *   <li>les {@link ColumnDistinctValues} pour chaque colonne marquée
     *       {@code __FILTER_LIST__} dans le YAML.
     * </ul>
     *
     * <p>Les deux types cohabitent dans le même {@link FilterListEntry} ; le
     * frontend les distingue via la propriété {@code @class} du payload JSON.
     *
     * <p>Conçu pour ne jamais lever : en cas d'erreur SQL sur une colonne
     * particulière , on logge et on saute la colonne plutôt que de faire
     * échouer l'endpoint complet ( on préfère afficher la dropdown vide à
     * un blocage UI ).
     */
    private FilterListComputeResult computeFilterListEntries(
            final Application application, final String refType) {
        final List<FilterListEntry> entries = new java.util.ArrayList<>();
        final int[] failed = { 0 };   // mutable counter pour fermeture lambda
        final var dataRepo = repository.getRepository(application).data();

        // 1. Filtres reference ( historique - inchange )
        final List<FilterList> filterLists = dataRepo.getFilterList(refType)
                .collectList()
                .block();
        if (filterLists != null) {
            entries.addAll(filterLists);
        }

        // 2. Pour chaque colonne filtrable opt-in :
        //    - __FILTER_LIST__   -> valeurs distinctes completes ( DISTINCT )
        //    - __FILTER_TEXT__   -> uniquement le drapeau hasEmpty ( EXISTS )
        //    - ReferenceChecker  -> drapeau hasEmpty seulement ( les options
        //      viennent de la table dimension via FilterList ci-dessus ;
        //      hasEmpty conditionne le bouton "+ (vide)" cf. §5.7 de
        //      FILTER_TEXT_LIST.md ) . Pour DRY , on reutilise exactement
        //      la meme methode `getColumnHasEmpty` que pour FILTER_TEXT :
        //      la requete EXISTS est agnostique au type de la colonne
        //      ( elle teste null dans le JSON , independamment du checker ) .
        //
        // Tout echec ( SQL timeout , pool saturation , RLS deny ... ) est
        // compte dans `failed` ; le caller decide si le resultat partiel
        // doit etre cache ou non ( cf computeAndMaybeCache ) .
        application.findData(refType).ifPresent(dataDescription ->
                dataDescription.componentDescriptions().values().stream()
                        .filter(c -> c.isFilterableAsList() || c.isFilterableAsText()
                                || c.findReferenceCheckerType().isPresent())
                        .forEach(component -> {
                            try {
                                final var multiplicity = component.checker() != null
                                        ? component.checker().multiplicity()
                                        : fr.inra.oresing.domain.checker.Multiplicity.ONE;
                                if (component.isFilterableAsList()) {
                                    entries.add(dataRepo.getColumnDistinctValues(
                                            refType, component.componentKey(), multiplicity));
                                } else {
                                    // Couvre a la fois FILTER_TEXT et ReferenceChecker :
                                    // seul `hasEmpty` est necessaire ; `values` reste
                                    // vide ( pour les FK les options viennent deja
                                    // de la FilterList du refType lie ) .
                                    entries.add(dataRepo.getColumnHasEmpty(
                                            refType, component.componentKey(), multiplicity));
                                }
                            } catch (Exception e) {
                                failed[0]++;
                                log.warn("Failed to load filter metadata for {}::{} - will NOT cache partial result",
                                        refType, component.componentKey(), e);
                            }
                        }));

        return new FilterListComputeResult(entries, failed[0]);
    }

    /**
     * Sérialise la liste d'entrées en JSON , calcule l'ETag stable et stocke
     * le tout dans le cache. Retourne l'entrée pour que les callers ( hit
     * miss ou refresh asynchrone ) puissent renvoyer JSON + ETag sans aller
     * relire le cache.
     */
    private FilterListValue serializeAndCache(String cacheKey, List<FilterListEntry> list) {
        FilterListValue value = serializeOnly(list);
        // LRU eviction + put geres par MemoryCache.put en interne .
        filterListCache.put(cacheKey, value);
        return value;
    }

    /**
     * Serialise la liste en JSON + ETag SANS ecrire au cache .
     * Utilise pour les chemins ou la memoisation est indesirable :
     *  - cache desactive ( {@code openadom.cache.filter-list.enabled=false} )
     *  - compute partiel ( cf {@link #computeAndMaybeCache} )
     * Fallback paylod vide en cas d'echec de serialisation - jamais cache .
     */
    private FilterListValue serializeOnly(List<FilterListEntry> list) {
        try {
            String json = cacheObjectMapper.writeValueAsString(list);
            return new FilterListValue(json, computeEtag(json));
        } catch (Exception e) {
            log.error("Failed to serialize filterList", e);
            return new FilterListValue("[]", computeEtag("[]"));
        }
    }

    /**
     * Reconstruit le cache des filtres pour un dataType donné, en asynchrone.
     * À appeler après un dépôt ou une suppression de données RÉUSSIE.
     *
     * L'ancien cache reste lisible pendant la reconstruction (pas de suppression préalable).
     * Le nouveau résultat remplace l'ancien atomiquement via ConcurrentHashMap.put().
     * En cas d'erreur SQL, l'ancien cache reste en place — pas de perte de service.
     */
    public void refreshFilterListCache(final Application application, final String refType) {
        log.info("filterList cache refresh started for {}::{}", application.getName(), refType);
        String cacheKey = application.getName() + "::" + refType;
        // Réutilise computeFilterListEntries() qui agrège les FilterList ( SQL
        // historique ) et les ColumnDistinctValues ( colonnes __FILTER_LIST__ ).
        // L'opération reste asynchrone via Mono.fromCallable + boundedElastic.
        reactor.core.publisher.Mono.fromCallable(() -> computeFilterListEntries(application, refType))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doOnNext(computed -> {
                    if (computed.isComplete()) {
                        serializeAndCache(cacheKey, computed.entries());
                        log.info("filterList cache refreshed for {}", cacheKey);
                    } else {
                        // Refresh partiel : on conserve l'ancien cache plutot
                        // que de l'ecraser par un payload incomplet ( meme
                        // motivation que computeAndMaybeCache ) . Prochain
                        // depot / publication declenchera un nouveau refresh .
                        log.warn("filterList refresh partial for {} ( {} component(s) failed ) - keeping previous cache entry",
                                cacheKey, computed.failedComponents());
                    }
                })
                .doOnError(error -> log.warn(
                        "Failed to refresh filterList cache for {}::{}",
                        application.getName(), refType, error))
                .subscribe();
        // Audit OA_FULL_REVIEW (8/5/26) - les scopes d'autorisation ( cf.
        // AuthorizationService.getAuthorizationScopes ) ET les
        // checkedFormatComponents sont invalidés aux mêmes événements
        // puisqu'un import / delete change le contenu de referencevalue dont
        // dépendent la fonction SQL getnodes() ET les ReferenceChecker
        // préchargés par CheckerFactory. Sans ça , un user pourrait voir un
        // site périmé pendant 5 min ( fenêtre TTL ).
        serviceContainer.authorizationService().invalidateAuthorizationScopesForApplication(application.getName());
        invalidateCheckedFormatComponentsForApplication(application.getName());
    }

    /**
     * Invalide le cache filterList pour une application et un dataType donnés.
     * Utilisé par le endpoint GET /filters?refresh=true pour forcer un rechargement manuel.
     */
    public void invalidateFilterListCache(final Application application, final String refType) {
        String cacheKey = application.getName() + "::" + refType;
        if (filterListCache != null) filterListCache.invalidate(cacheKey);
        // Drop la slot singleflight si presente : sans ca , un compute en
        // cours ( ou une slot residuelle d'avant le fix de cleanup ) restait
        // figee et continuait a servir un payload eventuellement stale apres
        // l'invalidation du MemoryCache .
        filterListInFlight.invalidate(cacheKey);
        log.info("filterList cache invalidated for {}", cacheKey);
        // Idem que refreshFilterListCache : on aligne les invalidations
        // pour ne jamais servir un arbre / un set de checkers stale après
        // refresh manuel.
        serviceContainer.authorizationService().invalidateAuthorizationScopesForApplication(application.getName());
        invalidateCheckedFormatComponentsForApplication(application.getName());
    }

    /**
     * Invalide les entrées de cache filterList pour une application donnée
     * ( tous les datatypes de cette app ). Audit OA_FULL_REVIEW (8/5/26) :
     * évite la purge globale via invalidateAllFilterListCaches quand seul
     * un app a été modifié ( import / delete / refresh manuel ).
     */
    public void invalidateFilterListCacheForApplication(String appName) {
        if (appName == null || filterListCache == null) return;
        final String prefix = appName + "::";
        int removed = filterListCache.invalidateMatching(k -> k.startsWith(prefix));
        // Symetrie : drop aussi les slots singleflight de cette app
        // ( sans ca , un compute residuel resurface apres eviction du
        // MemoryCache et masque les nouvelles donnees ) .
        int removedInFlight = filterListInFlight.invalidateMatching(k -> k.startsWith(prefix));
        log.info("filterList cache invalidated for app {} ( {} cache entries , {} in-flight slots )",
                appName, removed, removedInFlight);
        if (cacheMetrics != null) cacheMetrics.recordFilterListInvalidate();
    }

    /**
     * Invalide tout le cache filterList (toutes les applications, tous les dataTypes).
     */
    public void invalidateAllFilterListCaches() {
        if (filterListCache != null) filterListCache.invalidateAll();
        int removedInFlight = filterListInFlight.invalidateAll();
        log.info("All filterList caches invalidated ( {} in-flight slots dropped )", removedInFlight);
    }

    public void readEntry(File zipBundleFile, String entryName, Consumer<InputStream> consumer) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipBundleFile)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) throw new FileNotFoundException("Entrée absente: " + entryName);
            try (InputStream is = zipFile.getInputStream(entry)) {
                consumer.accept(is); // tout traitement doit être fait ici
            }
        }
    }


}