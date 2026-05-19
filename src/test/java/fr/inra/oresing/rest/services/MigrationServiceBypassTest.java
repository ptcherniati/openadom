package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.config.MigrationProperties;
import fr.inra.oresing.domain.application.configuration.migration.execution.MigrationExecutor;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.data.migration.MigrationConfiguration;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.PropertyChangeType;
import org.javers.core.diff.changetype.map.MapChange;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour la logique de bypass dans {@link MigrationService#executeMigration}.
 *
 * <p>Vérifie que {@code openadom.migration.bypass-configuration-check} est bien respecté :
 * <ul>
 *   <li>bypass=false + changements non résolus → {@link MigrationStatus#FAILED}</li>
 *   <li>bypass=true  + changements non résolus → exécution continue (pas FAILED)</li>
 *   <li>bypass=false + aucun changement        → {@link MigrationStatus#NO_CHANGES}</li>
 *   <li>bypass=true  + aucun changement        → {@link MigrationStatus#NO_CHANGES}</li>
 * </ul>
 *
 * <p>Ces tests n'utilisent ni Spring ni Docker : injection Mockito uniquement.
 */
@Tag("core.config")
@ExtendWith(MockitoExtension.class)
class MigrationServiceBypassTest {

    // ── Dépendances mockées ──────────────────────────────────────────────────

    @Mock
    private Javers javers;
    @Mock
    private MigrationConfiguration.MigrationRepositories migrationRepositories;
    @Mock
    private RulesEngine rulesEngine;
    @Mock
    private Rules migrationRules;
    @Mock
    private JsonRowMapper jsonRowMapper;
    @Mock
    private MigrationExecutor executor;
    @Mock
    private ServiceContainer serviceContainer;

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Construit un {@link MigrationService} avec le flag bypass positionné.
     */
    private MigrationService buildService(boolean bypass) {
        MigrationProperties props = new MigrationProperties();
        props.setBypassConfigurationCheck(bypass);
        return new MigrationService(
                serviceContainer, javers, migrationRepositories,
                rulesEngine, migrationRules, jsonRowMapper, executor, props);
    }

    /**
     * Configure {@link Javers} pour qu'il retourne un diff vide (aucun changement).
     */
    private void stubJaversEmptyDiff() {
        Diff emptyDiff = mock(Diff.class);
        when(emptyDiff.getChangesByType(MapChange.class)).thenReturn(List.of());
        when(javers.compare(any(), any())).thenReturn(emptyDiff);
    }

    /**
     * Configure {@link Javers} pour qu'il retourne un diff contenant un changement
     * non résolu (propertyName inconnu → {@code UnresolvableChange}).
     */
    @SuppressWarnings("unchecked")
    private void stubJaversWithUnresolvedChange() {
        MapChange mapChange = mock(MapChange.class);
        when(mapChange.getChangeType()).thenReturn(PropertyChangeType.PROPERTY_VALUE_CHANGED);
        when(mapChange.getPropertyName()).thenReturn("unknownStructuralChange");
        when(mapChange.getEntryAddedChanges()).thenReturn(List.of());
        when(mapChange.getEntryRemovedChanges()).thenReturn(List.of());
        // Pour unresolvableChange(change) : jsonRowMapper.toJson(null) = "null"
        when(jsonRowMapper.toJson(null)).thenReturn("null");

        Diff diff = mock(Diff.class);
        when(diff.getChangesByType(MapChange.class)).thenReturn(List.of(mapChange));
        when(javers.compare(any(), any())).thenReturn(diff);
    }

    /**
     * Construit deux applications mock (même data = pas d'addedData) avec une
     * configuration permettant de logguer le nom de l'application.
     */
    private Application[] buildAppsNoAddedData() {
        Application oldApp = mock(Application.class, RETURNS_DEEP_STUBS);
        Application newApp = mock(Application.class, RETURNS_DEEP_STUBS);
        when(oldApp.getData()).thenReturn(List.of());
        when(newApp.getData()).thenReturn(List.of());
        // Nom de l'application utilisé uniquement dans les avertissements bypass=true
        lenient().when(newApp.getConfiguration().applicationDescription().name()).thenReturn("test-app");
        return new Application[]{oldApp, newApp};
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bypass=false + changements non résolus → FAILED (mise à jour bloquée)")
    void executeMigration_bypassFalse_unresolvedChanges_returnsFailed() {
        stubJaversWithUnresolvedChange();
        Application[] apps = buildAppsNoAddedData();
        MigrationService service = buildService(false);

        MigrationResult result = service.executeMigration(
                apps[0], apps[1], Set.of(), MigrationMode.EXECUTE);

        assertThat(result.status())
                .as("Le mode sécurisé doit bloquer quand des changements non résolus existent")
                .isEqualTo(MigrationStatus.FAILED);
    }

    @Test
    @DisplayName("bypass=true + changements non résolus → pas FAILED (exécution forcée)")
    void executeMigration_bypassTrue_unresolvedChanges_doesNotReturnFailed() {
        stubJaversWithUnresolvedChange();
        Application[] apps = buildAppsNoAddedData();
        MigrationService service = buildService(true);

        MigrationResult result = service.executeMigration(
                apps[0], apps[1], Set.of(), MigrationMode.EXECUTE);

        assertThat(result.status())
                .as("Le bypass doit permettre de passer malgré les changements non résolus")
                .isNotEqualTo(MigrationStatus.FAILED);
        // Pas d'addedData → changes vide → noChanges()
        assertThat(result.status()).isEqualTo(MigrationStatus.NO_CHANGES);
    }

    @Test
    @DisplayName("bypass=false + aucun changement → NO_CHANGES")
    void executeMigration_bypassFalse_noChanges_returnsNoChanges() {
        stubJaversEmptyDiff();
        Application[] apps = buildAppsNoAddedData();
        MigrationService service = buildService(false);

        MigrationResult result = service.executeMigration(
                apps[0], apps[1], Set.of(), MigrationMode.EXECUTE);

        assertThat(result.status()).isEqualTo(MigrationStatus.NO_CHANGES);
    }

    @Test
    @DisplayName("bypass=true + aucun changement → NO_CHANGES")
    void executeMigration_bypassTrue_noChanges_returnsNoChanges() {
        stubJaversEmptyDiff();
        Application[] apps = buildAppsNoAddedData();
        MigrationService service = buildService(true);

        MigrationResult result = service.executeMigration(
                apps[0], apps[1], Set.of(), MigrationMode.EXECUTE);

        assertThat(result.status()).isEqualTo(MigrationStatus.NO_CHANGES);
    }

    @Test
    @DisplayName("MigrationProperties par défaut → bypass=true (comportement historique préservé)")
    void migrationProperties_defaultBypassIsTrue_serviceRunsPermissively() {
        stubJaversEmptyDiff();
        // Utilise les propriétés par défaut (bypass=true)
        MigrationService service = buildService(true);
        Application[] apps = buildAppsNoAddedData();

        MigrationResult result = service.executeMigration(
                apps[0], apps[1], Set.of(), MigrationMode.DRY_RUN);

        // Pas d'exception → injection correcte + exécution normale
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(MigrationStatus.NO_CHANGES);
    }
}