package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DisplayName("Tests pour ApplicationUser")
class ApplicationUserTest {

    @Mock
    private static Application mockApplication;
    
    @Mock
    private FileOrUUID mockFileOrUUID;
    
    @Mock
    private BinaryFileDataset mockBinaryFileDataset;
    
    @Mock
    public static AuthorizationParsed authorizationParsed;

    /**
     * Créer une instance d'Application mock pour les tests statiques
     */
    private static Application createMockApplication() {
        Application app = Mockito.mock(Application.class);
        when(app.getId()).thenReturn(UUID.randomUUID());
        when(app.getName()).thenReturn("Test Application");
        // Configurer toString pour inclure le nom de l'application
        when(app.toString()).thenReturn("Application(Test Application)");
        return app;
    }
    
    /**
     * Fournit des implémentations de ApplicationUser pour les tests paramétrés
     */
    static Stream<Arguments> provideUserImplementations() {
        Application staticMockApp = createMockApplication();
        
        return Stream.of(
                Arguments.of(
                        "ApplicationDataReader",
                        new ApplicationDataReader(staticMockApp)
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
                )
        );
    }

    /**
     * Fournit des cas de test spécifiques pour la hiérarchie d'interfaces
     */
    static Stream<Arguments> provideHierarchyImplementations() {
        return Stream.of(
                Arguments.of(
                        "ApplicationDataReader implémente ApplicationUser",
                        new ApplicationDataReader(mockApplication),
                        new Class<?>[] {ApplicationUser.class, ApplicationPersona.class}
                ),
                Arguments.of(
                        "ApplicationDataWriter implémente ApplicationUser et étend ApplicationDataReader",
                        new ApplicationAdminUser(mockApplication),
                        new Class<?>[] {ApplicationDataWriter.class, ApplicationUser.class, ApplicationPersona.class}
                )
        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockApplication.getId()).thenReturn(UUID.randomUUID());
        when(mockApplication.getName()).thenReturn("Test Application");
        when(mockApplication.isData("testData")).thenReturn(false);
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

    @Test
    @DisplayName("Le sealed interface devrait restreindre les implémentations autorisées")
    void sealedInterfaceShouldRestrictAllowedImplementations() {
        // Vérifier que ApplicationUser est bien une interface scellée
        assertTrue(ApplicationUser.class.isSealed(), 
                "ApplicationUser devrait être une interface sealed");
        
        // Obtenir les sous-classes autorisées directement via getPermittedSubclasses
        Class<?>[] permittedSubclasses = ApplicationUser.class.getPermittedSubclasses();
        assertNotNull(permittedSubclasses, 
                "ApplicationUser devrait avoir des sous-classes autorisées");
        
        // Vérifier que les classes autorisées sont exactement celles attendues
        assertThat(Arrays.asList(permittedSubclasses), 
                containsInAnyOrder(
                        ApplicationDataReader.class,
                        ApplicationDataWriter.class
                ));
        
        // Vérifier que le nombre de classes autorisées est égal à 2
        assertEquals(2, permittedSubclasses.length, 
                "ApplicationUser devrait permettre exactement 2 implémentations");
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
    
    @Test
    @DisplayName("ApplicationUser devrait être au centre d'une hiérarchie cohérente")
    void shouldHaveCoherentInterfaceHierarchy() {
        // Créer des instances des différentes implémentations
        ApplicationDataReader reader = new ApplicationDataReader(mockApplication);
        ApplicationDataWriter writer = new ApplicationAdminUser(mockApplication);
        
        // Vérifier les relations d'héritage
        assertTrue(reader instanceof ApplicationUser, 
                "ApplicationDataReader devrait implémenter ApplicationUser");
        assertTrue(writer instanceof ApplicationUser, 
                "ApplicationDataWriter devrait implémenter ApplicationUser");
        
        // Vérifier que ApplicationDataWriter étend ApplicationUser
        assertTrue(ApplicationUser.class.isAssignableFrom(ApplicationDataWriter.class),
                "ApplicationDataWriter devrait étendre ApplicationUser");
        
        // Vérifier que l'accès à l'application est cohérent dans toute la hiérarchie
        assertSame(mockApplication, reader.application(), 
                "L'accès à l'application devrait être cohérent pour ApplicationDataReader");
        assertSame(mockApplication, writer.application(), 
                "L'accès à l'application devrait être cohérent pour ApplicationDataWriter");
        
        // Tester le polymorphisme - utiliser les objets via l'interface parent
        ApplicationUser userFromReader = reader;
        ApplicationUser userFromWriter = writer;
        
        assertSame(mockApplication, userFromReader.application(),
                "Le polymorphisme de ApplicationUser -> ApplicationDataReader devrait fonctionner");
        assertSame(mockApplication, userFromWriter.application(), 
                "Le polymorphisme de ApplicationUser -> ApplicationDataWriter devrait fonctionner");
    }
    
    @Test
    @DisplayName("Le polymorphisme devrait fonctionner dans toute la hiérarchie")
    void polymorphismShouldWorkThroughoutHierarchy() {
        // Créer une chaîne d'instances qui traversent la hiérarchie
        ApplicationAdminUser adminUser = new ApplicationAdminUser(mockApplication);
        
        // Tester les conversions ascendantes (upcasting)
        ApplicationDataWriter asWriter = adminUser;
        ApplicationUser asUser = adminUser;
        ApplicationPersona asPersona = adminUser;
        
        // Vérifier que toutes les conversions maintiennent l'identité de l'objet
        assertSame(mockApplication, adminUser.application());
        assertSame(mockApplication, asWriter.application());
        assertSame(mockApplication, asUser.application());
        assertSame(mockApplication, asPersona.application());
        
        // Tester les conversions descendantes (downcasting) - uniquement là où c'est type-safe
        ApplicationDataWriter writerFromUser = (ApplicationDataWriter) asUser;
        ApplicationAdminUser adminFromWriter = (ApplicationAdminUser) asWriter;
        
        // Vérifier que les conversions descendantes préservent l'identité
        assertSame(adminUser, adminFromWriter);
        assertTrue(writerFromUser instanceof ApplicationDataWriter);
    }
}