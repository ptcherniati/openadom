package fr.inra.oresing.domain.checker.type.reference;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;

import java.util.Set;
import java.util.UUID;

/**
 * Résout une clé naturelle de référentiel ( {@link Ltree} ) vers son ou ses
 * {@link UUID} en base, pour la validation des colonnes de type référence à
 * l'import.
 *
 * <h2>Pourquoi cette abstraction</h2>
 *
 * <p>Historiquement, {@code ReferenceType} cumulait deux responsabilités :
 * le contrat {@code FieldType} ( orchestration du {@code check()} ) ET le
 * stockage + lookup des valeurs de référence ( map de base, overlay
 * incrémental, index O(1) naturalKey, caractères spéciaux ). Cette interface
 * isole la <b>seconde</b> ( SRP ) : {@code ReferenceType} délègue désormais la
 * résolution à un {@code ReferenceResolver}, sans connaître <b>comment</b> les
 * références sont chargées ( DIP ).
 *
 * <p>Cela ouvre ( OCP ) plusieurs stratégies de chargement interchangeables
 * derrière le même contrat, sans toucher au checker ni au pipeline :
 * <ul>
 *   <li>{@link MapBackedReferenceResolver} : tout chargé en mémoire ( eager,
 *       comportement historique ) ;</li>
 *   <li>( à venir ) chargement borné aux clés pré-scannées du fichier ;</li>
 *   <li>( à venir ) chargement paresseux à la demande ( gros référentiels ).</li>
 * </ul>
 *
 * <h2>Concurrence</h2>
 *
 * <p>Un même resolver est <b>partagé</b> entre l'instance {@code ReferenceType}
 * d'origine et ses copies par worker ( cf {@code ReferenceType.copy()} ) :
 * {@link #findKey} et {@link #resolveUuids} doivent être thread-safe en lecture
 * concurrente. {@link #register} ( ajout incrémental du mode récursif ordonné )
 * est mono-thread par contrat ; {@link #replaceBase} est appelé hors hot path.
 */
public interface ReferenceResolver {

    /**
     * Lookup O(1) : retourne la clé d'identité ( naturalKey + hierarchicalKey +
     * patternColumnName ) correspondant à la valeur de clé naturelle donnée, ou
     * {@code null} si aucune référence ne porte cette clé.
     */
    DataValue.LineIdentityColumnName findKey(Ltree naturalKey);

    /**
     * Résout les UUID d'une clé : valeurs de base d'abord, puis overlay
     * incrémental. Retourne {@code null} si la clé est inconnue.
     */
    ImmutableSet<UUID> resolveUuids(DataValue.LineIdentityColumnName key);

    /**
     * Enregistre incrémentalement une nouvelle référence découverte pendant
     * l'import ( mode récursif ordonné, mono-thread ). No-op si la clé est déjà
     * connue ( base ou overlay ). Iso-résultat avec un {@link #replaceBase}
     * équivalent : même clé visible, mêmes UUID.
     */
    void register(DataValue.LineIdentityColumnName key, ImmutableSet<UUID> uuids);

    /**
     * Remplace l'ensemble des valeurs de base et reconstruit l'index + les
     * caractères spéciaux. L'overlay incrémental n'est pas vidé ( sémantique
     * historique de {@code setReferenceValues} ).
     */
    void replaceBase(ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues);

    /**
     * Vue immuable des valeurs de base ( hors overlay ), utilisée pour la fusion
     * pré-chargé + incréments à la construction des checkers et pour le rapport
     * d'erreur ( liste des clés connues ).
     */
    ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> baseValues();

    /**
     * Ensemble des codes de caractères spéciaux présents dans les clés
     * naturelles connues, utilisé par l'échappement {@code Ltree} avant lookup.
     */
    Set<String> knownSpecialCharacters();
}
