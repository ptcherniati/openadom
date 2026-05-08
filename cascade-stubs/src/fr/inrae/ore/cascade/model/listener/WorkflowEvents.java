package fr.inrae.ore.cascade.model.listener;
import fr.inrae.ore.cascade.model.workflow.ProcessingStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
public class WorkflowEvents {

    public static class WorkflowStartEvent {
        private final String correlationId; private final String userId;
        private final String sourceName; private final String sinkName; private final Instant startTime;
        public WorkflowStartEvent(String correlationId, String userId, String sourceName, String sinkName, Instant startTime) {
            this.correlationId=correlationId; this.userId=userId; this.sourceName=sourceName; this.sinkName=sinkName; this.startTime=startTime;
        }
        public String correlationId() { return correlationId; }
        public String userId() { return userId; }
    }

    public static class WorkflowEndEvent {
        private final String correlationId; private final String userId;
        private final ProcessingStatus status; private final long recordsProcessed; private final long recordsFailed;
        private final int chunksProcessed; private final Instant startTime; private final Instant endTime;
        private final Duration duration; private final List<String> errors; private final Throwable fatalError;
        public WorkflowEndEvent(String correlationId, String userId, ProcessingStatus status, long recordsProcessed,
                long recordsFailed, int chunksProcessed, Instant startTime, Instant endTime,
                Duration duration, List<String> errors, Throwable fatalError) {
            this.correlationId=correlationId; this.userId=userId; this.status=status;
            this.recordsProcessed=recordsProcessed; this.recordsFailed=recordsFailed; this.chunksProcessed=chunksProcessed;
            this.startTime=startTime; this.endTime=endTime; this.duration=duration; this.errors=errors; this.fatalError=fatalError;
        }
        public String correlationId() { return correlationId; }
        public ProcessingStatus status() { return status; }
        public String errorMessage() { return fatalError != null ? fatalError.getMessage() : null; }
    }

    public static class WorkflowAliveEvent {
        private final String correlationId; private final long nanoTime; private final Instant time;
        public WorkflowAliveEvent(String correlationId, long nanoTime, Instant time) {
            this.correlationId=correlationId; this.nanoTime=nanoTime; this.time=time;
        }
        public String correlationId() { return correlationId; }
        public Instant time() { return time; }
    }

    public static class WorkflowHeartbeatEvent {
        private final String correlationId;
        public WorkflowHeartbeatEvent(String correlationId) { this.correlationId = correlationId; }
        public String correlationId() { return correlationId; }
    }

    public static class SourceTotalChunksKnownEvent {
        private final String correlationId; private final int totalChunks; private final Instant time;
        public SourceTotalChunksKnownEvent(String correlationId, int totalChunks, Instant time) {
            this.correlationId=correlationId; this.totalChunks=totalChunks; this.time=time;
        }
        public String correlationId() { return correlationId; }
        public int totalChunks() { return totalChunks; }
    }

    public static class SourceFetchStartEvent {
        private final String correlationId; private final int slotIndex; private final String workerName; private final Instant time;
        public SourceFetchStartEvent(String correlationId, int slotIndex, String workerName, Instant time) {
            this.correlationId=correlationId; this.slotIndex=slotIndex; this.workerName=workerName; this.time=time;
        }
        public String correlationId() { return correlationId; }
        public String workerName() { return workerName; }
        public Instant time() { return time; }
    }

    public static class SourceChunkEmittedEvent {
        private final String correlationId; private final int chunkIndex; private final long recordsEmitted;
        private final String workerName; private final int sourceQueueDepth; private final ProcessingStatus status;
        private final String errorMessage; private final Instant time;
        public SourceChunkEmittedEvent(String correlationId, int chunkIndex, long recordsEmitted, String workerName,
                int sourceQueueDepth, ProcessingStatus status, String errorMessage, Instant time) {
            this.correlationId=correlationId; this.chunkIndex=chunkIndex; this.recordsEmitted=recordsEmitted;
            this.workerName=workerName; this.sourceQueueDepth=sourceQueueDepth; this.status=status;
            this.errorMessage=errorMessage; this.time=time;
        }
        public String correlationId() { return correlationId; }
        public int chunkIndex() { return chunkIndex; }
        public long recordsEmitted() { return recordsEmitted; }
        public String workerName() { return workerName; }
        public Instant time() { return time; }
    }

    public static class ChunkStartEvent {
        private final String correlationId; private final int chunkIndex; private final long recordsExpected;
        private final String workerName; private final Instant startTime;
        public ChunkStartEvent(String correlationId, int chunkIndex, long recordsExpected, String workerName, Instant startTime) {
            this.correlationId=correlationId; this.chunkIndex=chunkIndex; this.recordsExpected=recordsExpected;
            this.workerName=workerName; this.startTime=startTime;
        }
        public String correlationId() { return correlationId; }
        public int chunkIndex() { return chunkIndex; }
        public long recordsExpected() { return recordsExpected; }
        public String workerName() { return workerName; }
        public Instant startTime() { return startTime; }
    }

    public static class ChunkProgressEvent {
        private final String correlationId; private final int chunkIndex; private final long totalProcessedSoFar;
        private final String workerName; private final Instant time;
        public ChunkProgressEvent(String correlationId, int chunkIndex, long totalProcessedSoFar, String workerName, Instant time) {
            this.correlationId=correlationId; this.chunkIndex=chunkIndex; this.totalProcessedSoFar=totalProcessedSoFar;
            this.workerName=workerName; this.time=time;
        }
        public String correlationId() { return correlationId; }
        public int chunkIndex() { return chunkIndex; }
        public long totalProcessedSoFar() { return totalProcessedSoFar; }
        public String workerName() { return workerName; }
        public Instant time() { return time; }
    }

    public static class ChunkEndEvent {
        private final String correlationId; private final int chunkIndex; private final ProcessingStatus status;
        private final long recordsProcessed; private final long recordsFailed; private final String workerName;
        private final Instant startTime; private final Instant endTime; private final Duration duration;
        private final String errorMessage;
        public ChunkEndEvent(String correlationId, int chunkIndex, ProcessingStatus status,
                long recordsProcessed, long recordsFailed, String workerName,
                Instant startTime, Instant endTime, Duration duration, String errorMessage) {
            this.correlationId=correlationId; this.chunkIndex=chunkIndex; this.status=status;
            this.recordsProcessed=recordsProcessed; this.recordsFailed=recordsFailed; this.workerName=workerName;
            this.startTime=startTime; this.endTime=endTime; this.duration=duration; this.errorMessage=errorMessage;
        }
        public String correlationId() { return correlationId; }
        public int chunkIndex() { return chunkIndex; }
        public long recordsProcessed() { return recordsProcessed; }
        public ProcessingStatus status() { return status; }
        public String errorMessage() { return errorMessage; }
        public Instant endTime() { return endTime; }
        public String workerName() { return workerName; }
        public Duration duration() { return duration; }
    }

    public static class SinkChunkAcceptedEvent {
        private final String correlationId; private final int chunkIndex; private final String workerName; private final Instant time;
        public SinkChunkAcceptedEvent(String correlationId, int chunkIndex, String workerName, Instant time) {
            this.correlationId=correlationId; this.chunkIndex=chunkIndex; this.workerName=workerName; this.time=time;
        }
        public String correlationId() { return correlationId; }
        public int chunkIndex() { return chunkIndex; }
        public String workerName() { return workerName; }
        public Instant time() { return time; }
    }

    public static class SinkChunkWrittenEvent {
        private final String correlationId; private final int chunkIndex; private final String workerName;
        private final ProcessingStatus status; private final String errorMessage; private final Instant endTime;
        private final Duration duration; private final long recordsWritten;
        public SinkChunkWrittenEvent(String correlationId, int chunkIndex, String workerName, ProcessingStatus status,
                String errorMessage, Instant endTime, Duration duration, long recordsWritten) {
            this.correlationId=correlationId; this.chunkIndex=chunkIndex; this.workerName=workerName;
            this.status=status; this.errorMessage=errorMessage; this.endTime=endTime;
            this.duration=duration; this.recordsWritten=recordsWritten;
        }
        public String correlationId() { return correlationId; }
        public int chunkIndex() { return chunkIndex; }
        public String workerName() { return workerName; }
        public ProcessingStatus status() { return status; }
        public String errorMessage() { return errorMessage; }
        public Instant endTime() { return endTime; }
        public Duration duration() { return duration; }
        public long recordsWritten() { return recordsWritten; }
    }

    public static class PoolHeartbeatEvent {
        private final String correlationId; private final Instant time; private final List<PoolSample> pools;
        public PoolHeartbeatEvent(String correlationId, Instant time, List<PoolSample> pools) {
            this.correlationId=correlationId; this.time=time; this.pools=pools;
        }
        public String correlationId() { return correlationId; }
        public Instant time() { return time; }
        public List<PoolSample> pools() { return pools; }
    }

    public interface PoolSample {
        String stage();
        int poolSize(); int activeCount(); long completedTaskCount();
        int queueSize(); int queueCapacity();
    }
}
