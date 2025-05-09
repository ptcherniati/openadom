package fr.inra.oresing.rest.authentication.evaluator;

import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForApplication;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForSystem;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.domain.services.authorization.AuthorizationService;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests de l'évaluateur de permissions annotation @PreAuthorize")
public class ApplicationPermissionEvaluatorTest {

    @Mock
    private AuthorizationService authorizationService;

    private ApplicationPermissionEvaluator permissionEvaluator;

    @BeforeEach
    void setUp() {
        // Définir une fabrique de privilèges qui retournera le bon type de persona selon le contexte
        PrivilegeFactory privilegeFactory = new PrivilegeFactory();

        // Configurer le service d'autorisation pour utiliser cette fabrique
        lenient().when(authorizationService.getPrivilegeAssessorForSystem(any(PrivilegeSystemDomain.class)))
                .thenAnswer(invocation -> {
                    PrivilegeSystemDomain domain = invocation.getArgument(0);
                    return privilegeFactory.createSystemAssessor(domain);
                });

        lenient().when(authorizationService.getPrivilegeAssessorForApplication(any(PrivilegeApplicationDomain.class), anyString()))
                .thenAnswer(invocation -> {
                    PrivilegeApplicationDomain domain = invocation.getArgument(0);
                    String applicationName = invocation.getArgument(1);
                    return privilegeFactory.createApplicationAssessor(domain, applicationName);
                });

        permissionEvaluator = new ApplicationPermissionEvaluator(authorizationService);
    }

    // Classe interne pour gérer la création des assessors et personas
    private class PrivilegeFactory {
        // Cache des assessors pour éviter de recréer des objets
        private final Map<PrivilegeSystemDomain, PrivilegeAssessorDomainForSystem> systemAssessors = new HashMap<>();
        private final Map<String, Map<PrivilegeApplicationDomain, PrivilegeAssessorDomainForApplication>> applicationAssessors = new HashMap<>();

        // Cache des personas pour garantir la cohérence
        private final Map<String, SystemPersona> systemPersonas = new HashMap<>();
        private final Map<String, ApplicationPersona> applicationPersonas = new HashMap<>();

        public PrivilegeAssessorDomainForSystem createSystemAssessor(PrivilegeSystemDomain domain) {
            return systemAssessors.computeIfAbsent(domain, d -> {
                PrivilegeAssessorDomainForSystem assessor = mock(PrivilegeAssessorDomainForSystem.class);

                // Configurer l'assessor selon le domaine
                switch (d) {
                    case SYSTEM_USER_CONNECTED:
                        lenient().when(assessor.connectedUser()).thenReturn((ConnectedUser) getOrCreateSystemPersona("ConnectedUser"));
                        lenient().when(assessor.forAdministrationManagement()).thenReturn((OpenAdomAdmin) getOrCreateSystemPersona("OpenAdomAdmin"));
                        lenient().when(assessor.forCreateApplication()).thenReturn((ApplicationCreator) getOrCreateSystemPersona("ApplicationCreator"));
                        break;
                    case SYSTEM_ADMINISTRATION:
                        lenient().when(assessor.forAdministrationManagement()).thenReturn((OpenAdomAdmin) getOrCreateSystemPersona("OpenAdomAdmin"));
                        lenient().when(assessor.forCreateApplication()).thenReturn((ApplicationCreator) getOrCreateSystemPersona("ApplicationCreator"));
                        break;
                }

                return assessor;
            });
        }

        public PrivilegeAssessorDomainForApplication createApplicationAssessor(PrivilegeApplicationDomain domain, String applicationName) {
            return applicationAssessors
                    .computeIfAbsent(applicationName, name -> new HashMap<>())
                    .computeIfAbsent(domain, d -> {
                        PrivilegeAssessorDomainForApplication assessor = mock(PrivilegeAssessorDomainForApplication.class);

                        // Configurer l'assessor selon le domaine
                        switch (d) {
                            case APPLICATION_MANAGER:
                                lenient().when(assessor.forUpdateApplication())
                                        .thenReturn((ApplicationManager) getOrCreateApplicationPersona("ApplicationManager"));
                                lenient().when(assessor.forManageAdministrator())
                                        .thenReturn((ApplicationAdminUser) getOrCreateApplicationPersona("ApplicationAdminUser"));
                                break;
                            case DATA_MANAGEMENT:
                                lenient().when(assessor.forManageAuthorizations())
                                        .thenReturn((ApplicationManager) getOrCreateApplicationPersona("ApplicationAdminUser"));
                                lenient().when(assessor.forDeleteAuthorization())
                                        .thenReturn((ApplicationAdminUser) getOrCreateApplicationPersona("ApplicationAdminUser"));
                                break;
                            case DATA_READ:
                                lenient().when(assessor.forDataRead(anyString()))
                                        .thenReturn((ApplicationDataReaderUser) getOrCreateApplicationPersona("ApplicationDataReader"));
                                lenient().when(assessor.forDataDelete(anyString()))
                                        .thenReturn((ApplicationDataDelete) getOrCreateApplicationPersona("ApplicationDeleteUser"));
                                lenient().when(assessor.forDataWrite(anyString(), eq(false)))
                                        .thenReturn((ApplicationDataWriter) getOrCreateApplicationPersona("ApplicationDepositWriter"));
                                lenient().when(assessor.forDataWrite(anyString(), eq(true)))
                                        .thenReturn((ApplicationDataWriter) getOrCreateApplicationPersona("ApplicationPublishWriter"));
                                break;
                            case DATA_WRITE:
                                lenient().when(assessor.forDataWrite(anyString(), eq(false)))
                                        .thenReturn((ApplicationDataWriter) getOrCreateApplicationPersona("ApplicationDepositWriter"));
                                lenient().when(assessor.forDataWrite(anyString(), eq(true)))
                                        .thenReturn((ApplicationDataWriter) getOrCreateApplicationPersona("ApplicationPublishWriter"));
                                break;
                        }

                        return assessor;
                    });
        }

        private SystemPersona getOrCreateSystemPersona(String type) {
            return systemPersonas.computeIfAbsent(type, t -> {
                switch (t) {
                    case "ConnectedUser":
                        return mock(ConnectedUser.class);
                    case "OpenAdomAdmin":
                        return mock(OpenAdomAdmin.class);
                    case "ApplicationCreator":
                        return mock(ApplicationCreatorUser.class);
                    default:
                        return mock(SystemPersona.class);
                }
            });
        }

        private ApplicationPersona getOrCreateApplicationPersona(String type) {
            return applicationPersonas.computeIfAbsent(type, t -> {
                switch (t) {
                    case "ApplicationManager":
                        return mock(ApplicationManagerUser.class);
                    case "ApplicationAdminUser":
                        return mock(ApplicationAdminUser.class);
                    case "ApplicationDataReader":
                        return mock(ApplicationDataReaderUser.class);
                    case "ApplicationDepositWriter":
                        return mock(ApplicationDepositWriterUser.class);
                    case "ApplicationPublishWriter":
                        return mock(ApplicationPublishWriterUser.class);
                    case "ApplicationDeleteUser":
                        return mock(ApplicationDeleteUser.class);
                    default:
                        return mock(ApplicationPersona.class);
                }
            });
        }
    }


    /**
     * Configuration d'un cas de test pour l'évaluateur de permissions
     */
    record UserTest(
            String targetDomain,
            String permission,
            String description,
            List<OreSiAuthenticationToken> authorizedTokens,
            List<OreSiAuthenticationToken> unauthorizedTokens
    ) {
    }

    /**
     * Crée un token d'authentification pour les tests
     */
    private OreSiAuthenticationToken createToken(UUID userId, String role) {
        OreSiUserRequestClient userRequestClient = new OreSiUserRequestClient(userId, new OreSiUserRole());
        return new OreSiAuthenticationToken(userRequestClient, role, Collections.emptyList());
    }

    /**
     * Liste des cas de test
     */
    private List<UserTest> getPermissionTestCases() {
        OreSiAuthenticationToken openAdomAdmin = createToken(UUID.randomUUID(), "openAdomAdmin");
        OreSiAuthenticationToken applicationCreatorUser = createToken(UUID.randomUUID(), "applicationCreatorUser");
        OreSiAuthenticationToken connectedUser = createToken(UUID.randomUUID(), "connectedUser");
        OreSiAuthenticationToken applicationDataReaderUser = createToken(UUID.randomUUID(), "applicationDataReaderUser");
        OreSiAuthenticationToken applicationAdminUser = createToken(UUID.randomUUID(), "applicationAdminUser");
        OreSiAuthenticationToken applicationDeleteUser = createToken(UUID.randomUUID(), "applicationDeleteUser");
        OreSiAuthenticationToken applicationManagerUser = createToken(UUID.randomUUID(), "applicationManagerUser");
        OreSiAuthenticationToken applicationDepositWriterUser = createToken(UUID.randomUUID(), "applicationDepositWriterUser");
        OreSiAuthenticationToken applicationPublishWriterUser = createToken(UUID.randomUUID(), "applicationPublishWriterUser");
        OreSiAuthenticationToken applicationToken = createToken(UUID.randomUUID(), "nobody");
        applicationToken.setApplicationName("testApplication");
        applicationToken.setDataName("testData");

        return List.of(
                // Tests pour le domaine SYSTEM
                new UserTest(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_USER,
                        "Utilisateur système connecté",
                        List.of(connectedUser),
                        List.of()
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_OPENADOM_ADMIN,
                        "Administrateur OpenADOM",
                        List.of(openAdomAdmin),
                        List.of()
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_APPLICATION_CREATOR,
                        "Création d'application",
                        List.of(openAdomAdmin,applicationCreatorUser),
                        List.of()
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_MANAGE_ROLE_FOR_UPDATE,
                        "Gestion des rôles système - mise à jour",
                        List.of(openAdomAdmin),
                        List.of()
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_MANAGE_ROLE_FOR_DELETE,
                        "Gestion des rôles système - suppression",
                        List.of(openAdomAdmin),
                        List.of()
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_USER_READER,
                        "Lecteur d'utilisateurs système",
                        List.of(openAdomAdmin),
                        List.of()
                ),

                // Tests pour le domaine APPLICATION
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_APPLICATION_MODIFY,
                        "Modification d'application",
                        List.of(applicationToken),
                        List.of(applicationAdminUser,applicationManagerUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_ROLE_MANAGEMENT_FOR_DELETE,
                        "Gestion des rôles - suppression",
                        List.of(applicationToken),
                        List.of(applicationAdminUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE,
                        "Gestion des rôles - mise à jour",
                        List.of(applicationToken),
                        List.of(applicationAdminUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ,
                        "Gestion des autorisations - lecture",
                        List.of(applicationToken),
                        List.of(applicationAdminUser,applicationManagerUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE,
                        "Gestion des autorisations - suppression",
                        List.of(applicationToken),
                        List.of(applicationAdminUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE,
                        "Gestion des autorisations - mise à jour",
                        List.of(applicationToken),
                        List.of(applicationAdminUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD,
                        "Gestion des autorisations - ajout",
                        List.of(applicationToken),
                        List.of(applicationAdminUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_DATA_READ,
                        "Lecture de données",
                        List.of(applicationToken),
                        List.of(applicationAdminUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_DATA_WRITE,
                        "Écriture de données",
                        List.of(applicationToken),
                        List.of(applicationDataReaderUser,applicationDeleteUser,applicationPublishWriterUser,
                                applicationDepositWriterUser,applicationCreatorUser,applicationManagerUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_WRITE_FILE,
                        "Écriture de fichier",
                        List.of(applicationToken),
                        List.of(applicationDeleteUser,applicationPublishWriterUser,
                                applicationCreatorUser,applicationManagerUser) // Token sans nom d'application
                ),
                new UserTest(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_DELETE_FILE,
                        "Suppression de fichier",
                        List.of(applicationToken),
                        List.of(applicationDeleteUser,applicationPublishWriterUser,
                                applicationCreatorUser,applicationManagerUser) // Token sans nom d'application
                )
        );
    }

    /**
     * Test pour un cas autorisé
     */
    private void testAuthorizedPermission(UserTest testCase, OreSiAuthenticationToken token) {
        // Exécution
        boolean result = permissionEvaluator.hasPermission(token, testCase.targetDomain, testCase.permission);

        // Vérification
        assertEquals(true, result,
                "La permission " + testCase.permission + " devrait être accordée pour le domaine " + testCase.targetDomain);

        // Vérifier que la persona a été assignée au token
        if (testCase.targetDomain.equals(ApplicationPermissionEvaluator.SYSTEM)) {
            assertNotNull(token.getSystemPersona());
        } else if (testCase.targetDomain.equals(ApplicationPermissionEvaluator.APPLICATION)
                && token.getApplicationName() != null) {
            assertNotNull(token.getApplicationPersona());
        }
    }

    /**
     * Test pour un cas non autorisé
     */
    private void testUnauthorizedPermission(UserTest testCase, OreSiAuthenticationToken token) {
        // Exécution
        boolean result = permissionEvaluator.hasPermission(token, testCase.targetDomain, testCase.permission);

        // Vérification
        assertEquals(false, result,
                "La permission " + testCase.permission + " devrait être refusée pour le domaine " + testCase.targetDomain);
    }

    /**
     * Factory pour générer les tests par target, permission et token
     */
    @TestFactory
    @DisplayName("Tests des combinaisons de domaines et permissions")
    Stream<DynamicNode> permissionTests() {
        return getPermissionTestCases().stream()
                .map(testCase -> dynamicContainer(
                        testCase.targetDomain + " - " + testCase.permission + " - " + testCase.description,
                        Stream.of(
                                dynamicContainer(
                                        "utilisateurs autorisés",
                                        testCase.authorizedTokens.stream()
                                                .map(token -> dynamicTest(
                                                       "%s (%s)".formatted(token.getCredentials(), testCase.description),
                                                        () -> testAuthorizedPermission(testCase, token)
                                                ))
                                ),
                                dynamicContainer(
                                        "utilisateurs non autorisés",
                                        testCase.unauthorizedTokens.stream()
                                                .map(token -> dynamicTest(
                                                        "%s (%s)".formatted(token.getCredentials(), testCase.description),
                                                        () -> testUnauthorizedPermission(testCase, token)
                                                ))
                                )
                        )
                ));
    }

    /**
     * Autre organisation: tests par target
     */
    @TestFactory
    @DisplayName("Tests organisés par domaine cible")
    Stream<DynamicNode> targetDomainTests() {
        Map<String, List<UserTest>> testsByTarget = getPermissionTestCases().stream()
                .collect(java.util.stream.Collectors.groupingBy(UserTest::targetDomain));

        return testsByTarget.entrySet().stream()
                .map(entry -> dynamicContainer(
                        "Domaine: " + entry.getKey(),
                        entry.getValue().stream()
                                .map(testCase -> dynamicContainer(
                                        testCase.permission + " - " + testCase.description,
                                        Stream.of(
                                                dynamicContainer(
                                                        "utilisateurs autorisés",
                                                        testCase.authorizedTokens.stream()
                                                                .map(token -> dynamicTest(
                                                                        "%s (%s)".formatted(token.getCredentials(), testCase.description),
                                                                        () -> testAuthorizedPermission(testCase, token)
                                                                ))
                                                ),
                                                dynamicContainer(
                                                        "utilisateurs non autorisés",
                                                        testCase.unauthorizedTokens.stream()
                                                                .map(token -> dynamicTest(
                                                                        "Token ID: " + (token.getPrincipal() instanceof OreSiUserRequestClient ?
                                                                                ((OreSiUserRequestClient) token.getPrincipal()).id() : "inconnu"),
                                                                        () -> testUnauthorizedPermission(testCase, token)
                                                                ))
                                                )
                                        )
                                ))
                ));
    }

    /**
     * Teste le fonctionnement avec un token invalide
     */
    @TestFactory
    @DisplayName("Tests avec authentification invalide")
    Stream<DynamicTest> invalidAuthenticationTests() {
        BiFunction<Authentication, String, DynamicTest> createTest = (authentication, description) ->
                dynamicTest(description, () -> {
                    boolean result = permissionEvaluator.hasPermission(authentication, ApplicationPermissionEvaluator.SYSTEM, ApplicationPermissionEvaluator.SYSTEM_USER);
                    assertEquals(false, result, "La permission devrait être refusée pour une authentification invalide");
                });

        return Stream.of(
                createTest.apply(null, "Token null"),
                createTest.apply(mock(Authentication.class), "Token non OreSiAuthenticationToken")
        );
    }
}