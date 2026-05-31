package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.DataValue;

import java.util.Set;
import java.util.UUID;

/**
 * Port étroit ( DIP / ISP ) d'accès aux identifiants de référence en base, pour
 * le chargement à l'import. Découple les {@link ReferenceLoadingStrategy} de la
 * couche persistance concrète ( {@code DataRepository} ) : les stratégies sont
 * ainsi testables avec un simple fake, sans base ni Spring.
 *
 * <p>Implémenté ( ou adapté ) par {@code DataRepository} ; expose exactement les
 * deux modes de chargement existants :
 * <ul>
 *   <li>{@link #loadAll} : toutes les références du type ( full preload ) ;</li>
 *   <li>{@link #loadByNaturalKeys} : seulement les clés naturelles fournies
 *       ( chargement borné O(|clés|) ).</li>
 * </ul>
 */
public interface ReferenceIdLoader {

    /** Charge l'identifiant de TOUTES les références du type ( full preload ). */
    ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadAll(String refType);

    /**
     * Charge l'identifiant des seules références dont la clé naturelle figure
     * dans {@code naturalKeys} ( chargement borné ).
     */
    ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadByNaturalKeys(String refType, Set<String> naturalKeys);
}
