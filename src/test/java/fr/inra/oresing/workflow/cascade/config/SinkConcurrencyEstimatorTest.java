package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("domain.model")
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
        var e = SinkConcurrencyEstimator.calculateEstimate(base());
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_INHERENT, e.mode());
        assertEquals(1, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + PER_CONNECTION_TEMP forced to 1")
    void directCopyPerConn() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
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
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
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
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
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
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
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
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_FORCED, e.mode());
        assertEquals(1, e.effectiveSinks());
    }

    @Test
    @DisplayName("DIRECT_COPY + PER_WORKFLOW_TABLE + STAGED = pool.sink workers")
    void perWorkflowStaged() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "PER_WORKFLOW_TABLE");
        m.put("pipelineMode", "STAGED");
        m.put("pool.sink", 3);
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.PARALLEL_POOL, e.mode());
        assertEquals(3, e.effectiveSinks());
        assertTrue(e.explanation().contains("STAGED"));
    }

    @Test
    @DisplayName("Fallback sur config indéfinie (sinkStrategy null) = SINGLE_INHERENT")
    void fallbackUndefinedConfig() {
        Map<String, Object> m = new HashMap<>();
        // Pas de sinkStrategy défini → null → fallback
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_INHERENT, e.mode());
        assertEquals(1, e.effectiveSinks());
        assertNotNull(e.explanation());
    }

    @Test
    @DisplayName("Fallback sur sinkStrategy inconnue = SINGLE_INHERENT")
    void fallbackUnknownStrategy() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "UNKNOWN_STRATEGY");
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
        assertEquals(SinkConcurrencyEstimator.Mode.SINGLE_INHERENT, e.mode());
        assertEquals(1, e.effectiveSinks());
    }

    @Test
    @DisplayName("intOf() avec pool.sink String numérique est parsé correctement")
    void intOfStringValue() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("pipelineMode", "PIPELINED");
        m.put("pool.sink", "5");   // String, pas Integer
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
        assertEquals(5, e.effectiveSinks());
    }

    @Test
    @DisplayName("intOf() avec pool.sink non-numérique revient au défaut (1)")
    void intOfNonNumericFallsBackToDefault() {
        Map<String, Object> m = base();
        m.put("sinkStrategy", "DIRECT_COPY");
        m.put("stagingStrategy", "SHARED_UNLOGGED");
        m.put("pipelineMode", "PIPELINED");
        m.put("pool.sink", "not-a-number");
        var e = SinkConcurrencyEstimator.calculateEstimate(m);
        // intOf fallback = 1 → Math.max(1,1) = 1 → SINGLE_FORCED
        assertEquals(1, e.effectiveSinks());
    }
}