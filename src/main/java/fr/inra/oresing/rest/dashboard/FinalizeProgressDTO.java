package fr.inra.oresing.rest.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Snapshot du bloc CHARGEMENT FINAL pour oa-live . Sert au frontend a
 * afficher separement la phase cascade ( source / transform / sink emit )
 * et la phase finalize ( UPSERT staging -> referencevalue pour DIRECT_COPY
 * ou COPY merged.csv -> referencevalue pour MERGE_FILE ) , avec un debit
 * et une duree distincts pour chaque phase .
 *
 * <p>Etats {@link #phase} :
 * <ul>
 *   <li>{@code CASCADE_RUNNING} : chunks emit en cours</li>
 *   <li>{@code FINALIZE_RUNNING} : phase chargement final</li>
 *   <li>{@code COMPLETED} : OK , baseline rows arrivees</li>
 *   <li>{@code ROLLBACK_IN_PROGRESS} : tx Postgres rollback</li>
 *   <li>{@code ROLLBACK_DONE} : rollback fini , baseline restoree</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Schema(name = "FinalizeProgress",
        description = "Suivi temps reel de la phase chargement final ( bloc CHARGEMENT FINAL ) ")
public record FinalizeProgressDTO(

        @Schema(description = "Phase courante du workflow")
        String  phase,

        @Schema(description = "Strategy sink utilisee ( MERGE_FILE | DIRECT_COPY ) ")
        String  sinkStrategy,

        @Schema(description = "Strategy staging ( null pour MERGE_FILE ) ")
        String  stagingStrategy,

        @Schema(description = "Demarrage cascade ( emit chunks )")
        Instant cascadeStartedAt,

        @Schema(description = "Fin cascade ( null tant que CASCADE_RUNNING )")
        Instant cascadeFinishedAt,

        @Schema(description = "Demarrage phase finalize ( null si pas commence )")
        Instant finalizeStartedAt,

        @Schema(description = "Fin phase finalize ( null si pas fini )")
        Instant finalizeFinishedAt,

        @Schema(description = "Demarrage rollback ( null si pas de rollback )")
        Instant rollbackStartedAt,

        @Schema(description = "Fin rollback ( null si rollback pas fini )")
        Instant rollbackFinishedAt,

        @Schema(description = "Duree cascade ms ( fige a 100% )")
        long    cascadeDurationMs,

        @Schema(description = "Duree finalize ms ( live tant que FINALIZE_RUNNING )")
        long    finalizeDurationMs,

        @Schema(description = "Duree rollback ms ( live tant que ROLLBACK_IN_PROGRESS )")
        long    rollbackDurationMs,

        @Schema(description = "Total rows emit par cascade ( = workflow_log.records_processed )")
        long    expectedTotal,

        @Schema(description = "Rows arrivees dans <app>.referencevalue ( count par binaryfile )")
        long    finalCount,

        @Schema(description = "Rows restantes en oa_staging ( SHARED_UNLOGGED uniquement , sinon -1 )")
        long    stagingRemaining,

        @Schema(description = "Debit cascade l/s ( fige a phase >= FINALIZE_RUNNING )")
        long    cascadeThroughput,

        @Schema(description = "Debit finalize l/s ( live tant que FINALIZE_RUNNING )")
        long    finalizeThroughput,

        @Schema(description = "Message d'erreur fatale ( null sauf phase ROLLBACK_* )")
        String  errorMessage,

        @Schema(description = "Compteur in-memory rows ecrites en staging par sink ( cumul live , approximation )")
        long    stagingRowsWritten,

        @Schema(description = "Compteur in-memory rows ecrites en table finale ( 0 pendant phase B atomique , = expected quand COMPLETED )")
        long    finalRowsWritten,

        @Schema(description = "True si la progression du remplissage staging est observable temps-reel")
        boolean stagingDeterminate,

        @Schema(description = "True si la progression du transfert vers la finale est observable temps-reel")
        boolean finalDeterminate,

        @Schema(description = "Sous-phase courante du workflow . Valeurs : "
                + "MERGE_FILE phases ( MERGE_LOCAL | TEMP_LOAD | UPSERT_FINAL ) , "
                + "ou phases post-UPSERT communes ( REFREF_REBUILD | "
                + "SYNTHESIS_REBUILD | CACHE_CAPTURE ) . Null si aucune "
                + "sous-phase publiee par le backend . La live view rend un "
                + "spinner indeterminate sous la bar 'UPSERT staging -> "
                + "table finale' selon cette valeur .")
        String  subPhase) {
}
