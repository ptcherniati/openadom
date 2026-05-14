package fr.inra.oresing.workflow.cascade.progress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link LoggingImportProgressReporter}.
 *
 * <p>Couvre :
 * <ul>
 *   <li>comptage de lignes sans total connu (mode sans barre)</li>
 *   <li>comptage de lignes avec total connu (mode barre de progression)</li>
 *   <li>libération mémoire via {@code release()}</li>
 *   <li>résistance à l'appel nul et aux valeurs limites</li>
 * </ul>
 */
@Tag("domain.model")
@DisplayName("LoggingImportProgressReporter")
class LoggingImportProgressReporterTest {

    private LoggingImportProgressReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new LoggingImportProgressReporter();
    }

    // ─── mode sans total connu ────────────────────────────────────────────────

    @Nested
    @DisplayName("Mode sans total connu (onTotalLinesKnown non appelé)")
    class WithoutTotal {

        @Test
        @DisplayName("onLinesProcessed accumule le compteur")
        void accumulatesLines() {
            reporter.onLinesProcessed("id-1", 100);
            reporter.onLinesProcessed("id-1", 200);

            // aucune assertion sur le log, mais le compteur interne ne doit pas lever
            assertThat(reporter.trackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Deux correlationIds distincts sont tracés séparément")
        void twoDistinctCorrelationIds() {
            reporter.onLinesProcessed("id-A", 50);
            reporter.onLinesProcessed("id-B", 75);

            assertThat(reporter.trackedCount()).isEqualTo(2);
        }
    }

    // ─── mode avec total connu ────────────────────────────────────────────────

    @Nested
    @DisplayName("Mode avec total connu (onTotalLinesKnown appelé avant)")
    class WithTotal {

        @Test
        @DisplayName("onTotalLinesKnown enregistre le total sans lever d'exception")
        void totalLinesKnownDoesNotThrow() {
            reporter.onTotalLinesKnown("id-1", 1000L);
            // trackedCount n'est pas incrémenté par onTotalLinesKnown seul
            assertThat(reporter.trackedCount()).isZero();
        }

        @Test
        @DisplayName("onLinesProcessed affiche la barre (pas d'exception pour valeurs normales)")
        void progressBarRenderedWithoutException() {
            reporter.onTotalLinesKnown("id-1", 1000L);
            reporter.onLinesProcessed("id-1", 250);
            reporter.onLinesProcessed("id-1", 250);
            reporter.onLinesProcessed("id-1", 500);
            // 1000/1000 = 100% — ne doit pas lever d'exception (filled >= BAR_WIDTH)
            assertThat(reporter.trackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("onTotalLinesKnown avec 0 lignes ne cause pas de division par zéro")
        void zeroTotalLinesNoDivisionByZero() {
            reporter.onTotalLinesKnown("id-zero", 0L);
            reporter.onLinesProcessed("id-zero", 10);
            assertThat(reporter.trackedCount()).isEqualTo(1);
        }
    }

    // ─── release ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("release() — nettoyage mémoire")
    class Release {

        @Test
        @DisplayName("release() retourne le total cumulé et supprime le compteur")
        void releaseReturnsTotalAndClearsCounter() {
            reporter.onLinesProcessed("id-1", 300);
            reporter.onLinesProcessed("id-1", 700);

            long total = reporter.release("id-1");

            assertThat(total).isEqualTo(1000L);
            assertThat(reporter.trackedCount()).isZero();
        }

        @Test
        @DisplayName("release() supprime aussi le grandTotal")
        void releaseAlsoClearsGrandTotal() {
            reporter.onTotalLinesKnown("id-1", 500L);
            reporter.onLinesProcessed("id-1", 250);
            reporter.release("id-1");

            // après release, onLinesProcessed ne doit plus voir le grandTotal
            // (pas de NullPointerException, barre absente)
            reporter.onLinesProcessed("id-1", 50);
            assertThat(reporter.trackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("release(null) retourne 0 sans exception")
        void releaseNullReturnsZero() {
            long result = reporter.release(null);
            assertThat(result).isZero();
        }

        @Test
        @DisplayName("release() sur un id inconnu retourne 0")
        void releaseUnknownIdReturnsZero() {
            long result = reporter.release("unknown-id");
            assertThat(result).isZero();
        }
    }

    // ─── interface par défaut ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Interface ImportProgressReporter — méthode default")
    class DefaultMethod {

        @Test
        @DisplayName("L'implémentation no-op de onTotalLinesKnown ne lève pas d'exception")
        void defaultOnTotalLinesKnownIsNoOp() {
            ImportProgressReporter noop = (cid, delta) -> { /* no-op */ };
            noop.onTotalLinesKnown("id-1", 100L); // méthode default
            // La méthode default doit exister et ne pas lever d'exception
            assertThat(noop).isNotNull();
        }
    }
}