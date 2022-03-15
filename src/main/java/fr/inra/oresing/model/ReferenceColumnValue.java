package fr.inra.oresing.model;

import java.util.Collection;
import java.util.function.Function;

/**
 * Représente la valeur pour une colonne donnée d'une ligne d'un référentiel donné.
 *
 * Voir les sous-classes qui gère chacune une forme de multiplicité.
 *
 * @param <T> le type dans lequel ça va être transformé pour être stocké sous forme de JSON en base
 * @param <F> le type dans lequel ça va être transformé pour être envoyé au frontend sous forme de JSON
 */
public interface ReferenceColumnValue<T, F> extends SomethingToBeStoredAsJsonInDatabase<T>, SomethingToBeSentToFrontend<F> {

    /**
     * L'ensemble des valeurs pour lesquelles il faut appliquer les checkers
     */
    Collection<String> getValuesToCheck();

    /**
     * Une copie de l'objet mais après avoir appliqué une transformation sur toutes les valeurs contenues.
     */
    ReferenceColumnValue<T, F> transform(Function<String, String> transformation);

}
