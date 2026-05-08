package fr.inra.oresing.workflow.extraction;

import fr.inra.oresing.workflow.cascade.history.HeartbeatService;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.history.WorkflowSnapshot;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link ExtractionLifecycle} — sans contexte Spring.
 * Toutes les dépendances sont mockées via Mockito.
 *
 * <p>Couvre : start(), Handle.complete(), Handle.fail(), Handle.close(),
 * workflowTypeToMetricsKey (via complete/fail), dégradation sur erreur
 * recordStart, idempotence de close après complete.
 */
@ExtendWith(MockitoExtension.class)
@Tag("domain.model")
@DisplayName("ExtractionLifecycle — tests unitaires")
class ExtractionLifecycleTest {

    @Mock private WorkflowLogRepository    repository;
    @Mock private WorkflowLogWriter        logWriter;
    @Mock private WorkflowActiveRegistry   activeRegistry;
    @Mock private HeartbeatService         heartbeatService;
    @Mock private OpenadomMetrics          metrics;

    @InjectMocks
    private ExtractionLifecycle lifecycle;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_LOGIN = "testUser";
    private static final String APP = "myapp";
    private static final String DATA_TYPE = "myDataType";
    private static final String RESOURCE = "file.csv";

    /** Prépare un Heartbeat NOOP pour les tests. */
    private void stubHeartbeat() {
        when(heartbeatService.start(any(UUID.class)))
                .thenReturn(HeartbeatService.Heartbeat.NOOP);
    }

    // ─── start() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start() retourne un Handle non-null")
    void startReturnsHandle() {
        stubHeartbeat();
        ExtractionLifecycle.Handle h = lifecycle.start(
                WorkflowLogEntry.TYPE_EXTRACT_ZIP, USER_ID, USER_LOGIN,
                APP, DATA_TYPE, RESOURCE, 1024L);
        assertNotNull(h);
        assertNotNull(h.correlationId());
    }

    @Test
    @DisplayName("start() appelle recordStart et registry.start")
    void startCallsDeps() {
        stubHeartbeat();
        lifecycle.start(WorkflowLogEntry.TYPE_EXTRACT_ZIP, USER_ID, USER_LOGIN,
                APP, DATA_TYPE, RESOURCE, 2048L);

        verify(logWriter).recordStart(any(WorkflowLogEntry.class));
        verify(activeRegistry).start(any(WorkflowSnapshot.class));
        verify(metrics).markExtractionStart("zip");
    }

    @Test
    @DisplayName("start() continue en mode dégradé si recordStart lève une exception")
    void startDegradedOnRecordStartError() {
        stubHeartbeat();
        doThrow(new RuntimeException("DB down")).when(logWriter)
                .recordStart(any(WorkflowLogEntry.class));

        // Ne doit pas propager l'exception
        assertDoesNotThrow(() -> {
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_CSV, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L);
            assertNotNull(h);
        });
    }

    @Test
    @DisplayName("start() continue en mode dégradé si registry.start lève une exception")
    void startDegradedOnRegistryError() {
        stubHeartbeat();
        doThrow(new RuntimeException("registry error")).when(activeRegistry)
                .start(any(WorkflowSnapshot.class));

        assertDoesNotThrow(() -> {
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_CHARTE, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L);
            assertNotNull(h);
        });
    }

    // ─── Handle.complete() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Handle.complete()")
    class HandleComplete {

        @Test
        @DisplayName("complete() appelle recordEnd, markExtractionEnd et activeRegistry.finish")
        void completeCallsDeps() {
            stubHeartbeat();
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_ZIP, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 100L);

            h.complete(50L);

            verify(logWriter).recordEnd(any(WorkflowLogEntry.class));
            verify(metrics).markExtractionEnd("zip");
            verify(activeRegistry).finish(any(UUID.class));
        }

        @Test
        @DisplayName("complete() puis close() est idempotent (recordEnd appelé 1 seule fois)")
        void completeIdempotentOnClose() {
            stubHeartbeat();
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_ZIP, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 100L);

            h.complete(50L);
            h.close(); // 2e finalisation : doit être no-op

            verify(logWriter, times(1)).recordEnd(any(WorkflowLogEntry.class));
        }
    }

    // ─── Handle.fail() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Handle.fail()")
    class HandleFail {

        @Test
        @DisplayName("fail(error) finalise avec STATUS_FAILED")
        void failWithException() {
            stubHeartbeat();
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_CSV, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L);

            h.fail(new RuntimeException("something went wrong"));

            verify(logWriter).recordEnd(any(WorkflowLogEntry.class));
            verify(metrics).markExtractionEnd("csv");
        }

        @Test
        @DisplayName("fail(null) ne lève pas d'exception")
        void failWithNull() {
            stubHeartbeat();
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_CHARTE, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L);

            assertDoesNotThrow(() -> h.fail(null));
            verify(logWriter).recordEnd(any(WorkflowLogEntry.class));
        }
    }

    // ─── Handle.close() safety net ──────────────────────────────────────────

    @Nested
    @DisplayName("Handle.close() safety net")
    class HandleClose {

        @Test
        @DisplayName("close() sans complete/fail appelle recordEnd (safety net)")
        void closeSafetyNet() {
            stubHeartbeat();
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L);

            h.close();

            verify(logWriter).recordEnd(any(WorkflowLogEntry.class));
            verify(metrics).markExtractionEnd("additional");
        }

        @Test
        @DisplayName("close() après fail() est no-op (idempotence)")
        void closeAfterFail() {
            stubHeartbeat();
            ExtractionLifecycle.Handle h = lifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_ZIP, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L);

            h.fail(new RuntimeException("error"));
            h.close();

            verify(logWriter, times(1)).recordEnd(any(WorkflowLogEntry.class));
        }
    }

    // ─── workflowTypeToMetricsKey (via start) ───────────────────────────────

    @Nested
    @DisplayName("workflowType → metricsKey mapping")
    class MetricsKeyMapping {

        @Test
        @DisplayName("TYPE_EXTRACT_ZIP → 'zip'")
        void zipMapping() {
            stubHeartbeat();
            lifecycle.start(WorkflowLogEntry.TYPE_EXTRACT_ZIP, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L).close();
            verify(metrics).markExtractionStart("zip");
            verify(metrics).markExtractionEnd("zip");
        }

        @Test
        @DisplayName("TYPE_EXTRACT_CSV → 'csv'")
        void csvMapping() {
            stubHeartbeat();
            lifecycle.start(WorkflowLogEntry.TYPE_EXTRACT_CSV, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L).close();
            verify(metrics).markExtractionStart("csv");
            verify(metrics).markExtractionEnd("csv");
        }

        @Test
        @DisplayName("TYPE_EXTRACT_CHARTE → 'charte'")
        void charteMapping() {
            stubHeartbeat();
            lifecycle.start(WorkflowLogEntry.TYPE_EXTRACT_CHARTE, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L).close();
            verify(metrics).markExtractionStart("charte");
        }

        @Test
        @DisplayName("TYPE_EXTRACT_ADDITIONAL_FILES → 'additional'")
        void additionalMapping() {
            stubHeartbeat();
            lifecycle.start(WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES, USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L).close();
            verify(metrics).markExtractionStart("additional");
        }

        @Test
        @DisplayName("type inconnu → toLowerCase")
        void unknownTypeToLowerCase() {
            stubHeartbeat();
            lifecycle.start("EXTRACT_CUSTOM", USER_ID, USER_LOGIN,
                    APP, DATA_TYPE, RESOURCE, 0L).close();
            verify(metrics).markExtractionStart("extract_custom");
        }
    }
}
