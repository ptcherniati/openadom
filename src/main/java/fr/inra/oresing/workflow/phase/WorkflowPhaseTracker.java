package fr.inra.oresing.workflow.phase;

import fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleCoordinator;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single point of truth for emitting workflow phase transitions to
 * <ol>
 *   <li>{@code workflow_log.metadata.phase} ( persistent , read by the
 *       Dashboard endpoint for history + WorkflowSummary table ) , and</li>
 *   <li>{@link WorkflowActiveRegistry#setSubPhase} ( in-memory ,
 *       read by the live finalize block via {@code FinalizeProgressDTO
 *       .subPhase} for the spinner under "UPSERT staging -> finale" ) .</li>
 * </ol>
 *
 * <h2>Why this abstraction</h2>
 *
 * <p>Before consolidation , callers in {@code PublishLifecyclePhase2Handler}
 * had to manually :
 * <ol>
 *   <li>Call {@code logRepository.updatePhase(parentCid, phase)} .</li>
 *   <li>Resolve the cascade child correlation id from the coordinator
 *       ( {@code coordinator.getChildImport(parentCid).orElse(parentCid)} )
 *       - DashboardService uses the child cid for all in-memory side-map
 *       lookups ( registry.findSubPhase , findFinalizePhase , ... ) .</li>
 *   <li>Null-check the registry and the coordinator .</li>
 *   <li>Call {@code registry.setSubPhase(childCid, phase)} with the
 *       resolved child cid .</li>
 * </ol>
 *
 * <p>Repeating this 4-step boilerplate at every phase transition was
 * error-prone : forgetting step 2 caused the UI to silently freeze at
 * 100 % during 1-2 min of post-UPSERT processing because the registry
 * write went to the wrong cid . This component encapsulates it once .
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * phaseTracker.transitionTo(parentCorrelationId, WorkflowPhase.SYNTHESIS_REBUILD);
 * }</pre>
 *
 * <h2>Atomicity guarantees</h2>
 *
 * <p>Persistent and in-memory updates are NOT atomic together : the
 * persistent update happens first ; the in-memory update is best-effort
 * ( {@code RuntimeException} swallowed and logged ) . Worst case the
 * dashboard shows the previous sub-phase while {@code workflow_log.phase}
 * already advanced - a UI lag of one polling cycle , no data corruption .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class WorkflowPhaseTracker {

    private final WorkflowLogRepository           logRepository;
    private final WorkflowActiveRegistry          registry;
    private final PublishLifecycleCoordinator     coordinator;

    public WorkflowPhaseTracker(WorkflowLogRepository logRepository,
                                WorkflowActiveRegistry registry,
                                PublishLifecycleCoordinator coordinator) {
        this.logRepository = logRepository;
        this.registry      = registry;
        this.coordinator   = coordinator;
    }

    /**
     * Emits a phase transition for the given parent workflow . Updates
     * both the persistent workflow_log row and the in-memory registry
     * snapshot ( resolved to the cascade child cid when applicable ) .
     *
     * @param parentCorrelationId publish/unpublish/delete workflow cid -
     *                            null is a no-op
     * @param phase               phase string from {@code WorkflowPhase}
     *                            constants - null is a no-op
     */
    public void transitionTo(UUID parentCorrelationId, String phase) {
        if (parentCorrelationId == null || phase == null) {
            return;
        }
        try {
            logRepository.updatePhase(parentCorrelationId, phase);
        } catch (RuntimeException ex) {
            log.warn("phaseTracker : logRepository.updatePhase failed for cid={} phase={} : {}",
                    parentCorrelationId, phase, ex.getMessage());
        }
        try {
            UUID dashboardCid = resolveDashboardCid(parentCorrelationId);
            registry.setSubPhase(dashboardCid, phase);
        } catch (RuntimeException ex) {
            log.warn("phaseTracker : registry.setSubPhase failed for cid={} phase={} : {}",
                    parentCorrelationId, phase, ex.getMessage());
        }
    }

    /**
     * Resolves the correlation id the dashboard uses for in-memory
     * side-map lookups : child cascade cid for PUBLISH ( has IMPORT
     * child registered via {@link PublishLifecycleCoordinator
     * #registerChildImport} ) , parent cid for UNPUBLISH / DELETE_FILE
     * ( no child cascade ) .
     *
     * <p>Mirrors {@code DashboardService.finalizeProgress} dataCid
     * resolution - keep in sync if that logic changes .
     */
    private UUID resolveDashboardCid(UUID parentCorrelationId) {
        if (coordinator == null) {
            return parentCorrelationId;
        }
        return coordinator.getChildImport(parentCorrelationId).orElse(parentCorrelationId);
    }
}
