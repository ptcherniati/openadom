package fr.inra.oresing.workflow.guard;

/**
 * Levee par {@link HeapGuardService} quand une demande publish /
 * unpublish / delete-file arrive alors que le heap JVM depasse le
 * seuil configure ( {@code refuse-publish-threshold-pct} , defaut 80% ) .
 *
 * <p>Semantique HTTP : doit etre mappee a <b>503 Service Unavailable</b>
 * avec header {@code Retry-After: 60} par le handler global d'erreur .
 * Le client peut retry dans 1 min ; le sweeper interne ou les workflows
 * en cours auront liberees du heap entre-temps .
 *
 * <p>Different de {@code WorkflowConflictException} ( 409 Conflict :
 * conflit metier , immediat ) - ici c'est un probleme transitoire
 * d'infrastructure , pas un conflit de donnees .
 *
 * @author R.YAHIAOUI
 */
public class BackendOverloadedException extends RuntimeException {

    private final double currentHeapPct;
    private final int    thresholdPct;

    public BackendOverloadedException(double currentHeapPct, int thresholdPct) {
        super(String.format(
                "Backend memoire saturee ( heap %.1f %% , seuil %d %% ) . "
                        + "Nouvelle publication refusee pour eviter un crash OOM . "
                        + "Reessayez dans quelques minutes le temps que la pression heap baisse .",
                currentHeapPct, thresholdPct));
        this.currentHeapPct = currentHeapPct;
        this.thresholdPct   = thresholdPct;
    }

    public double getCurrentHeapPct() {
        return currentHeapPct;
    }

    public int getThresholdPct() {
        return thresholdPct;
    }
}
