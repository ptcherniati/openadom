package fr.inra.oresing.workflow.cascade.history;

/**
 * Cascade strategy summary attached to a {@link WorkflowSnapshot} so the
 * oa-live dashboard can show how a workflow was configured ( sink path ,
 * staging mode , execution mode , streaming mode ) without forcing the
 * user to dig through env vars .
 *
 * <p>Resolved once at workflow registration time and never mutated .
 *
 * @param sinkStrategy        MERGE_FILE | DIRECT_COPY
 * @param stagingStrategy     PER_CONNECTION_TEMP | SHARED_UNLOGGED ( null in MERGE_FILE )
 * @param executionMode       SYNC | ASYNC
 * @param streamingMode       BUFFERED | BACKPRESSURED
 * @param directWriteParallel true = sink writes parallelised on SYNC path
 */
public record StrategySnapshot(
        String  sinkStrategy,
        String  stagingStrategy,
        String  executionMode,
        String  streamingMode,
        boolean directWriteParallel) {
}
