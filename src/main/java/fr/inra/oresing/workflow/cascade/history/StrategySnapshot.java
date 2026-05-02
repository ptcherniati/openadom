package fr.inra.oresing.workflow.cascade.history;

/**
 * Cascade strategy summary attached to a {@link WorkflowSnapshot} so the
 * oa-live dashboard can show how a workflow was configured ( sink path ,
 * staging mode , pipeline mode , sink parallelism ) without forcing the
 * user to dig through env vars .
 *
 * <p>Resolved once at workflow registration time and never mutated .
 *
 * @param sinkStrategy    MERGE_FILE | DIRECT_COPY
 * @param stagingStrategy PER_CONNECTION_TEMP | SHARED_UNLOGGED | PER_WORKFLOW_TABLE ( null in MERGE_FILE )
 * @param pipelineMode    STAGED | PIPELINED ( cascade 2.1.0 )
 * @param sinkParallelism number of concurrent sink writers
 */
public record StrategySnapshot(
        String sinkStrategy,
        String stagingStrategy,
        String pipelineMode,
        int    sinkParallelism) {
}
