package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdminException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationCreator;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationCreatorUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.OpenAdomAdmin;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Optional;
import java.util.Set;

public record PrivilegeAssessorDomainForSystem<PrivilegeSystemDomain>(
        AuthorizationsForSystemUser authorizations,
        fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain domain
) implements PrivilegeAssessorDomain {
    public OpenAdomAdmin forAdministrationManagement() {
        return Optional.of(authorizations())
                .filter(authorizationsForSystemUser -> authorizationsForSystemUser.currentUserRoles().isOpenAdomAdmin())
                .map(t->new OpenAdomAdmin())
                .orElseThrow(NotOpenAdomAdminException::new);
    }

    public ApplicationCreator forCreateApplication() {
        Set<String> applicationCreatorPatterns = Optional.of(authorizations())
                .map(AuthorizationsForSystemUser::applicationCreator)
                .filter(CollectionUtils::isNotEmpty)
                .orElseThrow(NotApplicationCreatorRightsException::new);
        if (authorizations().currentUserRoles().isOpenAdomAdmin()) {
            return new OpenAdomAdmin();
        }
        return new ApplicationCreatorUser(applicationCreatorPatterns);
    }

    public ConnectedUser connectedUser() {
        return new ConnectedUser(
                authorizations().currentUserRoles(),
                authorizations().applicationCreator()
        );
    }
}
