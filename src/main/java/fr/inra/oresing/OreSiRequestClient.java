package fr.inra.oresing;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.UUID;

@Getter
@Setter
@ToString
public class OreSiRequestClient {

    private UUID id;

    private OreSiUserRole role;

    private static final OreSiRequestClient ANONYMOUS_SINGLETON =
            new OreSiRequestClient() {
                @Override
                public UUID getId() {
                    throw new UnsupportedOperationException("la requête est faite en tant qu'utilisateur anonyme, il n'y a pas d'identifiant associé");
                }

                @Override
                public OreSiUserRole getRole() {
                    return OreSiUserRole.anonymous();
                }

                @Override
                public void setId(UUID id) {
                    throw new IllegalStateException("on ne modifie pas " + ANONYMOUS_SINGLETON);
                }

                @Override
                public void setRole(OreSiUserRole role) {
                    throw new IllegalStateException("on ne modifie pas " + ANONYMOUS_SINGLETON);
                }

                @Override
                public String toString() {
                    return new ToStringBuilder(this)
                            .append("role", getRole())
                            .toString();
                }
            };

    public static OreSiRequestClient anonymous() {
        return ANONYMOUS_SINGLETON;
    }

    public static OreSiRequestClient forUser(UUID id, OreSiUserRole role) {
        OreSiRequestClient newOreSiRequestClient = new OreSiRequestClient();
        newOreSiRequestClient.setId(id);
        newOreSiRequestClient.setRole(role);
        return newOreSiRequestClient;
    }

    public boolean isAnonymous() {
        return getRole().isAnonymous();
    }
}
