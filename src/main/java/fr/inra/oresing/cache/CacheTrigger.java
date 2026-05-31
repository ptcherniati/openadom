package fr.inra.oresing.cache;

import java.time.Instant;

/**
 * Last cause that invalidated / refreshed a backend memory cache .
 *
 * <p>Recorded by {@link CacheInvalidationTracker} at every invalidation
 * call site so the admin UI ( oa-live , Caches tab ) can explain WHY the
 * "last update" timestamp moved : which business operation , on which
 * datatype / reference , triggered by whom , and when .
 *
 * @param operation business operation : DEPOSIT , PUBLISH , UNPUBLISH ,
 *                  DELETE_FILE , DELETE_DATA , CONFIG_UPDATE ,
 *                  MANUAL_REFRESH , ADMIN_CLEAR_APP , ADMIN_CLEAR_ALL .
 * @param application application name ( null for global admin clear ) .
 * @param targetKind DATATYPE | REFERENCE | null ( e.g. config update has
 *                   no single target ) .
 * @param targetName datatype or reference name ( null when not applicable ) .
 * @param login      login that triggered the operation ( null if unknown ) .
 * @param at         instant the trigger was recorded .
 */
public record CacheTrigger(
        String operation,
        String application,
        String targetKind,
        String targetName,
        String login,
        Instant at
) {
    public static final String DATATYPE = "DATATYPE";
    public static final String REFERENCE = "REFERENCE";

    /** Convenience factory stamping {@code at = Instant.now()} . */
    public static CacheTrigger now(String operation, String application,
                                   String targetKind, String targetName, String login) {
        return new CacheTrigger(operation, application, targetKind, targetName, login, Instant.now());
    }
}
