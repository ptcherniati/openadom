package fr.inra.oresing.domain.application.configuration.migration.action;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;
import fr.inra.oresing.domain.port.AuthenticationPort;
import fr.inra.oresing.domain.port.MigrationApplicationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link SaveConfigurationAction} et {@link CreateIndexesForDataAction}.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("MigrationAction records — unit tests")
class MigrationActionTest {

    @Mock private MigrationApplicationPort migrationApplicationPort;
    @Mock private AuthenticationPort authenticationPort;
    @Mock private Application newApplication;

    private MigrationContext ctx(String appName) {
        return new MigrationContext(
                migrationApplicationPort,
                authenticationPort,
                appName,
                null,
                newApplication,
                null,
                null);
    }

    // ─── SaveConfigurationAction ──────────────────────────────────────────────

    @Test
    @DisplayName("SaveConfigurationAction : phase = CORE")
    void saveConfigPhase() {
        SaveConfigurationAction action = new SaveConfigurationAction("save-1", newApplication);
        assertThat(action.phase()).isEqualTo(ActionPhase.CORE);
    }

    @Test
    @DisplayName("SaveConfigurationAction : requiresUserConfirmation = false")
    void saveConfigNoUserConfirmation() {
        SaveConfigurationAction action = new SaveConfigurationAction("save-1", newApplication);
        assertThat(action.requiresUserConfirmation()).isFalse();
    }

    @Test
    @DisplayName("SaveConfigurationAction : isSingleton = true")
    void saveConfigSingleton() {
        SaveConfigurationAction action = new SaveConfigurationAction("save-1", newApplication);
        assertThat(action.isSingleton()).isTrue();
    }

    @Test
    @DisplayName("SaveConfigurationAction : description non vide")
    void saveConfigDescription() {
        SaveConfigurationAction action = new SaveConfigurationAction("save-1", newApplication);
        assertThat(action.description()).isNotBlank();
    }

    @Test
    @DisplayName("SaveConfigurationAction.execute() → appelle migrationApplicationPort.storeApplication")
    void saveConfigExecute() {
        fr.inra.oresing.domain.application.configuration.Configuration config =
                mock(fr.inra.oresing.domain.application.configuration.Configuration.class);
        when(newApplication.getConfiguration()).thenReturn(config);

        SaveConfigurationAction action = new SaveConfigurationAction("save-1", newApplication);
        action.execute(ctx("myapp"));

        verify(migrationApplicationPort).storeApplication(newApplication);
    }

    // ─── CreateIndexesForDataAction ───────────────────────────────────────────

    @Test
    @DisplayName("CreateIndexesForDataAction : phase = POST")
    void createIndexesPhase() {
        CreateIndexesForDataAction action = new CreateIndexesForDataAction(
                "idx-1", "myData", null);
        assertThat(action.phase()).isEqualTo(ActionPhase.POST);
    }

    @Test
    @DisplayName("CreateIndexesForDataAction : requiresUserConfirmation = false")
    void createIndexesNoUserConfirmation() {
        CreateIndexesForDataAction action = new CreateIndexesForDataAction(
                "idx-1", "myData", null);
        assertThat(action.requiresUserConfirmation()).isFalse();
    }

    @Test
    @DisplayName("CreateIndexesForDataAction : description contient le nom des données")
    void createIndexesDescription() {
        CreateIndexesForDataAction action = new CreateIndexesForDataAction(
                "idx-1", "myData", null);
        assertThat(action.description()).contains("myData");
    }

    @Test
    @DisplayName("CreateIndexesForDataAction.execute() → activate admin, update indexes, restore role")
    void createIndexesExecute() {
        CreateIndexesForDataAction action = new CreateIndexesForDataAction(
                "idx-1", "myData", null);
        action.execute(ctx("myapp"));

        verify(authenticationPort).activateAdminRole();
        verify(migrationApplicationPort).updateAuthorizationIndexes(newApplication);
        verify(authenticationPort).setRoleForClient();
    }
}
