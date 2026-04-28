package fr.inra.oresing.persistence;

/**
 * Élément d'un payload {@code /filters} : soit un {@link FilterList}
 * ( valeurs de {@code ReferenceChecker} liées au datatype , historique du
 * endpoint ) , soit un {@link ColumnDistinctValues} ( valeurs distinctes
 * d'une colonne marquée {@code __FILTER_LIST__} ).
 *
 * <p><b>Pas de {@code @JsonTypeInfo}</b> : la version précédente en posait
 * une pour exposer un discriminateur {@code @class} côté wire , mais cela
 * cassait la déserialization Jackson de {@code FilterList} dans
 * {@code JsonRowMapper} ( le JSON émis par la requête SQL ne porte pas le
 * champ {@code @class} dans le contenu - il est dans une colonne séparée -
 * et Jackson l'exigeait à cause de l'annotation polymorphique ).
 *
 * <p>Sealed pour garantir au compile-time que tout consommateur côté Java
 * énumère les deux types. Côté frontend , la discrimination se fait par
 * **forme** dans {@code buildFilterListMap} :
 * <ul>
 *   <li>{@code typeof entry.listName === "string"} -> {@code FilterList}</li>
 *   <li>{@code Array.isArray(entry.values)} -> {@code ColumnDistinctValues}</li>
 * </ul>
 * Pas de risque de collision : les deux records n'ont aucun champ commun.
 */
public sealed interface FilterListEntry permits FilterList, ColumnDistinctValues {
}
