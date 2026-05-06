package fr.inra.oresing.workflow.cascade.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires d'OpenadomMetrics avec un SimpleMeterRegistry en mémoire.
 * Aucun contexte Spring : instanciation directe + appel à registerGauges().
 */
@DisplayName("OpenadomMetrics")
@Tag("domain.model")
class OpenadomMetricsTest {

    private MeterRegistry registry;
    private OpenadomMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new OpenadomMetrics(registry);
        // Déclenche @PostConstruct manuellement
        metrics.registerGauges();
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("recordImportCompleted")
    class RecordImportCompleted {

        @Test
        @DisplayName("incrémente oa_import_total avec les bons tags")
        void incrementsImportTotal() {
            metrics.recordImportCompleted("myApp", "dataX", "COMPLETED",
                    Duration.ofSeconds(5), 1000, 0, 10, 2048L);

            Counter counter = registry.find("oa_import_total")
                    .tags("application", "myApp", "data_type", "dataX", "status", "COMPLETED")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("application null → tag 'unknown'")
        void nullApplicationBecomesUnknown() {
            metrics.recordImportCompleted(null, "d", "COMPLETED",
                    Duration.ofMillis(100), 0, 0, 0, 0L);

            Counter counter = registry.find("oa_import_total")
                    .tags("application", "unknown")
                    .counter();
            assertThat(counter).isNotNull();
        }

        @Test
        @DisplayName("recordsProcessed > 0 → counter records_processed_total alimenté")
        void recordsProcessedCounterFed() {
            metrics.recordImportCompleted("app", "dt", "COMPLETED",
                    Duration.ofSeconds(1), 500, 0, 0, 0L);

            Counter r = registry.find("oa_import_records_processed_total")
                    .tags("application", "app", "data_type", "dt")
                    .counter();
            assertThat(r).isNotNull();
            assertThat(r.count()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("recordsFailed > 0 → counter records_failed_total alimenté")
        void recordsFailedCounterFed() {
            metrics.recordImportCompleted("app", "dt", "FAILED",
                    Duration.ofSeconds(1), 0, 50, 0, 0L);

            Counter r = registry.find("oa_import_records_failed_total")
                    .tags("application", "app", "data_type", "dt")
                    .counter();
            assertThat(r).isNotNull();
            assertThat(r.count()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("fileSizeBytes > 0 → counter bytes_total alimenté")
        void bytesFed() {
            metrics.recordImportCompleted("app", "dt", "COMPLETED",
                    Duration.ofSeconds(1), 0, 0, 0, 4096L);

            Counter b = registry.find("oa_import_bytes_total")
                    .tags("application", "app", "data_type", "dt")
                    .counter();
            assertThat(b).isNotNull();
            assertThat(b.count()).isEqualTo(4096.0);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("recordImportRateLimited")
    class RecordImportRateLimited {

        @Test
        @DisplayName("incrémente oa_import_rate_limited_total")
        void incrementsCounter() {
            metrics.recordImportRateLimited();
            metrics.recordImportRateLimited();

            Counter c = registry.find("oa_import_rate_limited_total").counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(2.0);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("recordExtractionCompleted")
    class RecordExtractionCompleted {

        @Test
        @DisplayName("incrémente oa_extraction_total avec les bons tags")
        void incrementsExtractionTotal() {
            metrics.recordExtractionCompleted("zip", "myApp", "refX", "COMPLETED",
                    Duration.ofSeconds(3), 8192L);

            Counter c = registry.find("oa_extraction_total")
                    .tags("type", "zip", "application", "myApp",
                          "data_type", "refX", "status", "COMPLETED")
                    .counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("bytesStreamed > 0 → counter extraction_bytes_total alimenté")
        void bytesStreamedFed() {
            metrics.recordExtractionCompleted("csv", "a", "b", "COMPLETED",
                    Duration.ofMillis(200), 2048L);

            Counter c = registry.find("oa_extraction_bytes_total")
                    .tags("type", "csv", "application", "a", "data_type", "b")
                    .counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(2048.0);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("recordExtractionRateLimited")
    class RecordExtractionRateLimited {

        @Test
        @DisplayName("incrémente oa_extraction_rate_limited_total avec tag type")
        void incrementsCounter() {
            metrics.recordExtractionRateLimited("csv");

            Counter c = registry.find("oa_extraction_rate_limited_total")
                    .tags("type", "csv")
                    .counter();
            assertThat(c).isNotNull();
            assertThat(c.count()).isEqualTo(1.0);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("markExtractionStart / markExtractionEnd")
    class MarkExtraction {

        @Test
        @DisplayName("start + end ne lèvent pas d'exception")
        void startEndNoException() {
            assertThatCode(() -> {
                metrics.markExtractionStart("zip");
                metrics.markExtractionStart("zip");
                metrics.markExtractionEnd("zip");
                metrics.markExtractionEnd("zip");
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("markExtractionEnd avec type inconnu ne lève pas NPE")
        void endUnknownTypeNoNPE() {
            assertThatCode(() -> metrics.markExtractionEnd("does-not-exist"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("markExtractionStart avec null → type 'unknown' sans exception")
        void startNullType() {
            assertThatCode(() -> metrics.markExtractionStart(null))
                    .doesNotThrowAnyException();
        }
    }
}