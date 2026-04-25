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
        RuntimeInfo runtime) {

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
            int maxConcurrentExtractionsPerUser) { }

    @Schema(name = "DashboardConfig.Runtime")
    public record RuntimeInfo(
            @Schema(description = "Version de la lib cascade utilisee", example = "1.3.0")
            String cascadeVersion,
            @Schema(description = "Version de la JVM hote ( ex. 25 , 21 )", example = "25")
            String javaVersion,
            @Schema(description = "True si les virtual threads sont actives ( cascade-6 opt-in )")
            boolean virtualThreadsEnabled,
            @Schema(description = "Nombre de workflows actuellement actifs dans le registry")
            int activeWorkflowCount,
            @Schema(description = "Champs additionnels exposes par les composants ( extensible )")
            Map<String, Object> extra) { }
}
