package fr.inrae.ore.cascade.core.execution;
public interface PoolSnapshot {
    String stage();
    String threadNamePrefix();
    int configuredThreads();
    int activeCount();
    int poolSize();
    int queueSize();
    int queueCapacity();
    long taskCount();
    long completedTaskCount();
    boolean virtualThreads();
}
