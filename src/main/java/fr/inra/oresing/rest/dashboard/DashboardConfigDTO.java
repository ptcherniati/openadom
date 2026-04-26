package fr.inra.oresing.rest.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Read-only snapshot of the cascade + openADOM runtime configuration ,
 * exposed by {@code GET /api/dashboard/config} ( admin only ).
 *
 * <p>Surfaces enough info for an operator to answer questions like
 * "comment est dimensionne le rate-limiter ?" , "est-ce que les virtual
 * threads sont actives ?" , "quelle version de cascade tourne ?" , sans
 * avoir a se connecter a la machine.
 *
 * <p>Phase 3 dashboard ( issue #62 - plan D ).
 */
@Schema(name = "DashboardConfig",
        description = "Cascade + openADOM runtime configuration ( admin only )")
public record DashboardConfigDTO(

        @Schema(description = "Import-pipeline tunables ( cf. ImportProperties )")
        ImportConfig importConfig,

        @Schema(description = "Quotas par utilisateur appliques aux imports / extractions")
        RateLimitConfig rateLimit,

        @Schema(description = "Etat du runtime JVM + cascade")
        RuntimeInfo runtime,

        @Schema(description = "Snapshots live des pools cascade ( source / transform / sink / ordering )")
        java.util.List<CascadePool> cascadePools,

        @Schema(description = "Defauts cascade ( WorkflowConfig.defaults() ) appliques quand un import "
                + "ne specifie rien")
        CascadeDefaults cascadeDefaults) {

    @Schema(name = "DashboardConfig.Import")
    public record ImportConfig(
            @Schema(description = "Lignes par chunk", example = "1000")
            int chunkSizeLines,
            @Schema(description = "Nombre de workers cascade en parallele", example = "2")
            int parallelism,
            @Schema(description = "Granularite des notifications de progression ( en lignes )",
                    example = "100")
            int progressBatchSize,
            @Schema(description = "Seuil d'erreurs au-dela duquel le workflow est avorte",
                    example = "100")
            int maxErrorsThreshold,
            @Schema(description = "Repertoire de stockage des chunks bruts decoupes")
            String chunksTempDir,
            @Schema(description = "Repertoire de stockage des chunks transformes")
            String processedTempDir) { }

    @Schema(name = "DashboardConfig.RateLimit")
    public record RateLimitConfig(
            @Schema(description = "Quota d'imports concurrents par utilisateur", example = "3")
            int maxConcurrentImportsPerUser,
            @Schema(description = "Quota d'extractions concurrentes par utilisateur ( -1 si non plafonne )",
                    example = "5")
            int maxConcurrentExtractionsPerUser,
            @Schema(description = "Timeout d'acquisition d'un slot d'extraction ( secondes ). "
                    + "0 = rejet immediat , > 0 = blocage borne avant abandon.",
                    example = "0")
            long extractionAcquireTimeoutSeconds,
            @Schema(description = "Snapshot des slots d'import actuellement reserves par utilisateur. "
                    + "Cle = userId , valeur = nombre de slots utilises. Les utilisateurs "
                    + "sans slot actif ne sont pas inclus. Lecture courante a l'instant T.")
            Map<String, Integer> usedImportSlotsByUser) { }

    @Schema(name = "DashboardConfig.Runtime")
    public record RuntimeInfo(
            @Schema(description = "Version de la lib cascade utilisee", example = "1.5.0")
            String cascadeVersion,
            @Schema(description = "Version de la JVM hote ( ex. 25 , 21 )", example = "25")
            String javaVersion,
            @Schema(description = "True si les virtual threads sont actives ( cascade-6 opt-in )")
            boolean virtualThreadsEnabled,
            @Schema(description = "Nombre de workflows actuellement actifs dans le registry")
            int activeWorkflowCount,
            @Schema(description = "Champs additionnels exposes par les composants ( extensible )")
            Map<String, Object> extra) { }

    @Schema(name = "DashboardConfig.CascadePool",
            description = "Snapshot live d'un pool cascade ( introspection ThreadPoolExecutor )")
    public record CascadePool(
            @Schema(description = "Nom logique du stage", example = "transform",
                    allowableValues = {"source", "transform", "sink", "ordering"})
            String stage,
            @Schema(description = "Prefixe utilise par la NamedThreadFactory", example = "transform-")
            String threadNamePrefix,
            @Schema(description = "Taille configuree du pool ( = corePoolSize = maximumPoolSize ). "
                    + "-1 quand cascade.virtualThreads=true ( pool sans borne )")
            int    configuredThreads,
            @Schema(description = "Threads en cours d'execution d'une tache",  example = "2")
            int    activeCount,
            @Schema(description = "Threads vivants ( <= configuredThreads )",   example = "4")
            int    poolSize,
            @Schema(description = "Taches en attente dans la queue bornee",     example = "12")
            int    queueSize,
            @Schema(description = "Capacite max de la queue",                   example = "100")
            int    queueCapacity,
            @Schema(description = "Total des taches jamais soumises ( cumulatif )")
            long   taskCount,
            @Schema(description = "Total des taches terminees ( cumulatif )")
            long   completedTaskCount,
            @Schema(description = "True si le pool tourne avec des virtual threads")
            boolean virtualThreads) { }

    @Schema(name = "DashboardConfig.CascadeDefaults",
            description = "Valeurs par defaut de cascade ( WorkflowConfig.defaults() )")
    public record CascadeDefaults(
            @Schema(description = "sourceChunkSize ( deprecated : non honore par cascade core )")
            int sourceChunkSize,
            @Schema(description = "collectorChunkSize ( 0 = pas de consolidation , -1 = merge tout )")
            int collectorChunkSize,
            @Schema(description = "Nombre max d'erreurs avant abort", example = "100")
            int maxErrors,
            @Schema(description = "Activation des metrics chunk + workflow ( + JVM stats )")
            boolean enableMetrics,
            @Schema(description = "Parallelisme par defaut applique aux stages source/transform/sink "
                    + "quand l'override est -1")
            int defaultParallelism,
            @Schema(description = "Parallelisme source ( -1 = use defaultParallelism )")
            int sourceParallelism,
            @Schema(description = "Parallelisme transform ( -1 = use defaultParallelism )")
            int transformParallelism,
            @Schema(description = "Parallelisme sink ( -1 = use defaultParallelism )")
            int sinkParallelism,
            @Schema(description = "Queue size source")
            int sourceQueueSize,
            @Schema(description = "Queue size transform")
            int transformQueueSize,
            @Schema(description = "Queue size sink")
            int sinkQueueSize,
            @Schema(description = "Quota cascade per-user ( max workflows simultanes par utilisateur )")
            int rateLimitMaxWorkflowsPerUser,
            @Schema(description = "Timeout d'acquisition d'un slot rate-limit ( secondes )")
            long rateLimitAcquireTimeoutSeconds,
            @Schema(description = "Politique de rejet rate-limit ( REJECT_IMMEDIATELY / WAIT )")
            String rateLimitRejectionPolicy,
            @Schema(description = "Rate-limit cascade actif ?")
            boolean rateLimitEnabled) { }
}
