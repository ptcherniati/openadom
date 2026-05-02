package fr.inra.oresing.workflow.cascade.config;

import fr.inra.oresing.workflow.cascade.config.rules.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsistencyRule strategies")
class ConsistencyRuleTest {

    private ConsistencyRule.EffectiveConfig cfg(Map<String, Object> overrides) {
        Map<String, Object> base = new HashMap<>();
        base.put("sinkStrategy", "MERGE_FILE");
        base.put("stagingStrategy", "PER_CONNECTION_TEMP");
        base.put("executionMode", "SYNC");
        base.put("streamingMode", "BUFFERED");
        base.put("directWriteParallel", false);
        base.putAll(overrides);
        return new ConsistencyRule.EffectiveConfig(base);
    }

    @Test
    @DisplayName("MergeFileAsyncParallelRule blocks MERGE_FILE+ASYNC+directWriteParallel")
    void mergeFileAsyncParallel_blocked() {
        var rule = new MergeFileAsyncParallelRule();
        assertEquals(ConsistencyRule.Severity.BLOCKING, rule.severity());
        assertTrue(rule.check(cfg(Map.of(
                "sinkStrategy", "MERGE_FILE",
                "executionMode", "ASYNC",
                "directWriteParallel", true))).isPresent());
        assertFalse(rule.check(cfg(Map.of(
                "sinkStrategy", "MERGE_FILE",
                "executionMode", "ASYNC",
                "directWriteParallel", false))).isPresent());
    }

    @Test
    @DisplayName("StagingIgnoredOnMergeFileRule warns when MERGE_FILE+stagingStrategy set")
    void stagingIgnored_warning() {
        var rule = new StagingIgnoredOnMergeFileRule();
        assertEquals(ConsistencyRule.Severity.WARNING, rule.severity());
        assertTrue(rule.check(cfg(Map.of(
                "sinkStrategy", "MERGE_FILE",
                "stagingStrategy", "SHARED_UNLOGGED"))).isPresent());
        assertFalse(rule.check(cfg(Map.of(
                "sinkStrategy", "DIRECT_COPY",
                "stagingStrategy", "PER_CONNECTION_TEMP"))).isPresent());
    }

    @Test
    @DisplayName("DirectCopyPerConnectionStickyWarning warns serial sink")
    void directCopySticky_warning() {
        var rule = new DirectCopyPerConnectionStickyWarning();
        assertEquals(ConsistencyRule.Severity.WARNING, rule.severity());
        assertTrue(rule.check(cfg(Map.of(
                "sinkStrategy", "DIRECT_COPY",
                "stagingStrategy", "PER_CONNECTION_TEMP"))).isPresent());
        assertFalse(rule.check(cfg(Map.of(
                "sinkStrategy", "MERGE_FILE",
                "stagingStrategy", "PER_CONNECTION_TEMP"))).isPresent());
    }

    @Test
    @DisplayName("SyncDirectParallelRule warns SYNC+directWriteParallel")
    void syncDirectParallel_warning() {
        var rule = new SyncDirectParallelRule();
        assertTrue(rule.check(cfg(Map.of(
                "executionMode", "SYNC",
                "directWriteParallel", true))).isPresent());
        assertFalse(rule.check(cfg(Map.of(
                "executionMode", "SYNC",
                "directWriteParallel", false))).isPresent());
    }

    @Test
    @DisplayName("MergeFileAsyncNoEffectRule warns no real effect")
    void mergeFileAsyncNoEffect_warning() {
        var rule = new MergeFileAsyncNoEffectRule();
        assertTrue(rule.check(cfg(Map.of(
                "sinkStrategy", "MERGE_FILE",
                "executionMode", "ASYNC"))).isPresent());
        assertFalse(rule.check(cfg(Map.of(
                "sinkStrategy", "DIRECT_COPY",
                "executionMode", "ASYNC"))).isPresent());
    }

    @Test
    @DisplayName("Rules have unique codes")
    void uniqueCodes() {
        var codes = java.util.List.of(
                new MergeFileAsyncParallelRule().code(),
                new StagingIgnoredOnMergeFileRule().code(),
                new DirectCopyPerConnectionStickyWarning().code(),
                new SyncDirectParallelRule().code(),
                new MergeFileAsyncNoEffectRule().code());
        assertEquals(codes.size(), new java.util.HashSet<>(codes).size(),
                "All rule codes must be unique");
    }
}
