package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.DataValue;

import java.util.UUID;

/**
 * Stratégie de chargement <b>eager</b> ( comportement historique, défaut ) :
 * précharge TOUTES les références du type en mémoire via
 * {@link ReferenceIdLoader#loadAll}. Le {@code naturalKeysHint} du contexte est
 * délibérément ignoré ( c'est le rôle des stratégies bornée / paresseuse de
 * Phase 3 d'en tirer parti ).
 *
 * <p>Sans état, donc partageable / réutilisable sans risque.
 */
public final class EagerReferenceLoadingStrategy implements ReferenceLoadingStrategy {

    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadIdsPerKey(
            ReferenceIdLoader loader, String refType, ReferenceLoadContext context) {
        return loader.loadAll(refType);
    }
}
