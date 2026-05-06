package fr.inra.oresing.workflow.cascade.progress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires de l'interface ImportProgressReporter et de son
 * implémentation LoggingImportProgressReporter.
 * Aucun contexte Spring.
 */
@DisplayName("ImportProgressReporter + LoggingImportProgressReporter")
@Tag("domain.model")
class ImportProgressReporterTest {

    // ---------------------------------------------------------------
    // Interface : méthode default onTotalLinesKnown
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("ImportProgressReporter (interface)")
    class InterfaceTest {

        @Test
        @DisplayName("onTotalLinesKnown par défaut ne lève pas d'exception")
        void defaultOnTotalLinesKnownIsNoOp() {
            // Implémentation minimale (lambda) qui ne fournit que onLinesProcessed
            ImportProgressReporter reporter = (cid, delta) -> { /* no-op */ };
            assertThatCode(() -> reporter.onTotalLinesKnown("id-1", 1000))
                    .doesNotThrowAnyException();
        }
    }

    // ---------------------------------------------------------------
    // LoggingImportProgressReporter
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("LoggingImportProgressReporter")
    class LoggingReporterTest {

        private final LoggingImportProgressReporter reporter = new LoggingImportProgressReporter();

        @Test
        @DisplayName("onLinesProcessed sans totalKnown accumule sans NPE")
        void onLinesProcessedWithoutTotal() {
            assertThatCode(() -> {
                reporter.onLinesProcessed("cid-1", 100);
                reporter.onLinesProcessed("cid-1", 200);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("onTotalLinesKnown puis onLinesProcessed s'exécute sans erreur")
        void withTotalKnown() {
            reporter.onTotalLinesKnown("cid-2", 500);
            assertThatCode(() -> {
                reporter.onLinesProcessed("cid-2", 50);
                reporter.onLinesProcessed("cid-2", 50);
                reporter.onLinesProcessed("cid-2", 400);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Plusieurs correlationIds coexistent sans interférence")
        void multipleCorrelationIds() {
            reporter.onTotalLinesKnown("a", 100);
            reporter.onTotalLinesKnown("b", 200);
            assertThatCode(() -> {
                reporter.onLinesProcessed("a", 10);
                reporter.onLinesProcessed("b", 20);
                reporter.onLinesProcessed("a", 90);
                reporter.onLinesProcessed("b", 180);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("onTotalLinesKnown avec totalLines=0 ne provoque pas de division par zéro")
        void totalZeroNoDivisionByZero() {
            reporter.onTotalLinesKnown("cid-3", 0);
            assertThatCode(() -> reporter.onLinesProcessed("cid-3", 10))
                    .doesNotThrowAnyException();
        }
    }
}