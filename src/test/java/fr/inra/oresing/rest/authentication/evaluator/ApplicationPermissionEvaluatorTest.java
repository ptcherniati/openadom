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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        // Au lieu de créer des mocks des persona, nous allons modifier notre approche

        // Créer des mocks des domains avec RETURNS_SELF pour les méthodes à chaîner
        PrivilegeAssessorDomainForSystem systemUserConnected = mock(PrivilegeAssessorDomainForSystem.class);
        PrivilegeAssessorDomainForSystem systemAdministration = mock(PrivilegeAssessorDomainForSystem.class);
        PrivilegeAssessorDomainForApplication applicationManager = mock(PrivilegeAssessorDomainForApplication.class);
        PrivilegeAssessorDomainForApplication dataManagement = mock(PrivilegeAssessorDomainForApplication.class);
        PrivilegeAssessorDomainForApplication dataRead = mock(PrivilegeAssessorDomainForApplication.class);
        PrivilegeAssessorDomainForApplication dataWrite = mock(PrivilegeAssessorDomainForApplication.class);
        PrivilegeAssessorDomainForApplication dataAccess = mock(PrivilegeAssessorDomainForApplication.class);

        ConnectedUser connectedUserPersonna = mock(ConnectedUser.class);
        OpenAdomAdmin openAdomAdminPersona = mock(OpenAdomAdmin.class);
        ApplicationCreatorUser applicationCreatorPersona = mock(ApplicationCreatorUser.class);

        ApplicationManagerUser applicationManagerPersona = mock(ApplicationManagerUser.class);
        ApplicationAdminUser applicationAdminUserPersona = mock(ApplicationAdminUser.class);
        ApplicationDataReader applicationDataReadPersona = mock(ApplicationDataReader.class);
        ApplicationDepositWriterUser applicationDataWriteOnDepositPersona = mock(ApplicationDepositWriterUser.class);
        ApplicationPublishWriterUser applicationDataWriteOnPublishPersona = mock(ApplicationPublishWriterUser.class);
        ApplicationDeleteUser applicationDataDeletePersona = mock(ApplicationDeleteUser.class);
        // Configurer tous les assessors pour qu'ils retournent le même objet persona
        lenient().when(systemUserConnected.connectedUser()).thenReturn(connectedUserPersonna);
        lenient().when(systemUserConnected.forAdministrationManagement()).thenReturn(openAdomAdminPersona);
        lenient().when(systemUserConnected.forCreateApplication()).thenReturn(applicationCreatorPersona);

        lenient().when(systemAdministration.forAdministrationManagement()).thenReturn(openAdomAdminPersona);
        lenient().when(systemAdministration.forCreateApplication()).thenReturn(applicationCreatorPersona);

        lenient().when(applicationManager.forUpdateApplication()).thenReturn(applicationManagerPersona);
        lenient().when(applicationManager.forManageAdministrator()).thenReturn(applicationAdminUserPersona);

        lenient().when(dataManagement.forManageAuthorizations()).thenReturn(applicationManagerPersona);
        lenient().when(dataManagement.forDeleteAuthorization()).thenReturn(applicationAdminUserPersona);

        lenient().when(dataRead.forDataRead(anyString())).thenReturn(applicationDataReadPersona);
        lenient().when(dataRead.forDataDelete(anyString())).thenReturn(applicationDataDeletePersona);
        lenient().when(dataRead.forDataWrite(anyString(), anyBoolean())).thenReturn(applicationDataWriteOnDepositPersona);

        lenient().when(dataWrite.forDataWrite(anyString(), anyBoolean())).thenReturn(applicationDataWriteOnDepositPersona);

        // Configuration du service d'autorisation
        lenient().when(authorizationService.getPrivilegeAssessorForSystem(any(PrivilegeSystemDomain.class)))
                .thenAnswer(invocation -> {
                    PrivilegeSystemDomain domain = invocation.getArgument(0);
                    if (domain == PrivilegeSystemDomain.SYSTEM_USER_CONNECTED) {
                        return systemUserConnected;
                    } else if (domain == PrivilegeSystemDomain.SYSTEM_ADMINISTRATION) {
                        return systemAdministration;
                    }
                    return mock(PrivilegeAssessorDomainForSystem.class);
                });

        lenient().when(authorizationService.getPrivilegeAssessorForApplication(any(PrivilegeApplicationDomain.class), anyString()))
                .thenAnswer(invocation -> {
                    PrivilegeApplicationDomain domain = invocation.getArgument(0);
                    if (domain == PrivilegeApplicationDomain.APPLICATION_MANAGER) {
                        return applicationManager;
                    } else if (domain == PrivilegeApplicationDomain.DATA_MANAGEMENT) {
                        return dataManagement;
                    } else if (domain == PrivilegeApplicationDomain.DATA_READ) {
                        return dataRead;
                    } else if (domain == PrivilegeApplicationDomain.DATA_WRITE) {
                        return dataWrite;
                    } else if (domain == PrivilegeApplicationDomain.DATA_ACCESS) {
                        return dataAccess;
                    }
                    return mock(PrivilegeAssessorDomainForApplication.class);
                });

        permissionEvaluator = new ApplicationPermissionEvaluator(authorizationService);
    }


    /**
     * Configuration d'un cas de test pour l'évaluateur de permissions
     */
    record PermissionTestCase(
            String targetDomain,
            String permission,
            String description,
            Class<? extends SystemPersona> systemPersonaClass,
            Class<? extends ApplicationPersona> applicationPersonaClass,
            List<OreSiAuthenticationToken> authorizedTokens,
            List<OreSiAuthenticationToken> unauthorizedTokens
    ) {
    }

    /**
     * Crée un token d'authentification pour les tests
     */
    private OreSiAuthenticationToken createToken(UUID userId) {
        OreSiUserRequestClient userRequestClient = new OreSiUserRequestClient(userId, new OreSiUserRole());
        return new OreSiAuthenticationToken(userRequestClient, "credentials", Collections.emptyList());
    }

    /**
     * Liste des cas de test
     */
    private List<PermissionTestCase> getPermissionTestCases() {
        OreSiAuthenticationToken systemToken = createToken(UUID.randomUUID());
        OreSiAuthenticationToken applicationToken = createToken(UUID.randomUUID());
        applicationToken.setApplicationName("testApplication");
        applicationToken.setDataName("testData");

        return List.of(
                // Tests pour le domaine SYSTEM
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_USER,
                        "Utilisateur système connecté",
                        null, // Pas besoin de définir le type de persona ici
                        null,
                        List.of(systemToken),
                        List.of()
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_OPENADOM_ADMIN,
                        "Administrateur OpenADOM",
                        null,
                        null,
                        List.of(systemToken),
                        List.of()
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_APPLICATION_CREATE,
                        "Création d'application",
                        null,
                        null,
                        List.of(systemToken),
                        List.of()
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_APPLICATION_CREATOR,
                        "Créateur d'application",
                        null,
                        null,
                        List.of(systemToken),
                        List.of()
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_MANAGE_ROLE_FOR_UPDATE,
                        "Gestion des rôles système - mise à jour",
                        null,
                        null,
                        List.of(systemToken),
                        List.of()
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_MANAGE_ROLE_FOR_DELETE,
                        "Gestion des rôles système - suppression",
                        null,
                        null,
                        List.of(systemToken),
                        List.of()
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.SYSTEM,
                        ApplicationPermissionEvaluator.SYSTEM_USER_READER,
                        "Lecteur d'utilisateurs système",
                        null,
                        null,
                        List.of(systemToken),
                        List.of()
                ),

                // Tests pour le domaine APPLICATION
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_APPLICATION_MODIFY,
                        "Modification d'application",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_ROLE_MANAGEMENT_FOR_DELETE,
                        "Gestion des rôles - suppression",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_ROLE_MANAGEMENT_FOR_UPDATE,
                        "Gestion des rôles - mise à jour",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_READ,
                        "Gestion des autorisations - lecture",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_DELETE,
                        "Gestion des autorisations - suppression",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_UPDATE,
                        "Gestion des autorisations - mise à jour",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD,
                        "Gestion des autorisations - ajout",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_DATA_READ,
                        "Lecture de données",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_DATA_WRITE,
                        "Écriture de données",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_WRITE_FILE,
                        "Écriture de fichier",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                ),
                new PermissionTestCase(
                        ApplicationPermissionEvaluator.APPLICATION,
                        ApplicationPermissionEvaluator.APPLICATION_DELETE_FILE,
                        "Suppression de fichier",
                        null,
                        null,
                        List.of(applicationToken),
                        List.of(systemToken) // Token sans nom d'application
                )
        );
    }

    /**
     * Test pour un cas autorisé
     */
    private void testAuthorizedPermission(PermissionTestCase testCase, OreSiAuthenticationToken token) {
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
    private void testUnauthorizedPermission(PermissionTestCase testCase, OreSiAuthenticationToken token) {
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
                                                        "Token ID: " + ((OreSiUserRequestClient) token.getPrincipal()).id(),
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
                ));
    }

    /**
     * Autre organisation: tests par target
     */
    @TestFactory
    @DisplayName("Tests organisés par domaine cible")
    Stream<DynamicNode> targetDomainTests() {
        Map<String, List<PermissionTestCase>> testsByTarget = getPermissionTestCases().stream()
                .collect(java.util.stream.Collectors.groupingBy(PermissionTestCase::targetDomain));

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
                                                                        "Token ID: " + ((OreSiUserRequestClient) token.getPrincipal()).id(),
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