package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core.auth")
@DisplayName("Tests pour ApplicationDataDelete")
class ApplicationDataDeleteTest {

    @Mock
    public static AuthorizationParsed authorizationParsed;
    @Mock
    private static Application mockApplication;
    @Mock
    private FileOrUUID mockFileOrUUID;

    @Mock
    private BinaryFileDataset mockBinaryFileDataset;

    /**
     * Fournit dApplicationDataDeletees implémentations de ApplicationDataDelete pour les tests paramétrés
     */
    static Stream<TestParameters> provideDeleteImplementations() {

        return Stream.of(
                new TestParameters("ApplicationAdminUser", () -> new ApplicationAdminUser(mockApplication), true, true, true),
                new TestParameters("ApplicationManagerUser", () -> new ApplicationManagerUser(mockApplication), true, true, true),
                new TestParameters("ApplicatioDeleteUser", () -> new ApplicationDeleteUser(mockApplication, "testData", new ArrayList<>(List.of(authorizationParsed))), true, false, false),
                new TestParameters("ApplicatioDeleteUserWithRepository",
                        () -> {
                            final Submission submission = mock(Submission.class);
                            when(mockApplication.findSubmission(anyString())).thenReturn(Optional.of(submission));
                            return new ApplicationDeleteUser(mockApplication, "testData", new ArrayList<>(List.of(authorizationParsed)));
                        }, true, true, false)
        );
    }

    /**
     * Fournit des cas de test pour vérifier le comportement polymorphique
     */
    static Stream<ParameterTest2> provideInterfaceImplementations() {

        return Stream.of(
                new ParameterTest2("ApplicationManager via AdminUser",
                        new ApplicationAdminUser(mockApplication),

                        new Class[]{
                                ApplicationManager.class,
                                ApplicationDataWriter.class,
                                ApplicationDataDelete.class
                        }),

                new ParameterTest2("ApplicationManager via ManagerUser",
                        new ApplicationManagerUser(mockApplication),
                        new Class[]{
                                ApplicationManager.class,
                                ApplicationDataWriter.class,
                                ApplicationDataDelete.class
                        }),
                new ParameterTest2("ApplicationDataDelete via PublishWriterUser",
                        new ApplicationDeleteUser(
                                mockApplication,
                                "testData",
                                new ArrayList<>(List.of(authorizationParsed))
                        ),
                        new Class[]{ApplicationDataDelete.class}
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
        when(authorizationParsed.operationTypes()).thenReturn(Set.of(OperationType.delete, OperationType.extraction));
    }

    record TestRight(
            String implName,
            ApplicationDataDelete writer,
            boolean expectedCanDelete,
            boolean expectedCanPublish,
            boolean expectedCanDeposit) {
    }

    @ParameterizedTest(name = "{0} - Vérification des droits")
    @MethodSource("provideDeleteImplementations")
    @DisplayName("Les droits d'accès devraient être correctement définis pour chaque implémentation")
    void shouldHaveCorrectAccessRights(TestParameters testParameters) {

        // Vérifier les droits de suppression
        assertThat(testParameters.implName() + " - Droit de suppression",
                testParameters.writer().get().canDelete(mockFileOrUUID),
                is(testParameters.expectedCanDelete()));

        // Vérifier les droits de publication
        assertThat(testParameters.implName() + " - Droit de publication",
                testParameters.writer().get().hasRightForPublishOrUnPublish(mockFileOrUUID),
                is(testParameters.expectedCanPublish()));

        // Vérifier les droits de dépôt
        assertThat(testParameters.implName() + " - Droit de dépôt",
                testParameters.writer().get().hasRightForDeposit(mockFileOrUUID),
                is(testParameters.expectedCanDeposit()));

        // Vérifier l'accès à l'application
        assertThat(testParameters.implName() + " - Référence à l'application",
                testParameters.writer().get().application(),
                is(notNullValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideInterfaceImplementations")
    @DisplayName("Le polymorphisme devrait fonctionner pour toutes les interfaces")
    void shouldSupportPolymorphism(ParameterTest2 parameterTest2) {

        // Vérifier que l'objet implémente toutes les interfaces attendues
        for (Class<?> expectedInterface : parameterTest2.expectedInterfaces()) {
            assertThat(parameterTest2.description() + " devrait implémenter " + expectedInterface.getSimpleName(),
                    parameterTest2.instance(), is(instanceOf(expectedInterface)));
        }

        // Vérifier l'accès à l'application selon l'interface
        if (parameterTest2.instance() instanceof ApplicationManager manager) {
            assertThat(parameterTest2.description() + " - Accès via ApplicationManager",
                    manager.application(), is(notNullValue()));
        }

        if (parameterTest2.instance() instanceof ApplicationDataDelete writer) {
            assertThat(parameterTest2.description() + " - Accès via ApplicationDataDelete",
                    writer.application(), is(notNullValue()));
        }

        if (parameterTest2.instance() instanceof ApplicationDataDelete deleter) {
            assertThat(parameterTest2.description() + " - Accès via ApplicationDataDelete",
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

        assertThat("AdminUser devrait implémenter ApplicationDataDelete",
                adminUser, instanceOf(ApplicationDataDelete.class));

        assertThat("AdminUser devrait implémenter ApplicationDataDelete",
                adminUser, instanceOf(ApplicationDataDelete.class));

        // Tester le cast vers différentes interfaces
        ApplicationManager asManager = adminUser;
        ApplicationDataWriter asWriter = adminUser;
        ApplicationDataDelete asDeleter = adminUser;

        // Vérifier que l'application est accessible via toutes les interfaces
        assertThat("Application accessible via ApplicationManager",
                asManager.application(), sameInstance(mockApplication));

        assertThat("Application accessible via ApplicationDataDelete",
                asWriter.application(), sameInstance(mockApplication));

        assertThat("Application accessible via ApplicationDataDelete",
                asDeleter.application(), sameInstance(mockApplication));
    }

    @Test
    @DisplayName("Le sealed interface devrait restreindre les implémentations autorisées")
    void sealedInterfaceShouldRestrictAllowedImplementations() {
        // Vérifier les implémentations autorisées dans l'interface sealed
        Class<?>[] permittedClasses = ApplicationDataDelete.class.getPermittedSubclasses();

        assertThat("L'interface sealed devrait avoir des sous-classes autorisées",
                permittedClasses, is(notNullValue()));

        assertThat("L'interface sealed devrait permettre ApplicationAdminUser",
                permittedClasses, hasItemInArray(ApplicationAdminUser.class));

        assertThat("L'interface sealed devrait permettre ApplicationManagerUser",
                permittedClasses, hasItemInArray(ApplicationManagerUser.class));

        assertThat("L'interface sealed devrait permettre ApplicationDataDelete",
                permittedClasses, hasItemInArray(ApplicationAdminUser.class));

        assertThat("L'interface sealed devrait permettre ApplicationPublishWriterUser",
                permittedClasses, hasItemInArray(ApplicationDeleteUser.class));
    }

    private static record TestParameters(String implName, Supplier<ApplicationDataDelete> writer,
                                         boolean expectedCanDelete,
                                         boolean expectedCanPublish, boolean expectedCanDeposit) {
    }

    private static record ParameterTest2(String description, Object instance, Class<?>[] expectedInterfaces) {
    }
}