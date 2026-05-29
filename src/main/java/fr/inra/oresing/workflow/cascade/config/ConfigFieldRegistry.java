package fr.inra.oresing.workflow.cascade.config;

import fr.inrae.ore.cascade.model.workflow.PipelineMode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Source unique de verite pour les fields de configuration cascade
 * exposes a l'edition admin live et au snapshot read-only .
 *
 * <p>Tous les fields ( hot et cold ) sont declares ici une fois ;
 * {@code ConfigEditService} se contente de boucler sur le registry
 * pour appliquer un patch ou produire le schema . Ajouter un nouveau
 * field se fait en une seule ligne dans {@link #registerAll()} .
 *
 * @author R.YAHIAOUI
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigFieldRegistry {

    private final ImportProperties importProperties;
    private final PublishProperties publishProperties;
    private final PoolReloader     poolReloader;
    private final fr.inra.oresing.workflow.cascade.ImportRateLimiter     importRateLimiter;
    private final fr.inra.oresing.workflow.cascade.ExtractionRateLimiter extractionRateLimiter;
    /**
     * Optional : le bean est conditionnel ( {@code @ConditionalOnProperty}
     * sur {@code app.workflow.zombie-enabled} ) . Si désactivé , le
     * threshold n'apparaît pas dans le ConfigEditPanel ( cohérent avec
     * "feature off" ) .
     */
    private final java.util.Optional<fr.inra.oresing.workflow.cascade.history.WorkflowZombieSweeper> workflowZombieSweeper;

    private final Map<String, ConfigField<?>> fields = new LinkedHashMap<>();

    @PostConstruct
    void registerAll() {
        // ---- ImportProperties hot ints ----
        register(ConfigField.intField("chunkSizeLines")
                .description("Nombre de lignes CSV par chunk . Réduire pour limiter "
                        + "la RAM / chunk ; augmenter pour réduire l'overhead de chunking .")
                .getter(importProperties::getChunkSizeLines)
                .setter(importProperties::setChunkSizeLines)
                .range(1, 1_000_000)
                .build());

        register(ConfigField.intField("progressBatchSize")
                .description("Granularité des notifications de progression ( en lignes ) .")
                .getter(importProperties::getProgressBatchSize)
                .setter(importProperties::setProgressBatchSize)
                .range(1, 100_000)
                .build());

        register(ConfigField.intField("maxErrorsThreshold")
                .description("Seuil d'erreurs au-delà duquel le workflow abort .")
                .getter(importProperties::getMaxErrorsThreshold)
                .setter(importProperties::setMaxErrorsThreshold)
                .range(0, 1_000_000)
                .build());

        register(ConfigField.intField("collectorChunkSize")
                .description("Consolidation Collector cascade ( 0 = pas de "
                        + "consolidation , -1 = merge tout , >0 = consolide ) .")
                .getter(importProperties::getCollectorChunkSize)
                .setter(importProperties::setCollectorChunkSize)
                .range(-1, 1_000_000)
                .build());

        register(ConfigField.intField("stagingSharedOrphanTtlMinutes")
                .description("TTL ( minutes ) pour le sweep des rows orphelines de SHARED_UNLOGGED .")
                .getter(importProperties::getStagingSharedOrphanTtlMinutes)
                .setter(importProperties::setStagingSharedOrphanTtlMinutes)
                .range(1, 86_400)
                .build());

        // ---- ImportProperties hot booleans ----
        register(ConfigField.boolField("enableMetrics")
                .description("Active interceptors metrics ( CPU/RAM par chunk + JVM stats ) .")
                .getter(importProperties::isEnableMetrics)
                .setter(importProperties::setEnableMetrics)
                .build());

        register(ConfigField.boolField("skipCsvReencoding")
                .description("Skip ré-encodage CSV ( gain perf ; requires clean input ) .")
                .getter(importProperties::isSkipCsvReencoding)
                .setter(importProperties::setSkipCsvReencoding)
                .build());

        // ---- ImportProperties hot strings ----
        register(ConfigField.stringField("stagingSharedTableName")
                .description("Nom de la table SHARED_UNLOGGED ( doit exister via Flyway ) .")
                .getter(importProperties::getStagingSharedTableName)
                .setter(importProperties::setStagingSharedTableName)
                .notBlank()
                .build());

        register(ConfigField.intField("finalizeBatchSize")
                .description("Taille de batch de l'UPSERT staging -> table finale ( lignes par iteration ) . "
                        + "Moins d'iterations sur gros volume , verrous tenus un peu plus longtemps par batch . "
                        + "<= 0 -> defaut 50000 .")
                .getter(importProperties::getFinalizeBatchSize)
                .setter(importProperties::setFinalizeBatchSize)
                .build());

        // ---- Memoire de travail de la session finalize ( hot ) ----
        register(ConfigField.stringField("finalizeWorkMem")
                .description("SET LOCAL work_mem du finalize ( tris / hash / agregats ) . "
                        + "Format Postgres ( ex 256MB , 1GB ) . Vide = default cluster .")
                .getter(importProperties::getFinalizeWorkMem)
                .setter(importProperties::setFinalizeWorkMem)
                .build());

        register(ConfigField.stringField("finalizeMaintenanceWorkMem")
                .description("SET LOCAL maintenance_work_mem du finalize ( index / VACUUM ) . "
                        + "Format Postgres ( ex 512MB , 1GB ) . Vide = default cluster .")
                .getter(importProperties::getFinalizeMaintenanceWorkMem)
                .setter(importProperties::setFinalizeMaintenanceWorkMem)
                .build());

        // ---- ImportProperties hot enums ----
        register(ConfigField.enumField("sinkStrategy", ImportProperties.SinkStrategy.class)
                .description("MERGE_FILE = pipeline legacy fichier merge -> storeAll . "
                        + "DIRECT_COPY = StagingPostgresSink COPY direct .")
                .getter(() -> importProperties.getSinkStrategy().name())
                .setter(s -> importProperties.setSinkStrategy(
                        ImportProperties.SinkStrategy.valueOf(s)))
                .build());

        register(ConfigField.enumField("stagingStrategy", ImportProperties.StagingStrategy.class)
                .description("Sous DIRECT_COPY : table TEMP par connection sticky "
                        + "( atomique , 1 thread sink ) ou table UNLOGGED partagée "
                        + "( N threads sink ) .")
                .getter(() -> importProperties.getStagingStrategy().name())
                .setter(s -> importProperties.setStagingStrategy(
                        ImportProperties.StagingStrategy.valueOf(s)))
                .build());

        register(ConfigField.enumField("intraDuplicatePolicy", ImportProperties.IntraDuplicatePolicy.class)
                .description("Doublons de cle naturelle ( hierarchicalKey_uniqueness ) intra-import , "
                        + "detectes sur le staging avant l'UPSERT . OFF = aucun scan ( comportement historique ) . "
                        + "WARN ( defaut ) = scan + log , import inchange ( meme resultat ) . "
                        + "FAIL = erreur metier claire avant toute mutation .")
                .getter(() -> importProperties.getIntraDuplicatePolicy().name())
                .setter(s -> importProperties.setIntraDuplicatePolicy(
                        ImportProperties.IntraDuplicatePolicy.valueOf(s)))
                .build());

        register(ConfigField.enumField("pipelineMode", PipelineMode.class)
                .description("STAGED = transform termine pour tous les chunks avant que le sink ne demarre ; ordre preserve . "
                        + "PIPELINED = pipeline transform / sink en parallele via une bounded queue ; "
                        + "wall-clock = max(transformTime , sinkTime) au lieu de la somme . "
                        + "Cf cascade 2.1.0 PipelineMode .")
                .getter(() -> importProperties.getPipelineMode().name())
                .setter(s -> importProperties.setPipelineMode(PipelineMode.valueOf(s)))
                .build());

        // ---- Workflow lifecycle - Zombie sweeper ( hot ) ----
        workflowZombieSweeper.ifPresent(sweeper -> register(
                ConfigField.intField("workflow.zombieThresholdMinutes")
                        .description("Seuil ( minutes ) au-delà duquel un workflow IN_PROGRESS "
                                + "sans heartbeat récent est marqué CANCELLED par le sweeper "
                                + "( fatal_error = 'presumed dead' ) . Évalué sur "
                                + "COALESCE(last_heartbeat_at, start_time) . Augmenter si des "
                                + "imports légitimes restent silencieux > seuil ( ex. JVM GC "
                                + "long ) ; réduire pour détecter les vrais zombies plus vite . "
                                + "Le cron de sweep ( 5 min par défaut ) reste env-only . "
                                + "Mutation à chaud effective au prochain tick .")
                        .getter(sweeper::getThresholdMinutes)
                        .setter(sweeper::setThresholdMinutes)
                        .range(1, 360)
                        .build()));

        register(ConfigField.intField("pipelineQueueCapacity")
                .description("Capacite de la queue inter-stage transform / sink "
                        + "( mode PIPELINED uniquement ) . Trop bas ( ex 2 ) provoque "
                        + "du backpressure inutile : les workers transform bloquent "
                        + "en attendant un slot . Trop haut consomme de la heap . "
                        + "Defaut 50 ( cascade benchmarks transform >> sink ) . "
                        + "Ignore en mode STAGED .")
                .getter(importProperties::getPipelineQueueCapacity)
                .setter(importProperties::setPipelineQueueCapacity)
                .range(1, 10_000)
                .build());

        // ---- PublishProperties hot ( profil republication ) ----
        // Distinct de ImportProperties : preferences memoire-friendly
        // pour le toggle publish/unpublish ( data deja validee , concurrent
        // avec autres workflows , profil different de l'upload initial ) .
        register(ConfigField.intField("publish.chunkSizeLines")
                .description("Publish/republish : nombre de lignes CSV par chunk . "
                        + "Plus petit que l'upload initial ( default 200 vs 1000 ) "
                        + "pour minimiser le pic memoire pendant la republication .")
                .getter(publishProperties::getChunkSizeLines)
                .setter(publishProperties::setChunkSizeLines)
                .range(1, 1_000_000)
                .build());

        register(ConfigField.intField("publish.parallelism")
                .description("Publish/republish : parallelisme du pipeline cascade . "
                        + "Default 2 ( vs 4 pour upload ) : minimise la concurrence "
                        + "avec autres workflows en cours pendant la republication .")
                .getter(publishProperties::getParallelism)
                .setter(publishProperties::setParallelism)
                .range(1, 64)
                .build());

        register(ConfigField.intField("publish.maxErrorsThreshold")
                .description("Publish/republish : seuil d'erreurs avant abort . "
                        + "Default 100 ( data deja validee donc 0 erreur attendue ) .")
                .getter(publishProperties::getMaxErrorsThreshold)
                .setter(publishProperties::setMaxErrorsThreshold)
                .range(0, 1_000_000)
                .build());

        register(ConfigField.enumField("publish.sinkStrategy", ImportProperties.SinkStrategy.class)
                .description("Publish/republish : MERGE_FILE = temp files locaux + 1 COPY final "
                        + "atomique ; DIRECT_COPY = N workers COPY parallel vers staging .")
                .getter(() -> publishProperties.getSinkStrategy().name())
                .setter(s -> publishProperties.setSinkStrategy(
                        ImportProperties.SinkStrategy.valueOf(s)))
                .build());

        register(ConfigField.enumField("publish.stagingStrategy", ImportProperties.StagingStrategy.class)
                .description("Publish/republish : pertinent uniquement si sinkStrategy=DIRECT_COPY . "
                        + "PER_CONNECTION_TEMP / SHARED_UNLOGGED / PER_WORKFLOW_TABLE .")
                .getter(() -> publishProperties.getStagingStrategy().name())
                .setter(s -> publishProperties.setStagingStrategy(
                        ImportProperties.StagingStrategy.valueOf(s)))
                .build());

        register(ConfigField.enumField("publish.pipelineMode", PipelineMode.class)
                .description("Publish/republish : STAGED ( atomicite , memoire elevee ) vs "
                        + "PIPELINED ( memoire bornee , commits partiels acceptables car "
                        + "data deja validee ) . Default PIPELINED .")
                .getter(() -> publishProperties.getPipelineMode().name())
                .setter(s -> publishProperties.setPipelineMode(PipelineMode.valueOf(s)))
                .build());

        // ---- Publish FAST path + mode CACHED_ROTATION ( hot ) ----
        register(ConfigField.enumField("publish.publishMode", PublishProperties.PublishMode.class)
                .description("Strategie pipeline publication . CASCADE_ALWAYS = legacy "
                        + "( cascade re-execute a chaque republish ) . CACHED_ROTATION = "
                        + "snapshot rows -> processed_data au unpublish , COPY -> referencevalue "
                        + "+ clear au republish . Pas de duplication storage en steady state .")
                .getter(() -> publishProperties.getPublishMode().name())
                .setter(s -> publishProperties.setPublishMode(PublishProperties.PublishMode.valueOf(s)))
                .build());

        register(ConfigField.boolField("publish.fastPathEnabled")
                .description("Active le FAST path republish ( COPY processed_data -> "
                        + "referencevalue direct , bypass DataImporter ) si cache + hash OK . "
                        + "Fallback automatique sur cascade LITE/FULL si erreur ou conditions non remplies .")
                .getter(publishProperties::isFastPathEnabled)
                .setter(publishProperties::setFastPathEnabled)
                .build());

        register(ConfigField.boolField("publish.captureProcessedEnabled")
                .description("Mode CASCADE_ALWAYS : capture JSON processed pendant cascade pour "
                        + "armer FAST path subsequent ( +30% disk ) . Ignore en CACHED_ROTATION "
                        + "( cache alimente par snapshot SQL au unpublish ) .")
                .getter(publishProperties::isCaptureProcessedEnabled)
                .setter(publishProperties::setCaptureProcessedEnabled)
                .build());

        register(ConfigField.boolField("publish.refreshHashOnFullPath")
                .description("Met a jour params.configHash apres republish FULL ( hash mismatch ) . "
                        + "Permet aux republishes suivants de basculer en LITE/FAST si config stable .")
                .getter(publishProperties::isRefreshHashOnFullPath)
                .setter(publishProperties::setRefreshHashOnFullPath)
                .build());

        // ---- Quotas par utilisateur ( hot ) ----
        register(ConfigField.intField("rateLimit.import.maxConcurrentPerUser")
                .description("Quota d'imports concurrents par utilisateur . "
                        + "Le changement reset les semaphores ; les imports en cours "
                        + "conservent leurs permits . S'applique aux nouvelles sessions .")
                .getter(importRateLimiter::getMaxConcurrentPerUser)
                .setter(importRateLimiter::setMaxConcurrentPerUser)
                .range(1, 1000)
                .build());

        register(ConfigField.intField("rateLimit.extraction.maxConcurrentPerUser")
                .description("Quota d'extractions concurrentes par utilisateur "
                        + "( charte / additional files / CSV / ZIP ) . Le changement "
                        + "reinitialise le UserRateLimiter cascade ( les permits "
                        + "actuellement acquis restent valides ) .")
                .getter(extractionRateLimiter::getMaxConcurrentPerUser)
                .setter(n -> extractionRateLimiter.reconfigure(
                        n, extractionRateLimiter.getAcquireTimeoutSeconds()))
                .range(1, 1000)
                .build());

        register(ConfigField.intField("rateLimit.extraction.acquireTimeoutSeconds")
                .description("Timeout d'acquisition d'un slot d'extraction ( secondes ) . "
                        + "0 = REJECT_IMMEDIATELY ; > 0 = WAIT_WITH_TIMEOUT . "
                        + "Le changement reinitialise le UserRateLimiter cascade .")
                .getter(() -> (int) extractionRateLimiter.getAcquireTimeoutSeconds())
                .setter(t -> extractionRateLimiter.reconfigure(
                        extractionRateLimiter.getMaxConcurrentPerUser(), t))
                .range(0, 3600)
                .build());

        // ---- Cascade pool parallelism ( hot via TPE setters ) ----
        for (PoolReloader.Stage stage : PoolReloader.Stage.values()) {
            String fieldName = "pool." + stage.name().toLowerCase();
            register(ConfigField.intField(fieldName)
                    .description("Parallélisme pool " + stage.name().toLowerCase()
                            + " ( hot resize via ThreadPoolExecutor.setCore/setMaximumPoolSize ) .")
                    .getter(() -> {
                        PoolReloader.PoolSnapshot s = poolReloader.snapshot(stage);
                        return s == null ? -1 : s.corePoolSize();
                    })
                    .setter(n -> poolReloader.resize(stage, n))
                    .range(1, 64)
                    .build());
        }

        // ---- Cold ( read-only , next restart ) ----
        for (PoolReloader.Stage stage : PoolReloader.Stage.values()) {
            String name = "queueSize." + stage.name().toLowerCase();
            register(ConfigField.intField(name)
                    .getter(() -> {
                        PoolReloader.PoolSnapshot s = poolReloader.snapshot(stage);
                        return s == null ? -1 : s.queueCapacity();
                    })
                    .readOnly("Taille de la queue d'entrée du pool "
                            + stage.name().toLowerCase()
                            + " . BlockingQueue interne fixée à la construction ; "
                            + "redémarrage requis pour modifier .")
                    .build());
        }

        register(ConfigField.boolField("virtualThreads")
                .getter(() -> {
                    PoolReloader.PoolSnapshot s = poolReloader.snapshot(PoolReloader.Stage.SOURCE);
                    return s != null && s.corePoolSize() == -1;
                })
                .readOnly("Active les Project Loom virtual threads pour les pools cascade . "
                        + "Modification = redémarrage : VirtualThread vs ThreadPoolExecutor "
                        + "sont des types incompatibles .")
                .build());

        register(ConfigField.stringField("chunksTempDir")
                .getter(importProperties::getChunksTempDir)
                .readOnly("Répertoire des chunks bruts . Modification = redémarrage "
                        + "( les workflows en cours référencent le path actuel ) .")
                .build());

        register(ConfigField.stringField("processedTempDir")
                .getter(importProperties::getProcessedTempDir)
                .readOnly("Répertoire des chunks transformés . Redémarrage requis .")
                .build());

        log.info("ConfigFieldRegistry : {} fields registered ( {} hot / {} cold )",
                fields.size(),
                fields.values().stream().filter(ConfigField::hot).count(),
                fields.values().stream().filter(ConfigField::restartRequired).count());
    }

    private void register(ConfigField<?> field) {
        if (fields.containsKey(field.name())) {
            throw new IllegalStateException("Duplicate config field : " + field.name());
        }
        fields.put(field.name(), field);
    }

    /** Snapshot ( cle = field name , valeur = current value ) . */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (ConfigField<?> f : fields.values()) {
            out.put(f.name(), f.currentValue());
        }
        return out;
    }

    /** Liste des field metas ( pour le schema endpoint UI ) . */
    public List<FieldMeta> schema() {
        return fields.values().stream().map(f -> new FieldMeta(
                f.name(),
                f.type().name().toLowerCase(),
                f.hot(),
                f.restartRequired(),
                f.description(),
                f.allowedValues())).toList();
    }

    /**
     * Applique une valeur sur un field si editable .
     * @throws java.util.NoSuchElementException si le field n'existe pas
     * @throws UnsupportedOperationException si le field est read-only
     * @throws IllegalArgumentException si valeur invalide / hors plage
     */
    @SuppressWarnings("java:S1452")
    public ConfigField.Mutation<?> apply(String fieldName, Object value) {
        ConfigField<?> f = Optional.ofNullable(fields.get(fieldName))
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Unknown config field : " + fieldName));
        return f.apply(value);
    }

    @SuppressWarnings("java:S1452")
    public Optional<ConfigField<?>> find(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }

    public int size() { return fields.size(); }

    public record FieldMeta(
            String name,
            String type,
            boolean hot,
            boolean restartRequired,
            String description,
            List<String> allowedValues) { }
}
