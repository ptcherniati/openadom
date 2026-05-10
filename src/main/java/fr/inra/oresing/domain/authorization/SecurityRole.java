package fr.inra.oresing.domain.authorization;
/**
 * Constantes des rôles Spring Security utilisées dans le domaine.
 * <p>Extrait de {@code rest.security.AuthorizationFilter} pour supprimer la dépendance
 * du domaine vers la couche REST/Security.</p>
 */
public final class SecurityRole {
    public static final String ROLE_AUTHENTIFIED_USER_VALUE = "ROLE_AUTHENTIFIED_USER";
    public static final String ROLE_UNAUTHENTIFIED_UPDATE_USER_VALUE = "ROLE_UNAUTHENTIFIED_UPDATE_USER";
    public static final String ROLE_UNAUTHENTIFIED_CREATE_USER_VALUE = "ROLE_UNAUTHENTIFIED_CREATE_USER";
    private SecurityRole() {}
}
