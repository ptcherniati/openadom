package fr.inra.oresing.monitoring.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link SessionInfo}.
 */
@Tag("domain.model")
@DisplayName("SessionInfo — snapshot in-memory d'une session utilisateur")
class SessionInfoTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final Instant LOGIN   = Instant.parse("2025-06-01T08:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2025-06-01T16:00:00Z");

    private SessionInfo active() {
        return new SessionInfo(SESSION_ID, USER_ID, "alice", "127.0.0.1",
                "Mozilla/5.0", LOGIN, EXPIRES, null, null, "hash42");
    }

    // ─── isActive ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isActive() retourne true si pas de endTime et JWT pas encore expiré")
    void isActiveWhenNotExpired() {
        Instant now = LOGIN.plusSeconds(30);
        assertTrue(active().isActive(now));
    }

    @Test
    @DisplayName("isActive() retourne false si endTime est défini")
    void isActiveWhenEndTimeSet() {
        SessionInfo terminated = active().withEnd(LOGIN.plusSeconds(60), SessionInfo.END_LOGOUT);
        Instant now = LOGIN.plusSeconds(61);
        assertFalse(terminated.isActive(now));
    }

    @Test
    @DisplayName("isActive() retourne false si JWT expiré (now >= expiresAt)")
    void isActiveWhenJwtExpired() {
        Instant now = EXPIRES.plusSeconds(1);
        assertFalse(active().isActive(now));
    }

    @Test
    @DisplayName("isActive() retourne true si expiresAt est null (pas de TTL JWT)")
    void isActiveWhenExpiresAtNull() {
        SessionInfo noExpiry = new SessionInfo(SESSION_ID, USER_ID, "bob", "10.0.0.1",
                "curl/7.0", LOGIN, null, null, null, null);
        assertTrue(noExpiry.isActive(LOGIN.plusSeconds(999_999)));
    }

    // ─── status ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("status() retourne ACTIVE quand session vivante")
    void statusActive() {
        assertEquals("ACTIVE", active().status(LOGIN.plusSeconds(10)));
    }

    @Test
    @DisplayName("status() retourne DISCONNECTED quand session terminée")
    void statusDisconnected() {
        SessionInfo ended = active().withEnd(LOGIN.plusSeconds(5), SessionInfo.END_LOGOUT);
        assertEquals("DISCONNECTED", ended.status(LOGIN.plusSeconds(10)));
    }

    // ─── duration ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("duration() retourne null si loginTime est null")
    void durationNullLoginTime() {
        SessionInfo s = new SessionInfo(SESSION_ID, USER_ID, "u", "ip", "ua",
                null, null, null, null, null);
        assertNull(s.duration(Instant.now()));
    }

    @Test
    @DisplayName("duration() utilise now quand session encore active")
    void durationActiveSession() {
        Instant now = LOGIN.plusSeconds(120);
        Duration d = active().duration(now);
        assertNotNull(d);
        assertEquals(120, d.getSeconds());
    }

    @Test
    @DisplayName("duration() utilise endTime quand session terminée")
    void durationEndedSession() {
        Instant end = LOGIN.plusSeconds(300);
        SessionInfo ended = active().withEnd(end, SessionInfo.END_LOGOUT);
        Duration d = ended.duration(end.plusSeconds(100));
        assertEquals(300, d.getSeconds());
    }

    @Test
    @DisplayName("duration() utilise expiresAt quand JWT expiré (pas de endTime)")
    void durationJwtExpired() {
        // La session est active en RAM mais now > expiresAt
        Instant now = EXPIRES.plusSeconds(60);
        Duration d = active().duration(now);
        // L'implémentation prend expiresAt si now > expiresAt
        assertEquals(Duration.between(LOGIN, EXPIRES), d);
    }

    // ─── withEnd ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("withEnd() crée un nouveau SessionInfo immuable avec endTime et reason")
    void withEnd() {
        Instant end = LOGIN.plusSeconds(500);
        SessionInfo ended = active().withEnd(end, SessionInfo.END_LOGOUT);

        assertEquals(end, ended.endTime());
        assertEquals(SessionInfo.END_LOGOUT, ended.endReason());
        // Champs inchangés
        assertEquals(SESSION_ID, ended.sessionId());
        assertEquals(USER_ID, ended.userId());
        assertEquals("alice", ended.userLogin());
    }

    @Test
    @DisplayName("withEnd() avec raison JWT_EXPIRED")
    void withEndJwtExpired() {
        SessionInfo ended = active().withEnd(EXPIRES, SessionInfo.END_JWT_EXPIRED);
        assertEquals(SessionInfo.END_JWT_EXPIRED, ended.endReason());
    }

    @Test
    @DisplayName("withEnd() avec raison KICK")
    void withEndKick() {
        SessionInfo ended = active().withEnd(LOGIN.plusSeconds(10), SessionInfo.END_KICK);
        assertEquals(SessionInfo.END_KICK, ended.endReason());
    }

    // ─── constantes ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("constantes END_* ont les bonnes valeurs")
    void endConstants() {
        assertEquals("LOGOUT",      SessionInfo.END_LOGOUT);
        assertEquals("JWT_EXPIRED", SessionInfo.END_JWT_EXPIRED);
        assertEquals("KICK",        SessionInfo.END_KICK);
    }
}
