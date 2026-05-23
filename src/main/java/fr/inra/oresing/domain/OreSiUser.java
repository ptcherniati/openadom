package fr.inra.oresing.domain;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class OreSiUser extends OreSiEntity {
    private String login;
    private String password;
    private String email;
    private Set<String> authorizations = new HashSet<>();
    private OreSiUserStates accountstate;
    private Map<String, Timestamp> chartes = new HashMap<>();
    /**
     * Email cible d'un changement en cours , avant validation de la cle .
     * {@code null} = aucun changement en attente . Decouple l'intention de
     * la validation : tant que la cle n'est pas validee , {@link #email}
     * reste l'email courant , {@link #pendingEmail} porte la cible .
     * Au commit , swap atomique pendingEmail -> email et reset a null .
     * Cf migration V17 + AuthenticationService.commitEmailChange .
     */
    private String pendingEmail;

    @SuppressWarnings("java:S115")
    public enum OreSiUserStates {
        idle, active, pending, closed
    }
}