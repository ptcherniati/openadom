package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

/**
 * Empêche un utilisateur de se révoquer lui-même son rôle
 * {@code applicationManager} ou {@code userManager} sur une application
 * dont il est gestionnaire , sauf s'il est aussi {@code openAdomAdmin} .
 *
 * <p><b>Pourquoi ce garde-fou :</b> sans lui , un gestionnaire
 * d'application pouvait se révoquer lui-même par mégarde ( ou par
 * malveillance ) , laissant potentiellement une application sans
 * aucun gestionnaire et donc orpheline ( la modification ne peut
 * être annulée que par un {@code openAdomAdmin} ) .
 *
 * <p>Le contournement reste possible : un {@code openAdomAdmin} peut
 * révoquer n'importe quel utilisateur , et un autre
 * {@code applicationManager} de la même application peut aussi
 * révoquer ses pairs ( cas du retrait d'un membre de l'équipe par un
 * autre membre ) . Seule l'auto-révocation par soi-même est bloquée .
 */
public class CantSelfRevokeApplicationRoleException extends OreSiTechnicalException {
    public static final String CANT_SELF_REVOKE_APPLICATION_ROLE = "CANT_SELF_REVOKE_APPLICATION_ROLE";

    public CantSelfRevokeApplicationRoleException() {
        super(CANT_SELF_REVOKE_APPLICATION_ROLE);
    }
}
