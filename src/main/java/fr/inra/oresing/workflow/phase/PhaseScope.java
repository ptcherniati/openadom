package fr.inra.oresing.workflow.phase;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Try-with-resources wrapper around {@link WorkflowPhaseTracker} that
 * makes phase tracking IMPOSSIBLE to forget . Every long-running step
 * runs inside {@link #run(String, ThrowingRunnable)} which auto-publishes
 * the named phase to both {@code workflow_log.metadata.phase} and the
 * in-memory {@link
 * fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry} ,
 * and auto-logs the elapsed duration when the next phase begins or the
 * scope closes ( exception-safe ) .
 *
 * <h2>Why</h2>
 *
 * <p>The previous handwritten pattern :
 * <pre>{@code
 * logRepository.updatePhase(cid, "SOME_PHASE");
 * if (workflowActiveRegistry != null) {
 *     UUID dashboardCid = coordinator != null
 *             ? coordinator.getChildImport(cid).orElse(cid)
 *             : cid;
 *     workflowActiveRegistry.setSubPhase(dashboardCid, "SOME_PHASE");
 * }
 * // ... do the work ...
 * }</pre>
 *
 * was repeated at every transition site , each copy-paste creating an
 * opportunity to forget a step ( leading to the "EN ATTENTE" UI
 * symptom where workflows run silently for minutes with no phase
 * published ) .
 *
 * <p>With {@link PhaseScope} the pattern becomes :
 * <pre>{@code
 * try (PhaseScope scope = PhaseScope.open(phaseTracker, parentCid)) {
 *     scope.run(WorkflowPhase.COUNTING_ROWS, () -> {
 *         long n = repo.countByFileId(fileId);
 *         progressReporter.reportTotal(parentCid, n);
 *     });
 *     scope.run(WorkflowPhase.DELETE_ROWS, () -> {
 *         repo.removeByFileId(fileId);
 *     });
 * }
 * }</pre>
 *
 * The compiler ensures the resource is closed ; the type system
 * ensures every step has a phase label ; the logs include every
 * phase duration .
 *
 * <h2>Exception safety</h2>
 *
 * <p>If {@link #run} throws , the scope still emits the last phase
 * duration on {@link #close} . The exception propagates unchanged .
 * Phase tracking failures inside the tracker are swallowed and
 * logged by {@link WorkflowPhaseTracker} ; they do not abort the
 * underlying work .
 *
 * <h2>Thread safety</h2>
 *
 * <p>A {@code PhaseScope} is NOT thread-safe ; each workflow thread
 * must use its own instance . Phases are sequential by definition .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
public final class PhaseScope implements AutoCloseable {

    private final WorkflowPhaseTracker tracker;
    private final UUID                 parentCorrelationId;
    private       String               currentPhase;
    private       long                 phaseStartNanos;

    private PhaseScope(WorkflowPhaseTracker tracker, UUID parentCorrelationId) {
        this.tracker             = tracker;
        this.parentCorrelationId = parentCorrelationId;
    }

    /**
     * Opens a new phase scope bound to the given parent workflow .
     * Null tracker is supported ( useful for tests / tracking
     * disabled ) - the scope becomes a no-op .
     */
    public static PhaseScope open(WorkflowPhaseTracker tracker, UUID parentCorrelationId) {
        return new PhaseScope(tracker, parentCorrelationId);
    }

    /**
     * Runs the work in the given phase . Transition is published
     * BEFORE the work starts so the UI sees the phase immediately ,
     * not after the work completes .
     */
    public <T> T run(String phase, ThrowingSupplier<T> work) throws Exception {
        transitionTo(phase);
        return work.get();
    }

    /** Same as {@link #run(String, ThrowingSupplier)} for void work . */
    public void run(String phase, ThrowingRunnable work) throws Exception {
        transitionTo(phase);
        work.run();
    }

    /**
     * Manually transitions to a phase without running work in the
     * same call . Useful when the work is structured across multiple
     * statements that cannot be wrapped in a single lambda
     * ( e.g. cancel checkpoint between transition and the actual
     * SQL call ) .
     */
    public void transitionTo(String phase) {
        finishCurrent();
        currentPhase = phase;
        phaseStartNanos = System.nanoTime();
        if (tracker != null) {
            tracker.transitionTo(parentCorrelationId, phase);
        }
    }

    /** Phase name currently active , or null if the scope has not
     *  transitioned to any phase yet . */
    public String currentPhase() {
        return currentPhase;
    }

    @Override
    public void close() {
        finishCurrent();
    }

    private void finishCurrent() {
        if (currentPhase != null) {
            long ms = (System.nanoTime() - phaseStartNanos) / 1_000_000L;
            log.debug("[{}] phase {} completed in {} ms", parentCorrelationId, currentPhase, ms);
            currentPhase = null;
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
