package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;

@Tag("core.auth")
@DisplayName("Tests pour ApplicationDataWriter")
class ApplicationDataWriterTest {

    @Mock
    public static AuthorizationParsed authorizationParsed;
    @Mock
    private static Application mockApplication;
    @Mock
    private FileOrUUID mockFileOrUUID;

    @Mock
    private BinaryFileDataset mockBinaryFileDataset;

    /**
     * Fournit des implémentations de ApplicationDataWriter pour les tests paramétrés
     */
    static Stream<CorrectAccessRightsParameters> provideWriterImplementations() {

        return Stream.of(
                new CorrectAccessRightsParameters("ApplicationAdminUser", new ApplicationAdminUser(mockApplication), true, true, true),
                new CorrectAccessRightsParameters("ApplicationManagerUser", new ApplicationManagerUser(mockApplication), true, true, true),
                new CorrectAccessRightsParameters("ApplicationPublishWriterUser", new ApplicationPublishWriterUser(mockApplication, "testData", new ArrayList<>(List.of(authorizationParsed))), true, true, true)
        );
    }

    /**
     * Fournit des cas de test pour vérifier le comportement polymorphique
     */
    static Stream<Arguments> provideInterfaceImplementations() {

        return Stream.of(
                Arguments.of("ApplicationManager via AdminUser",
                        new ApplicationAdminUser(mockApplication),

                        new Class[]{
                                ApplicationManager.class,
                                ApplicationDataWriter.class,
                                ApplicationDataDelete.class
                        }),

                Arguments.of("ApplicationManager via ManagerUser",
                        new ApplicationManagerUser(mockApplication),
                        new Class[]{
                                ApplicationManager.class,
                                ApplicationDataWriter.class,
                                ApplicationDataDelete.class
                        }),
                Arguments.of("ApplicationDataWriter via PublishWriterUser",
                        new ApplicationPublishWriterUser(
                                mockApplication,
                                "testData",
                                new ArrayList<>(List.of(authorizationParsed))
                        ),
                        new Class[]{ApplicationDataWriter.class}
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

    @ParameterizedTest(name = "{0} - Vérification des droits")
    @MethodSource("provideWriterImplementations")
    @DisplayName("Les droits d'accès devraient être correctement définis pour chaque implémentation")
    void shouldHaveCorrectAccessRights(CorrectAccessRightsParameters correctAccessRightsParameters) {

        // Vérifier les droits de suppression
        assertThat(correctAccessRightsParameters.implName() + " - Droit de suppression",
                correctAccessRightsParameters.writer().canDelete(mockFileOrUUID),
                is(correctAccessRightsParameters.expectedCanDelete()));

        // Vérifier les droits de publication
        assertThat(correctAccessRightsParameters.implName() + " - Droit de publication",
                correctAccessRightsParameters.writer().hasRightForPublishOrUnPublish(mockFileOrUUID),
                is(correctAccessRightsParameters.expectedCanPublish()));

        // Vérifier les droits de dépôt
        assertThat(correctAccessRightsParameters.implName() + " - Droit de dépôt",
                correctAccessRightsParameters.writer().hasRightForDeposit(mockFileOrUUID),
                is(correctAccessRightsParameters.expectedCanDeposit()));

        // Vérifier l'accès à l'application
        assertThat(correctAccessRightsParameters.implName() + " - Référence à l'application",
                correctAccessRightsParameters.writer().application(),
                is(notNullValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInterfaceImplementations")
    @DisplayName("Le polymorphisme devrait fonctionner pour toutes les interfaces")
    void shouldSupportPolymorphism(
            String description,
            Object instance,
            Class<?>[] expectedInterfaces) {

        // Vérifier que l'objet implémente toutes les interfaces attendues
        for (Class<?> expectedInterface : expectedInterfaces) {
            assertThat(description + " devrait implémenter " + expectedInterface.getSimpleName(),
                    instance, is(instanceOf(expectedInterface)));
        }

        // Vérifier l'accès à l'application selon l'interface
        if (instance instanceof ApplicationManager manager) {
            assertThat(description + " - Accès via ApplicationManager",
                    manager.application(), is(notNullValue()));
        }

        if (instance instanceof ApplicationDataWriter writer) {
            assertThat(description + " - Accès via ApplicationDataWriter",
                    writer.application(), is(notNullValue()));
        }

        if (instance instanceof ApplicationDataDelete deleter) {
            assertThat(description + " - Accès via ApplicationDataDelete",
                    deleter.application(), is(notNullValue()));
        }
    }

    @Test
    @DisplayName("ApplicationAdminUser devrait implémenter correctement les interfaces multiples")
    void applicationAdminUserShouldImplementMultipleInterfaces() {
        // Arrangement
        ApplicationAdminUser adminUser = new ApplicationAdminUser(mockApplication);

        // Action & Assert - Vérifier toutes les interfaces implémentées
        assertThat("AdminUser devrait implémenter ApplicationManager",
                adminUser, instanceOf(ApplicationManager.class));

        assertThat("AdminUser devrait implémenter ApplicationDataWriter",
                adminUser, instanceOf(ApplicationDataWriter.class));

        assertThat("AdminUser devrait implémenter ApplicationDataDelete",
                adminUser, instanceOf(ApplicationDataDelete.class));

        // Tester le cast vers différentes interfaces
        ApplicationManager asManager = adminUser;
        ApplicationDataWriter asWriter = adminUser;
        ApplicationDataDelete asDeleter = adminUser;

        // Vérifier que l'application est accessible via toutes les interfaces
        assertThat("Application accessible via ApplicationManager",
                asManager.application(), sameInstance(mockApplication));

        assertThat("Application accessible via ApplicationDataWriter",
                asWriter.application(), sameInstance(mockApplication));

        assertThat("Application accessible via ApplicationDataDelete",
                asDeleter.application(), sameInstance(mockApplication));
    }

    @Test
    @DisplayName("Le sealed interface devrait restreindre les implémentations autorisées")
    void sealedInterfaceShouldRestrictAllowedImplementations() {
        // Vérifier les implémentations autorisées dans l'interface sealed
        Class<?>[] permittedClasses = ApplicationDataWriter.class.getPermittedSubclasses();

        assertThat("L'interface sealed devrait avoir des sous-classes autorisées",
                permittedClasses, is(notNullValue()));

        assertThat("L'interface sealed devrait permettre ApplicationAdminUser",
                permittedClasses, hasItemInArray(ApplicationAdminUser.class));

        assertThat("L'interface sealed devrait permettre ApplicationManagerUser",
                permittedClasses, hasItemInArray(ApplicationManagerUser.class));

        assertThat("L'interface sealed devrait permettre ApplicationDataDelete",
                permittedClasses, hasItemInArray(ApplicationDataDelete.class));

        assertThat("L'interface sealed devrait permettre ApplicationPublishWriterUser",
                permittedClasses, hasItemInArray(ApplicationPublishWriterUser.class));
    }

    private static record CorrectAccessRightsParameters(String implName, ApplicationDataWriter writer,
                                                        boolean expectedCanDelete, boolean expectedCanPublish,
                                                        boolean expectedCanDeposit) {
    }
}