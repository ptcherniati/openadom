package fr.inra.oresing.workflow.cascade.history;

import java.time.Instant;

/**
 * Snapshot de la phase chargement final d'un workflow d'import .
 *
 * <p>Decompose la duree totale en 3 sous-phases pour permettre a oa-live
 * de freezer le debit cascade quand les chunks ne sont plus emits et
 * d'afficher separement le suivi de l'UPSERT staging -> referencevalue
 * ( DIRECT_COPY ) ou du COPY merged.csv -> referencevalue ( MERGE_FILE ) :
 *
 * <ul>
 *   <li>{@code CASCADE_RUNNING} : source / transform / sink emettent</li>
 *   <li>{@code FINALIZE_RUNNING} : UPSERT staging->final ( DIRECT_COPY )
 *       OU storeAll merged.csv->final ( MERGE_FILE )</li>
 *   <li>{@code COMPLETED} : finalize OK , 0 row staging restante</li>
 *   <li>{@code ROLLBACK_IN_PROGRESS} : exception capture , Postgres
 *       rollback la tx ( atomique cote DB , pas de granularite chunk )</li>
 *   <li>{@code ROLLBACK_DONE} : rollback fini , baseline restoree</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
public record FinalizePhaseSnapshot(
        String  phase,
        Instant cascadeStartedAt,
        Instant cascadeFinishedAt,
        Instant finalizeStartedAt,
        Instant finalizeFinishedAt,
        Instant rollbackStartedAt,
        Instant rollbackFinishedAt,
        long    rowsBeforeRollback,
        String  errorMessage) {

    public static final String PHASE_CASCADE_RUNNING     = "CASCADE_RUNNING";
    public static final String PHASE_FINALIZE_RUNNING    = "FINALIZE_RUNNING";
    public static final String PHASE_COMPLETED           = "COMPLETED";
    public static final String PHASE_ROLLBACK_IN_PROGRESS = "ROLLBACK_IN_PROGRESS";
    public static final String PHASE_ROLLBACK_DONE       = "ROLLBACK_DONE";

    public static FinalizePhaseSnapshot starting(Instant startedAt) {
        return new FinalizePhaseSnapshot(
                PHASE_CASCADE_RUNNING,
                startedAt, null, null, null, null, null,
                0L, null);
    }

    public FinalizePhaseSnapshot withCascadeFinished(Instant at) {
        return new FinalizePhaseSnapshot(
                PHASE_FINALIZE_RUNNING,
                cascadeStartedAt, at, at, null, null, null,
                rowsBeforeRollback, errorMessage);
    }

    public FinalizePhaseSnapshot withFinalizeFinished(Instant at) {
        return new FinalizePhaseSnapshot(
                PHASE_COMPLETED,
                cascadeStartedAt, cascadeFinishedAt, finalizeStartedAt, at,
                rollbackStartedAt, rollbackFinishedAt,
                rowsBeforeRollback, errorMessage);
    }

    public FinalizePhaseSnapshot withRollbackStarted(Instant at, long rowsBefore, String error) {
        return new FinalizePhaseSnapshot(
                PHASE_ROLLBACK_IN_PROGRESS,
                cascadeStartedAt, cascadeFinishedAt, finalizeStartedAt, finalizeFinishedAt,
                at, null,
                rowsBefore, error);
    }

    public FinalizePhaseSnapshot withRollbackFinished(Instant at) {
        return new FinalizePhaseSnapshot(
                PHASE_ROLLBACK_DONE,
                cascadeStartedAt, cascadeFinishedAt, finalizeStartedAt, finalizeFinishedAt,
                rollbackStartedAt, at,
                rowsBeforeRollback, errorMessage);
    }
}
