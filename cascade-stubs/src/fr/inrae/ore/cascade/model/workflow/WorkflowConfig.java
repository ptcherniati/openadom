package fr.inrae.ore.cascade.model.workflow;
import fr.inrae.ore.cascade.model.ratelimit.RateLimitConfig;
public interface WorkflowConfig {
    static WorkflowConfig defaults() { return new WorkflowConfig() {}; }
    default int sourceChunkSize() { return 1000; }
    default int collectorChunkSize() { return 1000; }
    default int maxErrors() { return 100; }
    default boolean enableMetrics() { return false; }
    default int fallbackParallelism() { return 1; }
    default int sourceParallelism() { return 1; }
    default int transformParallelism() { return 1; }
    default int sinkParallelism() { return 1; }
    default int sourceQueueSize() { return 50; }
    default int transformQueueSize() { return 50; }
    default int sinkQueueSize() { return 50; }
    default RateLimitConfig rateLimit() { return new RateLimitConfig(); }
}
