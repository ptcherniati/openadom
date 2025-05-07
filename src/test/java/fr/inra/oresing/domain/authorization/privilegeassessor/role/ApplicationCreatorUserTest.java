package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException.NO_RIGHT_FOR_APPLICATION_CREATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests pour ApplicationCreatorUser")
@Tag("core.auth")
class ApplicationCreatorUserTest {

    /**
     * Fournit des cas de test pour la validation des noms d'applications par motif
     */
    static Stream<Arguments> providePatternTestCases() {
        return Stream.of(
                // Motif exact
                Arguments.of(
                        "Motif exact",
                        Set.of("app1", "app2", "app3"),
                        "app2",
                        true
                ),
                // Motif avec joker au début
                Arguments.of(
                        "Motif avec joker au début",
                        Set.of("test.*"),
                        "test-application",
                        true
                ),
                // Motif avec joker à la fin
                Arguments.of(
                        "Motif avec joker à la fin",
                        Set.of(".*-inra"),
                        "application-inra",
                        true
                ),
                // Motif complexe
                Arguments.of(
                        "Motif complexe",
                        Set.of("[a-z]+\\d{2}"),
                        "test42",
                        true
                ),
                // Nom non autorisé
                Arguments.of(
                        "Nom non autorisé",
                        Set.of("app\\d+", "test.*"),
                        "application",
                        false
                ),
                // Multiple motifs dont un correspond
                Arguments.of(
                        "Multiple motifs dont un correspond",
                        Set.of("wrong.*", ".*correct", "nope"),
                        "this-is-correct",
                        true
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providePatternTestCases")
    @DisplayName("La validation des noms d'applications devrait respecter les motifs")
    void shouldValidateApplicationNamesByPattern(
            String description,
            Set<String> patterns,
            String applicationName,
            boolean shouldBeValid) {
        
        // Arrangement
        ApplicationCreatorUser creatorUser = new ApplicationCreatorUser(patterns);
        
        if (shouldBeValid) {
            // Action et assertion - ne devrait pas lever d'exception
            assertDoesNotThrow(() -> creatorUser.canCreateApplication(applicationName),
                    description + " : " + applicationName + " devrait être autorisé");
        } else {
            // Action et assertion - devrait lever une exception
            NotApplicationCreatorRightsException exception = assertThrows(
                    NotApplicationCreatorRightsException.class,
                    () -> creatorUser.canCreateApplication(applicationName),
                    description + " : " + applicationName + " ne devrait pas être autorisé"
            );
            
            // Vérifier que l'exception contient les informations pertinentes
            assertThat(exception.getMessage(), containsString(NO_RIGHT_FOR_APPLICATION_CREATION));
        }
    }

    @Test
    @DisplayName("Une liste vide de motifs ne devrait autoriser aucun nom d'application")
    void emptyPatternsShouldNotAllowAnyName() {
        // Arrangement
        ApplicationCreatorUser creatorUser = new ApplicationCreatorUser(new HashSet<>());
        
        // Action et assertion
        NotApplicationCreatorRightsException exception = assertThrows(
                NotApplicationCreatorRightsException.class,
                () -> creatorUser.canCreateApplication("test-application"),
                "Un ensemble vide de motifs ne devrait pas autoriser la création d'application"
        );
        
        assertThat(exception.getMessage(), containsString(NotApplicationCreatorRightsException.NO_RIGHT_FOR_APPLICATION_CREATION));
    }

    @Test
    @DisplayName("Le rôle APPLICATION_CREATOR_ROLE devrait être correctement défini")
    void applicationCreatorRoleShouldBeCorrectlyDefined() {
        // Vérifier que le rôle est correctement défini comme constante
        assertEquals("applicationCreator", ApplicationCreatorUser.APPLICATION_CREATOR_ROLE,
                "La constante APPLICATION_CREATOR_ROLE devrait avoir la valeur 'applicationCreator'");
    }

    @Test
    @DisplayName("ApplicationCreatorUser devrait implémenter ApplicationCreator")
    void shouldImplementApplicationCreator() {
        // Arrangement
        ApplicationCreatorUser creatorUser = new ApplicationCreatorUser(Set.of("test.*"));
        
        // Vérifier l'implémentation de l'interface
        assertTrue(creatorUser instanceof ApplicationCreator,
                "ApplicationCreatorUser devrait implémenter ApplicationCreator");
        
        // Vérifier le cast vers l'interface
        ApplicationCreator asCreator = creatorUser;
        
        // Vérifier l'accès aux méthodes via l'interface
        assertDoesNotThrow(() -> asCreator.canCreateApplication("test-app"),
                "La méthode canCreateApplication devrait être accessible via l'interface");
    }

    @Test
    @DisplayName("L'interface sealed ApplicationCreator devrait restreindre les implémentations autorisées")
    void sealedInterfaceShouldRestrictAllowedImplementations() {
        // Vérifier que ApplicationCreator est une interface scellée
        assertTrue(ApplicationCreator.class.isSealed(), 
                "ApplicationCreator devrait être une interface sealed");
        
        // Obtenir les implémentations autorisées
        Class<?>[] permittedClasses = ApplicationCreator.class.getPermittedSubclasses();
        
        assertThat("L'interface sealed devrait avoir des sous-classes autorisées",
                permittedClasses, is(notNullValue()));
        
        // Vérifier que ApplicationCreatorUser est une implémentation autorisée
        assertThat("L'interface sealed devrait permettre ApplicationCreatorUser",
                permittedClasses, hasItemInArray(ApplicationCreatorUser.class));
    }
}