package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

/**
 * Factory pour créer des instances de rôles pour les tests
 */
public class RoleFactory {

    /**
     * Crée une application mock pour les tests
     */
    public static Application createMockApplication() {
        Application app = Mockito.mock(Application.class);
        when(app.getId()).thenReturn(UUID.randomUUID());
        when(app.getName()).thenReturn("Test Application");
        when(app.toString()).thenReturn("Application(Test Application)");
        return app;
    }

    /**
     * Génère un ApplicationDataReader avec une application mock
     */
    public static ApplicationDataReader createDataReader() {
        return new ApplicationDataReader(createMockApplication());
    }

    /**
     * Génère un ApplicationAdminUser avec une application mock
     */
    public static ApplicationAdminUser createAdminUser() {
        return new ApplicationAdminUser(createMockApplication());
    }

    /**
     * Génère un ApplicationManagerUser avec une application mock
     */
    public static ApplicationManagerUser createManagerUser() {
        return new ApplicationManagerUser(createMockApplication());
    }

    /**
     * Génère un ApplicationDeleteUser avec une application mock
     */
    public static ApplicationDeleteUser createDeleteUser() {
        return new ApplicationDeleteUser(createMockApplication(), "testData", new ArrayList<>());
    }

    /**
     * Génère un ApplicationPublishWriterUser avec une application mock
     */
    public static ApplicationPublishWriterUser createPublishWriterUser() {
        return new ApplicationPublishWriterUser(createMockApplication(), "testData", new ArrayList<>());
    }

    /**
     * Génère un stream de toutes les implémentations d'ApplicationUser
     */
    public static Stream<ApplicationUser> createAllApplicationUsers() {
        return Stream.of(
                createDataReader(),
                createAdminUser(),
                createManagerUser(),
                createDeleteUser(),
                createPublishWriterUser()
        );
    }

    /**
     * Génère un stream de toutes les implémentations d'ApplicationDataWriter
     */
    public static Stream<ApplicationDataWriter> createAllApplicationDataWriters() {
        return Stream.of(
                createAdminUser(),
                createManagerUser(),
                createDeleteUser(),
                createPublishWriterUser()
        );
    }
}