package fr.inra.oresing.persistence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Élément d'un payload {@code /filters} : soit un {@link FilterList}
 * ( valeurs de {@code ReferenceChecker} liées au datatype , historique du
 * endpoint ) , soit un {@link ColumnDistinctValues} ( valeurs distinctes
 * d'une colonne marquée {@code __FILTER_LIST__} ).
 *
 * <p>Le {@link JsonTypeInfo} expose la propriété {@code @class} déjà émise
 * par la couche SQL via {@code JsonRowMapper} , ce qui permet au frontend
 * de discriminer les deux types sans renommer le contrat existant.
 *
 * <p>Sealed pour garantir au compile-time que tout consommateur côté Java
 * énumère les deux types ; le frontend , lui , se contentera d'un
 * {@code switch} sur {@code @class}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FilterList.class, name = "fr.inra.oresing.persistence.FilterList"),
        @JsonSubTypes.Type(value = ColumnDistinctValues.class, name = "fr.inra.oresing.persistence.ColumnDistinctValues")
})
public sealed interface FilterListEntry permits FilterList, ColumnDistinctValues {
}
