package fr.inra.oresing.model;

/**
 * Indique qu'un objet a vocation à être transformer en un autre tel qu'il est attendu par le frontend
 *
 * @param <T> le type qui est attentu par le frontend
 */
public interface SomethingToBeSentToFrontend<T> {

    T toJsonForFrontend();
}
