package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class PrivilegeAssessorDomainForApplicationFactory {
    Application application = Mockito.mock(Application.class);
    GetGrantableResult grantable;
    private AuthorizationsForApplicationUser authorizations;
    private PrivilegeAssessorDomain domain;

    public PrivilegeAssessorDomainForApplicationFactory() {
    }

    public static PrivilegeAssessorDomainForApplicationFactory builder() {
        return new PrivilegeAssessorDomainForApplicationFactory();
    }

    public static PrivilegeAssessorDomainForApplication forAddAuthorization() {
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withIsUserManager(true)
                                .build()).
                build();
    }

    public static PrivilegeAssessorDomainForApplication forUserManager() {
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withIsUserManager(true)
                                .build()).
                build();
    }

    public static PrivilegeAssessorDomainForApplication foApplicationManager() {
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withIsApplicationManager(true)
                                .build()).
                build();
    }

    public static PrivilegeAssessorDomainForApplication forManageAdministrator() {
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withIsApplicationManager(true)
                                .build()).
                build();
    }

    public static PrivilegeAssessorDomainForApplication forDataRead() {
        final AuthorizationParsed authorizationParsed = Mockito.mock(AuthorizationParsed.class);
        when(authorizationParsed.operationTypes()).thenReturn(Set.of(OperationType.extraction));
        Map<String, List<AuthorizationParsed>> userAuthorizations = Map.<String, List<AuthorizationParsed>>of("dataName", List.<AuthorizationParsed>of(authorizationParsed));
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withUserAuthorizations(userAuthorizations)
                                .build()
                )
                .build();
    }

    public static PrivilegeAssessorDomainForApplication forDeleteAuthorization() {
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withIsApplicationManager(true)
                                .build()
                )
                .build();
    }

    public static PrivilegeAssessorDomainForApplication forDataDeposit() {
        final AuthorizationParsed authorizationParsed = Mockito.mock(AuthorizationParsed.class);
        when(authorizationParsed.operationTypes()).thenReturn(Set.of(OperationType.depot));
        Map<String, List<AuthorizationParsed>> userAuthorizations = Map.<String, List<AuthorizationParsed>>of("dataName", List.<AuthorizationParsed>of(authorizationParsed));
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withUserAuthorizations(userAuthorizations)
                                .build()
                )
                .build();
    }

    public static PrivilegeAssessorDomainForApplication forDataPublish() {
        final AuthorizationParsed authorizationParsed = Mockito.mock(AuthorizationParsed.class);
        when(authorizationParsed.operationTypes()).thenReturn(Set.of(OperationType.publication));
        Map<String, List<AuthorizationParsed>> userAuthorizations = Map.<String, List<AuthorizationParsed>>of("dataName", List.<AuthorizationParsed>of(authorizationParsed));
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withUserAuthorizations(userAuthorizations)
                                .build()
                )
                .build();
    }

    public PrivilegeAssessorDomainForApplication build() {
        final PrivilegeAssessorDomainForApplication privilegeAssessorDomainForApplication = new PrivilegeAssessorDomainForApplication(authorizations, domain, application, grantable);
        return privilegeAssessorDomainForApplication;
    }

    public PrivilegeAssessorDomainForApplicationFactory withAuthorizations(AuthorizationsForApplicationUser authorizations) {
        this.authorizations = authorizations;
        return this;
    }

    public PrivilegeAssessorDomainForApplicationFactory withDomain(PrivilegeAssessorDomain domain) {
        this.domain = domain;
        return this;
    }

    public PrivilegeAssessorDomainForApplicationFactory withGrantable(GetGrantableResult grantable) {
        this.grantable = grantable;
        return this;
    }

    public PrivilegeAssessorDomainForApplicationFactory withApplication(Application application) {
        this.application = application;
        return this;
    }

    public static PrivilegeAssessorDomainForApplication forUpdateApplication() {
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withIsApplicationManager(true)
                                .build()).
                build();
    }

    public static PrivilegeAssessorDomainForApplication forManageAuthorizations() {
        return builder().withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withIsUserManager(true)
                                .build()).
                build();
    }
}