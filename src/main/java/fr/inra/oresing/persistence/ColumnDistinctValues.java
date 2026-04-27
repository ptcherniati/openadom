package fr.inra.oresing.persistence;

import java.util.List;

/**
 * Entrée du payload {@code /filters} portant les **valeurs distinctes**
 * effectivement présentes dans une colonne marquée {@code __FILTER_LIST__} ,
 * pour alimenter une dropdown searchable côté frontend ( cf.
 * {@code ListFilter.vue} ).
 *
 * <p><b>Multiplicité</b> : pour les colonnes {@code MANY} , le SQL utilise
 * {@code jsonb_array_elements_text} pour déplier le tableau avant le
 * {@code DISTINCT} ; pour {@code ONE} , {@code refvalues #>> '{key}'} suffit.
 *
 * <p><b>Valeurs vides</b> : un {@code null} dans {@link #values} signifie
 * "valeur absente / vide" et doit être rendu *(vide)* côté UI ( i18n ).
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
 *                     contenir un {@code null} pour signaler "vide"
 * @param truncated    {@code true} si le {@code DISTINCT} a été plafonné
 *                     à 10 000 ; {@code false} sinon
 */
public record ColumnDistinctValues(
        String componentKey,
        List<String> values,
        boolean truncated
) implements FilterListEntry {
    /**
     * Plafond de cardinalité de la dropdown ( décision Q4 ). Au-delà , la
     * SQL tronque et {@link #truncated} passe à {@code true}.
     */
    public static final int DISTINCT_VALUES_LIMIT = 10_000;
}
