package fr.inra.oresing.workflow.guard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests pour {@link HeapGuardService} - verifie la validation des
 * seuils , l'initialisation , le smoothing , et la semantique
 * {@link HeapGuardService#isUnderPressure()} .
 */
class HeapGuardServiceTest {

    @Test
    void invalid_refuse_threshold_below_1_throws() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new HeapGuardService(0, 90))
                .withMessageContaining("refuse-publish-threshold-pct");
    }

    @Test
    void invalid_refuse_threshold_above_100_throws() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new HeapGuardService(101, 90))
                .withMessageContaining("refuse-publish-threshold-pct");
    }

    @Test
    void critical_below_refuse_throws() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new HeapGuardService(80, 70))
                .withMessageContaining("critical-threshold-pct");
    }

    @Test
    void critical_above_100_throws() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new HeapGuardService(80, 101))
                .withMessageContaining("critical-threshold-pct");
    }

    @Test
    void valid_thresholds_construct_ok() {
        HeapGuardService g = new HeapGuardService(80, 90);
        assertThat(g.getRefusePublishPct()).isEqualTo(80);
        assertThat(g.getCriticalPct()).isEqualTo(90);
    }

    @Test
    void equal_thresholds_ok() {
        HeapGuardService g = new HeapGuardService(85, 85);
        assertThat(g.getRefusePublishPct()).isEqualTo(85);
        assertThat(g.getCriticalPct()).isEqualTo(85);
    }

    @Test
    void initialSample_populates_currentStats() {
        HeapGuardService g = new HeapGuardService(99, 99);
        g.initialSample();
        HeapGuardService.HeapStats stats = g.currentStats();
        assertThat(stats.heapMaxBytes()).isPositive();
        assertThat(stats.heapUsedBytes()).isNotNegative();
        assertThat(stats.refusePublishThresholdPct()).isEqualTo(99);
        assertThat(stats.criticalThresholdPct()).isEqualTo(99);
        // current heap usage en test est normalement << 99%
        assertThat(stats.currentUsagePct()).isLessThan(99.0);
    }

    @Test
    void underPressure_false_when_threshold_high_and_heap_low() {
        // Seuil 99% : impossible a atteindre sur un test JUnit standard
        HeapGuardService g = new HeapGuardService(99, 99);
        g.initialSample();
        assertThat(g.isUnderPressure()).isFalse();
    }

    @Test
    void scan_emits_no_log_when_heap_is_low() {
        HeapGuardService g = new HeapGuardService(99, 99);
        g.initialSample();
        // Pas d'exception, juste verifie que scan tourne sans error
        g.scan();
        assertThat(g.isUnderPressure()).isFalse();
    }

    @Test
    void currentStats_reflects_thresholds() {
        HeapGuardService g = new HeapGuardService(75, 88);
        g.initialSample();
        HeapGuardService.HeapStats stats = g.currentStats();
        assertThat(stats.refusePublishThresholdPct()).isEqualTo(75);
        assertThat(stats.criticalThresholdPct()).isEqualTo(88);
        assertThat(stats.underPressure()).isEqualTo(stats.smoothedUsagePct() >= 75);
    }
}
