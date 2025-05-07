package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.*;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForApplicationFactory.*;
import static fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForSystemFactory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@DisplayName("Tests organisés des méthodes de PrivilegeAssessorDomainForApplication")
public class PrivilegeAccessorDomainTest {
    sealed interface MethodeInfo<P extends PrivilegeAssessorDomain> permits MethodeApplicationInfo, MethodeSystemInfo {
        String name();

        String description();

        Function<P, Object> methodCall();

        Class<?> returnType();

        Class<? extends OreSiTechnicalException> exceptionType();

        List<P> authorizedAssessors();

        List<P> unauthorizedAssessors();
    }

    /**
     * Configuration d'une méthode d'accès à tester pour les droits application.
     */
    record MethodeApplicationInfo<PrivilegeAssessorDomainForApplication>(
            String name,
            String description,
            Function<PrivilegeAssessorDomainForApplication, Object> methodCall,
            Class<?> returnType,
            Class<? extends OreSiTechnicalException> exceptionType,
            List<PrivilegeAssessorDomainForApplication> authorizedAssessors,
            List<PrivilegeAssessorDomainForApplication> unauthorizedAssessors
    ) implements MethodeInfo {
    }

    /**
     * Configuration d'une méthode d'accès à tester pour les droits system
     */
    record MethodeSystemInfo<PrivilegeAssessorDomainForSystem>(
            String name,
            String description,
            Function<PrivilegeAssessorDomainForSystem, Object> methodCall,
            Class<?> returnType,
            Class<? extends OreSiTechnicalException> exceptionType,
            List<PrivilegeAssessorDomainForSystem> authorizedAssessors,
            List<PrivilegeAssessorDomainForSystem> unauthorizedAssessors
    ) implements MethodeInfo {
    }

    /**
     * Définition des méthodes à tester avec leurs assessors autorisés et non autorisés
     */
    private <MethodeInfo> List getMethodsApplicationToTest() {
        return List.of(
                // Test pour forDataRead - lecture des données
                new MethodeSystemInfo(
                        "forCreateApplication",
                        "Création d'une application",
                        assessor -> ((PrivilegeAssessorDomainForSystem) assessor).forCreateApplication(),
                        ApplicationCreator.class,
                        NotApplicationCreatorRightsException.class,
                        List.of(
                                APPLICATION_CREATOR
                        ),
                        List.of(
                                OPENADOM_ADMIN,
                                NO_SYSTEM_RIGHTS
                        )
                ),
                new MethodeSystemInfo(
                        "forAdministrationManagement",
                        "Administration des utilisateurs system",
                        assessor -> ((PrivilegeAssessorDomainForSystem) assessor).forAdministrationManagement(),
                        OpenAdomAdmin.class,
                        NotOpenAdomAdminException.class,
                        List.of(
                                OPENADOM_ADMIN
                        ),
                        List.of(APPLICATION_CREATOR,
                                NO_SYSTEM_RIGHTS
                        )
                ),
                new MethodeSystemInfo(
                        "connectedUserconnectedUser",
                        "Utilisateur cherchant à se connecter ayant les bons indentifiants",
                        assessor -> ((PrivilegeAssessorDomainForSystem) assessor).connectedUser(),
                        ConnectedUser.class,
                        null,
                        List.of(
                                APPLICATION_CREATOR,
                                NO_SYSTEM_RIGHTS,
                                OPENADOM_ADMIN
                        ),
                        List.of(
                        )
                ),
                // Test pour forDataRead - lecture des données
                new MethodeApplicationInfo(
                        "forDataRead",
                        "Lecture des données",
                        assessor -> ((PrivilegeAssessorDomainForApplication) assessor).forDataRead(DEFAULT_DATA_NAME),
                        ApplicationDataReader.class,
                        NotApplicationDataReaderException.class,
                        List.of(
                                USER_MANAGER_FOR_ADD_AUTHORIZATION,
                                USER_MANAGER,
                                APPLICATION_MANAGER,
                                APPLICATION_MANAGER_FOR_ADMIN,
                                DATA_READER,
                                DATA_DELETE_WITH_REPOSITORY,
                                DATA_DELETE_WITHOUT_REPOSITORY,
                                APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION,
                                DATA_DEPOSIT_WRITER,
                                DATA_PUBLISH_WRITER,
                                APPLICATION_MANAGER_FOR_UPDATE
                        ),
                        List.of(
                                NO_RIGHTS
                        )
                ),

                // Test pour forDataDeposit - dépôt de données
                new MethodeApplicationInfo(
                        "forDataDeposit",
                        "Dépôt de données",
                        assessor -> ((PrivilegeAssessorDomainForApplication) assessor).forDataWrite(DEFAULT_DATA_NAME, true),
                        ApplicationDataWriter.class,
                        NotApplicationDataWriterException.class,
                        List.of(
                                APPLICATION_MANAGER,
                                APPLICATION_MANAGER_FOR_ADMIN,
                                APPLICATION_MANAGER_FOR_UPDATE,
                                APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION,
                                USER_MANAGER,
                                USER_MANAGER_FOR_ADD_AUTHORIZATION,
                                DATA_DEPOSIT_WRITER,
                                DATA_PUBLISH_WRITER,
                                DATA_DELETE_WITHOUT_REPOSITORY
                        ),
                        List.of(
                                DATA_DELETE_WITH_REPOSITORY,
                                NO_RIGHTS,
                                DATA_READER
                        )
                ),

                // Test pour forApplicationManager - gestion d'application
                new MethodeApplicationInfo(
                        "forUpdateApplication",
                        "Gestion de la mise ) jour l'application",
                        assessor -> ((PrivilegeAssessorDomainForApplication) assessor).forUpdateApplication(),
                        ApplicationManager.class,
                        NotApplicationManagerRightsException.class,
                        List.of(
                                APPLICATION_MANAGER,
                                APPLICATION_MANAGER_FOR_ADMIN,
                                APPLICATION_MANAGER_FOR_UPDATE,
                                APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION,
                                USER_MANAGER_FOR_ADD_AUTHORIZATION
                        ),
                        List.of(
                                USER_MANAGER,
                                DATA_DELETE_WITHOUT_REPOSITORY,
                                DATA_DEPOSIT_WRITER,
                                DATA_PUBLISH_WRITER,
                                NO_RIGHTS,
                                DATA_READER
                        )
                ),

                // Test pour forApplicationManager - gestion d'application
                new MethodeApplicationInfo(
                        "forApplicationAuthorizationManager",
                        "Gestion des authorizations de l'application",
                        assessor -> ((PrivilegeAssessorDomainForApplication) assessor).forManageAuthorizations(),
                        ApplicationManager.class,
                        NotApplicationUserManagerRightsException.class,
                        List.of(
                                USER_MANAGER_FOR_ADD_AUTHORIZATION,
                                APPLICATION_MANAGER,
                                USER_MANAGER,
                                APPLICATION_MANAGER_FOR_ADMIN,
                                APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION,
                                APPLICATION_MANAGER_FOR_UPDATE
                        ),
                        List.of(
                                DATA_DELETE_WITHOUT_REPOSITORY,
                                DATA_DEPOSIT_WRITER,
                                DATA_PUBLISH_WRITER,
                                NO_RIGHTS,
                                DATA_READER
                        )
                ),

                // Test pour forDataDelete - suppression de données
                new MethodeApplicationInfo(
                        "forDataDelete",
                        "Suppression de données",
                        assessor -> ((PrivilegeAssessorDomainForApplication) assessor).forDataDelete(DEFAULT_DATA_NAME),
                        ApplicationDataDelete.class,
                        NotApplicationCanDeleteRightsException.class,

                        List.of(
                                USER_MANAGER_FOR_ADD_AUTHORIZATION,
                                USER_MANAGER,
                                APPLICATION_MANAGER,
                                APPLICATION_MANAGER_FOR_ADMIN,
                                DATA_DELETE_WITHOUT_REPOSITORY,
                                APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION,
                                DATA_DEPOSIT_WRITER,
                                DATA_PUBLISH_WRITER,
                                APPLICATION_MANAGER_FOR_UPDATE,
                                DATA_DELETE_WITHOUT_REPOSITORY,
                                DATA_DELETE_WITH_REPOSITORY
                        ),
                        List.of(
                                NO_RIGHTS,
                                DATA_READER
                        )
                ),

                // Test pour forDataDelete - suppression de données
                new MethodeApplicationInfo(
                        "forManageAdministrator",
                        "Gestion des administrateurs de l'application",
                        assessor -> ((PrivilegeAssessorDomainForApplication) assessor).forManageAdministrator(),
                        ApplicationAdminUser.class,
                        NotApplicationManagerRightsException.class,

                        List.of(
                                USER_MANAGER_FOR_ADD_AUTHORIZATION,
                                APPLICATION_MANAGER,
                                APPLICATION_MANAGER_FOR_ADMIN,
                                APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION,
                                APPLICATION_MANAGER_FOR_UPDATE
                        ),
                        List.of(
                                NO_RIGHTS,
                                DATA_DELETE_WITHOUT_REPOSITORY,
                                DATA_DELETE_WITH_REPOSITORY,
                                DATA_READER,
                                DATA_DEPOSIT_WRITER,
                                DATA_PUBLISH_WRITER,
                                DATA_DELETE_WITHOUT_REPOSITORY,
                                USER_MANAGER
                        )
                )
        );
    }

    /**
     * Obtient un nom lisible pour un assessor
     */
    private <P extends PrivilegeAssessorDomain> String getReadableAssessorName(P assessor) {
        if (assessor == APPLICATION_MANAGER) {
            return "APPLICATION_MANAGER";
        } else if (assessor == USER_MANAGER) {
            return "USER_MANAGER";
        } else if (assessor == DATA_READER) {
            return "DATA_READER";
        } else if (assessor == DATA_DEPOSIT_WRITER) {
            return "DATA_DEPOSIT_WRITER";
        } else if (assessor == DATA_PUBLISH_WRITER) {
            return "DATA_PUBLISH_WRITER";
        } else if (assessor == DATA_DELETE_WITH_REPOSITORY) {
            return "DATA_DELETE_WITH_REPOSITORY";
        } else if (assessor == DATA_DELETE_WITHOUT_REPOSITORY) {
            return "DATA_DELETE_WITHOUT_REPOSITORY";
        } else if (assessor == NO_RIGHTS) {
            return "NO_RIGHTS";
        } else if (assessor == APPLICATION_MANAGER_FOR_UPDATE) {
            return "APPLICATION_MANAGER_FOR_UPDATE";
        } else if (assessor == USER_MANAGER_FOR_ADD_AUTHORIZATION) {
            return "USER_MANAGER_FOR_ADD_AUTHORIZATION";
        } else if (assessor == APPLICATION_MANAGER_FOR_ADMIN) {
            return "APPLICATION_MANAGER_FOR_ADMIN";
        } else if (assessor == APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION) {
            return "APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION";
        } else if (assessor == USER_MANAGER_FOR_MANAGE_AUTHORIZATIONS) {
            return "USER_MANAGER_FOR_MANAGE_AUTHORIZATIONS";
        } else if (assessor == OPENADOM_ADMIN) {
            return "OPENADOM_ADMIN";
        } else if (assessor == APPLICATION_CREATOR) {
            return "APPLICATION_CREATOR";
        }
        return "UNKNOWN_ASSESSOR";
    }

    /**
     * Génère des tests pour toutes les méthodes et leurs assessors
     */
    @TestFactory
    @DisplayName("Tests des méthodes avec différents rôles")
    <P extends PrivilegeAssessorDomain, M extends MethodeInfo> Stream<DynamicNode> methodApplicationTests() {
        return getMethodsApplicationToTest().stream()
                .map(method -> dynamicContainer(
                        ((MethodeInfo) method).name() + " - " + ((MethodeInfo) method).description(),
                        (Iterable<? extends DynamicNode>) Stream.of(
                                // Container pour les assessors autorisés
                                dynamicContainer(
                                        "utilisateurs avec droits",
                                        ((MethodeInfo) method).authorizedAssessors().stream()
                                                .map(assessor -> dynamicTest(
                                                        getReadableAssessorName((P) assessor),
                                                        () -> testAuthorizedAccess((M) method, (P) assessor)
                                                ))
                                ),
                                // Container pour les assessors non autorisés
                                dynamicContainer(
                                        "utilisateurs sans droit",
                                        ((M) method).unauthorizedAssessors().stream()
                                                .map(assessor -> dynamicTest(
                                                        getReadableAssessorName((P) assessor),
                                                        () -> testUnauthorizedAccess((M) method, (P) assessor)
                                                ))
                                )
                        )
                                .collect(Collectors.toSet())
                ));
    }

    /**
     * Test pour un utilisateur autorisé à exécuter la méthode
     */
    private <M extends MethodeInfo, P extends PrivilegeAssessorDomain> void testAuthorizedAccess(M method, P assessor) {
        // Exécute la méthode et vérifie qu'elle ne lance pas d'exception
        Object result = assertDoesNotThrow(
                () -> method.methodCall().apply(assessor),
                "L'assessor " + getReadableAssessorName(assessor) +
                        " devrait pouvoir appeler " + method.name() + " sans exception"
        );

        // Vérifie que le résultat est du type attendu
        assertNotNull(result, "Le résultat ne devrait pas être null");
        assertTrue(
                method.returnType().isInstance(result),
                "Le résultat devrait être une instance de " + method.returnType().getSimpleName() +
                        " mais était " + result.getClass().getSimpleName()
        );
    }

    /**
     * Test pour un utilisateur non autorisé à exécuter la méthode
     */
    private <M extends MethodeInfo, P extends PrivilegeAssessorDomain> void testUnauthorizedAccess(M method, P assessor) {
        // Vérifie que l'exception attendue est lancée
        Throwable exception = assertThrows(
                method.exceptionType(),
                () -> method.methodCall().apply(assessor),
                "L'assessor " + getReadableAssessorName(assessor) +
                        " devrait déclencher une exception " + method.exceptionType().getSimpleName() +
                        " en appelant " + method.name()
        );

        // Vérifications supplémentaires sur l'exception si nécessaire
        assertNotNull(exception, "L'exception ne devrait pas être null");
    }

    /**
     * Organisation alternative : tests par assessor
     */
    @TestFactory
    @DisplayName("Tests d'accès par type d'utilisateur")
    <M extends MethodeInfo, P extends PrivilegeAssessorDomain> Stream<DynamicNode> assessorBasedTests() {
        // Liste de tous les assessors à tester
        List<PrivilegeAssessorDomainForApplication> allAssessors = new ArrayList<>();
        allAssessors.add(APPLICATION_MANAGER);
        allAssessors.add(APPLICATION_MANAGER_FOR_ADMIN);
        allAssessors.add(APPLICATION_MANAGER_FOR_UPDATE);
        allAssessors.add(APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION);
        allAssessors.add(USER_MANAGER);
        allAssessors.add(USER_MANAGER_FOR_ADD_AUTHORIZATION);
        allAssessors.add(DATA_READER);
        allAssessors.add(DATA_DEPOSIT_WRITER);
        allAssessors.add(DATA_PUBLISH_WRITER);
        allAssessors.add(DATA_DELETE_WITH_REPOSITORY);
        allAssessors.add(DATA_DELETE_WITHOUT_REPOSITORY);
        allAssessors.add(NO_RIGHTS);

        return allAssessors.stream()
                .map(assessor -> {
                    // Collecte les méthodes autorisées et non autorisées pour cet assessor
                    List<MethodeApplicationInfo> authorizedMethods = getMethodsApplicationToTest().stream()
                            .filter(method -> ((M) method).authorizedAssessors().contains(assessor))
                            .toList();

                    List<MethodeApplicationInfo> unauthorizedMethods = getMethodsApplicationToTest().stream()
                            .filter(method -> ((M) method).unauthorizedAssessors().contains(assessor))
                            .toList();

                    return dynamicContainer(
                            getReadableAssessorName(assessor),
                            Stream.of(
                                    dynamicContainer(
                                            "méthodes autorisées",
                                            authorizedMethods.stream()
                                                    .map(method -> dynamicTest(
                                                            method.name() + " - " + method.description(),
                                                            () -> testAuthorizedAccess((M) method, (P) assessor)
                                                    ))
                                    ),
                                    dynamicContainer(
                                            "méthodes non autorisées",
                                            unauthorizedMethods.stream()
                                                    .map(method -> dynamicTest(
                                                            method.name() + " - " + method.description(),
                                                            () -> testUnauthorizedAccess((M) method, (P) assessor)
                                                    ))
                                    )
                            )
                    );
                });
    }

    /**
     * Organisation par type d'accès (CRUD)
     */
    @TestFactory
    @DisplayName("Tests par type d'opération")
    <M extends MethodeInfo, P extends PrivilegeAssessorDomain> Stream<DynamicNode> operationTypeTests() {
        // Définition des groupes d'opérations
        Map<String, List<String>> operationGroups = Map.of(
                "Opérations de lecture", List.of("forDataRead"),
                "Opérations d'écriture", List.of("forDataDeposit"),
                "Opérations de suppression", List.of("forDataDelete"),
                "Opérations de gestion", List.of("forUpdateApplication", "forApplicationAuthorizationManager", "forManageAdministrator")
        );

        return operationGroups.entrySet().stream()
                .map(entry -> {
                    String groupName = entry.getKey();
                    List<String> operationNames = entry.getValue();

                    // Filtre les méthodes pour ce groupe
                    List<MethodeApplicationInfo> methodsInGroup = getMethodsApplicationToTest().stream()
                            .filter(method -> operationNames.contains(((M) method).name()))
                            .toList();

                    return dynamicContainer(
                            groupName,
                            methodsInGroup.stream()
                                    .map(method -> dynamicContainer(
                                            method.name() + " - " + method.description(),
                                            Stream.of(
                                                    dynamicContainer(
                                                            "utilisateurs avec droits",
                                                            method.authorizedAssessors().stream()
                                                                    .map(assessor -> dynamicTest(
                                                                            getReadableAssessorName((P) assessor),
                                                                            () -> testAuthorizedAccess((M) method, (P) assessor)
                                                                    ))
                                                    ),
                                                    dynamicContainer(
                                                            "utilisateurs sans droit",
                                                            method.unauthorizedAssessors().stream()
                                                                    .map(assessor -> dynamicTest(
                                                                            getReadableAssessorName((P) assessor),
                                                                            () -> testUnauthorizedAccess((M) method, (P) assessor)
                                                                    ))
                                                    )
                                            )
                                    ))
                    );
                });
    }

    /**
     * Test additionnel: Matrix complète des rôles et méthodes
     */
    @TestFactory
    @DisplayName("Matrice complète des droits")
    <M extends MethodeInfo, P extends PrivilegeAssessorDomain> Stream<DynamicNode> fullMatrix() {
        // Liste de tous les assessors
        List<PrivilegeAssessorDomain> allAssessors = Arrays.<PrivilegeAssessorDomain>asList(
                USER_MANAGER_FOR_ADD_AUTHORIZATION,
                USER_MANAGER,
                APPLICATION_MANAGER,
                APPLICATION_MANAGER_FOR_ADMIN,
                DATA_READER,
                DATA_DELETE_WITH_REPOSITORY,
                DATA_DELETE_WITHOUT_REPOSITORY,
                APPLICATION_MANAGER_FOR_DELETE_AUTHORIZATION,
                DATA_DEPOSIT_WRITER,
                DATA_PUBLISH_WRITER,
                APPLICATION_MANAGER_FOR_UPDATE,
                NO_RIGHTS
        );

        // Tests pour chaque méthode
        return getMethodsApplicationToTest().stream()
                .map(method -> dynamicContainer(
                        ((M) method).name() + " - " + ((M) method).description(),
                        allAssessors.stream()
                                .filter(assessor ->
                                        (method instanceof MethodeSystemInfo) && (assessor instanceof PrivilegeAssessorDomainForSystem) ||
                                                (method instanceof MethodeApplicationInfo) && (assessor instanceof PrivilegeAssessorDomainForApplication)
                                )
                                .map(assessor -> {
                                    String assessorName = getReadableAssessorName(assessor);
                                    boolean isAuthorized = ((M) method).authorizedAssessors().contains(assessor);
                                    boolean isUnauthorized = ((M) method).unauthorizedAssessors().contains(assessor);

                                    if (isAuthorized) {
                                        return dynamicTest(
                                                assessorName + " ✓",
                                                () -> testAuthorizedAccess((M) method, (P) assessor)
                                        );
                                    } else if (isUnauthorized) {
                                        return dynamicTest(
                                                assessorName + " ✗",
                                                () -> testUnauthorizedAccess((M) method, (P) assessor)
                                        );
                                    } else {
                                        // Assessors non explicitement testés
                                        return dynamicTest(
                                                assessorName + " ?",
                                                () -> {
                                                    try {
                                                        Object result = ((M) method).methodCall().apply(assessor);
                                                        System.out.println("ATTENTION: L'assessor " + assessorName +
                                                                " peut accéder à " + ((M) method).name() +
                                                                " mais n'était pas explicitement listé comme autorisé");
                                                        // Le test passe mais affiche un avertissement
                                                    } catch (Exception e) {
                                                        if (((M) method).exceptionType().isInstance(e)) {
                                                            System.out.println("L'assessor " + assessorName +
                                                                    " ne peut pas accéder à " + ((M) method).name() +
                                                                    " comme attendu");
                                                        } else {
                                                            fail("Exception inattendue: " + e.getClass().getSimpleName() +
                                                                    " au lieu de " + ((M) method).exceptionType().getSimpleName());
                                                        }
                                                    }
                                                }
                                        );
                                    }
                                })
                ));
    }
}