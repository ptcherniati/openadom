package fr.inra.oresing.rest.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link DashboardConfigDTO} et ses sous-records.
 * Vérifie la construction et l'accès aux champs sans Spring context.
 */
@Tag("domain.model")
@DisplayName("DashboardConfigDTO — records de configuration dashboard")
class DashboardConfigDtoTest {

    // ─── ImportConfig ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("ImportConfig : tous les champs accessibles")
    void importConfigFields() {
        DashboardConfigDTO.ImportConfig cfg = new DashboardConfigDTO.ImportConfig(
                1000, 4, 100, 50, "/tmp/chunks", "/tmp/processed");
        assertThat(cfg.chunkSizeLines()).isEqualTo(1000);
        assertThat(cfg.parallelism()).isEqualTo(4);
        assertThat(cfg.progressBatchSize()).isEqualTo(100);
        assertThat(cfg.maxErrorsThreshold()).isEqualTo(50);
        assertThat(cfg.chunksTempDir()).isEqualTo("/tmp/chunks");
        assertThat(cfg.processedTempDir()).isEqualTo("/tmp/processed");
    }

    @Test
    @DisplayName("ImportConfig : record equality")
    void importConfigEquality() {
        DashboardConfigDTO.ImportConfig a = new DashboardConfigDTO.ImportConfig(500, 2, 50, 100, "/a", "/b");
        DashboardConfigDTO.ImportConfig b = new DashboardConfigDTO.ImportConfig(500, 2, 50, 100, "/a", "/b");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    // ─── RateLimitConfig ──────────────────────────────────────────────────────

    @Test
    @DisplayName("RateLimitConfig : tous les champs accessibles")
    void rateLimitConfigFields() {
        Map<String, Integer> slots = Map.of("user1", 2);
        DashboardConfigDTO.RateLimitConfig cfg = new DashboardConfigDTO.RateLimitConfig(3, 5, 0L, slots);
        assertThat(cfg.maxConcurrentImportsPerUser()).isEqualTo(3);
        assertThat(cfg.maxConcurrentExtractionsPerUser()).isEqualTo(5);
        assertThat(cfg.extractionAcquireTimeoutSeconds()).isZero();
        assertThat(cfg.usedImportSlotsByUser()).containsEntry("user1", 2);
    }

    // ─── RuntimeInfo ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("RuntimeInfo : tous les champs accessibles")
    void runtimeInfoFields() {
        DashboardConfigDTO.RuntimeInfo info = new DashboardConfigDTO.RuntimeInfo(
                "3.0.0", "25", true, 3, Map.of("key", "val"));
        assertThat(info.cascadeVersion()).isEqualTo("3.0.0");
        assertThat(info.javaVersion()).isEqualTo("25");
        assertThat(info.virtualThreadsEnabled()).isTrue();
        assertThat(info.activeWorkflowCount()).isEqualTo(3);
        assertThat(info.extra()).containsEntry("key", "val");
    }

    @Test
    @DisplayName("RuntimeInfo : extra null est accepté")
    void runtimeInfoNullExtra() {
        DashboardConfigDTO.RuntimeInfo info = new DashboardConfigDTO.RuntimeInfo(
                "1.0.0", "21", false, 0, null);
        assertThat(info.extra()).isNull();
    }

    // ─── CascadePool ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("CascadePool : tous les champs accessibles")
    void cascadePoolFields() {
        DashboardConfigDTO.CascadePool pool = new DashboardConfigDTO.CascadePool(
                "transform", "transform-", 4, 2, 4, 12, 100, 500L, 450L, false);
        assertThat(pool.stage()).isEqualTo("transform");
        assertThat(pool.threadNamePrefix()).isEqualTo("transform-");
        assertThat(pool.configuredThreads()).isEqualTo(4);
        assertThat(pool.activeCount()).isEqualTo(2);
        assertThat(pool.poolSize()).isEqualTo(4);
        assertThat(pool.queueSize()).isEqualTo(12);
        assertThat(pool.queueCapacity()).isEqualTo(100);
        assertThat(pool.taskCount()).isEqualTo(500L);
        assertThat(pool.completedTaskCount()).isEqualTo(450L);
        assertThat(pool.virtualThreads()).isFalse();
    }

    @Test
    @DisplayName("CascadePool : record equality")
    void cascadePoolEquality() {
        DashboardConfigDTO.CascadePool a = new DashboardConfigDTO.CascadePool(
                "source", "source-", 1, 0, 1, 0, 50, 100L, 100L, true);
        DashboardConfigDTO.CascadePool b = new DashboardConfigDTO.CascadePool(
                "source", "source-", 1, 0, 1, 0, 50, 100L, 100L, true);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    // ─── CascadeDefaults ──────────────────────────────────────────────────────

    @Test
    @DisplayName("CascadeDefaults : tous les champs accessibles")
    void cascadeDefaultsFields() {
        DashboardConfigDTO.CascadeDefaults d = new DashboardConfigDTO.CascadeDefaults(
                0, -1, 100, true, 4, -1, -1, -1, 50, 50, 50, 3, 0L, "REJECT_IMMEDIATELY", true);
        assertThat(d.collectorChunkSize()).isEqualTo(-1);
        assertThat(d.maxErrors()).isEqualTo(100);
        assertThat(d.enableMetrics()).isTrue();
        assertThat(d.defaultParallelism()).isEqualTo(4);
        assertThat(d.rateLimitMaxWorkflowsPerUser()).isEqualTo(3);
        assertThat(d.rateLimitRejectionPolicy()).isEqualTo("REJECT_IMMEDIATELY");
        assertThat(d.rateLimitEnabled()).isTrue();
    }

    // ─── DashboardConfigDTO root ──────────────────────────────────────────────

    @Test
    @DisplayName("DashboardConfigDTO root : tous les champs accessibles")
    void rootRecord() {
        DashboardConfigDTO.ImportConfig ic = new DashboardConfigDTO.ImportConfig(
                1000, 4, 100, 100, "/c", "/p");
        DashboardConfigDTO.RateLimitConfig rl = new DashboardConfigDTO.RateLimitConfig(
                3, 5, 0L, Map.of());
        DashboardConfigDTO.RuntimeInfo rt = new DashboardConfigDTO.RuntimeInfo(
                "3.0.0", "25", false, 0, Map.of());
        DashboardConfigDTO.CascadePool pool = new DashboardConfigDTO.CascadePool(
                "sink", "sink-", 2, 1, 2, 0, 50, 10L, 10L, false);
        DashboardConfigDTO.CascadeDefaults cd = new DashboardConfigDTO.CascadeDefaults(
                0, 0, 100, false, 4, -1, -1, -1, 50, 50, 50, 3, 0L, "REJECT_IMMEDIATELY", false);

        DashboardConfigDTO dto = new DashboardConfigDTO(ic, rl, rt, List.of(pool), cd);

        assertThat(dto.importConfig()).isSameAs(ic);
        assertThat(dto.rateLimit()).isSameAs(rl);
        assertThat(dto.runtime()).isSameAs(rt);
        assertThat(dto.cascadePools()).hasSize(1).contains(pool);
        assertThat(dto.cascadeDefaults()).isSameAs(cd);
    }
}
