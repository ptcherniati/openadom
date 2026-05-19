package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CascadePoolReloader")
@Tag("domain.model")
class CascadePoolReloaderTest {

    private CascadePoolReloader newReloaderWithRealTpe(ThreadPoolExecutor tpe) {
        Map<PoolReloader.Stage, ExecutorService> map =
                new EnumMap<>(PoolReloader.Stage.class);
        for (PoolReloader.Stage s : PoolReloader.Stage.values()) map.put(s, tpe);
        return new CascadePoolReloader(map::get);
    }

    @Test
    @DisplayName("resize grow : setMax then setCore")
    void resizeGrow() {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(
                2, 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10));
        CascadePoolReloader r = newReloaderWithRealTpe(tpe);

        r.resize(PoolReloader.Stage.SOURCE, 8);

        assertEquals(8, tpe.getCorePoolSize());
        assertEquals(8, tpe.getMaximumPoolSize());
        tpe.shutdownNow();
    }

    @Test
    @DisplayName("resize shrink : setCore then setMax")
    void resizeShrink() {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(
                8, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10));
        CascadePoolReloader r = newReloaderWithRealTpe(tpe);

        r.resize(PoolReloader.Stage.TRANSFORM, 2);

        assertEquals(2, tpe.getCorePoolSize());
        assertEquals(2, tpe.getMaximumPoolSize());
        tpe.shutdownNow();
    }

    @Test
    @DisplayName("resize 0 throws")
    void resizeZero() {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(
                4, 4, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10));
        CascadePoolReloader r = newReloaderWithRealTpe(tpe);
        assertThrows(IllegalArgumentException.class,
                () -> r.resize(PoolReloader.Stage.SINK, 0));
        tpe.shutdownNow();
    }

    @Test
    @DisplayName("resize on virtual-thread executor throws ( not a TPE )")
    void resizeOnNonTpe() {
        ExecutorService vt = Executors.newSingleThreadExecutor();   // not a TPE in this case is hard
        // Use something definitely not TPE :
        ExecutorService raw = new ExecutorService() {
            @Override public void shutdown() {
                // mock
            }
            @Override public java.util.List<Runnable> shutdownNow() { return java.util.List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
            @Override public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) { throw new UnsupportedOperationException(); }
            @Override public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) { throw new UnsupportedOperationException(); }
            @Override public java.util.concurrent.Future<?> submit(Runnable task) { throw new UnsupportedOperationException(); }
            @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { throw new UnsupportedOperationException(); }
            @Override public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long t, TimeUnit u) { throw new UnsupportedOperationException(); }
            @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { throw new UnsupportedOperationException(); }
            @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long t, TimeUnit u) { throw new UnsupportedOperationException(); }
            @Override public void execute(Runnable command) {
                // mock
            }
        };
        CascadePoolReloader r = new CascadePoolReloader(s -> raw);

        assertThrows(IllegalStateException.class,
                () -> r.resize(PoolReloader.Stage.ORDERING, 4));
        vt.shutdownNow();
    }

    @Test
    @DisplayName("snapshot reflects TPE state")
    void snapshot() {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(
                3, 3, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(50));
        CascadePoolReloader r = newReloaderWithRealTpe(tpe);
        PoolReloader.PoolSnapshot s = r.snapshot(PoolReloader.Stage.SOURCE);
        assertEquals(PoolReloader.Stage.SOURCE, s.stage());
        assertEquals(3, s.corePoolSize());
        assertEquals(3, s.maximumPoolSize());
        assertEquals(50, s.queueCapacity());
        tpe.shutdownNow();
    }
}