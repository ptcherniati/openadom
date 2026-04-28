package fr.inra.oresing.persistence;

import java.util.List;

/**
 * Entrée du payload {@code /filters} portant des informations sur les
 * valeurs effectivement présentes dans une colonne filtrable ( marquée
 * {@code __FILTER_LIST__} ou {@code __FILTER_TEXT__} ).
 *
 * <p><b>Deux usages</b> :
 * <ul>
 *   <li>{@code __FILTER_LIST__} : alimente le dropdown searchable côté
 *       frontend ( cf. {@code ListFilter.vue} ) avec la liste complète
 *       des valeurs distinctes triées alphabétiquement.</li>
 *   <li>{@code __FILTER_TEXT__} : seul {@link #hasEmpty} est utile au
 *       front - il sert à conditionner l'affichage du bouton
 *       *"+ (vide)"*. {@link #values} est alors vide ; on évite la
 *       requête {@code DISTINCT} coûteuse.</li>
 * </ul>
 *
 * <p><b>Multiplicité</b> : pour les colonnes {@code MANY} , le SQL utilise
 * {@code jsonb_array_elements_text} pour déplier le tableau avant le
 * {@code DISTINCT} ; pour {@code ONE} , {@code refvalues #>> '{key}'} suffit.
 *
 * <p><b>Valeurs vides</b> : un {@code null} dans {@link #values} signifie
 * "valeur absente / vide" et doit être rendu *(vide)* côté UI ( i18n ).
 * Le drapeau {@link #hasEmpty} reflète la même information sous forme
 * boolean , utile en l'absence de {@link #values} ( cas FILTER_TEXT ).
 * Voir §5.7 de {@code FILTER_TEXT_LIST.md}.
 *
 * <p><b>Cardinalité</b> : la liste est tronquée côté SQL à 10 000 entrées
 * pour borner le coût ; quand la troncature s'applique , {@link #truncated}
 * est positionné à {@code true} et le frontend affiche un bandeau dans le
 * dropdown.
 *
 * @param componentKey clé de la colonne ( cohérente avec la clé du
 *                     {@code componentDescriptions} dans la
 *                     {@link fr.inra.oresing.domain.application.configuration.Configuration} )
 * @param values       valeurs distinctes triées alphabétiquement ; peut
 *                     contenir un {@code null} pour signaler "vide" ;
 *                     vide pour les colonnes {@code __FILTER_TEXT__}
 * @param truncated    {@code true} si le {@code DISTINCT} a été plafonné
 *                     à 10 000 ; {@code false} sinon
 * @param hasEmpty     {@code true} si la colonne a au moins une valeur
 *                     {@code null} en base ; sert au front pour décider
 *                     d'afficher le bouton *"+ (vide)"* dans
 *                     {@code TextFilter} et la chip *"(vide)"* dans
 *                     {@code ListFilter} sans avoir à scanner
 *                     {@link #values}
 */
public record ColumnDistinctValues(
        String componentKey,
        List<String> values,
        boolean truncated,
        boolean hasEmpty
) implements FilterListEntry {
    /**
     * Plafond de cardinalité de la dropdown ( décision Q4 ). Au-delà , la
     * SQL tronque et {@link #truncated} passe à {@code true}.
     */
    public static final int DISTINCT_VALUES_LIMIT = 10_000;
}
