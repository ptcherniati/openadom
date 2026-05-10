package fr.inra.oresing.domain.port;

import fr.inra.oresing.domain.application.Application;

import java.util.Collection;

/**
 * Port domaine pour les opérations de persistance liées aux migrations de configuration.
 * <p>
 * Défini dans le domaine selon l'approche Ports & Adapters.
 * Implémenté côté persistence via {@code persistence.ApplicationRepository}.
 */
public interface MigrationApplicationPort {

    /**
     * Persiste la configuration d'une application OpenADOM.
     *
     * @param application l'application à sauvegarder
     */
    void storeApplication(Application application);

    /**
     * Ajoute des identifiants de données dans les scopes d'autorisation de l'application.
     *
     * @param applicationName le nom de l'application
     * @param identifiers     les identifiants de données à ajouter
     * @return {@code true} si au moins un identifiant a été ajouté
     */
    boolean addReferenceToAuthorizationScope(String applicationName, Collection<String> identifiers);

    /**
     * Reconstruit les index d'autorisation pour l'application.
     *
     * @param application l'application dont les index sont à reconstruire
     */
    void updateAuthorizationIndexes(Application application);
}