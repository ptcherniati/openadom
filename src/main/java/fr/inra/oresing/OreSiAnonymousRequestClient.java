package fr.inra.oresing;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.DisconnectedException;
import fr.inra.oresing.domain.repository.authorization.role.OreSiAnonymousRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.UUID;

public enum OreSiAnonymousRequestClient implements OreSiRequestClient {

    ANONYMOUS;

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("role", role())
                .toString();
    }
}