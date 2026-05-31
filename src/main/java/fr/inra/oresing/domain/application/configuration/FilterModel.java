package fr.inra.oresing.domain.application.configuration;

/**
 * Modèle d'indexation utilisé pour les filtres sur {@code referencevalue.refvalues}.
 * Permet de choisir entre aucun index de filtre, le GIN JSONB historique et des
 * index sélectifs limités aux colonnes explicitement filtrables.
 */
public enum FilterModel {
    /** Aucun filtre ni index GIN : empreinte minimale de table. */
    NONE,
    /** Index GIN historique sur {@code refvalues jsonb_path_ops} et filtres JSONB {@code @>}. */
    LEGACY_GIN,
    /** Index sélectifs uniquement sur les colonnes taguées {@code __FILTER_TEXT__} / {@code __FILTER_LIST__}. */
    DEFINED_FILTERS;

    /**
     * Valeur appliquée quand la configuration YAML ne déclare pas explicitement
     * de modèle de filtres.
     */
    public static FilterModel defaultValue() {
        return NONE;
    }
}
