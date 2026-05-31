package fr.inra.oresing.cache;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * In-memory record of the LAST invalidation / refresh trigger per backend
 * memory cache ( same coarse granularity as {@link MemoryCache#lastWriteAt()}
 * : one entry per cache name , not per cache key ) .
 *
 * <p>Observability-only , best-effort : it never throws and never blocks the
 * calling operation . Lost on restart ( like the caches themselves ) . Read
 * by the admin Caches endpoint to populate the "what triggered the last
 * update" modal in oa-live .
 *
 * <p>Recording is done at the invalidation call sites ( deposit , publish ,
 * unpublish , file delete , config update , manual refresh , admin clear ) so
 * the cache internals ({@link MemoryCache}) stay untouched .
 */
@Component
public class CacheInvalidationTracker {

    /** Stable cache names , aligned with the admin stats endpoint keys . */
    public static final String FILTER_LIST = "filterList";
    public static final String AUTHORIZATION_SCOPES = "authorizationScopes";
    public static final String CHECKED_FORMAT_COMPONENTS = "checkedFormatComponents";
    public static final String REFERENCED_FILES = "referencedFiles";

    private final Map<String, CacheTrigger> lastByCache = new ConcurrentHashMap<>();

    /** Record the last trigger for a single cache ( no-op on null args ) . */
    public void record(String cacheName, CacheTrigger trigger) {
        if (cacheName == null || trigger == null) {
            return;
        }
        lastByCache.put(cacheName, trigger);
    }

    /**
     * Record for the filterList family ( filterList + authorizationScopes +
     * checkedFormatComponents ) which is always invalidated together in
     * cascade : a single business operation moves all three .
     */
    public void recordFilterFamily(CacheTrigger trigger) {
        record(FILTER_LIST, trigger);
        record(AUTHORIZATION_SCOPES, trigger);
        record(CHECKED_FORMAT_COMPONENTS, trigger);
    }

    /** Last trigger for a cache , or {@code null} if none recorded yet . */
    public CacheTrigger last(String cacheName) {
        return lastByCache.get(cacheName);
    }
}
