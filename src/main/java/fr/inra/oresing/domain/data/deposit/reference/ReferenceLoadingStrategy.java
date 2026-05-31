package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.DataValue;

import java.util.UUID;

/**
 * Stratégie de chargement des références d'un type à l'import ( Strategy
 * pattern ). Centralise la décision « quoi charger » jusqu'ici dispersée dans
 * les {@code if/else} de la construction du contexte d'import.
 *
 * <p>Implémentations interchangeables ( OCP ), sélectionnées par configuration
 * sans toucher au pipeline :
 * <ul>
 *   <li>{@link EagerReferenceLoadingStrategy} : full preload ( comportement
 *       historique, défaut ) ;</li>
 *   <li>( Phase 3 ) chargement borné aux clés pré-scannées du fichier ;</li>
 *   <li>( Phase 3 ) chargement paresseux à la demande ( gros référentiels ).</li>
 * </ul>
 *
 * <p>Dépend du port étroit {@link ReferenceIdLoader} ( DIP ) → testable sans
 * base ni Spring.
 *
 * @return l'identifiant par clé d'identité, prêt à alimenter un
 *         {@code ReferenceResolver}. Une map vide est valide ( ex. aucune
 *         référence préchargée nécessaire ).
 */
public interface ReferenceLoadingStrategy {

    ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadIdsPerKey(
            ReferenceIdLoader loader, String refType, ReferenceLoadContext context);
}
