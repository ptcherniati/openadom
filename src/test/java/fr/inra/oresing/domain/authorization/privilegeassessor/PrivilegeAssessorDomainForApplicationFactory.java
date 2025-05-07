package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Factory pour créer des instances de PrivilegeAssessorDomainForApplication pour les tests
 */
public class PrivilegeAssessorDomainForApplicationFactory {
    
    // Constante du nom de la donnée par défaut utilisée dans les tests
    public static final String DEFAULT_DATA_NAME = "dataName";
    
    /**
     * PrivilegeAssessorDomainForApplication avec le rôle USER_MANAGER
     * Permet d'ajouter des autorisations (forAddAuthorization)
     */
    public static final PrivilegeAssessorDomainForApplication USER_MANAGER_FOR_ADD_AUTHORIZATION = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .withIsApplicationManager(true)
                            .build())
            .build();
    
    /**
     * PrivilegeAssessorDomainForApplication avec le rôle USER_MANAGER
     * Permet de gérer les utilisateurs (forUserManager)
     */
    public static final PrivilegeAssessorDomainForApplication USER_MANAGER = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .withIsUserManager(true)
                            .build())
            .build();
    
    /**
     * PrivilegeAssessorDomainForApplication avec le rôle APPLICATION_MANAGER
     * Permet de gérer l'application (foApplicationManager)
     */
    public static final PrivilegeAssessorDomainForApplication APPLICATION_MANAGER = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .withIsApplicationManager(true)
                            .build())
            .build();
    
    /**
     * PrivilegeAssessorDomainForApplication avec le rôle APPLICATION_MANAGER
     * Permet de gérer les administrateurs (forManageAdministrator)
     */
    public static final PrivilegeAssessorDomainForApplication APPLICATION_MANAGER_FOR_ADMIN = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .withIsApplicationManager(true)
                            .build())
            .build();
    
    /**
     * PrivilegeAssessorDomainForApplication avec le rôle DATA_READER
     * Autorisations extraction sur dataName (forDataRead)
     */
    public static final PrivilegeAssessorDomainForApplication DATA_READER;

    /**
     * PrivilegeAssessorDomainForApplication avec le rôle DATA_READER
     * Autorisations extraction sur dataName (forDataRead)
     */
    public static final PrivilegeAssessorDomainForApplication DATA_DELETE_WITH_REPOSITORY;
    public static final PrivilegeAssessorDomainForApplication DATA_DELETE_WITHOUT_REPOSITORY;

    /**
     * PrivilegeAssessorDomainForApplication avec le rôle APPLICATION_MANAGER
     * Permet de supprimer des autorisations (forDeleteAuthorization)
     */
    public static final PrivilegeAssessorDomainForApplication APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .withIsApplicationManager(true)
                            .build())
            .build();
    
    /**
     * PrivilegeAssessorDomainForApplication avec des droits de dépôt
     * Autorisations depot sur dataName (forDataDeposit)
     */
    public static final PrivilegeAssessorDomainForApplication DATA_DEPOSIT_WRITER;
    
    /**
     * PrivilegeAssessorDomainForApplication avec des droits de publication
     * Autorisations publication sur dataName (forDataPublish)
     */
    public static final PrivilegeAssessorDomainForApplication DATA_PUBLISH_WRITER;
    
    /**
     * PrivilegeAssessorDomainForApplication avec le rôle APPLICATION_MANAGER
     * Permet de mettre à jour l'application (forUpdateApplication)
     */
    public static final PrivilegeAssessorDomainForApplication APPLICATION_MANAGER_FOR_UPDATE = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .withIsApplicationManager(true)
                            .build())
            .build();
    
    /**
     * PrivilegeAssessorDomainForApplication avec le rôle USER_MANAGER
     * Permet de gérer les autorisations (forManageAuthorizations)
     */
    public static final PrivilegeAssessorDomainForApplication USER_MANAGER_FOR_MANAGE_AUTHORIZATIONS = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .withIsUserManager(true)
                            .build())
            .build();
    
    /**
     * PrivilegeAssessorDomainForApplication sans aucun droit
     * Utile pour tester les cas d'erreur
     */
    public static final PrivilegeAssessorDomainForApplication NO_RIGHTS = builder()
            .withAuthorizations(
                    AuthorizationsForApplicationUserFactory.builder()
                            .build())
            .build();
    
    // Initialisation des constantes nécessitant des mocks
    static {
        // Initialisation de DATA_READER
        AuthorizationParsed extractionAuth = Mockito.mock(AuthorizationParsed.class);
        when(extractionAuth.operationTypes()).thenReturn(Set.of(OperationType.extraction));
        Map<String, List<AuthorizationParsed>> extractionAuthorizations = Map.of(DEFAULT_DATA_NAME, List.of(extractionAuth));
        DATA_READER = builder()
                .withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withUserAuthorizations(extractionAuthorizations)
                                .build())
                .build();
        
        // Initialisation de DATA_DEPOSIT_WRITER
        AuthorizationParsed depotAuth = Mockito.mock(AuthorizationParsed.class);
        when(depotAuth.operationTypes()).thenReturn(Set.of(OperationType.depot, OperationType.publication,OperationType.extraction, OperationType.delete));
        Map<String, List<AuthorizationParsed>> depotAuthorizations = Map.of(DEFAULT_DATA_NAME, List.of(depotAuth));
        DATA_DEPOSIT_WRITER = builder()
                .withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withUserAuthorizations(depotAuthorizations)
                                .build())
                .build();

        // Initialisation de DATA_DELETE_WITH_REPOSITORY
        AuthorizationParsed deleteWithrepositoryAuth = Mockito.mock(AuthorizationParsed.class, "deleteWithrepositoryAuth" );
        when(deleteWithrepositoryAuth.operationTypes()).thenReturn(Set.of(OperationType.delete));
        Map<String, List<AuthorizationParsed>> deleteWithRepositoryAuthorizations = Map.of(DEFAULT_DATA_NAME, List.of(deleteWithrepositoryAuth));
        Application applicationForDeleteWithRepository = Mockito.mock(Application.class, "applicationForDeleteWithRepository");
        Submission submissionForDeleteWithRepository = Mockito.mock(Submission.class, "submissionForDeleteWithRepository");
        when(submissionForDeleteWithRepository.strategy()).thenReturn(SubmissionType.OA_VERSIONING);
        when(applicationForDeleteWithRepository.findSubmission(DEFAULT_DATA_NAME))
                .thenReturn(Optional.of(submissionForDeleteWithRepository));
        DATA_DELETE_WITH_REPOSITORY = builder()
                .withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withApplication(applicationForDeleteWithRepository)
                                .withUserAuthorizations(deleteWithRepositoryAuthorizations)
                                .build())
                .withApplication(applicationForDeleteWithRepository)
                .build();

        // Initialisation de DATA_DELETE_WITHOUT_REPOSITORY
        AuthorizationParsed deleteWithoutRepositoryAuth = Mockito.mock(AuthorizationParsed.class);
        when(deleteWithoutRepositoryAuth.operationTypes()).thenReturn(Set.of(OperationType.publication));
        Map<String, List<AuthorizationParsed>> deleteWithoutRepositoryAuthorizations = Map.of(DEFAULT_DATA_NAME, List.of(deleteWithoutRepositoryAuth));
        DATA_DELETE_WITHOUT_REPOSITORY = builder()
                .withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withUserAuthorizations(deleteWithoutRepositoryAuthorizations)
                                .build())
                .build();
        
        // Initialisation de DATA_PUBLISH_WRITER
        AuthorizationParsed publishAuth = Mockito.mock(AuthorizationParsed.class);
        when(publishAuth.operationTypes()).thenReturn(Set.of(OperationType.publication));
        Map<String, List<AuthorizationParsed>> publishAuthorizations = Map.of(DEFAULT_DATA_NAME, List.of(publishAuth));
        DATA_PUBLISH_WRITER = builder()
                .withAuthorizations(
                        AuthorizationsForApplicationUserFactory.builder()
                                .withUserAuthorizations(publishAuthorizations)
                                .build())
                .build();
    }

    public Application getApplication() {
        return application;
    }

    // Attributs d'instance pour le builder
    Application application = Mockito.mock(Application.class);
    GetGrantableResult grantable;
    private AuthorizationsForApplicationUser authorizations;
    private PrivilegeAssessorDomain domain;

    public PrivilegeAssessorDomainForApplicationFactory() {
    }

    public static PrivilegeAssessorDomainForApplicationFactory builder() {
        return new PrivilegeAssessorDomainForApplicationFactory();
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
}