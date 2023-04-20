package fr.inra.oresing.model;

/**
 * Indique qu'un objet a vocation à être transformé dans une forme adaptée à se sérialisation pour stockage dans un JSON en base de données.
 *
 * @param <T> le type attendu qui sera sérialisable en JSON
 */
public interface SomethingToBeStoredAsJsonInDatabase<T> {

    T toJsonForDatabase();
}