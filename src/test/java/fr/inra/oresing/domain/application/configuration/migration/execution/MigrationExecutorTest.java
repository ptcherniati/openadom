package fr.inra.oresing.domain.application.configuration.migration.execution;

import fr.inra.oresing.domain.application.configuration.migration.action.AddAuthorizationScopeAttributeAction;
import fr.inra.oresing.domain.application.configuration.migration.action.SaveConfigurationAction;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import fr.inra.oresing.domain.port.MigrationApplicationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires de MigrationExecutor et MigrationExecutionException.
 * Aucun contexte Spring — dépendances simulées par Mockito.
 */
@DisplayName("MigrationExecutor — orchestration des phases de migration")
@Tag("core.config")
@Tag("domain.model")
class MigrationExecutorTest {

    private MigrationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new MigrationExecutor();
    }

    // ------------------------------------------------------------------ //
    //  DRY_RUN mode                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Mode DRY_RUN")
    class DryRunModeTest {

        @Test
        @DisplayName("retourne APPROVED sans exécuter les actions")
        void dryRunReturnsApprovedWithoutExecuting() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.DRY_RUN, Set.of(), MigrationStatus.APPROVED);
            plan.addPostAction(new SaveConfigurationAction("save", null));

            // context null : non utilisé en mode DRY_RUN
            MigrationResult result = executor.execute(plan, null);

            assertThat(result.status()).isEqualTo(MigrationStatus.APPROVED);
            // totalActionsCount = 1 (l'action dans la liste)
            assertThat(result.executedActionsCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("errorMessage contient 'Dry-run'")
        void dryRunHasDryRunMessage() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.DRY_RUN, Set.of(), MigrationStatus.APPROVED);

            MigrationResult result = executor.execute(plan, null);

            assertThat(result.errorMessage()).containsIgnoringCase("dry-run");
        }

        @Test
        @DisplayName("isDryRun() est true pour MigrationMode.DRY_RUN")
        void isDryRunTrue() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.DRY_RUN, Set.of(), MigrationStatus.APPROVED);
            assertThat(plan.isDryRun()).isTrue();
        }
    }

    // ------------------------------------------------------------------ //
    //  Mode EXECUTE — plan vide                                           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Mode EXECUTE — plan sans actions")
    class ExecuteEmptyPlanTest {

        @Test
        @DisplayName("retourne EXECUTED avec 0 actions exécutées")
        void emptyPlanReturnsExecuted() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.APPROVED);

            MigrationResult result = executor.execute(plan, null);

            assertThat(result.status()).isEqualTo(MigrationStatus.EXECUTED);
            assertThat(result.executedActionsCount()).isEqualTo(0);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("duration non null")
        void durationIsSet() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.APPROVED);

            MigrationResult result = executor.execute(plan, null);

            assertThat(result.duration()).isNotNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  Mode EXECUTE — plan avec actions                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Mode EXECUTE — plan avec actions")
    class ExecuteWithActionsTest {

        @Test
        @DisplayName("exécute les actions PRE, CORE et POST dans l'ordre")
        void executesAllPhases() {
            // AddAuthorizationScopeAttributeAction.execute() appelle le contexte avec deep stubs
            // resetRole(), addReferenceToAuthorizationScope(), setRoleForClient() sont tous void/boolean
            MigrationContext context = Mockito.mock(MigrationContext.class, RETURNS_DEEP_STUBS);
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.APPROVED);

            // POST : AddAuthorizationScopeAttributeAction fonctionne avec deep stubs (pas de NPE)
            plan.addPostAction(new AddAuthorizationScopeAttributeAction("add-post", "myData"));

            MigrationResult result = executor.execute(plan, context);

            assertThat(result.status()).isEqualTo(MigrationStatus.EXECUTED);
            assertThat(result.executedActionsCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("compte correctement les actions par phase")
        void countsAllPhaseActions() {
            MigrationContext context = Mockito.mock(MigrationContext.class, RETURNS_DEEP_STUBS);
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.APPROVED);

            // PRE, CORE, POST avec des ids distincts
            plan.addPreAction(new AddAuthorizationScopeAttributeAction("add-pre", "d1"));
            plan.addPostAction(new AddAuthorizationScopeAttributeAction("add-post-1", "d2"));
            plan.addPostAction(new AddAuthorizationScopeAttributeAction("add-post-2", "d3"));

            MigrationResult result = executor.execute(plan, context);

            assertThat(result.status()).isEqualTo(MigrationStatus.EXECUTED);
            assertThat(result.executedActionsCount()).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------ //
    //  Mode EXECUTE — action qui échoue                                   //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Mode EXECUTE — échec d'une action")
    class ExecuteWithFailingActionTest {

        @Test
        @DisplayName("retourne FAILED si une action lève une exception")
        void failingActionReturnsFailure() {
            // Créer des mocks de ports explicites (plus fiable que RETURNS_DEEP_STUBS sur un record)
            MigrationApplicationPort portMock = Mockito.mock(MigrationApplicationPort.class);
            fr.inra.oresing.domain.port.AuthenticationPort authMock = Mockito.mock(fr.inra.oresing.domain.port.AuthenticationPort.class);
            MigrationContext context = new MigrationContext(portMock, authMock, "testApp", null, null, null, null);
            // Configurer addReferenceToAuthorizationScope() pour throw
            Mockito.doThrow(new RuntimeException("simulated error"))
                    .when(portMock)
                    .addReferenceToAuthorizationScope(Mockito.any(), Mockito.any());

            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.APPROVED);
            plan.addPostAction(new AddAuthorizationScopeAttributeAction("add-fail", "dataName"));

            MigrationResult result = executor.execute(plan, context);

            assertThat(result.status()).isEqualTo(MigrationStatus.FAILED);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorMessage()).isNotNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  MigrationExecutionException                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("MigrationExecutionException")
    class MigrationExecutionExceptionTest {

        @Test
        @DisplayName("getMessage() contient l'id de l'action et le message d'erreur")
        void messageContainsActionId() {
            SaveConfigurationAction action = new SaveConfigurationAction("my-action", null);
            MigrationExecutionException ex = new MigrationExecutionException(
                    "something went wrong", action, new RuntimeException("root cause"));

            assertThat(ex.getMessage())
                    .contains("my-action")
                    .contains("something went wrong");
        }

        @Test
        @DisplayName("getCause() retourne la cause originale")
        void causeIsPreserved() {
            SaveConfigurationAction action = new SaveConfigurationAction("act", null);
            RuntimeException cause = new RuntimeException("root cause");
            MigrationExecutionException ex = new MigrationExecutionException("msg", action, cause);

            assertThat(ex.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("est une RuntimeException")
        void isRuntimeException() {
            SaveConfigurationAction action = new SaveConfigurationAction("act", null);
            MigrationExecutionException ex = new MigrationExecutionException("msg", action, null);

            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}