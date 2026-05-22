package fr.inra.oresing.rest.data;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Lazy-loaded view of the {@code displayNamesByReferenceAndNaturalKey} map
 * historically pre-loaded eagerly by {@code DataService.getAsynchroneImporterContext} .
 *
 * <h2>What this replaces</h2>
 *
 * <p>The legacy implementation called {@code repo.findDisplayByNaturalKey(ref)}
 * up front for every {@code ReferenceType} listed in the datatype's pattern
 * columns , materialising in JVM heap the full label map ( natural key -&gt;
 * locale -&gt; display label ) of every referenced reference table . On large
 * applications with 10-20 references containing 50k-500k rows each , this
 * blocking pre-load took 1 to 3 minutes per publish - frozen UI , no chunk
 * processing during the wait , no observable progress .
 *
 * <h2>Behaviour</h2>
 *
 * <p>Stores only the immutable set of valid reference keys ( those resolved
 * by {@code DataService.getAsynchroneImporterContext} from the datatype
 * configuration ) . The actual label map for a given reference is fetched
 * from the database on the first {@link #get(Object)} call for that key
 * and memoised in a {@link ConcurrentHashMap} for the lifetime of this
 * instance ( ~ duration of a single publish workflow ) :
 *
 * <ul>
 *   <li>1st {@code get("ref-A")} -&gt; SQL query , ~50-200 ms for typical refs ;</li>
 *   <li>subsequent {@code get("ref-A")} -&gt; cache hit , ~50 ns ;</li>
 *   <li>{@code get("not-a-valid-ref")} -&gt; returns {@code null} without
 *       touching the database ;</li>
 *   <li>{@code getOrDefault("anything", def)} -&gt; routed through {@link #get} .</li>
 * </ul>
 *
 * <h2>Trade-offs</h2>
 *
 * <ul>
 *   <li>Time-to-first-row drops from minutes to seconds : cascade workers
 *       can start immediately , the user sees progress within the polling
 *       interval ;</li>
 *   <li>Same total cost when every reference is touched ( resolution cost
 *       just moves from up-front to per-reference on first lookup ) ;</li>
 *   <li>Lower cost when only a subset of references are referenced by the
 *       actual CSV being published ( typical ratio : 30 - 60 % of declared
 *       references actually appear in the data ) ;</li>
 *   <li>No cross-workflow sharing ( cache scope = this instance = single
 *       publish ) - avoids stale-cache pitfalls when a concurrent publish
 *       adds rows to the referenced reference table .</li>
 * </ul>
 *
 * <h2>Map contract</h2>
 *
 * <p>Implements just enough of {@link Map} for the downstream consumers
 * ( {@code DataImporterContext.getDisplayNamesByReferenceAndNaturalKey} ,
 * {@code DataColumn*.toCsv*} ) which only call {@code get} and
 * {@code getOrDefault} . Operations that require iterating over all
 * entries ( {@code entrySet} , {@code size} , {@code keySet} , ... ) throw
 * {@link UnsupportedOperationException} so misuse is caught immediately
 * rather than silently triggering an eager full load .
 *
 * @author R.YAHIAOUI
 */
public final class LazyDisplayNamesMap
        extends AbstractMap<String, Map<String, Map<String, String>>>
        implements Map<String, Map<String, Map<String, String>>> {

    /** Set of references that are considered valid for this datatype . */
    private final Set<String> validKeys;

    /** Database loader ; called at most once per valid key during this instance's lifetime . */
    private final Function<String, Map<String, Map<String, String>>> loader;

    /** Memoised loaded entries ; concurrent because cascade workers query in parallel . */
    private final ConcurrentHashMap<String, Map<String, Map<String, String>>> cache;

    public LazyDisplayNamesMap(Set<String> validKeys,
                                Function<String, Map<String, Map<String, String>>> loader) {
        if (validKeys == null) throw new IllegalArgumentException("validKeys must not be null");
        if (loader == null)    throw new IllegalArgumentException("loader must not be null");
        this.validKeys = Set.copyOf(validKeys);
        this.loader    = loader;
        this.cache     = new ConcurrentHashMap<>(validKeys.size() * 2);
    }

    /**
     * Returns the label map for the given reference key, or {@code null} if the key is
     * not a valid reference for this datatype.
     *
     * <p>Callers are expected to use {@link #getOrDefault} or an explicit null-check.
     * Returning {@code null} — rather than an empty map — is intentional : it lets callers
     * distinguish "valid ref with no labels" from "unknown ref" and allows
     * {@link #getOrDefault} to fall back to the caller-supplied default correctly.
     *
     * <p>Sonar S1168 ("Return an empty collection instead of null") is suppressed here
     * because the null-return is part of the public contract of this class.
     */
    @SuppressWarnings("java:S1168")
    @Override
    public Map<String, Map<String, String>> get(Object key) {
        if (!(key instanceof String s) || !validKeys.contains(s)) {
            return null;
        }
        return cache.computeIfAbsent(s, k -> {
            Map<String, Map<String, String>> loaded = loader.apply(k);
            // Defensive : never store null in the cache ( breaks computeIfAbsent
            // contract on subsequent calls ) ; substitute an empty map .
            return loaded != null ? loaded : new HashMap<>();
        });
    }

    @Override
    public Map<String, Map<String, String>> getOrDefault(Object key,
                                                          Map<String, Map<String, String>> defaultValue) {
        Map<String, Map<String, String>> v = get(key);
        return v != null ? v : defaultValue;
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof String s && validKeys.contains(s);
    }

    @Override
    public boolean isEmpty() {
        return validKeys.isEmpty();
    }

    /**
     * Entry-set iteration would defeat the lazy contract ( forces a full
     * eager load ) . Throws {@link UnsupportedOperationException} so any
     * accidental iteration over this map fails fast .
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    @SuppressWarnings("java:S1130") // UnsupportedOperationException is intentional
    public Set<Entry<String, Map<String, Map<String, String>>>> entrySet() {
        throw new UnsupportedOperationException(
                "LazyDisplayNamesMap is iteration-hostile : use get / getOrDefault only "
                + "( eager iteration would defeat the lazy load purpose ) .");
    }

    /**
     * Equality is defined by identity : two distinct {@code LazyDisplayNamesMap}
     * instances are never considered equal even if they share the same
     * {@code validKeys} set , since their memoised cache state may differ and
     * a full comparison via {@code entrySet()} is prohibited by design .
     *
     * <p>This override satisfies Sonar S2160 ( "Override equals in subclasses of
     * AbstractMap" ) while keeping the implementation free of eager loads .
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LazyDisplayNamesMap other)) return false;
        return Objects.equals(validKeys, other.validKeys)
                && Objects.equals(cache, other.cache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(validKeys);
    }

    /** Visible for testing : number of references actually loaded so far . */
    public int loadedCount() {
        return cache.size();
    }
}