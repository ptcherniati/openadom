package fr.inra.oresing.rest.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs des records/DTOs du package dashboard.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
class DashboardDTOTest {

    // ------------------------------------------------------------------ //
    //  DashboardConfigDTO — inner records                                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("DashboardConfigDTO inner records")
    class DashboardConfigDTOTest {

        @Test
        void importConfigAccessors() {
            DashboardConfigDTO.ImportConfig ic = new DashboardConfigDTO.ImportConfig(
                    1000, 4, 100, 50, "/tmp/chunks", "/tmp/processed");
            assertThat(ic.chunkSizeLines()).isEqualTo(1000);
            assertThat(ic.parallelism()).isEqualTo(4);
            assertThat(ic.progressBatchSize()).isEqualTo(100);
            assertThat(ic.maxErrorsThreshold()).isEqualTo(50);
            assertThat(ic.chunksTempDir()).isEqualTo("/tmp/chunks");
            assertThat(ic.processedTempDir()).isEqualTo("/tmp/processed");
        }

        @Test
        void rateLimitConfigAccessors() {
            DashboardConfigDTO.RateLimitConfig rl = new DashboardConfigDTO.RateLimitConfig(
                    3, 5, 0L, Map.of("user1", 2));
            assertThat(rl.maxConcurrentImportsPerUser()).isEqualTo(3);
            assertThat(rl.maxConcurrentExtractionsPerUser()).isEqualTo(5);
            assertThat(rl.extractionAcquireTimeoutSeconds()).isEqualTo(0L);
            assertThat(rl.usedImportSlotsByUser()).containsEntry("user1", 2);
        }

        @Test
        void runtimeInfoAccessors() {
            DashboardConfigDTO.RuntimeInfo ri = new DashboardConfigDTO.RuntimeInfo(
                    "1.5.0", "25", true, 3, Map.of("extra", "val"));
            assertThat(ri.cascadeVersion()).isEqualTo("1.5.0");
            assertThat(ri.javaVersion()).isEqualTo("25");
            assertThat(ri.virtualThreadsEnabled()).isTrue();
            assertThat(ri.activeWorkflowCount()).isEqualTo(3);
            assertThat(ri.extra()).containsEntry("extra", "val");
        }

        @Test
        void cascadePoolAccessors() {
            DashboardConfigDTO.CascadePool cp = new DashboardConfigDTO.CascadePool(
                    "transform", "transform-", 4, 2, 4, 0, 100, 500L, 490L, false);
            assertThat(cp.stage()).isEqualTo("transform");
            assertThat(cp.configuredThreads()).isEqualTo(4);
            assertThat(cp.activeCount()).isEqualTo(2);
            assertThat(cp.queueSize()).isEqualTo(0);
            assertThat(cp.taskCount()).isEqualTo(500L);
            assertThat(cp.completedTaskCount()).isEqualTo(490L);
            assertThat(cp.virtualThreads()).isFalse();
        }

        @Test
        void cascadeDefaultsAccessors() {
            DashboardConfigDTO.CascadeDefaults cd = new DashboardConfigDTO.CascadeDefaults(
                    1000, 0, 100, true, 4, -1, -1, -1,
                    100, 100, 100, 3, 30L, "REJECT_IMMEDIATELY", true);
            assertThat(cd.sourceChunkSize()).isEqualTo(1000);
            assertThat(cd.collectorChunkSize()).isEqualTo(0);
            assertThat(cd.maxErrors()).isEqualTo(100);
            assertThat(cd.enableMetrics()).isTrue();
            assertThat(cd.defaultParallelism()).isEqualTo(4);
            assertThat(cd.rateLimitRejectionPolicy()).isEqualTo("REJECT_IMMEDIATELY");
            assertThat(cd.rateLimitEnabled()).isTrue();
        }

        @Test
        void fullDashboardConfigDTOAccessors() {
            DashboardConfigDTO.ImportConfig ic = new DashboardConfigDTO.ImportConfig(
                    500, 2, 50, 20, "/c", "/p");
            DashboardConfigDTO.RateLimitConfig rl = new DashboardConfigDTO.RateLimitConfig(
                    2, 3, 0L, Map.of());
            DashboardConfigDTO.RuntimeInfo ri = new DashboardConfigDTO.RuntimeInfo(
                    "2.0", "21", false, 0, Map.of());
            DashboardConfigDTO dto = new DashboardConfigDTO(ic, rl, ri, List.of(), null);
            assertThat(dto.importConfig()).isSameAs(ic);
            assertThat(dto.rateLimit()).isSameAs(rl);
            assertThat(dto.runtime()).isSameAs(ri);
            assertThat(dto.cascadePools()).isEmpty();
            assertThat(dto.cascadeDefaults()).isNull();
        }
    }
}