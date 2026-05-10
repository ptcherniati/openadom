package fr.inra.oresing.workflow.cascade.config.rules;

import fr.inra.oresing.workflow.cascade.config.ConsistencyRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les {@link ConsistencyRule} :
 * {@link DirectCopyPerConnectionStickyWarning} et
 * {@link StagingIgnoredOnMergeFileRule}.
 */
@Tag("domain.model")
@DisplayName("ConsistencyRules — règles de cohérence configuration")
class ConsistencyRulesTest {

    // ─── EffectiveConfig ──────────────────────────────────────────────────────

    @Test
    @DisplayName("EffectiveConfig.stringValue() retourne null quand la clé est absente")
    void effectiveConfigStringNull() {
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of());
        assertThat(cfg.stringValue("missing")).isNull();
    }

    @Test
    @DisplayName("EffectiveConfig.boolValue() retourne true pour Boolean.TRUE")
    void effectiveConfigBoolTrue() {
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(
                Map.of("enabled", Boolean.TRUE));
        assertThat(cfg.boolValue("enabled")).isTrue();
    }

    @Test
    @DisplayName("EffectiveConfig.boolValue() retourne false pour clé absente")
    void effectiveConfigBoolAbsent() {
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of());
        assertThat(cfg.boolValue("absent")).isFalse();
    }

    @Test
    @DisplayName("EffectiveConfig.boolValue() retourne true pour la chaîne 'true' (insensible à la casse)")
    void effectiveConfigBoolStringTrue() {
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(
                Map.of("flag", "True"));
        assertThat(cfg.boolValue("flag")).isTrue();
    }

    // ─── DirectCopyPerConnectionStickyWarning ────────────────────────────────

    @Test
    @DisplayName("DIRECT_COPY + PER_CONNECTION_TEMP → WARNING produit")
    void directCopyPlusPerConnectionWarning() {
        DirectCopyPerConnectionStickyWarning rule = new DirectCopyPerConnectionStickyWarning();
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of(
                "sinkStrategy",    "DIRECT_COPY",
                "stagingStrategy", "PER_CONNECTION_TEMP"));

        assertThat(rule.check(cfg)).isPresent();
        assertThat(rule.check(cfg).get()).containsIgnoringCase("sinkParallelism");
    }

    @Test
    @DisplayName("DIRECT_COPY + SHARED_UNLOGGED → pas de warning")
    void directCopyPlusSharedUnlogged() {
        DirectCopyPerConnectionStickyWarning rule = new DirectCopyPerConnectionStickyWarning();
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of(
                "sinkStrategy",    "DIRECT_COPY",
                "stagingStrategy", "SHARED_UNLOGGED"));

        assertThat(rule.check(cfg)).isEmpty();
    }

    @Test
    @DisplayName("MERGE_FILE + PER_CONNECTION_TEMP → pas de warning (rule scope DIRECT_COPY only)")
    void mergeFilePlusPerConnection() {
        DirectCopyPerConnectionStickyWarning rule = new DirectCopyPerConnectionStickyWarning();
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of(
                "sinkStrategy",    "MERGE_FILE",
                "stagingStrategy", "PER_CONNECTION_TEMP"));

        assertThat(rule.check(cfg)).isEmpty();
    }

    @Test
    @DisplayName("DirectCopyPerConnectionStickyWarning : code et severity correctement définis")
    void directCopyCodeAndSeverity() {
        DirectCopyPerConnectionStickyWarning rule = new DirectCopyPerConnectionStickyWarning();
        assertThat(rule.code()).isEqualTo("DIRECT_COPY_PER_CONNECTION_FORCES_SERIAL_SINK");
        assertThat(rule.severity()).isEqualTo(ConsistencyRule.Severity.WARNING);
    }

    // ─── StagingIgnoredOnMergeFileRule ────────────────────────────────────────

    @Test
    @DisplayName("MERGE_FILE + stagingStrategy non-null → WARNING produit")
    void mergFileWithStagingWarning() {
        StagingIgnoredOnMergeFileRule rule = new StagingIgnoredOnMergeFileRule();
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of(
                "sinkStrategy",    "MERGE_FILE",
                "stagingStrategy", "SHARED_UNLOGGED"));

        assertThat(rule.check(cfg)).isPresent();
        assertThat(rule.check(cfg).get()).containsIgnoringCase("SHARED_UNLOGGED");
    }

    @Test
    @DisplayName("MERGE_FILE + stagingStrategy null → pas de warning")
    void mergeFileNoStagingOk() {
        StagingIgnoredOnMergeFileRule rule = new StagingIgnoredOnMergeFileRule();
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of(
                "sinkStrategy", "MERGE_FILE"));

        assertThat(rule.check(cfg)).isEmpty();
    }

    @Test
    @DisplayName("DIRECT_COPY + stagingStrategy quelconque → pas de warning")
    void directCopyNotCoveredByMergeFileRule() {
        StagingIgnoredOnMergeFileRule rule = new StagingIgnoredOnMergeFileRule();
        ConsistencyRule.EffectiveConfig cfg = new ConsistencyRule.EffectiveConfig(Map.of(
                "sinkStrategy",    "DIRECT_COPY",
                "stagingStrategy", "PER_CONNECTION_TEMP"));

        assertThat(rule.check(cfg)).isEmpty();
    }

    @Test
    @DisplayName("StagingIgnoredOnMergeFileRule : code et severity correctement définis")
    void stagingIgnoredCodeAndSeverity() {
        StagingIgnoredOnMergeFileRule rule = new StagingIgnoredOnMergeFileRule();
        assertThat(rule.code()).isEqualTo("STAGING_STRATEGY_IGNORED_ON_MERGE_FILE");
        assertThat(rule.severity()).isEqualTo(ConsistencyRule.Severity.WARNING);
    }
}
