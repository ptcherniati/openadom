package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.file.FileOrUUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@Tag("core.auth")
@DisplayName("Tests pour ApplicationUser")
class ApplicationUserTest {

    @Mock
    private FileOrUUID mockFileOrUUID;

    @Mock
    private BinaryFileDataset mockBinaryFileDataset;

    // Application mock locale uniquement pour les instances créées dans setUp
    @Mock
    private Application localMockApplication;

    /**
     * Créer une instance d'Application mock pour les méthodes MethodSource
     */
    private static Application createTestMockApplication() {
        Application app = Mockito.mock(Application.class);
        when(app.getId()).thenReturn(UUID.randomUUID());
        when(app.getName()).thenReturn("Test Application");
        when(app.toString()).thenReturn("Application(Test Application)");
        return app;
    }

    /**
     * Fournit des implémentations de ApplicationUser pour les tests paramétrés
     */
    static Stream<Arguments> provideUserImplementations() {
        Application staticMockApp = createTestMockApplication();

        return Stream.of(
                Arguments.of(
                        "ApplicationDataReader",
                        new ApplicationDataReaderUser(staticMockApp)
                ),
                Arguments.of(
                        "ApplicationDataWriter via AdminUser",
                        new ApplicationAdminUser(staticMockApp)
                ),
                Arguments.of(
                        "ApplicationDataWriter via ManagerUser",
                        new ApplicationManagerUser(staticMockApp)
                ),
                Arguments.of(
                        "ApplicationDataWriter via DeleteUser",
                        new ApplicationDeleteUser(staticMockApp, "testData", new ArrayList<>())
                ),
                Arguments.of(
                        "ApplicationDataWriter via PublishWriterUser",
                        new ApplicationPublishWriterUser(staticMockApp, "testData", new ArrayList<>())
                )
        );
    }

    /**
     * Fournit des cas de test spécifiques pour la hiérarchie d'interfaces
     * Les instances sont créées avec une nouvelle application mock à chaque appel
     */
    static Stream<Arguments> provideHierarchyImplementations() {
        Application mockApp = createTestMockApplication();

        return Stream.of(
                Arguments.of(
                        "ApplicationDataReader implémente ApplicationUser",
                        new ApplicationDataReaderUser(mockApp),
                        new Class<?>[]{ApplicationUser.class, ApplicationPersona.class}
                ),
                Arguments.of(
                        "ApplicationDataWriter implémente ApplicationUser et étend ApplicationDataReader",
                        new ApplicationAdminUser(mockApp),
                        new Class<?>[]{ApplicationDataWriter.class, ApplicationUser.class, ApplicationPersona.class}
                ),
                Arguments.of(
                        "ApplicationPublishWriterUser implémente ApplicationDataWriter",
                        new ApplicationPublishWriterUser(mockApp, "testData", new ArrayList<>()),
                        new Class<?>[]{ApplicationDataWriter.class, ApplicationUser.class, ApplicationPersona.class}
                )
        );
    }

    /**
     * Fournit des cas de test pour le comportement polymorphique
     * Les instances sont créées avec une nouvelle application mock à chaque appel
     */
    static Stream<Arguments> providePolymorphismTestCases() {
        Application mockApp = createTestMockApplication();

        return Stream.of(
                Arguments.of(
                        "ApplicationAdminUser",
                        new ApplicationAdminUser(mockApp),
                        List.of(ApplicationDataWriter.class, ApplicationUser.class, ApplicationPersona.class, ApplicationManager.class, ApplicationDataDelete.class)
                ),
                Arguments.of(
                        "ApplicationManagerUser",
                        new ApplicationManagerUser(mockApp),
                        List.of(ApplicationDataWriter.class, ApplicationUser.class, ApplicationPersona.class, ApplicationManager.class, ApplicationDataDelete.class)
                ),
                Arguments.of(
                        "ApplicationDataReader",
                        new ApplicationDataReaderUser(mockApp),
                        List.of(ApplicationUser.class, ApplicationPersona.class)
                ),
                Arguments.of(
                        "ApplicationDeleteUser",
                        new ApplicationDeleteUser(mockApp, "testData", new ArrayList<>()),
                        List.of(ApplicationDataWriter.class, ApplicationUser.class, ApplicationPersona.class, ApplicationDataDelete.class)
                ),
                Arguments.of(
                        "ApplicationPublishWriterUser",
                        new ApplicationPublishWriterUser(mockApp, "testData", new ArrayList<>()),
                        List.of(ApplicationDataWriter.class, ApplicationUser.class, ApplicationPersona.class)
                )
        );
    }

    /**
     * Fournit des paires d'interfaces et de classes pour tester les relations sealed
     */
    static Stream<Arguments> provideSealedInterfacesAndPermittedClasses() {
        return Stream.of(
                Arguments.of(
                        "ApplicationUser",
                        ApplicationUser.class,
                        new Class[]{ApplicationDataReaderUser.class, ApplicationDataWriter.class},
                        2
                ),
                Arguments.of(
                        "ApplicationDataDelete",
                        ApplicationDataDelete.class,
                        new Class[]{ApplicationAdminUser.class, ApplicationDeleteUser.class, ApplicationManagerUser.class},
                        3
                ),
                Arguments.of(
                        "ApplicationManager",
                        ApplicationManager.class,
                        new Class[]{ApplicationAdminUser.class, ApplicationManagerUser.class},
                        2
                )
        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Utiliser un UUID constant au lieu d'en générer un nouveau à partir de RoleFactory
        UUID fixedId = UUID.randomUUID();
        when(localMockApplication.getId()).thenReturn(fixedId);
        when(localMockApplication.getName()).thenReturn("Test Application");
        when(localMockApplication.isData("testData")).thenReturn(false);

        when(mockFileOrUUID.binaryfiledataset()).thenReturn(mockBinaryFileDataset);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideUserImplementations")
    @DisplayName("Toutes les implémentations d'ApplicationUser devraient avoir accès à l'application")
    void allImplementationsShouldHaveAccessToApplication(
            String implName,
            ApplicationUser user) {

        // Vérifier l'accès à l'application
        assertThat(implName + " - application() devrait retourner l'application",
                user.application(), is(notNullValue()));

        // Vérifier que toString contient le nom de l'application
        assertThat(implName + " - toString() devrait contenir le nom de l'application",
                user.toString(), containsString("Test Application"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHierarchyImplementations")
    @DisplayName("La hiérarchie d'interfaces devrait être respectée")
    void shouldRespectInterfaceHierarchy(
            String description,
            Object instance,
            Class<?>[] expectedInterfaces) {

        // Vérifier que l'objet implémente toutes les interfaces attendues
        for (Class<?> expectedInterface : expectedInterfaces) {
            assertTrue(expectedInterface.isInstance(instance),
                    description + " devrait implémenter " + expectedInterface.getSimpleName());
        }

        // Vérifier le comportement polymorphique - accès à l'application via différentes interfaces
        if (instance instanceof ApplicationPersona persona) {
            assertNotNull(persona.application(),
                    "Accès à l'application via ApplicationPersona");
        }

        if (instance instanceof ApplicationUser user) {
            assertNotNull(user.application(),
                    "Accès à l'application via ApplicationUser");
        }

        if (instance instanceof ApplicationDataWriter writer) {
            assertNotNull(writer.application(),
                    "Accès à l'application via ApplicationDataWriter");
        }
    }

    @ParameterizedTest(name = "L'interface {0} devrait avoir les sous-classes autorisées attendues")
    @MethodSource("provideSealedInterfacesAndPermittedClasses")
    @DisplayName("Les interfaces sealed devraient restreindre les implémentations autorisées")
    void sealedInterfaceShouldRestrictAllowedImplementations(
            String interfaceName,
            Class<?> interfaceClass,
            Class<?>[] expectedPermittedClasses,
            int expectedCount) {

        // Vérifier que l'interface est bien scellée
        assertTrue(interfaceClass.isSealed(),
                interfaceName + " devrait être une interface sealed");

        // Obtenir les sous-classes autorisées directement via getPermittedSubclasses
        Class<?>[] permittedSubclasses = interfaceClass.getPermittedSubclasses();
        assertNotNull(permittedSubclasses,
                interfaceName + " devrait avoir des sous-classes autorisées");

        // Vérifier que les classes autorisées sont exactement celles attendues
        assertThat(Arrays.asList(permittedSubclasses),
                containsInAnyOrder(expectedPermittedClasses));

        // Vérifier que le nombre de classes autorisées est correct
        assertEquals(expectedCount, permittedSubclasses.length,
                interfaceName + " devrait permettre exactement " + expectedCount + " implémentations");
    }

    @Test
    @DisplayName("ApplicationUser devrait être une interface qui étend ApplicationPersona")
    void shouldExtendApplicationPersona() {
        // Vérifier que ApplicationUser étend ApplicationPersona
        assertTrue(ApplicationPersona.class.isAssignableFrom(ApplicationUser.class),
                "ApplicationUser devrait étendre ApplicationPersona");

        // Vérifier que toutes les implémentations de ApplicationUser sont aussi des ApplicationPersona
        for (Arguments args : provideUserImplementations().toList()) {
            Object impl = args.get()[1];
            assertTrue(impl instanceof ApplicationPersona,
                    impl.getClass().getSimpleName() + " devrait implémenter ApplicationPersona");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providePolymorphismTestCases")
    @DisplayName("Le polymorphisme devrait fonctionner correctement pour toutes les implémentations")
    void polymorphismShouldWorkCorrectly(String className, Object instance, List<Class<?>> expectedTypes) {
        // Vérifier que l'instance est assignable à tous les types attendus
        for (Class<?> type : expectedTypes) {
            assertTrue(type.isInstance(instance),
                    className + " devrait être assignable à " + type.getSimpleName());
        }

        // Accéder à l'application à travers l'instance fournie
        Application app = ((ApplicationPersona) instance).application();
        assertNotNull(app, "L'application ne devrait pas être null");

        // Vérifier que l'accès à l'application est cohérent quel que soit le type utilisé
        if (instance instanceof ApplicationPersona persona) {
            assertSame(app, persona.application(),
                    "L'accès à l'application via ApplicationPersona devrait être cohérent");
        }

        if (instance instanceof ApplicationUser user) {
            assertSame(app, user.application(),
                    "L'accès à l'application via ApplicationUser devrait être cohérent");
        }

        if (instance instanceof ApplicationDataWriter writer) {
            assertSame(app, writer.application(),
                    "L'accès à l'application via ApplicationDataWriter devrait être cohérent");
        }

        if (instance instanceof ApplicationManager manager) {
            assertSame(app, manager.application(),
                    "L'accès à l'application via ApplicationManager devrait être cohérent");
        }
    }

    @ParameterizedTest(name = "Test upcasting/downcasting pour {0}")
    @MethodSource("provideUserImplementations")
    @DisplayName("Les conversions ascendantes et descendantes devraient préserver l'identité de l'objet")
    void castingShouldPreserveObjectIdentity(String implName, ApplicationUser user) {
        // Conversion ascendante (upcasting) vers ApplicationPersona
        ApplicationPersona asPersona = user;

        // Vérifier que l'identité est préservée
        assertSame(user.application(), asPersona.application(),
                "La conversion ascendante devrait préserver l'accès à l'application");

        // Vérifier les conversions descendantes (downcasting) sécurisées
        if (user instanceof ApplicationDataWriter) {
            ApplicationDataWriter writer = (ApplicationDataWriter) user;
            assertSame(user.application(), writer.application(),
                    "La conversion descendante vers ApplicationDataWriter devrait préserver l'identité");

            // Tests spécifiques pour chaque type
            if (implName.contains("AdminUser")) {
                ApplicationAdminUser admin = (ApplicationAdminUser) writer;
                assertSame(user, admin, "La conversion devrait préserver l'identité pour AdminUser");
            } else if (implName.contains("ManagerUser")) {
                ApplicationManagerUser manager = (ApplicationManagerUser) writer;
                assertSame(user, manager, "La conversion devrait préserver l'identité pour ManagerUser");
            } else if (implName.contains("DeleteUser")) {
                ApplicationDeleteUser deleter = (ApplicationDeleteUser) writer;
                assertSame(user, deleter, "La conversion devrait préserver l'identité pour DeleteUser");
            } else if (implName.contains("PublishWriterUser")) {
                ApplicationPublishWriterUser publisher = (ApplicationPublishWriterUser) writer;
                assertSame(user, publisher, "La conversion devrait préserver l'identité pour PublishWriterUser");
            }
        }
    }
}