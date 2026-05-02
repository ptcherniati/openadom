package fr.inra.oresing.rest.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Agregat global du bloc CHARGEMENT FINAL pour la page Live d'oa-live .
 * Vue infrastructure : combien de workflows en finalize en cours , combien
 * en rollback , progress cumule . Visible en permanence en tete de page ,
 * cache si {@code nbActive == 0} cote frontend .
 *
 * <p>Periode de calcul : workflows actuellement dans
 * {@link fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry}
 * dont la phase finalize est demarree ( pas {@code CASCADE_RUNNING} ) .
 *
 * <p>Auth : non-admin agrege uniquement ses propres workflows ; admin
 * agrege tous workflows ( meme cross-user ) .
 *
 * @author R.YAHIAOUI
 */
@Schema(name = "FinalizeAggregate",
        description = "Agregat global de la phase CHARGEMENT FINAL ( vue Live d'oa-live ) ")
public record FinalizeAggregateDTO(

        @Schema(description = "Nombre de workflows actifs ( toutes phases confondues ) ")
        int     nbActive,

        @Schema(description = "Nombre de workflows en phase FINALIZE_RUNNING ")
        int     nbFinalize,

        @Schema(description = "Nombre de workflows en phase ROLLBACK_IN_PROGRESS ou ROLLBACK_DONE ")
        int     nbRollback,

        @Schema(description = "Nombre de workflows en phase COMPLETED ( deja terminés mais pas encore retires du registry )")
        int     nbCompleted,

        @Schema(description = "Total rows attendues ( somme expected des workflows agreges )")
        long    expectedTotalSum,

        @Schema(description = "Total rows arrivees au final ( somme finalCount )")
        long    finalCountSum,

        @Schema(description = "Total staging restant ( somme stagingRemaining ; -1 inclus comme 0 )")
        long    stagingRemainingSum,

        @Schema(description = "Debit finalize cumule l/s ")
        long    finalizeThroughputSum) {
}
