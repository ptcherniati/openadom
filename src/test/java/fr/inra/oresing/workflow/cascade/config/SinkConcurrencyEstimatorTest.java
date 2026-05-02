package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SinkConcurrencyEstimator ( cascade 2.1.0 )")
class SinkConcurrencyEstimatorTest {

    private Map<String, Object> base() {
        Map<String, Object> m = new HashMap<>();
        m.put("sinkStrategy", "MERGE_FILE");
        m.put("stagingStrategy", "PER_CONNECTION_TEMP");
        m.put("pipelineMode", "STAGED");
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
    @DisplayName("DIRECT_COPY + SHARED_UNLOGGED + STAGED = pool.sink workers")
    void sharedStaged() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("pipelineMode", "STAGED");
        m.put("pool.sink", 8);
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.PARALLEL_POOL, e.mode());
        assertEquals(8, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + SHARED_UNLOGGED + PIPELINED = pool.sink workers")
    void sharedPipelined() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("pipelineMode", "PIPELINED");
        m.put("pool.sink", 6);
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.PARALLEL_POOL, e.mode());
        assertEquals(6, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + PER_WORKFLOW_TABLE + PIPELINED = pool.sink workers")
    void perWorkflowPipelined() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "PER_WORKFLOW_TABLE");
        m.put("pipelineMode", "PIPELINED");
        m.put("pool.sink", 4);
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.PARALLEL_POOL, e.mode());
        assertEquals(4, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + SHARED_UNLOGGED + pool.sink=1 = SINGLE_FORCED")
    void sharedSinglePool() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("pool.sink", 1);
        var e = SinkConcurrencyEstimator.estimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_FORCED, e.mode());
        assertEquals(1, e.effectiveSinks());
    }
}
