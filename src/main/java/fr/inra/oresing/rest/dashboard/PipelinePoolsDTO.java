package fr.inra.oresing.rest.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Snapshot des pools cascade en idle ( pas de workflow actif ) . Permet
 * a oa-live d'afficher la structure du pipeline avec workers placeholder
 * en mode "supervision continue" sans devoir attendre un workflow .
 *
 * @author R.YAHIAOUI
 */
@Schema(name = "PipelinePools", description = "Structure idle des pools cascade ( supervision sans workflow ) ")
public record PipelinePoolsDTO(
        PoolDTO source,
        PoolDTO transform,
        PoolDTO sink,
        PoolDTO ordering) {

    @Schema(name = "PipelinePool")
    public record PoolDTO(
            @Schema(description = "Stage label ( SOURCE | TRANSFORM | SINK | ORDERING ) ")
            String stage,

            @Schema(description = "corePoolSize ( = parallelism configure )")
            int corePoolSize,

            @Schema(description = "Threads actifs au moment du snapshot ( 0 si idle )")
            int activeCount,

            @Schema(description = "Tasks en queue a l'instant T")
            int queueSize,

            @Schema(description = "Capacite max queue")
            int queueCapacity) {

        public static PoolDTO from(
                fr.inra.oresing.workflow.cascade.config.PoolReloader.PoolSnapshot s) {
            if (s == null) return new PoolDTO("UNKNOWN", 0, 0, 0, 0);
            return new PoolDTO(
                    s.stage().name(),
                    s.corePoolSize(),
                    s.activeCount(),
                    s.queueSize(),
                    s.queueCapacity());
        }
    }
}
