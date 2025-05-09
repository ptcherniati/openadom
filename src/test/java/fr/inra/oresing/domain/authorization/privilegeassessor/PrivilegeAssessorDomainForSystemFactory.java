package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;

import java.util.Set;

import static fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForApplicationFactory.DEFAULT_DATA_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Factory pour créer des instances de PrivilegeAssessorDomainForSystem pour les tests
 */
public class PrivilegeAssessorDomainForSystemFactory {
    public static final PrivilegeAssessorDomainForSystem OPENADOM_ADMIN = builder()
            .withIsOpenAdomAdmin(true)
            .build();
    public static final PrivilegeAssessorDomainForSystem APPLICATION_CREATOR = builder()
            .withIsApplicationCreator(Set.of(DEFAULT_DATA_NAME))
            .build();

    private PrivilegeAssessorDomainForSystemFactory withIsApplicationCreator(Set<String> defaultDataNamePatterns) {
        when(authorizations.applicationCreator()).thenReturn(defaultDataNamePatterns);
        return this;
    }

    public static final PrivilegeAssessorDomainForSystem NO_SYSTEM_RIGHTS = builder()
            .build();

    // Initialisation des constantes nécessitant des mocks
    static {

    }

    private PrivilegeAssessorDomainForSystemFactory withIsOpenAdomAdmin(boolean isOpenAdomAdmin) {
        when(currentUserRoles.isOpenAdomAdmin()).thenReturn(isOpenAdomAdmin);
        return this;
    }

    // Attributs d'instance pour le builder
    private AuthorizationsForSystemUser authorizations = mock(AuthorizationsForSystemUser.class, "mock authorizations");
    private PrivilegeSystemDomainEnum privilegeSystemDomainEnum;
    private CurrentUserRoles currentUserRoles = mock(CurrentUserRoles.class, "mock currentUserRoles");


    public static PrivilegeAssessorDomainForSystemFactory builder() {
        final PrivilegeAssessorDomainForSystemFactory privilegeAssessorDomainForSystemFactory = new PrivilegeAssessorDomainForSystemFactory();
        when(privilegeAssessorDomainForSystemFactory.authorizations.currentUserRoles()).thenReturn(privilegeAssessorDomainForSystemFactory.currentUserRoles);
        return privilegeAssessorDomainForSystemFactory;
    }

    public PrivilegeAssessorDomainForSystem build() {
        final PrivilegeAssessorDomainForSystem privilegeAssessorDomainForApplication = new PrivilegeAssessorDomainForSystem(authorizations, privilegeSystemDomainEnum);
        return privilegeAssessorDomainForApplication;
    }

    public PrivilegeAssessorDomainForSystemFactory withAuthorizations(AuthorizationsForSystemUser authorizations) {
        this.authorizations = authorizations;
        return this;
    }

    public PrivilegeAssessorDomainForSystemFactory withDomain(PrivilegeSystemDomainEnum privilegeSystemDomainEnum) {
        this.privilegeSystemDomainEnum = privilegeSystemDomainEnum;
        return this;
    }

    public PrivilegeAssessorDomainForSystemFactory withCurrentUserRoles(CurrentUserRoles currentUserRoles) {
        this.currentUserRoles = currentUserRoles;
        return this;
    }
}