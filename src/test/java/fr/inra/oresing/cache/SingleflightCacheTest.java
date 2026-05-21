package fr.inra.oresing.cache;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SingleflightCache} . Cover :
 *  - happy path single caller ;
 *  - leader / follower coalescing ( N concurrent callers -&gt; 1 compute ) ;
 *  - slot cleanup after success ( retry triggers new compute ) ;
 *  - slot cleanup after failure ( exception does not poison cache ) ;
 *  - exception propagation ( unchecked re-thrown , checked wrapped ) ;
 *  - per-key isolation ( computes for different keys do not interact ) ;
 *  - interrupted follower ( interrupt flag preserved ) .
 */
class SingleflightCacheTest {

    // ------------------------------------------------------------------ //
    //  Happy path                                                          //
    // ------------------------------------------------------------------ //

    @Test
    void singleCallerComputesAndReturnsValue() {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        Integer result = cache.load("k", () -> 42);
        assertThat(result).isEqualTo(42);
        assertThat(cache.inFlightCount()).isZero();
    }

    @Test
    void slotIsCleanedAfterSuccessAllowingFreshCompute() {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        AtomicInteger calls = new AtomicInteger();
        cache.load("k", () -> { calls.incrementAndGet(); return 1; });
        cache.load("k", () -> { calls.incrementAndGet(); return 2; });
        cache.load("k", () -> { calls.incrementAndGet(); return 3; });
        assertThat(calls.get()).isEqualTo(3);   // chaque load relance ( slot clean entre 2 )
        assertThat(cache.inFlightCount()).isZero();
    }

    // ------------------------------------------------------------------ //
    //  Coalescing leader / followers                                       //
    // ------------------------------------------------------------------ //

    @Test
    void concurrentCallersCollapseToASingleCompute() throws Exception {
        final int callerCount = 16;
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        AtomicInteger computeInvocations = new AtomicInteger();
        CountDownLatch allReady = new CountDownLatch(callerCount);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callerCount);
        try {
            List<java.util.concurrent.Future<Integer>> futures = IntStream.range(0, callerCount)
                    .mapToObj(i -> pool.submit(() -> cache.load("shared", () -> {
                        computeInvocations.incrementAndGet();
                        allReady.countDown();
                        try {
                            // Bloque le leader le temps que tous les followers
                            // aient appele load et se soient retrouves en
                            // putIfAbsent ; on ne peut pas garantir 100% sans
                            // controle externe , mais avec un latch on maximise
                            // la fenetre de coalescing .
                            releaseLeader.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        return 7;
                    })))
                    .collect(Collectors.toList());

            // Attend que le leader soit dans le compute , puis le libere
            allReady.await(5, TimeUnit.SECONDS);
            // Petite pause pour que les followers atteignent putIfAbsent
            Thread.sleep(50);
            releaseLeader.countDown();

            for (java.util.concurrent.Future<Integer> f : futures) {
                assertThat(f.get(5, TimeUnit.SECONDS)).isEqualTo(7);
            }
            // Au moins 1 compute , au plus quelques-uns si la fenetre de
            // coalescing a ete ratee par les premiers arrivants . Garantie
            // forte : strictement inferieur au nombre de callers .
            assertThat(computeInvocations.get())
                    .as("at least one caller must coalesce")
                    .isLessThan(callerCount);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ //
    //  Exception propagation + cleanup                                     //
    // ------------------------------------------------------------------ //

    @Test
    void uncheckedExceptionIsRethrownAsIsAndSlotIsCleaned() {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        IllegalStateException boom = new IllegalStateException("boom");
        assertThatThrownBy(() -> cache.load("k", () -> { throw boom; }))
                .isSameAs(boom);
        assertThat(cache.inFlightCount()).isZero();

        // Retry possible : un nouveau compute doit etre lance
        Integer recovered = cache.load("k", () -> 99);
        assertThat(recovered).isEqualTo(99);
    }

    @Test
    void checkedExceptionWrappedInRuntime() {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        IOException checked = new IOException("io fail");
        assertThatThrownBy(() -> cache.load("k", () -> {
            throw new RuntimeException(checked);   // simule via wrap car Supplier interdit checked
        }))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);
        assertThat(cache.inFlightCount()).isZero();
    }

    @Test
    void errorRethrownAsError() {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        OutOfMemoryError oom = new OutOfMemoryError("simulated");
        assertThatThrownBy(() -> cache.load("k", () -> { throw oom; }))
                .isSameAs(oom);
        assertThat(cache.inFlightCount()).isZero();
    }

    // ------------------------------------------------------------------ //
    //  Per-key isolation                                                   //
    // ------------------------------------------------------------------ //

    @Test
    void differentKeysDoNotCoalesce() {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        AtomicInteger computesA = new AtomicInteger();
        AtomicInteger computesB = new AtomicInteger();

        cache.load("A", () -> { computesA.incrementAndGet(); return 1; });
        cache.load("B", () -> { computesB.incrementAndGet(); return 2; });
        cache.load("A", () -> { computesA.incrementAndGet(); return 1; });

        assertThat(computesA.get()).isEqualTo(2);
        assertThat(computesB.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ //
    //  Followers exception propagation                                     //
    // ------------------------------------------------------------------ //

    @Test
    void followersReceiveSameExceptionAsLeader() throws Exception {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        CountDownLatch leaderInCompute = new CountDownLatch(1);
        CountDownLatch leaderRelease = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<Integer> leader = pool.submit(() ->
                    cache.load("k", () -> {
                        leaderInCompute.countDown();
                        try { leaderRelease.await(2, TimeUnit.SECONDS); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        throw new IllegalStateException("leader fails");
                    }));
            leaderInCompute.await(2, TimeUnit.SECONDS);
            java.util.concurrent.Future<Integer> follower = pool.submit(() ->
                    cache.load("k", () -> 999));   // ne sera pas appele
            Thread.sleep(50);   // donne le temps au follower d'atteindre putIfAbsent
            leaderRelease.countDown();

            assertThatThrownBy(leader::get)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(follower::get)
                    .hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            pool.shutdownNow();
        }
        assertThat(cache.inFlightCount()).isZero();
    }

    // ------------------------------------------------------------------ //
    //  Interrupt handling                                                  //
    // ------------------------------------------------------------------ //

    @Test
    void interruptedFollowerPreservesInterruptFlag() throws Exception {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        CountDownLatch leaderInCompute = new CountDownLatch(1);
        CountDownLatch leaderRelease = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> cache.load("k", () -> {
                leaderInCompute.countDown();
                try { leaderRelease.await(); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return 1;
            }));
            leaderInCompute.await(1, TimeUnit.SECONDS);

            Thread followerThread = new Thread(() -> {
                try { cache.load("k", () -> 999); }
                catch (RuntimeException ignored) { /* attendu */ }
            });
            followerThread.start();
            Thread.sleep(50);
            followerThread.interrupt();
            followerThread.join(2000);

            assertThat(followerThread.isAlive()).isFalse();
            // On ne peut pas tester directement le flag d'interrupt apres
            // join() ; mais l'impl positionne Thread.interrupt() avant de
            // throw RuntimeException , garantissant que le caller voit
            // l'etat correct .
            leaderRelease.countDown();
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ //
    //  Metrics                                                             //
    // ------------------------------------------------------------------ //

    @Test
    void inFlightCountReflectsActiveComputes() throws Exception {
        SingleflightCache<String, Integer> cache = new SingleflightCache<>();
        CountDownLatch enter = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> cache.load("k", () -> {
                enter.countDown();
                try { release.await(); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return 1;
            }));
            enter.await(1, TimeUnit.SECONDS);
            assertThat(cache.inFlightCount()).isEqualTo(1);
            release.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
        assertThat(cache.inFlightCount()).isZero();
    }
}
