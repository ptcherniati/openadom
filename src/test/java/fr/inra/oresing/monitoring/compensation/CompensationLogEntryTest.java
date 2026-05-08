package fr.inra.oresing.monitoring.compensation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link CompensationLogEntry}.
 */
@Tag("domain.model")
@DisplayName("CompensationLogEntry — journal d'intent pour compensation")
class CompensationLogEntryTest {

    private static final UUID ID             = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final UUID USER_ID        = UUID.randomUUID();
    private static final Instant STARTED_AT  = Instant.parse("2025-06-01T10:00:00Z");

    private CompensationLogEntry pending() {
        return new CompensationLogEntry(
                ID, "IMPORT_BINARYFILE",
                "oa_data", "binary_file", "file-uuid-42",
                CORRELATION_ID, USER_ID, "alice",
                Map.of("key", "value"),
                CompensationLogEntry.STATUS_PENDING,
                STARTED_AT,
                CompensationLogEntry.DEFAULT_TTL_MINUTES,
                0, null, null);
    }

    @Test
    @DisplayName("Constante STATUS_PENDING vaut 'PENDING'")
    void statusPending() {
        assertEquals("PENDING", CompensationLogEntry.STATUS_PENDING);
    }

    @Test
    @DisplayName("Constante STATUS_FAILED vaut 'FAILED'")
    void statusFailed() {
        assertEquals("FAILED", CompensationLogEntry.STATUS_FAILED);
    }

    @Test
    @DisplayName("DEFAULT_TTL_MINUTES vaut 240 (4h)")
    void defaultTtl() {
        assertEquals(240, CompensationLogEntry.DEFAULT_TTL_MINUTES);
    }

    @Test
    @DisplayName("MAX_RETRIES vaut 5")
    void maxRetries() {
        assertEquals(5, CompensationLogEntry.MAX_RETRIES);
    }

    @Test
    @DisplayName("record conserve tous les champs")
    void allFields() {
        CompensationLogEntry e = pending();

        assertEquals(ID, e.id());
        assertEquals("IMPORT_BINARYFILE", e.operationType());
        assertEquals("oa_data", e.targetSchema());
        assertEquals("binary_file", e.targetTable());
        assertEquals("file-uuid-42", e.targetId());
        assertEquals(CORRELATION_ID, e.correlationId());
        assertEquals(USER_ID, e.userId());
        assertEquals("alice", e.userLogin());
        assertEquals(Map.of("key", "value"), e.payload());
        assertEquals(CompensationLogEntry.STATUS_PENDING, e.status());
        assertEquals(STARTED_AT, e.startedAt());
        assertEquals(240, e.ttlMinutes());
        assertEquals(0, e.attemptCount());
        assertNull(e.lastAttemptAt());
        assertNull(e.lastError());
    }

    @Test
    @DisplayName("record equality : deux entries identiques sont égales")
    void recordEquality() {
        CompensationLogEntry a = pending();
        CompensationLogEntry b = new CompensationLogEntry(
                ID, "IMPORT_BINARYFILE",
                "oa_data", "binary_file", "file-uuid-42",
                CORRELATION_ID, USER_ID, "alice",
                Map.of("key", "value"),
                CompensationLogEntry.STATUS_PENDING,
                STARTED_AT,
                CompensationLogEntry.DEFAULT_TTL_MINUTES,
                0, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("entry avec STATUS_FAILED conserve lastError")
    void failedEntry() {
        Instant lastAttempt = STARTED_AT.plusSeconds(3600);
        CompensationLogEntry failed = new CompensationLogEntry(
                ID, "IMPORT_BINARYFILE",
                "oa_data", "binary_file", "file-uuid-42",
                CORRELATION_ID, USER_ID, "alice",
                Map.of(),
                CompensationLogEntry.STATUS_FAILED,
                STARTED_AT, 240, 5, lastAttempt, "DB connection refused");

        assertEquals(CompensationLogEntry.STATUS_FAILED, failed.status());
        assertEquals(5, failed.attemptCount());
        assertEquals(lastAttempt, failed.lastAttemptAt());
        assertEquals("DB connection refused", failed.lastError());
    }
}
