package fr.inrae.ore.cascade.model.listener;
public interface WorkflowListener {
    default void onWorkflowStart(WorkflowEvents.WorkflowStartEvent e) {}
    default void onWorkflowEnd(WorkflowEvents.WorkflowEndEvent e) {}
    default void onWorkflowAlive(WorkflowEvents.WorkflowAliveEvent e) {}
    default void onSourceTotalChunksKnown(WorkflowEvents.SourceTotalChunksKnownEvent e) {}
    default void onSourceFetchStart(WorkflowEvents.SourceFetchStartEvent e) {}
    default void onSourceChunkEmitted(WorkflowEvents.SourceChunkEmittedEvent e) {}
    default void onChunkStart(WorkflowEvents.ChunkStartEvent e) {}
    default void onChunkProgress(WorkflowEvents.ChunkProgressEvent e) {}
    default void onChunkEnd(WorkflowEvents.ChunkEndEvent e) {}
    default void onSinkChunkAccepted(WorkflowEvents.SinkChunkAcceptedEvent e) {}
    default void onSinkChunkWritten(WorkflowEvents.SinkChunkWrittenEvent e) {}
    default void onPoolHeartbeat(WorkflowEvents.PoolHeartbeatEvent e) {}
    default void onWorkflowHeartbeat(WorkflowEvents.WorkflowHeartbeatEvent e) {}
}
