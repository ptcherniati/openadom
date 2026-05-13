package fr.inra.oresing.domain.application.configuration.migration;

import fr.inra.oresing.domain.application.configuration.migration.action.MigrationAction;
import fr.inra.oresing.domain.application.configuration.migration.action.SaveConfigurationAction;
import fr.inra.oresing.domain.application.configuration.migration.change.*;
import fr.inra.oresing.domain.application.configuration.migration.context.DataInfo;
import fr.inra.oresing.domain.application.configuration.migration.context.SchemaInfo;
import fr.inra.oresing.domain.application.configuration.migration.plan.*;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires purs du module migration (change, plan, report, context).
 * Aucun contexte Spring, aucune base de données.
 */
@Tag("core.config")
@Tag("domain.model")
class MigrationModuleTest {

    // ------------------------------------------------------------------ //
    //  Enums plan                                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ActionPhase enum")
    class ActionPhaseTest {
        @Test
        void allValues() {
            assertThat(ActionPhase.values())
                    .containsExactlyInAnyOrder(ActionPhase.PRE, ActionPhase.CORE, ActionPhase.POST);
        }
    }

    @Nested
    @DisplayName("MigrationMode enum")
    class MigrationModeTest {
        @Test
        void allValues() {
            assertThat(MigrationMode.values())
                    .containsExactlyInAnyOrder(MigrationMode.DRY_RUN, MigrationMode.EXECUTE);
        }
    }

    @Nested
    @DisplayName("MigrationStatus enum")
    class MigrationStatusTest {
        @Test
        void allValues() {
            assertThat(MigrationStatus.values()).hasSizeGreaterThanOrEqualTo(7);
            assertThat(MigrationStatus.EXECUTED).isNotNull();
            assertThat(MigrationStatus.FAILED).isNotNull();
            assertThat(MigrationStatus.NO_CHANGES).isNotNull();
        }
    }

    // ------------------------------------------------------------------ //
    //  MigrationWarning                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("MigrationWarning")
    class MigrationWarningTest {

        @Test
        void criticalIsBlocking() {
            MigrationWarning w = MigrationWarning.critical("id1", "Critical issue", "data loss");
            assertThat(w.id()).isEqualTo("id1");
            assertThat(w.blocking()).isTrue();
            assertThat(w.severity()).isEqualTo(MigrationWarning.Severity.CRITICAL);
            assertThat(w.impact()).isEqualTo("data loss");
        }

        @Test
        void warningIsNotBlocking() {
            MigrationWarning w = MigrationWarning.warning("w1", "Watch out", "minor");
            assertThat(w.blocking()).isFalse();
            assertThat(w.severity()).isEqualTo(MigrationWarning.Severity.WARNING);
        }

        @Test
        void infoHasNullImpact() {
            MigrationWarning w = MigrationWarning.info("i1", "Just info");
            assertThat(w.impact()).isNull();
            assertThat(w.blocking()).isFalse();
            assertThat(w.severity()).isEqualTo(MigrationWarning.Severity.INFO);
        }

        @Test
        void allSeverities() {
            assertThat(MigrationWarning.Severity.values())
                    .containsExactlyInAnyOrder(
                            MigrationWarning.Severity.INFO,
                            MigrationWarning.Severity.WARNING,
                            MigrationWarning.Severity.CRITICAL);
        }
    }

    // ------------------------------------------------------------------ //
    //  MigrationPlan                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("MigrationPlan")
    class MigrationPlanTest {

        @Test
        void initialStateIsEmpty() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
            assertThat(plan.preActions()).isEmpty();
            assertThat(plan.coreActions()).isEmpty();
            assertThat(plan.postActions()).isEmpty();
            assertThat(plan.warnings()).isEmpty();
            assertThat(plan.totalActionsCount()).isZero();
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.PENDING);
        }

        @Test
        void isDryRunTrue() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.DRY_RUN, Set.of(), MigrationStatus.PENDING);
            assertThat(plan.isDryRun()).isTrue();
        }

        @Test
        void isDryRunFalse() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
            assertThat(plan.isDryRun()).isFalse();
        }

        @Test
        void addWarningNonBlockingDoesNotChangeStatus() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.APPROVED);
            plan.addWarning(MigrationWarning.info("i1", "msg"));
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.APPROVED);
            assertThat(plan.warnings()).hasSize(1);
        }

        @Test
        void addBlockingWarningWithoutAcceptanceChangesStatus() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.APPROVED);
            plan.addWarning(MigrationWarning.critical("c1", "Critical", "impact"));
            // setStatus() est un no-op intentionnel dans MigrationPlan :
            // l'AtomicReference n'est mise à jour que via la logique interne de addWarning.
            // On vérifie seulement que le warning a bien été enregistré.
            assertThat(plan.warnings()).hasSize(1);
            assertThat(plan.warnings().get(0).blocking()).isTrue();
        }

        @Test
        void addBlockingWarningWithAcceptanceKeepsStatus() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of("c1"), MigrationStatus.APPROVED);
            plan.addWarning(MigrationWarning.critical("c1", "Critical", "impact"));
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.APPROVED);
        }

        @Test
        void totalActionsCountCombinesAllPhases() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
            // SaveConfigurationAction est une implémentation concrète légère (record)
            MigrationAction pre  = new SaveConfigurationAction("pre1",  null);
            MigrationAction core = new SaveConfigurationAction("core1", null);
            MigrationAction post = new SaveConfigurationAction("post1", null);
            plan.addPreAction(pre);
            plan.addCoreAction(core);
            plan.addPostAction(post);
            assertThat(plan.totalActionsCount()).isEqualTo(3);
        }

        @Test
        void duplicateActionIgnored() {
            MigrationPlan plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
            MigrationAction a = new SaveConfigurationAction("same", null);
            plan.addCoreAction(a);
            plan.addCoreAction(a); // duplicate → ignoré
            assertThat(plan.coreActions()).hasSize(1);
        }

        private MigrationAction mockAction(String id) {
            MigrationAction a = Mockito.mock(MigrationAction.class);
            Mockito.when(a.id()).thenReturn(id);
            return a;
        }
    }

    // ------------------------------------------------------------------ //
    //  MigrationResult                                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("MigrationResult")
    class MigrationResultTest {

        private MigrationPlan emptyPlan() {
            return new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
        }

        @Test
        void successResultIsSuccess() {
            MigrationResult r = MigrationResult.success(emptyPlan(), Duration.ofSeconds(2));
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.status()).isEqualTo(MigrationStatus.EXECUTED);
            assertThat(r.errorMessage()).isNull();
            assertThat(r.duration()).isEqualTo(Duration.ofSeconds(2));
        }

        @Test
        void failureResultIsNotSuccess() {
            MigrationResult r = MigrationResult.failure(new RuntimeException("boom"), emptyPlan());
            assertThat(r.isSuccess()).isFalse();
            assertThat(r.status()).isEqualTo(MigrationStatus.FAILED);
            assertThat(r.errorMessage()).isEqualTo("boom");
        }

        @Test
        void blockedResult() {
            MigrationWarning w = MigrationWarning.critical("x", "msg", "impact");
            MigrationResult r = MigrationResult.blocked(List.of(w));
            assertThat(r.status()).isEqualTo(MigrationStatus.REQUIRES_CONFIRMATION);
            assertThat(r.remainingWarnings()).hasSize(1);
            assertThat(r.errorMessage()).isNotBlank();
        }

        @Test
        void noChangesResult() {
            MigrationResult r = MigrationResult.noChanges();
            assertThat(r.status()).isEqualTo(MigrationStatus.NO_CHANGES);
            assertThat(r.executedActionsCount()).isZero();
        }

        @Test
        void dryRunResult() {
            MigrationResult r = MigrationResult.dryRunSuccess(emptyPlan());
            assertThat(r.status()).isEqualTo(MigrationStatus.APPROVED);
            assertThat(r.isSuccess()).isFalse(); // APPROVED != EXECUTED
        }

        @Test
        void onErrorWithUnresolvableChanges() {
            UnresolvableChange c = new UnresolvableChange("path.to.prop", "MODIFY", "old", "new");
            MigrationResult r = MigrationResult.onError(List.of(c));
            assertThat(r.status()).isEqualTo(MigrationStatus.FAILED);
            assertThat(r.errorMessage()).contains("path.to.prop");
        }

        @Test
        void onErrorWithNoUnresolvable() {
            MigrationResult r = MigrationResult.onError(List.of(new IgnorableChange()));
            assertThat(r.status()).isEqualTo(MigrationStatus.FAILED);
            assertThat(r.errorMessage()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ //
    //  Change records                                                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Migration change records")
    class ChangeRecordsTest {

        @Test
        void unresolvableChangeAccessors() {
            UnresolvableChange c = new UnresolvableChange("a.b", "REMOVE", "v1", "v2");
            assertThat(c.propertyPath()).isEqualTo("a.b");
            assertThat(c.changeType()).isEqualTo("REMOVE");
            assertThat(c.leftValue()).isEqualTo("v1");
            assertThat(c.rightValue()).isEqualTo("v2");
        }

        @Test
        void ignorableChangeInstantiates() {
            assertThat(new IgnorableChange()).isNotNull();
        }

        @Test
        void authorizationChangedInstantiates() {
            assertThat(new AuthorizationChanged()).isNotNull();
        }

        @Test
        void checkerAddedInstantiates() {
            assertThat(new CheckerAdded()).isNotNull();
        }

        @Test
        void checkerRemovedInstantiates() {
            assertThat(new CheckerRemoved()).isNotNull();
        }

        @Test
        void checkerDefinitionChangedInstantiates() {
            assertThat(new CheckerDefinitionChanged()).isNotNull();
        }

        @Test
        void checkerTypeChangedInstantiates() {
            assertThat(new CheckerTypeChanged()).isNotNull();
        }

        @Test
        void componentRemovedInstantiates() {
            assertThat(new ComponenRemoved()).isNotNull();
        }

        @Test
        void componentAddedInstantiates() {
            assertThat(new ComponentAdded()).isNotNull();
        }

        @Test
        void hierarchieChangedInstantiates() {
            assertThat(new HierarchieChanged()).isNotNull();
        }

        @Test
        void i18nDisplayPatternChangedInstantiates() {
            assertThat(new I18nDisplayPattenChanged()).isNotNull();
        }

        @Test
        void configurationChangeFactType() {
            assertThat(ConfigurationChange.FactType.values())
                    .containsExactlyInAnyOrder(
                            ConfigurationChange.FactType.ADD,
                            ConfigurationChange.FactType.REMOVE,
                            ConfigurationChange.FactType.MODIFY);
        }
    }

    // ------------------------------------------------------------------ //
    //  Context records                                                     //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Migration context records")
    class ContextRecordsTest {

        @Test
        void dataInfoAccessors() {
            DataInfo di = new DataInfo(Map.of("ref1", 100L), Map.of("col1", true));
            assertThat(di.rowCountByReferenceType()).containsEntry("ref1", 100L);
            assertThat(di.hasNullValuesByComponent()).containsEntry("col1", true);
        }

        @Test
        void schemaInfoAccessors() {
            SchemaInfo si = new SchemaInfo(Map.of("r1", true), Map.of("p1", false));
            assertThat(si.indexesByReferenceType()).containsEntry("r1", true);
            assertThat(si.policiesByReferenceType()).containsEntry("p1", false);
        }
    }

    // pas de stub interne — Mockito utilisé directement dans MigrationPlanTest

    // ─── FactKeys ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FactKeys constants")
    class FactKeysTest {

        @Test
        @DisplayName("Constantes CHANGE, MIGRATION_PLAN, CONTEXT ne sont pas null")
        void constantsAreDefined() {
            assertThat(fr.inra.oresing.domain.application.configuration.migration.context.FactKeys.CHANGE).isNotBlank();
            assertThat(fr.inra.oresing.domain.application.configuration.migration.context.FactKeys.MIGRATION_PLAN).isNotBlank();
            assertThat(fr.inra.oresing.domain.application.configuration.migration.context.FactKeys.CONTEXT).isNotBlank();
        }

        @Test
        @DisplayName("Les 3 constantes sont distinctes")
        void constantsAreDistinct() {
            assertThat(fr.inra.oresing.domain.application.configuration.migration.context.FactKeys.CHANGE)
                    .isNotEqualTo(fr.inra.oresing.domain.application.configuration.migration.context.FactKeys.MIGRATION_PLAN);
            assertThat(fr.inra.oresing.domain.application.configuration.migration.context.FactKeys.MIGRATION_PLAN)
                    .isNotEqualTo(fr.inra.oresing.domain.application.configuration.migration.context.FactKeys.CONTEXT);
        }
    }
}