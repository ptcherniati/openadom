package fr.inra.oresing.workflow.cascade.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProcessedCacheCaptureMetrics} .
 * Verifie que :
 *  - le timer s'enregistre une fois par outcome ;
 *  - les labels sont coherents ;
 *  - la distribution bytes ignore les valeurs negatives / zero ;
 *  - le helper measure() encapsule correctement le try/finally .
 */
class ProcessedCacheCaptureMetricsTest {

    private static final String METRIC_DURATION = "oa_processed_cache_capture_duration_seconds";
    private static final String METRIC_BYTES = "oa_processed_cache_capture_bytes";

    private MeterRegistry registry;
    private ProcessedCacheCaptureMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ProcessedCacheCaptureMetrics(registry);
    }

    @Test
    void durationTimerRecordsPerOutcome() {
        Timer.Sample s1 = metrics.startSample();
        metrics.stop(s1, ProcessedCacheCaptureMetrics.Outcome.SUCCESS);

        Timer.Sample s2 = metrics.startSample();
        metrics.stop(s2, ProcessedCacheCaptureMetrics.Outcome.FAILED);

        Timer success = registry.find(METRIC_DURATION).tag("outcome", "success").timer();
        Timer failed = registry.find(METRIC_DURATION).tag("outcome", "failed").timer();

        assertThat(success).isNotNull();
        assertThat(failed).isNotNull();
        assertThat(success.count()).isEqualTo(1);
        assertThat(failed.count()).isEqualTo(1);
    }

    @Test
    void durationTimerLabelsAllOutcomeValues() {
        for (ProcessedCacheCaptureMetrics.Outcome outcome : ProcessedCacheCaptureMetrics.Outcome.values()) {
            metrics.stop(metrics.startSample(), outcome);
        }
        for (ProcessedCacheCaptureMetrics.Outcome outcome : ProcessedCacheCaptureMetrics.Outcome.values()) {
            Timer t = registry.find(METRIC_DURATION).tag("outcome", outcome.label()).timer();
            assertThat(t)
                    .as("timer registered for outcome %s", outcome)
                    .isNotNull();
            assertThat(t.count()).isEqualTo(1);
        }
    }

    @Test
    void stopWithNullSampleOrOutcomeIsSafe() {
        metrics.stop(null, ProcessedCacheCaptureMetrics.Outcome.SUCCESS);
        metrics.stop(metrics.startSample(), null);
        // Aucun timer ne doit etre enregistre via un null
        assertThat(registry.find(METRIC_DURATION).timers()).isEmpty();
    }

    @Test
    void recordBytesIgnoresNonPositiveValues() {
        metrics.recordBytes(0);
        metrics.recordBytes(-100);
        assertThat(registry.find(METRIC_BYTES).summary().count()).isZero();
    }

    @Test
    void recordBytesAccumulatesPositiveValues() {
        metrics.recordBytes(1024);
        metrics.recordBytes(2048);
        metrics.recordBytes(4096);
        var summary = registry.find(METRIC_BYTES).summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.totalAmount()).isEqualTo(1024 + 2048 + 4096);
    }

    @Test
    void measureHelperRecordsDurationAndReturnsResult() {
        long result = metrics.measure(ProcessedCacheCaptureMetrics.Outcome.SUCCESS, () -> 42L);
        assertThat(result).isEqualTo(42L);
        Timer t = registry.find(METRIC_DURATION).tag("outcome", "success").timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1);
    }

    @Test
    void measureHelperPropagatesExceptionAndStillRecords() {
        try {
            metrics.measure(ProcessedCacheCaptureMetrics.Outcome.FAILED, () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException expected) { /* expected */ }
        Timer t = registry.find(METRIC_DURATION).tag("outcome", "failed").timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1);
    }

    @Test
    void constructorRegistersBytesSummaryEagerly() {
        // La distribution bytes doit etre presente meme sans appel a recordBytes
        var summary = registry.find(METRIC_BYTES).summary();
        assertThat(summary)
                .as("DistributionSummary bytes must be registered eagerly")
                .isNotNull();
    }
}
