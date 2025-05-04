package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForDepositException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.apache.commons.collections.CollectionUtils;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Tests pour ApplicationDepositWriterUser")
class ApplicationDepositWriterUserTest {

    @Mock
    private Application mockApplication;

    @Mock
    private static AuthorizationParsed authorizationParsed;
    
    @Mock
    private FileOrUUID mockFileOrUUID;
    
    @Mock
    private BinaryFileDataset mockBinaryFileDataset;
    
    @Mock
    private AuthorizationParsed mockAuthorization;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockApplication.getName()).thenReturn("Test Application");
        when(mockFileOrUUID.binaryfiledataset()).thenReturn(mockBinaryFileDataset);
        when(mockBinaryFileDataset.getRequiredAuthorizations()).thenReturn(new HashMap<>());
    }

    /**
     * Fournit des scénarios de test pour les droits de dépôt
     */

    static Stream<Arguments> provideDepositScenarios() {
        authorizationParsed = mock(AuthorizationParsed.class);
        return Stream.of(
                // Cas 1: isData() retourne true, avec authorizations non vides
                Arguments.of(
                        "Donnée avec authorizations",
                        true,
                        true,
                        new ArrayList<>(List.of(authorizationParsed)),
                        true,
                        false
                ),
                // Cas 2: isData() retourne true, avec authorizations vides
                Arguments.of(
                        "Donnée sans authorizations",
                        true,
                        true,
                        new ArrayList<>(List.of(authorizationParsed)),
                        false,
                        true
                ),
                // Cas 3: isData() retourne false, avec authorizations valides
                Arguments.of(
                        "Pas une donnée, avec authorizations valides",
                        false,
                        true,
                        new ArrayList<>(List.of(authorizationParsed)),
                        true,
                        false
                ),
                // Cas 4: isData() retourne false, sans authorizations valides
                Arguments.of(
                        "Pas une donnée, sans authorizations valides",
                        false,
                        false,
                        new ArrayList<>(List.of(authorizationParsed)),
                        false,
                        true
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideDepositScenarios")
    @DisplayName("Les droits de dépôt devraient être correctement évalués")
    void shouldEvaluateDepositRightsCorrectly(
            String scenario,
            boolean isData,
            boolean authorizationsMatch,
            ArrayList<AuthorizationParsed> authorizations,
            boolean expectedResult,
            boolean shouldThrowException
    ) {
        // Arrangement
        String dataName = "testData";
        ApplicationDepositWriterUser depositWriter = spy(new ApplicationDepositWriterUser(
                mockApplication, 
                dataName, 
                authorizations
        ));
        
        doReturn(isData).when(depositWriter).isData();
        
        // Si ce n'est pas une donnée, configurer le comportement pour tester les authorizations
        if (!isData) {
            doReturn(authorizationsMatch).when(depositWriter).testRequiredAuthorizations(
                    any(), any()
            );
            doReturn(false).when(depositWriter).isDateInRangeAuthorized(any(), any());
        }

        if (shouldThrowException) {
            depositWriter.authorizations().clear();
            // Action et assertion - devrait lever une exception
            NotApplicationDataWriterForDepositException exception = assertThrows(
                    NotApplicationDataWriterForDepositException.class,
                    () -> depositWriter.hasRightForDeposit(mockFileOrUUID),
                    "Devrait lever une exception de droit de dépôt"
            );
            
            // Vérifier que l'exception contient les informations pertinentes
            assertThat(exception.getMessage(), 
                    containsString(NotApplicationDataWriterForDepositException.NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT));
            assertThat(exception.getApplicationName(), is("Test Application"));
            assertThat(exception.getDataName(), is(dataName));
        } else {
            // Action et assertion - ne devrait pas lever d'exception
            boolean result = depositWriter.hasRightForDeposit(mockFileOrUUID);
            assertThat("Le résultat de hasRightForDeposit devrait être correct", 
                    result, is(expectedResult));
        }
    }

    @Test
    @DisplayName("Les droits de suppression devraient toujours être refusés")
    void deleteShouldAlwaysBeDenied() {
        // Arrangement
        ApplicationDepositWriterUser depositWriter = new ApplicationDepositWriterUser(
                mockApplication, 
                "testData", 
                new ArrayList<>(List.of(mockAuthorization))
        );
        
        // Action
        boolean canDelete = depositWriter.canDelete(mockFileOrUUID);
        
        // Assertion
        assertFalse(canDelete, "Le droit de suppression devrait toujours être refusé");
    }

    @Test
    @DisplayName("Les droits de publication devraient toujours être accordés")
    void publishRightsShouldAlwaysBeGranted() {
        // Arrangement
        ApplicationDepositWriterUser depositWriter = new ApplicationDepositWriterUser(
                mockApplication, 
                "testData", 
                new ArrayList<>(List.of(mockAuthorization))
        );
        
        // Action
        boolean canPublish = depositWriter.hasRightForPublishOrUnPublish(mockFileOrUUID);
        
        // Assertion
        assertTrue(canPublish, "Le droit de publication devrait toujours être accordé");
    }

    @Test
    @DisplayName("L'exception retournée devrait être du bon type")
    void shouldReturnCorrectExceptionType() {
        // Arrangement
        ApplicationDepositWriterUser depositWriter = new ApplicationDepositWriterUser(
                mockApplication, 
                "testData", 
                new ArrayList<>()
        );
        
        // Action
        NotApplicationDataWriterForDepositException exception = depositWriter.getException();
        
        // Assertion
        assertNotNull(exception, "L'exception ne devrait pas être null");
        assertThat(exception.getMessage(), 
                containsString(NotApplicationDataWriterForDepositException.NO_RIGHT_FOR_USER_DATA_WRITER_FOR_DEPOSIT));
        assertThat(exception.getApplicationName(), is("Test Application"));
        assertThat(exception.getDataName(), is("testData"));
    }

    @Test
    @DisplayName("La classe devrait implémenter correctement l'interface ApplicationDataWriter")
    void shouldImplementApplicationDataWriterInterface() {
        // Arrangement
        ApplicationDepositWriterUser depositWriter = new ApplicationDepositWriterUser(
                mockApplication, 
                "testData", 
                new ArrayList<>()
        );
        
        // Vérifier l'implémentation de l'interface
        assertTrue(depositWriter instanceof ApplicationDataWriter,
                "Devrait implémenter ApplicationDataWriter");
        
        // Vérifier l'accès à travers l'interface
        ApplicationDataWriter asWriter = depositWriter;
        
        assertSame(mockApplication, asWriter.application(), 
                "L'accès à l'application devrait fonctionner via l'interface");
        assertEquals("testData", asWriter.dataName(), 
                "L'accès au nom de données devrait fonctionner via l'interface");
    }

    @Test
    @DisplayName("Le sealed interface ApplicationDataWriter devrait autoriser ApplicationDepositWriterUser")
    void sealedInterfaceShouldAllowApplicationDepositWriterUser() {
        // Vérifier les implémentations autorisées dans l'interface sealed
        Class<?>[] permittedClasses = ApplicationDataWriter.class.getPermittedSubclasses();

        assertThat("L'interface sealed devrait avoir des sous-classes autorisées",
                permittedClasses, is(notNullValue()));

        assertThat("L'interface sealed devrait permettre ApplicationDepositWriterUser",
                permittedClasses, hasItemInArray(ApplicationDepositWriterUser.class));
    }
}