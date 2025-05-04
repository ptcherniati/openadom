package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.*;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Stream;

import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCanDeleteRightsException.NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION;
import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataReaderException.NO_RIGHT_FOR_USER_DATA_READER;
import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterException.NO_RIGHT_FOR_USER_DATA_WRITER;
import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationManagerRightsException.NO_RIGHT_FOR_APPLICATION_MANAGEMENT;
import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserManagerRightsException.NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT;
import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserReaderRightsException.NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Tests unitaires de PrivilegeAssessorDomainForApplication")
class PrivilegeAssessorDomainForApplicationTest {

    public static final PrivilegeAssessorDomainForApplication ASSESSOR_WITHOUT_RIGHTS =
            PrivilegeAssessorDomainForApplicationFactory.builder()
                    .withAuthorizations(AuthorizationsForApplicationUserFactory.builder()
                            .build())
                    .build();
    @Mock
    private AuthorizationsForApplicationUser mockAuthorizations;

    @Mock
    private Application mockApplication;
    @Mock
    private AuthorizationParsed authorizationParsed;

    @Mock
    private GetGrantableResult mockGrantable;

    private static final String APPLICATION_NAME = "TestApplication";
    private static final String DATA_NAME = "testData";

    /**
     * Fournit des scénarios de test pour les différents droits d'application.
     * <p>
     * Paramètres:
     * - boolean canUpdateApplication: droit de mise à jour d'application
     * - boolean canManageAuthorizations: droit de gestion des autorisations
     * - boolean canAddAuthorization: droit d'ajout d'autorisation
     * - boolean canManageAdministrator: droit de gestion des administrateurs
     */

    static Stream<Arguments> provideApplicationRightsScenarios() {
        return Stream.of(
                // Scénario 1: Utilisateur avec droits de mise à jour d'application
                Arguments.of(
                        "Utilisateur avec droits de mise à jour d'application (ApplicationAdminUser)",
                        true,
                        true,
                        true,
                        true,
                        new Class[]{ApplicationAdminUser.class, ApplicationManager.class},
                        new LinkedList(List.of(NotApplicationUserManagerRightsException.class))
                ),
                // Scénario 2: Utilisateur avec droits de gestion d'autorisations
                Arguments.of(
                        "Utilisateur avec droits de gestion d'autorisations (ApplicationManagerUser)",
                        false,
                        true,
                        false,
                        false,
                        new Class[]{ApplicationManager.class},
                        new LinkedList(List.of(NotApplicationManagerRightsException.class, NotApplicationUserReaderRightsException.class, NotApplicationManagerRightsException.class, NotApplicationUserReaderRightsException.class))
                ),
                // Scénario 3: Utilisateur avec droits d'ajout d'autorisations
                Arguments.of(
                        "Utilisateur avec droits d'ajout d'autorisations",
                        true,
                        true,
                        true,
                        false,
                        new Class[]{ApplicationAdminUser.class, ApplicationManager.class},
                        new LinkedList(List.of(NotApplicationManagerRightsException.class))
                ),
                // Scénario 4: Utilisateur avec droits de gestion des administrateurs
                Arguments.of(
                        "Utilisateur avec droits de gestion des administrateurs",
                        true,
                        true,
                        true,
                        true,
                        new Class[]{ApplicationAdminUser.class, ApplicationManager.class},
                        new LinkedList(List.of())
                )
        );
    }

    /**
     * Fournit des scénarios de test pour les droits d'accès aux données.
     * Vérifie les différents types d'accès: lecture, écriture, suppression.
     */
    static Stream<Arguments> provideDataRightsScenarios() {
        return Stream.of(
                // Scénario 1: Utilisateur avec droits de lecture
                Arguments.of(
                        "Utilisateur avec droits de lecture",
                        Map.of(
                                AuthorizationsForUserResult.Roles.READ, true,
                                AuthorizationsForUserResult.Roles.PUBLICATION, false,
                                AuthorizationsForUserResult.Roles.DELETE, false
                        ),
                        true,
                        ApplicationDataReader.class
                ),
                // Scénario 2: Utilisateur avec droits d'écriture
                Arguments.of(
                        "Utilisateur avec droits d'écriture",
                        Map.of(
                                AuthorizationsForUserResult.Roles.READ, true,
                                AuthorizationsForUserResult.Roles.PUBLICATION, false,
                                AuthorizationsForUserResult.Roles.DELETE, false
                        ),
                        true,
                        ApplicationDataWriter.class
                ),
                // Scénario 3: Utilisateur avec droits de suppression
                Arguments.of(
                        "Utilisateur avec droits de suppression",
                        Map.of(
                                AuthorizationsForUserResult.Roles.READ, true,
                                AuthorizationsForUserResult.Roles.PUBLICATION, false,
                                AuthorizationsForUserResult.Roles.DELETE, true
                        ),
                        true,
                        ApplicationDeleteUser.class
                ),
                // Scénario 4: Utilisateur sans droits
                Arguments.of(
                        "Utilisateur sans aucun droit",
                        Map.of(
                                AuthorizationsForUserResult.Roles.READ, false,
                                AuthorizationsForUserResult.Roles.PUBLICATION, false,
                                AuthorizationsForUserResult.Roles.DELETE, false
                        ),
                        false,
                        null
                )
        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockApplication.getName()).thenReturn(APPLICATION_NAME);
        when(mockApplication.getId()).thenReturn(UUID.randomUUID());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideApplicationRightsScenarios")
    @DisplayName("Vérification correcte des droits d'application")
    void shouldVerifyApplicationRightsCorrectly(
            String scenario,
            boolean canUpdateApplication,
            boolean canManageAuthorizations,
            boolean canAddAuthorization,
            boolean canManageAdministrator,
            Class<?>[] expectedResultType,
            List<Class<? extends Exception>> expectedExceptionType
    ) {
        // Arrangement
        when(mockAuthorizations.isApplicationManager()).thenReturn(canUpdateApplication || canManageAuthorizations || canAddAuthorization);
        when(mockAuthorizations.isApplicationManager()).thenReturn(canManageAdministrator);

        PrivilegeAssessorDomainForApplication<Object> privilegeAssessor = new PrivilegeAssessorDomainForApplication<>(
                mockAuthorizations,
                new Object(),
                mockApplication,
                mockGrantable
        );

        // Actions et assertions
        if (canUpdateApplication) {
            when(mockAuthorizations.isApplicationManager()).thenReturn(canUpdateApplication);
            ApplicationManager result = privilegeAssessor.forUpdateApplication();
            assertNotNull(result, "Le résultat ne doit pas être null pour un utilisateur avec droits de mise à jour");
            assertTrue(Arrays.stream(expectedResultType).anyMatch(c -> c.isInstance(result)), "Le résultat " + result + " doit être de l'un des types attendus: " + Arrays.toString(expectedResultType));
        } else {
            doReturn(canUpdateApplication).when(mockAuthorizations).isApplicationManager();
            Exception exception = assertThrows(
                    expectedExceptionType.removeFirst(),
                    privilegeAssessor::forUpdateApplication,
                    "Une exception doit être levée en l'absence de droits de mise à jour"
            );
            assertThat(exception.getMessage(), containsString(NO_RIGHT_FOR_APPLICATION_MANAGEMENT));
        }

        if (canManageAuthorizations) {
            when(mockAuthorizations.isUserManager()).thenReturn(canManageAuthorizations);
            ApplicationManager result = privilegeAssessor.forManageAuthorizations();
            assertNotNull(result, "Le résultat ne devrait pas être null pour les droits de gestion d'autorisations");
            assertTrue(Arrays.stream(expectedResultType).anyMatch(c -> c.isInstance(result)), "Le résultat " + result + "  devrait être du type " + expectedResultType);
        } else {
            Exception exception = assertThrows(
                    expectedExceptionType.removeFirst(),
                    privilegeAssessor::forManageAuthorizations,
                    "Une exception devrait être levée en l'absence de droits de gestion d'autorisations"
            );
            assertThat(exception.getMessage(), containsString(NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT));
        }

        if (canAddAuthorization) {
            when(mockAuthorizations.isUserManager()).thenReturn(canAddAuthorization);
            ApplicationManager result = privilegeAssessor.forAddAuthorization();
            assertNotNull(result, "Le résultat ne devrait pas être null pour les droits d'ajout d'autorisations");
            assertTrue(Arrays.stream(expectedResultType).anyMatch(c -> c.isInstance(result)), "Le résultat " + result + "  devrait être du type " + expectedResultType);
        } else {
            doReturn(canAddAuthorization).when(mockAuthorizations).isUserManager();
            Exception exception = assertThrows(
                    expectedExceptionType.removeFirst(),
                    privilegeAssessor::forAddAuthorization,
                    "Une exception devrait être levée en l'absence de droits d'ajout d'autorisations"
            );
            assertThat(exception.getMessage(), containsString(NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION));
        }

        if (canManageAdministrator) {
            ApplicationAdminUser result = privilegeAssessor.forManageAdministrator();
            assertNotNull(result, "Le résultat ne devrait pas être null pour les droits de gestion des administrateurs");
            assertTrue(Arrays.stream(expectedResultType).anyMatch(c -> c.isInstance(result)), "Le résultat " + result + "  devrait être du type " + expectedResultType);
        } else {
            when(mockAuthorizations.isApplicationManager()).thenReturn(canManageAdministrator);
            Exception exception = assertThrows(
                    expectedExceptionType.removeFirst(),
                    privilegeAssessor::forManageAdministrator,
                    "Une exception devrait être levée en l'absence de droits de gestion des administrateurs"
            );
            assertThat(exception.getMessage(), containsString(NO_RIGHT_FOR_APPLICATION_MANAGEMENT));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideDataRightsScenarios")
    @DisplayName("Vérification correcte des droits d'accès aux données")
    void shouldVerifyDataRightsCorrectly(
            String scenario,
            Map<AuthorizationsForUserResult.Roles, Boolean> authorizations,
            boolean expectedSuccess,
            Class<?> expectedResultType
    ) {
        // Arrangement
        // doReturn(new ArrayList(Set.of())).when(mockAuthorizations.getAuthorizations(eq(DATA_NAME), anySet()));

        PrivilegeAssessorDomainForApplication<Object> privilegeAssessor = new PrivilegeAssessorDomainForApplication<>(
                mockAuthorizations,
                new Object(),
                mockApplication,
                mockGrantable
        );

        // Action et assertion pour les droits de lecture
        if (expectedSuccess && authorizations.getOrDefault(AuthorizationsForUserResult.Roles.READ, false)) {
            when(mockAuthorizations.canRead(DATA_NAME)).thenReturn(expectedSuccess);
            ApplicationDataReader reader = privilegeAssessor.forDataRead(DATA_NAME);
            assertNotNull(reader, "Le résultat ne devrait pas être null pour les droits de lecture");
            assertEquals(APPLICATION_NAME, reader.application().getName(), "Le nom de données devrait être correctement transmis");
            assertEquals(mockApplication, reader.application(), "L'application devrait être correctement transmise");
        }

        // Action et assertion pour les droits d'écriture
        if (expectedSuccess && authorizations.getOrDefault(AuthorizationsForUserResult.Roles.PUBLICATION, false)) {
            when(mockAuthorizations.canWrite(DATA_NAME, false)).thenReturn(true);
            ApplicationDataWriter writer = privilegeAssessor.forDataWrite(DATA_NAME, false);
            assertNotNull(writer, "Le résultat ne devrait pas être null pour les droits d'écriture");
            assertTrue(expectedResultType.isAssignableFrom(writer.getClass()),
                    "Le résultat devrait être assignable à " + expectedResultType.getSimpleName());
            assertEquals(DATA_NAME, writer.dataName(), "Le nom de données devrait être correctement transmis");
            assertEquals(mockApplication, writer.application(), "L'application devrait être correctement transmise");
        }

        // Action et assertion pour les droits de suppression
        if (expectedSuccess && authorizations.getOrDefault(AuthorizationsForUserResult.Roles.DELETE, false)) {
            Submission submission = mock(Submission.class);
            when(submission.strategy()).thenReturn(SubmissionType.OA_VERSIONING);
            when(mockApplication.findSubmission("NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION")).thenReturn(Optional.of(submission));
            when(mockAuthorizations.canDelete(NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION, true)).thenReturn(true);
            ApplicationDataDelete deleter = privilegeAssessor.forDataDelete(NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION);
            assertNotNull(deleter, "Le résultat ne devrait pas être null pour les droits de suppression");
            assertTrue(expectedResultType.isAssignableFrom(deleter.getClass()),
                    "Le résultat devrait être assignable à " + expectedResultType.getSimpleName());
            assertEquals(NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION, deleter.dataName(), "Le nom de données devrait être correctement transmis");
            assertEquals(mockApplication, deleter.application(), "L'application devrait être correctement transmise");
        }
    }


    static Stream<Arguments> roles() {

        // Arrangement
        Map<AuthorizationsForUserResult.Roles, Boolean> expectedAuthorizations = new HashMap<>();
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.UPLOAD, false);
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.DELETE, false);
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.DOWNLOAD, false);
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.ANY, false);
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.PUBLICATION, false);
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.ACTIVE_APPLICATION_USER, false);
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.READ, false);
        expectedAuthorizations.put(AuthorizationsForUserResult.Roles.APPLICATION_USER, false);
        Map<AuthorizationsForUserResult.Roles, Boolean> expectedAuthorizationsApplicationManager = new HashMap<>(expectedAuthorizations);
        Map<AuthorizationsForUserResult.Roles, Boolean> expectedAuthorizationsUserManager = new HashMap<>(expectedAuthorizations);
        Map<AuthorizationsForUserResult.Roles, Boolean> expectedAuthorizationsLambda = new HashMap<>(expectedAuthorizations);
        expectedAuthorizationsApplicationManager.keySet().stream()
                .forEach(role -> expectedAuthorizationsApplicationManager.put(role, true));
        //Expected: is <{UPLOAD=false, APPLICATION_USER=false, DELETE=false, PUBLICATION=false, ANY=false, READ=false, DOWNLOAD=false, ACTIVE_APPLICATION_USER=false}>
        expectedAuthorizationsUserManager.keySet().stream()
//                .filter(List.<AuthorizationsForUserResult.Roles>of(
//
//                ) ::contains)
                .forEach(role -> expectedAuthorizationsUserManager.put(role, true));
        final Stream<Arguments> argumentsStream = Stream.of(
                Arguments.of(
                        "application Manager",
                        List.of(),
                        true,
                        true,
                        expectedAuthorizationsApplicationManager
                ),
                Arguments.of(
                        "user Manager",
                        List.of(),
                        false,
                        true,
                        expectedAuthorizationsUserManager
                ),
                Arguments.of(
                        "Lambda user",
                        List.of(),
                        false,
                        false,
                        expectedAuthorizationsLambda
                )
        );
        return argumentsStream
                .map(el -> new LinkedList<Arguments>(List.of(el)))
                .map(list -> {
                    Arguments arguments = list.get(0);
                    if (Arrays.stream(arguments.get())
                            .anyMatch(arg -> "Lambda user".equals(arg))) {
                        Arrays.stream(AuthorizationsForUserResult.Roles.values())
                                .forEach(role -> addRoleToList(list, new HashMap<AuthorizationsForUserResult.Roles, Boolean>(expectedAuthorizations), role));
                    }
                    return list;
                })
                .flatMap(List::stream);
    }

    private static void addRoleToList(LinkedList<Arguments> list, HashMap<AuthorizationsForUserResult.Roles, Boolean> rolesBooleanHashMap, AuthorizationsForUserResult.Roles role) {
        rolesBooleanHashMap.put(role, true);
        final Arguments arguments = Arguments.of(
                "Lambda user with %s".formatted(role.name()),
                List.of(role),
                false,
                false,
                rolesBooleanHashMap
        );
        list.add(arguments);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roles")
    @DisplayName("getAuthorizationsForUser retourne les autorisations correctes")
    void getAuthorizationsForUserShouldReturnCorrectAuthorizations(
            String scenario,
            List<AuthorizationsForUserResult.Roles> roles,
            boolean isApplicationManager,
            boolean isUSerManager,
            Map<AuthorizationsForUserResult.Roles, Boolean> expectedAuthorizations
    ) {
        when(mockAuthorizations.isApplicationManager()).thenReturn(isApplicationManager);
        when(mockAuthorizations.isUserManager()).thenReturn(isUSerManager);

        when(mockAuthorizations.getAuthorizations(eq(DATA_NAME), anySet())).thenReturn(new ArrayList<>(List.of(authorizationParsed)));

        PrivilegeAssessorDomainForApplication<Object> privilegeAssessor = new PrivilegeAssessorDomainForApplication<>(
                mockAuthorizations,
                new Object(),
                mockApplication,
                mockGrantable
        );
        AuthorizationsResult authorizationsResult = mock(AuthorizationsResult.class);
        when(mockGrantable.authorizationsForUser()).thenReturn(authorizationsResult);
        Map<String, List<AuthorizationParsed>> userAutorization = mock(Map.class);
        when(mockAuthorizations.userAuthorizations()).thenReturn(userAutorization);
        when(userAutorization.get(DATA_NAME)).thenReturn(List.of(authorizationParsed));
        Set<OperationType> operationTypes = new HashSet<>();
        Arrays.stream(OperationType.values()).forEach(operationType -> {
            operationTypes.add(operationType);
            switch (operationType) {
                case delete -> {
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.DELETE, true);
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.DOWNLOAD, true);
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.READ, true);
                }
                case publication -> {
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.UPLOAD, true);
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.DOWNLOAD, true);
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.READ, true);
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.PUBLICATION, true);
                }
                case depot -> {
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.UPLOAD, true);
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.ANY, true);
                }
                case extraction -> {
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.DOWNLOAD, true);
                    expectedAuthorizations.put(AuthorizationsForUserResult.Roles.READ, true);
                }
            }
        });
        when(authorizationParsed.operationTypes()).thenReturn(operationTypes);
        if (roles.contains(AuthorizationsForUserResult.Roles.APPLICATION_USER)) {
            when(authorizationsResult.applicationUser()).thenReturn(true);
            when(mockGrantable.authorizationsForUser()).thenReturn(authorizationsResult);
        }
        if (roles.contains(AuthorizationsForUserResult.Roles.ACTIVE_APPLICATION_USER)) {
            when(authorizationsResult.activeApplicationUser()).thenReturn(true);
            when(mockGrantable.authorizationsForUser()).thenReturn(authorizationsResult);
        }


        // Action
        Map<AuthorizationsForUserResult.Roles, Boolean> actualAuthorizations = privilegeAssessor.getAuthorizationsForUser(DATA_NAME);
        int times = !isApplicationManager ? 1 : 0;
        // Assertion
        assertThat(actualAuthorizations, is(expectedAuthorizations));
        verify(mockAuthorizations).userAuthorizations();
        verify(mockAuthorizations).publicAuthorizations();
        verify(mockAuthorizations).isApplicationManager();
        verify(mockAuthorizations, times(times)).isUserManager();
    }

    @Test
    @DisplayName("Implémentation correcte de l'interface PrivilegeAssessorDomain")
    void shouldImplementPrivilegeAssessorDomain() {
        // Arrangement
        PrivilegeAssessorDomainForApplication<Object> privilegeAssessor = new PrivilegeAssessorDomainForApplication<>(
                mockAuthorizations,
                new Object(),
                mockApplication,
                mockGrantable
        );

        // Assertion
        assertTrue(privilegeAssessor instanceof PrivilegeAssessorDomain,
                "Devrait implémenter PrivilegeAssessorDomain");
    }

    @Test
    @DisplayName("forDeleteAuthorization retourne ApplicationAdminUser avec les droits appropriés")
    void forDeleteAuthorizationShouldReturnApplicationAdminUserWhenRightsAvailable() {
        // Arrangement
        when(mockAuthorizations.isApplicationManager()).thenReturn(true);

        PrivilegeAssessorDomainForApplication<Object> privilegeAssessor = new PrivilegeAssessorDomainForApplication<>(
                mockAuthorizations,
                new Object(),
                mockApplication,
                mockGrantable
        );

        // Action
        ApplicationAdminUser result = privilegeAssessor.forDeleteAuthorization();

        // Assertion
        assertNotNull(result, "Le résultat ne devrait pas être null");
        assertEquals(mockApplication, result.application(), "L'application devrait être correctement transmise");
        verify(mockAuthorizations).isApplicationManager();
    }

    @Test
    @DisplayName("forDeleteAuthorization lève une exception en l'absence de droits")
    void forDeleteAuthorizationShouldThrowExceptionWhenRightsNotAvailable() {
        // Arrangement
        when(mockAuthorizations.isApplicationManager()).thenReturn(false);

        PrivilegeAssessorDomainForApplication<Object> privilegeAssessor = new PrivilegeAssessorDomainForApplication<>(
                mockAuthorizations,
                new Object(),
                mockApplication,
                mockGrantable
        );

        // Action et assertion
        NotApplicationManagerRightsException exception = assertThrows(
                NotApplicationManagerRightsException.class,
                privilegeAssessor::forDeleteAuthorization,
                "Devrait lever une exception NotApplicationAdminRightsException"
        );

        assertThat(exception.getMessage(), containsString(NO_RIGHT_FOR_APPLICATION_MANAGEMENT));
        verify(mockAuthorizations).isApplicationManager();
    }

    @Test
    void forSystem() {
    }

    @Test
    void forUpdateApplication() {
        PrivilegeAssessorDomainForApplication forApplication =
                PrivilegeAssessorDomainForApplicationFactory.forUpdateApplication();
        assertThat(
                "Un ApplicationAdminUser doit être créé sans déclencher d'exception NotApplicationManagerRightsException",
                assertDoesNotThrow(
                        () -> forApplication.forUpdateApplication(),
                        "Ne doit pas lever d'exception NotApplicationManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationAdminUser.class))
        );
        assertThatThrownBy(
                () -> ASSESSOR_WITHOUT_RIGHTS.forUpdateApplication(),
                "Une exception NotApplicationManagerRightsException doit être levée lors de l'appel à updateApplication sans droits"
        )
                .isInstanceOf(NotApplicationManagerRightsException.class)
                .hasMessageContaining(NO_RIGHT_FOR_APPLICATION_MANAGEMENT);
    }

    @Test
    void forManageAuthorizations() {
        PrivilegeAssessorDomainForApplication forApplication =
                PrivilegeAssessorDomainForApplicationFactory.forManageAuthorizations();
        assertThat(
                "Un ApplicationManagerUser doit être créé sans déclencher d'exception NotApplicationUserManagerRightsException",
                assertDoesNotThrow(
                        () -> forApplication.forManageAuthorizations(),
                        "Ne doit pas lever d'exception NotApplicationUserManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationManagerUser.class))
        );
        PrivilegeAssessorDomainForApplication notForApplication =
                ASSESSOR_WITHOUT_RIGHTS;
        assertThatThrownBy(
                () -> ASSESSOR_WITHOUT_RIGHTS.forManageAuthorizations(),
                "Sans droit une NotApplicationUserManagerRightsException est lancé pour updateApplication"
        )
                .isInstanceOf(NotApplicationUserManagerRightsException.class)
                .hasMessageContaining(NO_RIGHT_FOR_APPLICATION_USER_MANAGEMENT);
    }

    @Test
    void forAddAuthorization() {
        PrivilegeAssessorDomainForApplication forApplication =
                PrivilegeAssessorDomainForApplicationFactory.forAddAuthorization();
        assertThat(
                "Un ApplicationManagerUser doit être créé sans déclencher d'exception NotApplicationUserReaderRightsException",
                assertDoesNotThrow(
                        () -> forApplication.forAddAuthorization(),
                        "Ne doit pas lever d'exception NotApplicationUserReaderRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationManagerUser.class))
        );
        PrivilegeAssessorDomainForApplication notForApplication =
                ASSESSOR_WITHOUT_RIGHTS;
        assertThatThrownBy(
                () -> ASSESSOR_WITHOUT_RIGHTS.forAddAuthorization(),
                "Une exception NotApplicationUserReaderRightsException doit être levée lors de l'appel à forAddAuthorization sans droits"
        )
                .isInstanceOf(NotApplicationUserReaderRightsException.class)
                .hasMessageContaining(NO_RIGHT_FOR_APPLICATION_USER_READER_RIGHT_EXCEPTION);
    }

    @Test
    void forManageAdministrator() {
        PrivilegeAssessorDomainForApplication forApplication =
                PrivilegeAssessorDomainForApplicationFactory.forManageAdministrator();
        assertThat(
                "Un ApplicationAdminUser doit être créé sans déclencher d'exception NotApplicationManagerRightsException",
                assertDoesNotThrow(
                        () -> forApplication.forManageAdministrator(),
                        "Ne doit pas lever d'exception NotApplicationManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationAdminUser.class))
        );
        PrivilegeAssessorDomainForApplication notForApplication =
                ASSESSOR_WITHOUT_RIGHTS;
        assertThatThrownBy(
                () -> ASSESSOR_WITHOUT_RIGHTS.forManageAdministrator(),
                "Une exception NotApplicationManagerRightsException doit être levée lors de l'appel à forManageAdministrator sans droits"
        )
                .isInstanceOf(NotApplicationManagerRightsException.class)
                .hasMessageContaining(NO_RIGHT_FOR_APPLICATION_MANAGEMENT);
    }

    @Test
    void forDataRead() {
        PrivilegeAssessorDomainForApplication forDataRead =
                PrivilegeAssessorDomainForApplicationFactory.forDataRead();
        PrivilegeAssessorDomainForApplication forUserManager =
                PrivilegeAssessorDomainForApplicationFactory.forUserManager();
        PrivilegeAssessorDomainForApplication forApplicationManager =
                PrivilegeAssessorDomainForApplicationFactory.foApplicationManager();
        assertThat(
                "Un ApplicationDataReader doit être créé sans déclencher d'exception NotApplicationDataReaderException",
                assertDoesNotThrow(
                        () -> forApplicationManager.forDataRead("dataName"),
                        "Ne doit pas lever d'exception NotApplicationDataReaderException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationDataReader.class))
        );
        assertThat(
                "Un ApplicationDataReader devrait être créé sans lever NotApplicationDataReaderException",
                assertDoesNotThrow(
                        () -> forUserManager.forDataRead("dataName"),
                        "Ne devrait pas lever NotApplicationDataReaderException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationDataReader.class))
        );
        assertThat(
                "Un ApplicationDataReader devrait être créé sans lever NotApplicationDataReaderException",
                assertDoesNotThrow(
                        () -> forDataRead.forDataRead("dataName"),
                        "Ne devrait pas lever NotApplicationDataReaderException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationDataReader.class))
        );
        assertThatThrownBy(
                () -> ASSESSOR_WITHOUT_RIGHTS.forDataRead("dataName"),
                "Sans droit une NotApplicationDataReaderException est lancé pour updateApplication"
        )
                .isInstanceOf(NotApplicationDataReaderException.class)
                .hasMessageContaining(NO_RIGHT_FOR_USER_DATA_READER);
    }

    @Test
    void forDeleteAuthorization() {
        PrivilegeAssessorDomainForApplication forApplication =
                PrivilegeAssessorDomainForApplicationFactory.forDeleteAuthorization();
        assertThat(
                "Un ApplicationAdminUser devrait être créé sans lever NotApplicationManagerRightsException",
                assertDoesNotThrow(
                        () -> forApplication.forDeleteAuthorization(),
                        "Ne devrait pas lever NotApplicationManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationAdminUser.class))
        );
        assertThatThrownBy(
                () -> ASSESSOR_WITHOUT_RIGHTS.forDeleteAuthorization(),
                "Sans droit une NotApplicationManagerRightsException est lancé pour updateApplication"
        )
                .isInstanceOf(NotApplicationManagerRightsException.class)
                .hasMessageContaining(NO_RIGHT_FOR_APPLICATION_MANAGEMENT);
    }

    @Test
    void forDataWrite() {
        PrivilegeAssessorDomainForApplication foApplicationManager =
                PrivilegeAssessorDomainForApplicationFactory.foApplicationManager();
        PrivilegeAssessorDomainForApplication forUserManager =
                PrivilegeAssessorDomainForApplicationFactory.forUserManager();
        PrivilegeAssessorDomainForApplication forDataPublish =
                PrivilegeAssessorDomainForApplicationFactory.forDataPublish();
        PrivilegeAssessorDomainForApplication forDataDeposit =
                PrivilegeAssessorDomainForApplicationFactory.forDataDeposit();
        assertThat(
                "Un ApplicationAdminUser devrait être créé sans lever NotApplicationManagerRightsException",
                assertDoesNotThrow(
                        () -> foApplicationManager.forDataWrite("dataName", true),
                        "Ne devrait pas lever NotApplicationManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationAdminUser.class))
        );
        assertThat(
                "Un ApplicationAdminUser devrait être créé sans lever NotApplicationManagerRightsException",
                assertDoesNotThrow(
                        () -> forUserManager.forDataWrite("dataName", true),
                        "Ne devrait pas lever NotApplicationManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationManagerUser.class))
        );
        assertThat(
                "Un ApplicationAdminUser devrait être créé sans lever NotApplicationManagerRightsException",
                assertDoesNotThrow(
                        () -> forDataDeposit.forDataWrite("dataName", false),
                        "Ne devrait pas lever NotApplicationManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationPublishWriterUser.class))
        );
        assertThat(
                "Un ApplicationPublishWriterUser doit être créé sans déclencher d'exception NotApplicationManagerRightsException",
        
                assertDoesNotThrow(
                        () -> forDataPublish.forDataWrite("dataName", true),
                        "Ne doit pas lever d'exception NotApplicationManagerRightsException"
                ),
                both(notNullValue())
                        .and(instanceOf(ApplicationPublishWriterUser.class))
        );
        PrivilegeAssessorDomainForApplication notForApplication =
                ASSESSOR_WITHOUT_RIGHTS;
        assertThatThrownBy(
                () -> ASSESSOR_WITHOUT_RIGHTS.forDataWrite("dataName", true),
                "Sans droit une NotApplicationManagerRightsException est lancé pour updateApplication"
        )
                .isInstanceOf(NotApplicationDataWriterException.class)
                .hasMessageContaining(NO_RIGHT_FOR_USER_DATA_WRITER);
    }

    @Test
    void forDataDelete() {
    }

    @Test
    void authorizations() {
    }

    @Test
    void domain() {
    }

    @Test
    void application() {
    }

    @Test
    void grantable() {
    }
}