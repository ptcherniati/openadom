package fr.inra.oresing.executor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/// Configuration properties for async executors.
///
/// Maps properties from `application.properties` with prefix `executor`.
/// All properties can be overridden via environment variables.
///
/// ## Example environment variables
///
/// ```
/// export EXECUTOR_FAST_CORE_POOL_SIZE=300
/// export EXECUTOR_NORMAL_QUEUE_CAPACITY=2000
/// ```
@Configuration
@ConfigurationProperties(prefix = "executor")
public class ExecutorProperties {

    private final Fast fast = new Fast();
    private final Normal normal = new Normal();
    private final Heavy heavy = new Heavy();
    private final Backup backup = new Backup();

    private boolean useVirtualThreads = true;
    private boolean awaitTermination = true;
    private int awaitTerminationSeconds = 30;

    // Getters
    public Fast getFast() { return fast; }
    public Normal getNormal() { return normal; }
    public Heavy getHeavy() { return heavy; }
    public Backup getBackup() { return backup; }
    public boolean isUseVirtualThreads() { return useVirtualThreads; }
    public boolean isAwaitTermination() { return awaitTermination; }
    public int getAwaitTerminationSeconds() { return awaitTerminationSeconds; }

    // Setters
    public void setUseVirtualThreads(boolean useVirtualThreads) {
        this.useVirtualThreads = useVirtualThreads;
    }
    public void setAwaitTermination(boolean awaitTermination) {
        this.awaitTermination = awaitTermination;
    }
    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    /// Fast executor properties
    public static class Fast {
        private int corePoolSize = 200;
        private int maxPoolSize = 200;
        private int queueCapacity = Integer.MAX_VALUE;
        private String threadNamePrefix = "fast-";

        // Getters & Setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
    }

    /// Normal executor properties
    public static class Normal {
        private int corePoolSize = 100;
        private int maxPoolSize = 100;
        private int queueCapacity = 1000;
        private String threadNamePrefix = "normal-";

        // Getters & Setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
    }

    /// Heavy executor properties
    public static class Heavy {
        private int corePoolSize = 50;
        private int maxPoolSize = 50;
        private int queueCapacity = 200;
        private String threadNamePrefix = "heavy-";

        // Getters & Setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
    }

    /// Backup executor properties
    public static class Backup {
        private int corePoolSize = 50;
        private int maxPoolSize = 50;
        private int queueCapacity = 500;
        private String threadNamePrefix = "backup-";

        // Getters & Setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }
    }
}