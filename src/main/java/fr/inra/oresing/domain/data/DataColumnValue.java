package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;

import java.util.function.Function;

/**
 * Représente la valeur pour une colonne donnée d'une ligne d'un référentiel donné.
 * <p>
 * Voir les sous-classes qui gère chacune une forme de multiplicité.
 *
 * @param <T> le type dans lequel ça va être transformé pour être stocké sous forme de JSON en base
 * @param <F> le type dans lequel ça va être transformé pour être envoyé au frontend sous forme de JSON
 */
public interface DataColumnValue<T, F> extends SomethingToBeStoredAsJsonInDatabase<T>, SomethingToBeSentToFrontend<F> {

    /**
     * L'ensemble des valeurs pour lesquelles il faut appliquer les checkers
     */
    FieldType getValuesToCheck();

    /**
     * Une copie de l'objet mais après avoir appliqué une transformation sur toutes les valeurs contenues.
     */
    DataColumnValue<T, F> transform(Function<FieldType, FieldType> transformation);

    String toValueString(DataImporterContext referenceImporterContext, String referencedColumn, String key);
}