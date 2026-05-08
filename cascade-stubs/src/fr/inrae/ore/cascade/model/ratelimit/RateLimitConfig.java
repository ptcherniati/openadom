package fr.inrae.ore.cascade.model.ratelimit;
public class RateLimitConfig {
    private final int maxWorkflowsPerUser;
    private final long acquireTimeoutSeconds;
    private final boolean enabled;
    private final RejectionPolicy rejectionPolicy;

    public RateLimitConfig(int maxWorkflowsPerUser, long acquireTimeoutSeconds,
                           boolean enabled, RejectionPolicy rejectionPolicy) {
        this.maxWorkflowsPerUser = maxWorkflowsPerUser;
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
        this.enabled = enabled;
        this.rejectionPolicy = rejectionPolicy;
    }
    public RateLimitConfig() { this(5, 60L, false, null); }
    public int maxWorkflowsPerUser() { return maxWorkflowsPerUser; }
    public long acquireTimeoutSeconds() { return acquireTimeoutSeconds; }
    public boolean enabled() { return enabled; }
    public RejectionPolicy rejectionPolicy() { return rejectionPolicy; }
}
