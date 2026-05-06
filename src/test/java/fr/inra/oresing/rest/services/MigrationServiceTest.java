package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.migration.MigrationProperties;
import fr.inra.oresing.domain.application.configuration.migration.execution.MigrationExecutor;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.data.migration.MigrationConfiguration;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.PropertyChangeType;
import org.javers.core.diff.changetype.map.MapChange;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour MigrationService (sans Spring / sans Docker).
 * Toutes les dépendances sont mockées via Mockito.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("MigrationService – tests unitaires")
class MigrationServiceTest {

    @Mock
    private ServiceContainer serviceContainer;
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
    private OreSiRepository innerRepository;
    @Mock
    private OreSiRepository.RepositoryForApplication repoForApp;
    @Mock
    private fr.inra.oresing.domain.application.configuration.migration.context.SchemaInfo schemaInfo;
    @Mock
    private fr.inra.oresing.domain.application.configuration.migration.context.DataInfo dataInfo;

    private MigrationProperties migrationProperties;
    private MigrationService service;

    @BeforeEach
    void setUp() {
        migrationProperties = new MigrationProperties();
        service = new MigrationService(
                serviceContainer, javers, migrationRepositories,
                rulesEngine, migrationRules, jsonRowMapper,
                executor, migrationProperties
        );
    }

    // ---- helpers ----

    private Application buildApp(UUID id, String name, List<String> data) {
        Application app = new Application();
        app.setId(id);
        app.setName(name);
        app.setData(data);
        return app;
    }

    private Diff buildEmptyDiff() {
        Diff diff = mock(Diff.class);
        doReturn(List.of()).when(diff).getChangesByType(MapChange.class);
        return diff;
    }

    // =========================================================================
    //  executeMigration – cas sans changements de données ni de config
    // =========================================================================

    @Nested
    @DisplayName("executeMigration() – aucun changement")
    class NoChangesTest {

        @Test
        @DisplayName("retourne MigrationResult.noChanges() quand oldData == newData et aucune diff Javers")
        void returnsNoChangesWhenNoDiff() {
            UUID appId = UUID.randomUUID();
            Configuration config = mock(Configuration.class);
            Diff diff = buildEmptyDiff();

            Application oldApp = buildApp(appId, "myApp", List.of("ref1"));
            oldApp.setConfiguration(config);

            Application newApp = buildApp(UUID.randomUUID(), "myApp", List.of("ref1")); // même liste
            newApp.setConfiguration(config);

            when(javers.compare(any(), any())).thenReturn(diff);

            MigrationResult result = service.executeMigration(
                    oldApp, newApp, Set.of(), MigrationMode.DRY_RUN);

            assertThat(result.status()).isEqualTo(MigrationStatus.NO_CHANGES);
        }
    }

    // =========================================================================
    //  executeMigration – mode BYPASS (bypassConfigurationCheck = true, défaut)
    // =========================================================================

    @Nested
    @DisplayName("executeMigration() – bypass activé")
    class BypassEnabledTest {

        @Test
        @DisplayName("accepte un nouveau datatype sans bloquer (bypass = true)")
        void acceptsNewDatatypeWithBypass() {
            // bypass = true par défaut dans MigrationProperties

            UUID appId = UUID.randomUUID();
            Configuration oldConfig = mock(Configuration.class);
            Configuration newConfig = mock(Configuration.class);
            Diff diff = buildEmptyDiff();

            // La nouvelle config ajoute "temp" — pas encore dans oldData
            Application oldApp = buildApp(appId, "myApp", List.of());
            oldApp.setConfiguration(oldConfig);
            mockConfigForDataAdded(oldConfig, newConfig, "temp");

            Application newApp = buildApp(UUID.randomUUID(), "myApp", List.of("temp"));
            newApp.setConfiguration(newConfig);

            // Javers : aucune diff sur les configs (le nouveau datatype est traité en amont)
            when(javers.compare(any(), any())).thenReturn(diff);

            // buildContext() appelle migrationRepositories.repository().getRepository(appName)
            when(migrationRepositories.repository()).thenReturn(innerRepository);
            when(innerRepository.getRepository(any(String.class))).thenReturn(repoForApp);
            when(repoForApp.getSchemaInfo()).thenReturn(schemaInfo);
            when(repoForApp.getDataInfo()).thenReturn(dataInfo);

            MigrationResult executorResult = MigrationResult.success(
                    new fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan(
                            MigrationMode.EXECUTE, Set.of(),
                            MigrationStatus.PENDING),
                    java.time.Duration.ofMillis(1));
            when(executor.execute(any(), any())).thenReturn(executorResult);

            MigrationResult result = service.executeMigration(
                    oldApp, newApp, Set.of(), MigrationMode.EXECUTE);

            assertThat(result.status()).isEqualTo(MigrationStatus.EXECUTED);
        }

        @Test
        @DisplayName("retourne noChanges quand addedData est vide et aucune diff Javers")
        void returnsNoChangesWhenNothingAdded() {
            UUID appId = UUID.randomUUID();
            Configuration config = mock(Configuration.class);
            Diff diff = buildEmptyDiff();

            Application oldApp = buildApp(appId, "myApp", List.of("existing"));
            oldApp.setConfiguration(config);
            Application newApp = buildApp(UUID.randomUUID(), "myApp", List.of("existing"));
            newApp.setConfiguration(config);

            when(javers.compare(any(), any())).thenReturn(diff);

            MigrationResult result = service.executeMigration(
                    oldApp, newApp, Set.of(), MigrationMode.DRY_RUN);

            assertThat(result.status()).isEqualTo(MigrationStatus.NO_CHANGES);
        }
    }

    // =========================================================================
    //  executeMigration – mode SÉCURISÉ (bypassConfigurationCheck = false)
    // =========================================================================

    @Nested
    @DisplayName("executeMigration() – bypass désactivé")
    class BypassDisabledTest {

        @BeforeEach
        void disableBypass() {
            migrationProperties.setBypassConfigurationCheck(false);
        }

        @Test
        @DisplayName("retourne MigrationResult.onError quand Javers détecte des changements non résolus")
        void returnsErrorWhenUnresolvableJaversChange() {
            UUID appId = UUID.randomUUID();
            Configuration oldConfig = mock(Configuration.class);
            Configuration newConfig = mock(Configuration.class);

            Application oldApp = buildApp(appId, "myApp", List.of("ref1"));
            oldApp.setConfiguration(oldConfig);
            Application newApp = buildApp(UUID.randomUUID(), "myApp", List.of("ref1"));
            newApp.setConfiguration(newConfig);

            // Simuler un MapChange PROPERTY_VALUE_CHANGED avec propertyName = "unknown"
            // qui chutera dans le cas default → UnresolvableChange
            MapChange mapChange = mock(MapChange.class);
            when(mapChange.getChangeType())
                    .thenReturn(PropertyChangeType.PROPERTY_VALUE_CHANGED);
            when(mapChange.getPropertyName()).thenReturn("someUnknownProperty");
            when(mapChange.getEntryAddedChanges()).thenReturn(List.of());
            when(mapChange.getEntryRemovedChanges()).thenReturn(List.of());
            when(mapChange.getLeft()).thenReturn(null);
            when(mapChange.getRight()).thenReturn(null);
            when(jsonRowMapper.toJson(null)).thenReturn("null");

            Diff diff = mock(Diff.class);
            doReturn(List.of(mapChange)).when(diff).getChangesByType(MapChange.class);
            when(javers.compare(any(), any())).thenReturn(diff);

            MigrationResult result = service.executeMigration(
                    oldApp, newApp, Set.of(), MigrationMode.DRY_RUN);

            assertThat(result.status()).isEqualTo(MigrationStatus.FAILED);
        }

        @Test
        @DisplayName("retourne noChanges quand aucune donnée ajoutée et aucune diff Javers")
        void returnsNoChangesWithoutBypass() {
            UUID appId = UUID.randomUUID();
            Configuration config = mock(Configuration.class);
            Diff diff = buildEmptyDiff();

            Application oldApp = buildApp(appId, "myApp", List.of("ref1"));
            oldApp.setConfiguration(config);
            Application newApp = buildApp(UUID.randomUUID(), "myApp", List.of("ref1"));
            newApp.setConfiguration(config);

            when(javers.compare(any(), any())).thenReturn(diff);

            MigrationResult result = service.executeMigration(
                    oldApp, newApp, Set.of(), MigrationMode.DRY_RUN);

            assertThat(result.status()).isEqualTo(MigrationStatus.NO_CHANGES);
        }
    }

    // =========================================================================
    //  MigrationResult factories
    // =========================================================================

    @Nested
    @DisplayName("MigrationResult – factories statiques")
    class MigrationResultFactoriesTest {

        @Test
        @DisplayName("noChanges() retourne le statut NO_CHANGES")
        void noChangesReturnsCorrectStatus() {
            MigrationResult r = MigrationResult.noChanges();
            assertThat(r.status()).isEqualTo(MigrationStatus.NO_CHANGES);
            assertThat(r.executedActionsCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("blocked() retourne le statut REQUIRES_CONFIRMATION")
        void blockedReturnsRequiresConfirmation() {
            MigrationResult r = MigrationResult.blocked(List.of());
            assertThat(r.status()).isEqualTo(MigrationStatus.REQUIRES_CONFIRMATION);
        }

        @Test
        @DisplayName("isSuccess() retourne false pour NO_CHANGES")
        void isSuccessReturnsFalseForNoChanges() {
            MigrationResult r = MigrationResult.noChanges();
            assertThat(r.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("dryRunSuccess() retourne le statut APPROVED")
        void dryRunSuccessReturnsApproved() {
            fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan plan =
                    new fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan(
                            MigrationMode.DRY_RUN, Set.of(), MigrationStatus.PENDING);
            MigrationResult r = MigrationResult.dryRunSuccess(plan);
            assertThat(r.status()).isEqualTo(MigrationStatus.APPROVED);
        }
    }

    // =========================================================================
    //  MigrationPlan – comportement
    // =========================================================================

    @Nested
    @DisplayName("MigrationPlan – comportement")
    class MigrationPlanTest {

        @Test
        @DisplayName("totalActionsCount() est 0 pour un nouveau plan vide")
        void totalActionsCountIsZeroForEmptyPlan() {
            fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan plan =
                    new fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan(
                            MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
            assertThat(plan.totalActionsCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("isDryRun() retourne true pour MigrationMode.DRY_RUN")
        void isDryRunReturnsTrueForDryRun() {
            fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan plan =
                    new fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan(
                            MigrationMode.DRY_RUN, Set.of(), MigrationStatus.PENDING);
            assertThat(plan.isDryRun()).isTrue();
        }

        @Test
        @DisplayName("currentStatus() retourne le statut initial")
        void currentStatusReturnsInitialStatus() {
            fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan plan =
                    new fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan(
                            MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.PENDING);
        }
    }

    // ---- private helper ----

    private void mockConfigForDataAdded(Configuration oldConfig, Configuration newConfig, String newDataType) {
        fr.inra.oresing.domain.application.configuration.StandardDataDescription newDesc =
                mock(fr.inra.oresing.domain.application.configuration.StandardDataDescription.class);

        // oldConfig n'a pas le datatype
        java.util.Map<String, fr.inra.oresing.domain.application.configuration.StandardDataDescription> oldDescMap =
                new java.util.HashMap<>();
        when(oldConfig.dataDescription()).thenReturn(oldDescMap);

        // newConfig a le datatype
        java.util.Map<String, fr.inra.oresing.domain.application.configuration.StandardDataDescription> newDescMap =
                new java.util.HashMap<>();
        newDescMap.put(newDataType, newDesc);
        when(newConfig.dataDescription()).thenReturn(newDescMap);

        // applicationDescription requis par executeMigration ligne 234
        fr.inra.oresing.domain.application.configuration.ApplicationDescription appDesc =
                new fr.inra.oresing.domain.application.configuration.ApplicationDescription(
                        "test_app",
                        new fr.inra.oresing.domain.application.configuration.Version("1.0"),
                        java.util.Locale.FRENCH,
                        null
                );
        when(newConfig.applicationDescription()).thenReturn(appDesc);

        // i18n du newConfig pour le nouveau datatype
        fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations i18n =
                mock(fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations.class);
        when(i18n.getData()).thenReturn(new java.util.HashMap<>());
        when(newConfig.i18n()).thenReturn(i18n);
        when(oldConfig.i18n()).thenReturn(i18n);
    }
}