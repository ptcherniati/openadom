package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.DataValue;

import java.util.UUID;

/**
 * Stratégie de chargement <b>borné</b> : ne charge que les références dont la
 * clé naturelle figure dans le {@code naturalKeysHint} pré-scanné du fichier
 * ( {@link ReferenceIdLoader#loadByNaturalKeys} ). Mémoire O(|hint|) au lieu de
 * O(taille du référentiel) → évite l'OOM sur référentiels 10M+ lignes.
 *
 * <p>Repli sûr : si aucun hint n'est disponible, full preload
 * ( {@link ReferenceIdLoader#loadAll} ) pour ne jamais perdre une référence.
 *
 * <p>Sans état.
 */
public final class PrescanBoundedReferenceLoadingStrategy implements ReferenceLoadingStrategy {

    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadIdsPerKey(
            ReferenceIdLoader loader, String refType, ReferenceLoadContext context) {
        return context.hasNaturalKeysHint()
                ? loader.loadByNaturalKeys(refType, context.naturalKeysHint())
                : loader.loadAll(refType);
    }
}
