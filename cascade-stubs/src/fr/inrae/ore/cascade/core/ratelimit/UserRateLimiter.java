package fr.inrae.ore.cascade.core.ratelimit;
import fr.inrae.ore.cascade.model.ratelimit.RateLimitConfig;
public class UserRateLimiter {
    public static void reset() {}
    public static UserRateLimiter initialize(RateLimitConfig config) { return new UserRateLimiter(); }
    public boolean tryAcquire(String userId) { return true; }
    public int getActiveCount(String userId) { return 0; }
    public void release(String userId) {}
}
