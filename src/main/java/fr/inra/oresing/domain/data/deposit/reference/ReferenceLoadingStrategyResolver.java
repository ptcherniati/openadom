package fr.inra.oresing.domain.data.deposit.reference;

/**
 * Sélectionne la {@link ReferenceLoadingStrategy} de chargement des références
 * <b>self</b> ( référentiel chaînant ses propres clés ) à partir du
 * {@link ReferenceLoadContext}. Centralise la décision « quoi charger » jusque-là
 * dispersée en {@code if/else} dans la construction du contexte d'import.
 *
 * <p>Règle ( strictement iso au comportement historique ) :
 * <ul>
 *   <li>non récursif → {@link NoPreloadReferenceLoadingStrategy} ( IDs retrouvés
 *       post-UPSERT en SQL ) ;</li>
 *   <li>récursif + hint pré-scanné → {@link PrescanBoundedReferenceLoadingStrategy}
 *       ( O(|hint|), évite l'OOM gros référentiels ) ;</li>
 *   <li>récursif sans hint → {@link EagerReferenceLoadingStrategy} ( full preload,
 *       repli legacy ).</li>
 * </ul>
 *
 * <p>Sans état, stratégies réutilisables ( singletons internes ). Ajouter une
 * stratégie ( ex. paresseuse ) = une branche ici, sans toucher au pipeline ( OCP ).
 */
public final class ReferenceLoadingStrategyResolver {

    private final ReferenceLoadingStrategy noPreload = new NoPreloadReferenceLoadingStrategy();
    private final ReferenceLoadingStrategy eager = new EagerReferenceLoadingStrategy();
    private final ReferenceLoadingStrategy prescanBounded = new PrescanBoundedReferenceLoadingStrategy();

    public ReferenceLoadingStrategy resolve(ReferenceLoadContext context) {
        if (!context.recursive()) {
            return noPreload;
        }
        if (context.hasNaturalKeysHint()) {
            return prescanBounded;
        }
        return eager;
    }
}
