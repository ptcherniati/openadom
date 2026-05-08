package fr.inra.oresing.monitoring.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link UserSessionLogEntry}.
 */
@Tag("domain.model")
@DisplayName("UserSessionLogEntry — row persistée dans oa_audit.user_session_log")
class UserSessionLogEntryTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final Instant LOGIN   = Instant.parse("2025-06-01T08:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2025-06-01T16:00:00Z");
    private static final Instant END     = Instant.parse("2025-06-01T09:00:00Z");

    private SessionInfo terminatedSession() {
        return new SessionInfo(SESSION_ID, USER_ID, "alice", "192.168.1.1",
                "Mozilla/5.0", LOGIN, EXPIRES, END, SessionInfo.END_LOGOUT, "hashXYZ")
                .withEnd(END, SessionInfo.END_LOGOUT);
    }

    @Test
    @DisplayName("fromSession() crée l'entry avec tous les champs corrects")
    void fromSessionAllFields() {
        UserSessionLogEntry entry = UserSessionLogEntry.fromSession(terminatedSession());

        assertEquals(SESSION_ID, entry.sessionId());
        assertEquals(USER_ID, entry.userId());
        assertEquals("alice", entry.userLogin());
        assertEquals("192.168.1.1", entry.ipAddress());
        assertEquals("Mozilla/5.0", entry.userAgent());
        assertEquals(LOGIN, entry.loginTime());
        assertEquals(END, entry.logoutTime());
        assertEquals(SessionInfo.END_LOGOUT, entry.endReason());
        assertTrue(entry.durationMs() > 0, "durationMs doit être positif");
    }

    @Test
    @DisplayName("fromSession() calcule correctement la durée en ms")
    void fromSessionDurationMs() {
        UserSessionLogEntry entry = UserSessionLogEntry.fromSession(terminatedSession());
        // END - LOGIN = 1h = 3 600 000 ms
        assertEquals(3_600_000L, entry.durationMs());
    }

    @Test
    @DisplayName("fromSession() lance IllegalArgumentException si session non terminée")
    void fromSessionThrowsIfNotEnded() {
        SessionInfo active = new SessionInfo(SESSION_ID, USER_ID, "bob", "10.0.0.1",
                "curl", LOGIN, EXPIRES, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> UserSessionLogEntry.fromSession(active));
    }

    @Test
    @DisplayName("fromSession() : durationMs = 0 si loginTime est null")
    void fromSessionNullLoginTime() {
        SessionInfo s = new SessionInfo(SESSION_ID, USER_ID, "charlie", "1.2.3.4",
                "ua", null, null, END, SessionInfo.END_KICK, null);
        UserSessionLogEntry entry = UserSessionLogEntry.fromSession(s);
        assertEquals(0L, entry.durationMs());
    }

    @Test
    @DisplayName("fromSession() avec raison JWT_EXPIRED")
    void fromSessionJwtExpired() {
        SessionInfo s = new SessionInfo(SESSION_ID, USER_ID, "dave", "5.5.5.5",
                "ua", LOGIN, EXPIRES, EXPIRES, SessionInfo.END_JWT_EXPIRED, null);
        UserSessionLogEntry entry = UserSessionLogEntry.fromSession(s);
        assertEquals(SessionInfo.END_JWT_EXPIRED, entry.endReason());
    }

    @Test
    @DisplayName("UserSessionLogEntry record equality")
    void recordEquality() {
        UserSessionLogEntry a = UserSessionLogEntry.fromSession(terminatedSession());
        UserSessionLogEntry b = UserSessionLogEntry.fromSession(terminatedSession());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
