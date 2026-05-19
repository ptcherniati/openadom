package fr.inra.oresing.workflow.cascade.history;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link FinalizePhaseSnapshot}.
 * Couvre : constantes de phase, factory {@code starting()}, transitions with*.
 */
@Tag("domain.model")
@DisplayName("FinalizePhaseSnapshot — machine d'états finalize IMPORT")
class FinalizePhaseSnapshotTest {

    private static final Instant T0 = Instant.parse("2025-10-01T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(5);
    private static final Instant T2 = T0.plusSeconds(10);
    private static final Instant T3 = T0.plusSeconds(15);
    private static final Instant T4 = T0.plusSeconds(20);

    // ─── constantes ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("constantes de phase sont correctement définies")
    void phaseConstants() {
        assertThat(FinalizePhaseSnapshot.PHASE_CASCADE_RUNNING).isEqualTo("CASCADE_RUNNING");
        assertThat(FinalizePhaseSnapshot.PHASE_FINALIZE_RUNNING).isEqualTo("FINALIZE_RUNNING");
        assertThat(FinalizePhaseSnapshot.PHASE_COMPLETED).isEqualTo("COMPLETED");
        assertThat(FinalizePhaseSnapshot.PHASE_ROLLBACK_IN_PROGRESS).isEqualTo("ROLLBACK_IN_PROGRESS");
        assertThat(FinalizePhaseSnapshot.PHASE_ROLLBACK_DONE).isEqualTo("ROLLBACK_DONE");
    }

    // ─── starting() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("starting() produit CASCADE_RUNNING avec cascadeStartedAt")
    void starting() {
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(T0);
        assertThat(snap.phase()).isEqualTo(FinalizePhaseSnapshot.PHASE_CASCADE_RUNNING);
        assertThat(snap.cascadeStartedAt()).isEqualTo(T0);
        assertThat(snap.cascadeFinishedAt()).isNull();
        assertThat(snap.finalizeStartedAt()).isNull();
        assertThat(snap.finalizeFinishedAt()).isNull();
        assertThat(snap.rollbackStartedAt()).isNull();
        assertThat(snap.rollbackFinishedAt()).isNull();
        assertThat(snap.rowsBeforeRollback()).isEqualTo(0L);
        assertThat(snap.errorMessage()).isNull();
    }

    // ─── withCascadeFinished() ────────────────────────────────────────────────

    @Test
    @DisplayName("withCascadeFinished() → FINALIZE_RUNNING + finalizeStartedAt == cascadeFinishedAt")
    void withCascadeFinished() {
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(T0)
                .withCascadeFinished(T1);

        assertThat(snap.phase()).isEqualTo(FinalizePhaseSnapshot.PHASE_FINALIZE_RUNNING);
        assertThat(snap.cascadeFinishedAt()).isEqualTo(T1);
        assertThat(snap.finalizeStartedAt()).isEqualTo(T1);
        assertThat(snap.finalizeFinishedAt()).isNull();
    }

    // ─── withFinalizeFinished() ───────────────────────────────────────────────

    @Test
    @DisplayName("withFinalizeFinished() → COMPLETED + finalizeFinishedAt renseigné")
    void withFinalizeFinished() {
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(T0)
                .withCascadeFinished(T1)
                .withFinalizeFinished(T2);

        assertThat(snap.phase()).isEqualTo(FinalizePhaseSnapshot.PHASE_COMPLETED);
        assertThat(snap.finalizeFinishedAt()).isEqualTo(T2);
        assertThat(snap.cascadeStartedAt()).isEqualTo(T0);
    }

    // ─── withRollbackStarted() ────────────────────────────────────────────────

    @Test
    @DisplayName("withRollbackStarted() → ROLLBACK_IN_PROGRESS + rowsBefore + errorMessage")
    void withRollbackStarted() {
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(T0)
                .withCascadeFinished(T1)
                .withRollbackStarted(T2, 42L, "constraint violation");

        assertThat(snap.phase()).isEqualTo(FinalizePhaseSnapshot.PHASE_ROLLBACK_IN_PROGRESS);
        assertThat(snap.rollbackStartedAt()).isEqualTo(T2);
        assertThat(snap.rollbackFinishedAt()).isNull();
        assertThat(snap.rowsBeforeRollback()).isEqualTo(42L);
        assertThat(snap.errorMessage()).isEqualTo("constraint violation");
    }

    // ─── withRollbackFinished() ───────────────────────────────────────────────

    @Test
    @DisplayName("withRollbackFinished() → ROLLBACK_DONE + rollbackFinishedAt")
    void withRollbackFinished() {
        FinalizePhaseSnapshot snap = FinalizePhaseSnapshot.starting(T0)
                .withCascadeFinished(T1)
                .withRollbackStarted(T2, 10L, "err")
                .withRollbackFinished(T3);

        assertThat(snap.phase()).isEqualTo(FinalizePhaseSnapshot.PHASE_ROLLBACK_DONE);
        assertThat(snap.rollbackFinishedAt()).isEqualTo(T3);
        assertThat(snap.rowsBeforeRollback()).isEqualTo(10L);
        assertThat(snap.errorMessage()).isEqualTo("err");
    }

    // ─── record equality ─────────────────────────────────────────────────────

    @Test
    @DisplayName("record equality : deux snapshots identiques sont égaux")
    void recordEquality() {
        FinalizePhaseSnapshot a = FinalizePhaseSnapshot.starting(T0);
        FinalizePhaseSnapshot b = FinalizePhaseSnapshot.starting(T0);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("record equality : snapshots différents ne sont pas égaux")
    void recordInequality() {
        FinalizePhaseSnapshot a = FinalizePhaseSnapshot.starting(T0);
        FinalizePhaseSnapshot b = FinalizePhaseSnapshot.starting(T1);
        assertThat(a).isNotEqualTo(b);
    }

    // ─── full chain immutability ──────────────────────────────────────────────

    @Test
    @DisplayName("chaque with* retourne un nouveau snapshot (immutabilité)")
    void immutabilityFullChain() {
        FinalizePhaseSnapshot start   = FinalizePhaseSnapshot.starting(T0);
        FinalizePhaseSnapshot cascOk  = start.withCascadeFinished(T1);
        FinalizePhaseSnapshot finOk   = cascOk.withFinalizeFinished(T2);
        FinalizePhaseSnapshot rollSt  = finOk.withRollbackStarted(T3, 5L, "e");
        FinalizePhaseSnapshot rollEnd = rollSt.withRollbackFinished(T4);

        // Chaque étape est distincte
        assertThat(start).isNotSameAs(cascOk);
        assertThat(cascOk).isNotSameAs(finOk);
        assertThat(finOk).isNotSameAs(rollSt);
        assertThat(rollSt).isNotSameAs(rollEnd);

        // L'original n'a pas muté
        assertThat(start.phase()).isEqualTo(FinalizePhaseSnapshot.PHASE_CASCADE_RUNNING);
        assertThat(rollEnd.phase()).isEqualTo(FinalizePhaseSnapshot.PHASE_ROLLBACK_DONE);
    }
}
