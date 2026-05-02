package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SinkConcurrencyEstimator")
class SinkConcurrencyEstimatorTest {

    private Map<String, Object> base() {
        Map<String, Object> m = new HashMap<>();
        m.put("sinkStrategy", "MERGE_FILE");
        m.put("stagingStrategy", "PER_CONNECTION_TEMP");
        m.put("executionMode", "SYNC");
        m.put("directWriteParallel", false);
        m.put("pool.sink", 4);
        return m;
    }

    @Test
    @DisplayName("MERGE_FILE always serial inherent")
    void mergeFile() {
        var e = SinkConcurrencyEstimator.estimate(base());
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_INHERENT, e.mode());
        assertEquals(1, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + PER_CONNECTION_TEMP forced to 1")
    void directCopyPerConn() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_FORCED, e.mode());
        assertEquals(1, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + SHARED_UNLOGGED + ASYNC = pool parallel")
    void sharedAsync() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("executionMode", "ASYNC");
        m.put("pool.sink", 8);
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.PARALLEL_POOL, e.mode());
        assertEquals(8, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + SHARED_UNLOGGED + SYNC + dwp = pool parallel")
    void sharedSyncDwp() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("executionMode", "SYNC");
        m.put("directWriteParallel", true);
        m.put("pool.sink", 6);
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.PARALLEL_POOL, e.mode());
        assertEquals(6, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + SHARED_UNLOGGED + SYNC sans dwp = 1")
    void sharedSyncNoDwp() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("executionMode", "SYNC");
        m.put("directWriteParallel", false);
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_FORCED, e.mode());
        assertEquals(1, e.effectiveSinks());
    }
}
