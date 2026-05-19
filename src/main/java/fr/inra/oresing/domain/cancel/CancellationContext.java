package fr.inra.oresing.domain.cancel;

import java.util.UUID;

/**
 * ThreadLocal scope portant le {@link CancellationToken} de l'operation
 * en cours + le {@code parentCid} ( workflow publish/unpublish ) pour
 * les chemins ou ce dernier doit etre relaye au cascade pipeline .
 *
 * <p><b>Pourquoi unifier</b> ( remplace {@code PublishCorrelationContext}
 * desormais supprime ) :
 * <ul>
 *   <li>1 seul ThreadLocal au lieu de 2 -> aucune asymetrie possible
 *       entre token et parentCid ;</li>
 *   <li>API symetrique : {@link #set} / {@link #clear} encadrent un
 *       try/finally au point d'entree ( Phase2Handler.doPublish ) ;</li>
 *   <li>{@link #checkpoint} 1-liner reutilisable depuis n'importe ou
 *       dans l'arborescence d'appels prep ( DataService , CreateDataUseCase ,
 *       cascade init ) sans devoir propager le token explicitement dans
 *       toutes les signatures .</li>
 * </ul>
 *
 * <p><b>Cycle de vie type</b> :
 * <pre>{@code
 * CancellationContext.set(parentCid, () -> coordinator.isCancelled(parentCid));
 * try {
 *     dataService.addData(...);  // checkpoints internes
 * } finally {
 *     CancellationContext.clear();
 * }
 * }</pre>
 *
 * <p><b>Async</b> : le pool @Async openADOM est decore avec
 * {@code ContextPropagatingTaskDecorator} ( cf
 * {@code DataServiceContextConfiguration} ) . Cascade cree son propre pool
 * pour les sink/transform workers mais le ThreadLocal est consulte uniquement
 * a l'entree synchrone {@code CascadeImportPipeline.executeImport} qui s'execute
 * sur le thread du caller .
 *
 * @author R.YAHIAOUI
 */
public final class CancellationContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private CancellationContext() { /* static-only */ }

    /**
     * Active le scope pour le thread courant . A appeler avant l'operation
     * longue ; {@link #clear} OBLIGATOIRE dans un finally pour eviter la
     * fuite de scope entre 2 jobs du meme thread pool .
     */
    public static void set(UUID parentCid, CancellationToken token) {
        CURRENT.set(new Scope(parentCid, token != null ? token : CancellationToken.NONE));
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Token actif , {@link CancellationToken#NONE} si aucun scope .
     */
    public static CancellationToken currentToken() {
        Scope s = CURRENT.get();
        return s != null ? s.token() : CancellationToken.NONE;
    }

    /**
     * ParentCid du workflow publish/unpublish , null si aucun scope ou
     * appel hors contexte ( import direct utilisateur ) .
     */
    public static UUID currentParentCid() {
        Scope s = CURRENT.get();
        return s != null ? s.parentCid() : null;
    }

    /**
     * Raccourci : leve {@link java.util.concurrent.CancellationException}
     * si le token courant est cancelled , no-op sinon ( meme hors scope
     * grace au sentinel NONE ) . A poser aux frontieres d'etapes lourdes
     * de la phase prep ( validation fichier , init chunk dir , etc ) .
     */
    public static void checkpoint() {
        currentToken().throwIfCancelled();
    }

    public static void checkpoint(String stage) {
        currentToken().throwIfCancelled(stage);
    }

    private record Scope(UUID parentCid, CancellationToken token) { }
}
