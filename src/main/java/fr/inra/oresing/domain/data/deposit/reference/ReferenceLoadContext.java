package fr.inra.oresing.domain.data.deposit.reference;

import java.util.Set;

/**
 * Contexte de chargement des références d'un type, transmis à une
 * {@link ReferenceLoadingStrategy} pour qu'elle décide quoi charger.
 *
 * @param recursive       le référentiel est-il récursif ( self-référence ) ?
 *                        Le cas récursif exige les UUID parents en mémoire pour
 *                        chaîner parent→enfant ; le cas non récursif peut s'en
 *                        passer ( IDs récupérés après UPSERT ).
 * @param naturalKeysHint clés naturelles pré-scannées dans le fichier soumis,
 *                        ou {@code null} si non disponible. Permet à une
 *                        stratégie bornée / paresseuse de ne charger que le
 *                        sous-ensemble référencé.
 */
public record ReferenceLoadContext(boolean recursive, Set<String> naturalKeysHint) {

    public boolean hasNaturalKeysHint() {
        return naturalKeysHint != null && !naturalKeysHint.isEmpty();
    }

    public static ReferenceLoadContext nonRecursive() {
        return new ReferenceLoadContext(false, null);
    }

    public static ReferenceLoadContext recursive(Set<String> naturalKeysHint) {
        return new ReferenceLoadContext(true, naturalKeysHint);
    }
}
