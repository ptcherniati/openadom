package fr.inra.oresing.domain.port;

/**
 * Port domaine pour la gestion des rôles de sécurité PostgreSQL.
 * <p>
 * Défini dans le domaine selon l'approche Ports & Adapters.
 * Implémenté par {@code persistence.AuthenticationService}.
 */
public interface AuthenticationPort {

    /** Réinitialise le rôle courant (RESET ROLE PostgreSQL). */
    void resetRole();

    /** Active le rôle applicatif du client connecté. */
    void setRoleForClient();

    /** Active le rôle administrateur OpenADOM. */
    void activateAdminRole();
}