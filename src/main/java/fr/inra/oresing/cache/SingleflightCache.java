package fr.inra.oresing.cache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Generic singleflight pattern : coalesce concurrent compute requests
 * sharing the same key into a single computation . Followers wait on
 * the leader's {@link CompletableFuture} instead of triggering duplicate
 * work .
 *
 * <h2>Use case</h2>
 *
 * <p>On a cache miss for an expensive computation ( SQL query , remote
 * API , filesystem scan , etc. ) , N concurrent callers should not fire
 * N parallel computes . Singleflight guarantees exactly one compute per
 * concurrent miss ; the others wait .
 *
 * <h2>Trap evite</h2>
 *
 * <p>The naive pattern {@code computeIfAbsent + remove inside the mapper}
 * does NOT work : {@code ConcurrentHashMap} calls the mapper BEFORE
 * placing the returned value in the map , so {@code remove(key)} from
 * inside the lambda acts on a key not yet present -&gt; no-op . The slot
 * then stays forever , and the cached Future is returned indefinitely
 * to all subsequent callers . This class uses the correct
 * {@code putIfAbsent + remove(key , value)} idiom where cleanup runs
 * AFTER the slot is registered .
 *
 * <h2>Thread-safety</h2>
 *
 * <p>Safe for concurrent use . The leader does NOT hold any lock during
 * the compute ; followers acquire the future and call {@code get()} ,
 * which blocks until the leader signals completion . Atomic
 * {@code remove(key , value)} prevents stomping on a slot replaced by
 * another thread .
 *
 * <h2>Exception semantics</h2>
 *
 * <p>If the leader's {@code compute} throws , the exception is propagated
 * to the leader's caller AND to all followers awaiting the same Future
 * ( wrapped if checked ) . The slot is cleaned up so the next call to
 * {@link #load} retries the compute .
 *
 * @param <K> key type ( must define {@code equals} and {@code hashCode} )
 * @param <V> value type
 */
public final class SingleflightCache<K, V> {

    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    /**
     * Compute or await the value for {@code key} .
     *
     * <ul>
     *   <li>If no compute is in progress for this key , the calling thread
     *       becomes the leader : executes {@code compute} , publishes the
     *       result to any followers via the Future , and returns it .</li>
     *   <li>If a compute is already in progress for this key , the calling
     *       thread becomes a follower : awaits the leader's Future and
     *       returns its result without re-executing {@code compute} .</li>
     * </ul>
     *
     * <p>The in-flight slot is cleaned up atomically after completion
     * ( success or failure ) , allowing future calls to trigger a fresh
     * compute .
     *
     * @param key     cache key
     * @param compute supplier executed once per concurrent miss
     * @return the computed value
     * @throws RuntimeException if {@code compute} threw ( unchecked is
     *                          re-thrown as-is ; checked is wrapped )
     */
    public V load(K key, Supplier<V> compute) {
        final CompletableFuture<V> ourCf = new CompletableFuture<>();
        final CompletableFuture<V> existing = inFlight.putIfAbsent(key, ourCf);
        if (existing != null) {
            return await(existing, key);
        }
        try {
            V value = compute.get();
            ourCf.complete(value);
            return value;
        } catch (Throwable t) {
            ourCf.completeExceptionally(t);
            throw rethrow(t, "singleflight compute failed for key " + key);
        } finally {
            // Atomic remove only-if-same : drop the slot only if it still
            // holds OUR Future . Defends against a theoretical race where
            // another thread replaced our entry between completion and
            // cleanup ( cannot happen in current design since the slot is
            // held until completion , but correctness-by-default ) .
            inFlight.remove(key, ourCf);
        }
    }

    /**
     * Block until the leader's Future completes , propagate result or
     * exception cleanly .
     */
    private V await(CompletableFuture<V> future, K key) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw rethrow(cause, "singleflight wait failed for key " + key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("singleflight wait interrupted for key " + key, e);
        }
    }

    /**
     * Re-throw a captured exception preserving its type when unchecked ,
     * wrapping it in a {@link RuntimeException} when checked . Returns a
     * {@link RuntimeException} declared for callsites that need to satisfy
     * Java's exception flow ( {@code throw rethrow(...)} ) - though
     * control flow never returns because the method always throws .
     */
    private static RuntimeException rethrow(Throwable t, String fallbackMessage) {
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Error err) throw err;
        throw new RuntimeException(fallbackMessage, t);
    }

    /**
     * @return number of computes currently in flight ( for metrics ) .
     *         O(1) approximate count under heavy contention .
     */
    public int inFlightCount() {
        return inFlight.size();
    }
}
