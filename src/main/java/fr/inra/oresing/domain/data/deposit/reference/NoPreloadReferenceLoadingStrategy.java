package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.DataValue;

import java.util.UUID;

/**
 * Stratégie « aucun préchargement » : retourne une map vide.
 *
 * <p>Utilisée pour les référentiels self / datatypes <b>non récursifs</b> :
 * les identifiants existants n'ont pas besoin d'être matérialisés en RAM, ils
 * sont retrouvés en SQL ( JOIN sur referencevalue ) après l'UPSERT
 * ( cf {@code DataRepository.storeAll} ). Évite ~110s + ~2 GB heap sur gros
 * datatypes non récursifs ( refacto B ).
 *
 * <p>Sans état.
 */
public final class NoPreloadReferenceLoadingStrategy implements ReferenceLoadingStrategy {

    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadIdsPerKey(
            ReferenceIdLoader loader, String refType, ReferenceLoadContext context) {
        return ImmutableMap.of();
    }
}
