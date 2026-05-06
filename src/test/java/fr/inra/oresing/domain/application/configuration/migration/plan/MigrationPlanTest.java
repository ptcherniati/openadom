package fr.inra.oresing.domain.application.configuration.migration.plan;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.migration.action.MigrationAction;
import fr.inra.oresing.domain.application.configuration.migration.action.SaveConfigurationAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour {@link MigrationPlan}.
 */
@Tag("core.config")
class MigrationPlanTest {

    /**
     * Crée une instance concrète de MigrationAction (sealed → on utilise SaveConfigurationAction).
     */
    static MigrationAction action(String id) {
        Application app = Mockito.mock(Application.class);
        return new SaveConfigurationAction(id, app);
    }

    private MigrationPlan plan;

    @BeforeEach
    void setUp() {
        plan = new MigrationPlan(MigrationMode.EXECUTE, Set.of(), MigrationStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // isDryRun
    // -------------------------------------------------------------------------
    @Nested
    class IsDryRun {
        @Test
        void dryRunModeReturnTrue() {
            MigrationPlan dryRun = new MigrationPlan(MigrationMode.DRY_RUN, Set.of(), MigrationStatus.PENDING);
            assertThat(dryRun.isDryRun()).isTrue();
        }

        @Test
        void executeModeReturnFalse() {
            assertThat(plan.isDryRun()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // addPreAction / addCoreAction / addPostAction — déduplication
    // -------------------------------------------------------------------------
    @Nested
    class AddActions {
        @Test
        void addPreActionAppendsToList() {
            plan.addPreAction(action("pre1"));
            assertThat(plan.preActions()).hasSize(1);
            assertThat(plan.totalActionsCount()).isEqualTo(1);
        }

        @Test
        void addCoreActionAppendsToList() {
            plan.addCoreAction(action("core1"));
            assertThat(plan.coreActions()).hasSize(1);
        }

        @Test
        void addPostActionAppendsToList() {
            plan.addPostAction(action("post1"));
            assertThat(plan.postActions()).hasSize(1);
        }

        @Test
        void duplicateIdIsIgnored() {
            MigrationAction a = action("dup");
            plan.addPreAction(a);
            plan.addPreAction(a);  // même id → ignoré
            assertThat(plan.preActions()).hasSize(1);
        }

        @Test
        void duplicateAcrossPhaseIsIgnored() {
            plan.addPreAction(action("shared"));
            plan.addCoreAction(action("shared"));
            assertThat(plan.totalActionsCount()).isEqualTo(1);
        }

        @Test
        void totalActionsCountSumsAllPhases() {
            plan.addPreAction(action("p1"));
            plan.addCoreAction(action("c1"));
            plan.addPostAction(action("po1"));
            assertThat(plan.totalActionsCount()).isEqualTo(3);
        }
    }

    // -------------------------------------------------------------------------
    // addWarning
    // -------------------------------------------------------------------------
    @Nested
    class AddWarnings {
        @Test
        void nonBlockingWarningDoesNotChangeStatus() {
            plan.addWarning(MigrationWarning.warning("w1", "message", "impact"));
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.PENDING);
            assertThat(plan.warnings()).hasSize(1);
        }

        @Test
        void blockingWarningCallsSetStatusWhichIsNoOp() {
            // setStatus() est intentionnellement un no-op (voir MigrationPlan.setStatus())
            // Le statut reste donc PENDING même pour un warning bloquant
            plan.addWarning(MigrationWarning.critical("blocker", "block msg", "serious impact"));
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.PENDING);
        }

        @Test
        void blockingWarningWithAcceptedIdDoesNotChangeStatus() {
            MigrationPlan withAccepted = new MigrationPlan(MigrationMode.EXECUTE, Set.of("blocker"), MigrationStatus.PENDING);
            withAccepted.addWarning(MigrationWarning.critical("blocker", "block msg", "impact"));
            assertThat(withAccepted.currentStatus()).isEqualTo(MigrationStatus.PENDING);
        }

        @Test
        void infoWarningAddsToList() {
            plan.addWarning(MigrationWarning.info("info1", "just info"));
            assertThat(plan.warnings()).hasSize(1);
            assertThat(plan.warnings().getFirst().severity()).isEqualTo(MigrationWarning.Severity.INFO);
        }

        @Test
        void multipleWarnings() {
            plan.addWarning(MigrationWarning.info("i1", "info"));
            plan.addWarning(MigrationWarning.warning("w1", "warn", "impact"));
            plan.addWarning(MigrationWarning.critical("c1", "crit", "impact"));
            assertThat(plan.warnings()).hasSize(3);
        }
    }

    // -------------------------------------------------------------------------
    // currentStatus / setStatus
    // -------------------------------------------------------------------------
    @Nested
    class Status {
        @Test
        void initialStatusIsPending() {
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.PENDING);
        }

        @Test
        void setStatusIsNoOp() {
            // setStatus() est intentionnellement inopérant (status porté par addWarning)
            plan.setStatus(MigrationStatus.EXECUTED);
            assertThat(plan.currentStatus()).isEqualTo(MigrationStatus.PENDING);
        }
    }

    // -------------------------------------------------------------------------
    // MigrationWarning factory methods
    // -------------------------------------------------------------------------
    @Nested
    class MigrationWarningFactories {
        @Test
        void criticalIsBlocking() {
            MigrationWarning w = MigrationWarning.critical("id", "msg", "impact");
            assertThat(w.blocking()).isTrue();
            assertThat(w.severity()).isEqualTo(MigrationWarning.Severity.CRITICAL);
        }

        @Test
        void warningIsNotBlocking() {
            MigrationWarning w = MigrationWarning.warning("id", "msg", "impact");
            assertThat(w.blocking()).isFalse();
            assertThat(w.severity()).isEqualTo(MigrationWarning.Severity.WARNING);
        }

        @Test
        void infoHasNullImpact() {
            MigrationWarning w = MigrationWarning.info("id", "msg");
            assertThat(w.impact()).isNull();
            assertThat(w.severity()).isEqualTo(MigrationWarning.Severity.INFO);
        }
    }
}