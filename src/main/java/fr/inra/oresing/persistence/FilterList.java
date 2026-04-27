package fr.inra.oresing.persistence;

import java.util.List;

/**
 * Entrée historique du payload {@code /filters} : représente la liste des
 * valeurs d'un {@code ReferenceChecker} lié à un datatype , utilisée pour
 * alimenter les dropdowns "filtre par référence" côté frontend ( cf.
 * {@code ReferenceFilter.vue} ).
 *
 * <p>Cohabite avec {@link ColumnDistinctValues} dans la même réponse via
 * {@link FilterListEntry}.
 */
public record FilterList(
        String listName,
        List<RefsLinked> refsLinkeds
) implements FilterListEntry {
}