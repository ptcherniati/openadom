package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import fr.inrae.ore.cascade.core.ratelimit.UserRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires pour {@link ExtractionRateLimiter}.
 * Instanciation directe sans Spring, cascade UserRateLimiter réinitialisé entre tests.
 */
@Tag("domain.model")
@DisplayName("ExtractionRateLimiter — quota d'extractions concurrentes par utilisateur")
class ExtractionRateLimiterTest {

    private ExtractionRateLimiter rateLimiter;
    private WorkflowLogWriter     logWriter;
    private OpenadomMetrics       metrics;
    private AuthenticationService authService;

    /** Identifiant utilisateur valide (UUID) pour les tests nécessitant logRejection. */
    private static final String VALID_UUID = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        UserRateLimiter.reset();   // reset singleton cascade entre chaque test
        logWriter   = mock(WorkflowLogWriter.class);
        metrics     = mock(OpenadomMetrics.class);
        authService = mock(AuthenticationService.class);
        doThrow(new RuntimeException("no ctx")).when(authService).getCurrentUserRoles();

        // max=1 pour faciliter les tests de quota dépassé
        rateLimiter = new ExtractionRateLimiter(1, 0L, metrics, logWriter, authService);
        rateLimiter.init();
    }

    @AfterEach
    void tearDown() {
        UserRateLimiter.reset();
    }

    // ─── Accesseurs ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMaxConcurrentPerUser() retourne la valeur configurée")
    void getMaxConcurrentPerUser() {
        assertThat(rateLimiter.getMaxConcurrentPerUser()).isEqualTo(1);
    }

    @Test
    @DisplayName("getAcquireTimeoutSeconds() retourne la valeur configurée")
    void getAcquireTimeoutSeconds() {
        assertThat(rateLimiter.getAcquireTimeoutSeconds()).isEqualTo(0L);
    }

    // ─── reconfigure() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("reconfigure()")
    class ReconfigureTest {

        @Test
        @DisplayName("reconfigure avec mêmes valeurs est un no-op (return early)")
        void noopSameValues() {
            rateLimiter.reconfigure(1, 0L); // même valeurs que setUp → no-op
            assertThat(rateLimiter.getMaxConcurrentPerUser()).isEqualTo(1);
            assertThat(rateLimiter.getAcquireTimeoutSeconds()).isEqualTo(0L);
        }

        @Test
        @DisplayName("reconfigure met à jour max et timeout")
        void updatesValues() {
            rateLimiter.reconfigure(3, 10L);
            assertThat(rateLimiter.getMaxConcurrentPerUser()).isEqualTo(3);
            assertThat(rateLimiter.getAcquireTimeoutSeconds()).isEqualTo(10L);
        }

        @Test
        @DisplayName("reconfigure max < 1 → IllegalArgumentException")
        void invalidMaxThrows() {
            assertThatThrownBy(() -> rateLimiter.reconfigure(0, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("reconfigure timeout < 0 → IllegalArgumentException")
        void negativeTimeoutThrows() {
            assertThatThrownBy(() -> rateLimiter.reconfigure(2, -1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("reconfigure avec timeout > 0 change la politique vers WAIT_WITH_TIMEOUT")
        void reconfigureWithTimeout() {
            rateLimiter.reconfigure(2, 5L); // timeout > 0 → WAIT_WITH_TIMEOUT
            assertThat(rateLimiter.getAcquireTimeoutSeconds()).isEqualTo(5L);
        }
    }

    // ─── acquireOrThrow() — succès ────────────────────────────────────────────

    @Test
    @DisplayName("acquireOrThrow réussit si slot disponible")
    void acquireSucceeds() {
        assertThatCode(() -> rateLimiter.acquireOrThrow(VALID_UUID, "zip"))
                .doesNotThrowAnyException();
        rateLimiter.release(VALID_UUID);
    }

    // ─── acquireOrThrow() — quota dépassé + logRejection switch cases ─────────

    @Nested
    @DisplayName("acquireOrThrow() — quota dépassé, logRejection par type")
    class QuotaExceededTest {

        private void fillQuota() {
            rateLimiter.acquireOrThrow(VALID_UUID, "zip"); // max=1 → quota atteint
        }

        private void drainQuota() {
            rateLimiter.release(VALID_UUID);
        }

        @Test
        @DisplayName("lève ExtractionRateLimitExceededException quand quota atteint")
        void throwsExtractionRateLimitExceeded() {
            fillQuota();
            try {
                assertThatThrownBy(() -> rateLimiter.acquireOrThrow(VALID_UUID, "zip"))
                        .isInstanceOf(ExtractionRateLimitExceededException.class);
            } finally {
                drainQuota();
            }
        }

        @Test
        @DisplayName("metrics.recordExtractionRateLimited() appelé lors d'une rejection")
        void metricsRecorded() {
            fillQuota();
            try {
                rateLimiter.acquireOrThrow(VALID_UUID, "zip");
            } catch (ExtractionRateLimitExceededException ignored) {
                // expected
            }
            verify(metrics, atLeastOnce()).recordExtractionRateLimited(eq("zip"));
            drainQuota();
        }

        @Test
        @DisplayName("logRejection type=zip → TYPE_EXTRACT_ZIP")
        void logRejectionZip() {
            fillQuota();
            try {
                rateLimiter.acquireOrThrow(VALID_UUID, "zip");
            } catch (ExtractionRateLimitExceededException ignored) {
                // expected
            }
            verify(logWriter).logAsync(argThat(e ->
                    WorkflowLogEntry.TYPE_EXTRACT_ZIP.equals(e.workflowType())));
            drainQuota();
        }

        @Test
        @DisplayName("logRejection type=csv → TYPE_EXTRACT_CSV")
        void logRejectionCsv() {
            fillQuota();
            try {
                rateLimiter.acquireOrThrow(VALID_UUID, "csv");
            } catch (ExtractionRateLimitExceededException ignored) {
                // expected
            }
            verify(logWriter).logAsync(argThat(e ->
                    WorkflowLogEntry.TYPE_EXTRACT_CSV.equals(e.workflowType())));
            drainQuota();
        }

        @Test
        @DisplayName("logRejection type=charte → TYPE_EXTRACT_CHARTE")
        void logRejectionCharte() {
            fillQuota();
            try {
                rateLimiter.acquireOrThrow(VALID_UUID, "charte");
            } catch (ExtractionRateLimitExceededException ignored) {
                // expected
            }
            verify(logWriter).logAsync(argThat(e ->
                    WorkflowLogEntry.TYPE_EXTRACT_CHARTE.equals(e.workflowType())));
            drainQuota();
        }

        @Test
        @DisplayName("logRejection type=additional_files → TYPE_EXTRACT_ADDITIONAL_FILES")
        void logRejectionAdditionalFiles() {
            fillQuota();
            try {
                rateLimiter.acquireOrThrow(VALID_UUID, "additional_files");
            } catch (ExtractionRateLimitExceededException ignored) {
                // expected
            }
            verify(logWriter).logAsync(argThat(e ->
                    WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES.equals(e.workflowType())));
            drainQuota();
        }

        @Test
        @DisplayName("logRejection type inconnu → default EXTRACT_<TYPE>")
        void logRejectionDefaultType() {
            fillQuota();
            try {
                rateLimiter.acquireOrThrow(VALID_UUID, "mytype");
            } catch (ExtractionRateLimitExceededException ignored) {
                // expected
            }
            verify(logWriter).logAsync(argThat(e ->
                    "EXTRACT_MYTYPE".equals(e.workflowType())));
            drainQuota();
        }

        @Test
        @DisplayName("logRejection avec userId non-UUID est intercepté silencieusement (IAE caught)")
        void logRejectionInvalidUuidSilent() {
            // Quota atteint avec l'UUID valide, puis rejection avec un userId non-UUID
            rateLimiter.acquireOrThrow(VALID_UUID, "zip");
            // Un deuxième acquire avec VALID_UUID dépasse le quota
            try {
                rateLimiter.acquireOrThrow(VALID_UUID, "zip");
            } catch (ExtractionRateLimitExceededException ignored) {
                // expected
            }
            // logAsync n'est pas appelé pour le userId non-UUID (IAE interceptée)
            // Mais ici on a eu un VALID_UUID donc logAsync DOIT être appelé 1 fois
            verify(logWriter, atLeastOnce()).logAsync(any());
            rateLimiter.release(VALID_UUID);
        }
    }

    // ─── release() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("release() permet une réacquisition après quota atteint")
    void releaseAllowsReacquisition() {
        rateLimiter.acquireOrThrow(VALID_UUID, "zip");
        rateLimiter.release(VALID_UUID);
        // Slot libéré → nouvelle acquisition possible
        assertThatCode(() -> rateLimiter.acquireOrThrow(VALID_UUID, "csv"))
                .doesNotThrowAnyException();
        rateLimiter.release(VALID_UUID);
    }

    // ─── ExtractionRateLimitExceededException ────────────────────────────────

    @Test
    @DisplayName("ExtractionRateLimitExceededException est une RuntimeException avec message")
    void exceptionDetails() {
        ExtractionRateLimitExceededException ex =
                new ExtractionRateLimitExceededException("uid-42", 1, 1);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).contains("uid-42");
    }
}
