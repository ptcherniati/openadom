package fr.inra.oresing.workflow.cascade.history;

/**
 * Parallelism summary attached to a {@link WorkflowSnapshot} for the
 * oa-live header ( "Source: 1 │ Transform: 4 │ Sink: 1" ) . Resolved at
 * workflow registration time from the effective {@code WorkflowConfig} +
 * sink strategy guard rails ( e.g. PER_CONNECTION_TEMP forces sink = 1 ) .
 */
public record ParallelismSnapshot(
        int source,
        int transform,
        int sink) {

    public static ParallelismSnapshot empty() {
        return new ParallelismSnapshot(0, 0, 0);
    }
}
