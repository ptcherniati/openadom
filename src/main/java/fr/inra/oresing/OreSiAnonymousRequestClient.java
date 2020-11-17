package fr.inra.oresing;

import fr.inra.oresing.persistence.roles.OreSiAnonymousRole;
import fr.inra.oresing.persistence.roles.OreSiRole;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.UUID;

public enum OreSiAnonymousRequestClient implements OreSiRequestClient {

    ANONYMOUS;

    @Override
    public UUID getId() {
        throw new UnsupportedOperationException("la requête est faite en tant qu'utilisateur anonyme, il n'y a pas d'identifiant associé");
    }

    @Override
    public OreSiAnonymousRole getRole() {
        return OreSiRole.anonymous();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("role", getRole())
                .toString();
    }
}
