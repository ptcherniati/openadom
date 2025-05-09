package fr.inra.oresing;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.DisconnectedException;
import fr.inra.oresing.domain.repository.authorization.role.OreSiAnonymousRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRoleToAccessDatabase;

import java.util.UUID;

public interface OreSiRequestClient {

    public static final String DISCONECTED_EXCEPTION = "la requête est faite en tant qu'utilisateur anonyme, il n'y a pas d'identifiant associé";

    default UUID id() {
        throw new DisconnectedException(DISCONECTED_EXCEPTION);
    }

    default OreSiRoleToAccessDatabase role() {
        return OreSiRole.anonymous();
    }
}