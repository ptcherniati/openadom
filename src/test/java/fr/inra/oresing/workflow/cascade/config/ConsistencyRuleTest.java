package fr.inra.oresing.workflow.cascade.config;

import fr.inra.oresing.workflow.cascade.config.rules.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsistencyRule strategies ( cascade 2.1.0 )")
class ConsistencyRuleTest {

    private ConsistencyRule.EffectiveConfig cfg(Map<String, Object> overrides) {
        Map<String, Object> base = new HashMap<>();
        base.put("sinkStrategy", "MERGE_FILE");
        base.put("stagingStrategy", "PER_CONNECTION_TEMP");
        base.put("pipelineMode", "STAGED");
        base.putAll(overrides);
        return new ConsistencyRule.EffectiveConfig(base);
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
    @DisplayName("Rules have unique codes")
    void uniqueCodes() {
        var codes = java.util.List.of(
                new StagingIgnoredOnMergeFileRule().code(),
                new DirectCopyPerConnectionStickyWarning().code());
        assertEquals(codes.size(), new java.util.HashSet<>(codes).size(),
                "All rule codes must be unique");
    }
}
