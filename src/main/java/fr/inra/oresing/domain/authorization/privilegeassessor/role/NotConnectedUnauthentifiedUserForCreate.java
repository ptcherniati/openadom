package fr.inra.oresing.domain.authorization.privilegeassessor.role;


import fr.inra.oresing.rest.security.OreSiAuthorizationManager;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.util.List;

public record NotConnectedUnauthentifiedUserForCreate() implements NotConnectedUser {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Autorisation spécifique pour la création de compte
        return List.of(OreSiAuthorizationManager.ROLE_UNAUTHENTIFIED_UPDATE_USER);
    }

    @Override
    public String getPassword() {
        return null; // Aucun mot de passe disponible (utilisateur non authentifié)
    }

    @Override
    public String getUsername() {
        return "anonymous"; // Identifiant par défaut
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Permet l'accès temporaire
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Compte non verrouillé
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Juste pour la création
    }

    @Override
    public boolean isEnabled() {
        return true; // Actif pour cette action
    }
}


