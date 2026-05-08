package fr.inra.oresing.monitoring.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("domain.model")
@DisplayName("UserSessionRegistry")

/**
 * Unit tests pour {@link UserSessionRegistry} ( in-memory ) .
 *
 * <p>Couvre : start / finish ( idempotent ) , listActive avec lazy
 * detection JWT_EXPIRED , listAll , eviction des sessions terminees ,
 * status / duration helpers .
 */
class UserSessionRegistryTest {

    private static SessionInfo session(UUID userId, String login, Instant loginTime, Duration ttl) {
        return new SessionInfo(
                UUID.randomUUID(), userId, login, "127.0.0.1", "JUnit/UA",
                loginTime, loginTime.plus(ttl), null, null, null);
    }

    @Test
    @DisplayName("start adds entry findable by sessionId")
    void startAddsEntry() {
        UserSessionRegistry reg = new UserSessionRegistry();
        SessionInfo s = session(UUID.randomUUID(), "alice", Instant.now(), Duration.ofMinutes(30));
        reg.start(s);
        assertEquals(1, reg.size());
        assertTrue(reg.find(s.sessionId()).isPresent());
    }

    @Test
    @DisplayName("finish marks endTime + endReason and is idempotent")
    void finishIsIdempotent() {
        UserSessionRegistry reg = new UserSessionRegistry();
        SessionInfo s = session(UUID.randomUUID(), "bob", Instant.now(), Duration.ofMinutes(30));
        reg.start(s);
        Instant t1 = Instant.now();
        SessionInfo finished = reg.finish(s.sessionId(), SessionInfo.END_LOGOUT, t1).orElseThrow();
        assertEquals(SessionInfo.END_LOGOUT, finished.endReason());
        assertEquals(t1, finished.endTime());
        // 2nd finish = no-op
        assertTrue(reg.finish(s.sessionId(), SessionInfo.END_KICK, Instant.now()).isEmpty());
        assertEquals(SessionInfo.END_LOGOUT, reg.find(s.sessionId()).orElseThrow().endReason());
    }

    @Test
    @DisplayName("listActive filters expired and currently disconnected")
    void listActiveFiltersExpired() {
        UserSessionRegistry reg = new UserSessionRegistry();
        Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
        SessionInfo s1 = session(UUID.randomUUID(), "u1", t0, Duration.ofMinutes(30));
        SessionInfo s2 = session(UUID.randomUUID(), "u2", t0, Duration.ofMinutes(30));
        reg.start(s1);
        reg.start(s2);
        // Manual logout for s2
        reg.finish(s2.sessionId(), SessionInfo.END_LOGOUT, t0.plusSeconds(60));

        // Now = t0 + 10 min => s1 ACTIVE , s2 DISCONNECTED
        Instant tNow = t0.plus(Duration.ofMinutes(10));
        List<SessionInfo> active = reg.listActive(tNow);
        assertEquals(1, active.size());
        assertEquals(s1.sessionId(), active.get(0).sessionId());

        // Now = t0 + 31 min => s1 expired ( JWT_EXPIRED ) , listActive empty
        Instant tLater = t0.plus(Duration.ofMinutes(31));
        List<SessionInfo> activeLater = reg.listActive(tLater);
        assertTrue(activeLater.isEmpty());
        // s1 marked JWT_EXPIRED in registry
        SessionInfo s1Now = reg.find(s1.sessionId()).orElseThrow();
        assertEquals(SessionInfo.END_JWT_EXPIRED, s1Now.endReason());
        assertNotNull(s1Now.endTime());
    }

    @Test
    @DisplayName("evictTerminatedOlderThan removes only stale terminated entries")
    void evictTerminated() {
        UserSessionRegistry reg = new UserSessionRegistry();
        Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
        SessionInfo s1 = session(UUID.randomUUID(), "u1", t0, Duration.ofMinutes(30));
        SessionInfo s2 = session(UUID.randomUUID(), "u2", t0, Duration.ofMinutes(30));
        reg.start(s1);
        reg.start(s2);
        reg.finish(s1.sessionId(), SessionInfo.END_LOGOUT, t0.plusSeconds(10));   // terminated old
        reg.finish(s2.sessionId(), SessionInfo.END_LOGOUT, t0.plus(Duration.ofMinutes(60)));  // terminated recent

        Instant tNow = t0.plus(Duration.ofMinutes(65));
        int evicted = reg.evictTerminatedOlderThan(Duration.ofMinutes(30), tNow);
        assertEquals(1, evicted);                                         // s1 only ( endTime t0+10s vs cutoff t0+35min )
        assertTrue(reg.find(s1.sessionId()).isEmpty());
        assertTrue(reg.find(s2.sessionId()).isPresent());
    }

    @Test
    @DisplayName("status / duration helpers reflect lifecycle")
    void statusAndDuration() {
        Instant t0 = Instant.parse("2026-05-01T10:00:00Z");
        SessionInfo s = session(UUID.randomUUID(), "x", t0, Duration.ofMinutes(60));
        assertEquals("ACTIVE", s.status(t0.plus(Duration.ofMinutes(10))));
        SessionInfo finished = s.withEnd(t0.plus(Duration.ofMinutes(15)), SessionInfo.END_LOGOUT);
        assertEquals("DISCONNECTED", finished.status(Instant.now()));
        assertEquals(15 * 60_000L, finished.duration(Instant.now()).toMillis());
    }

    @Test
    @DisplayName("UserSessionLogEntry.fromSession requires endTime")
    void logEntryRequiresEndTime() {
        SessionInfo open = session(UUID.randomUUID(), "x", Instant.now(), Duration.ofMinutes(30));
        assertThrows(IllegalArgumentException.class,
                () -> UserSessionLogEntry.fromSession(open));
        SessionInfo finished = open.withEnd(Instant.now(), SessionInfo.END_LOGOUT);
        UserSessionLogEntry entry = UserSessionLogEntry.fromSession(finished);
        assertEquals(finished.sessionId(), entry.sessionId());
        assertEquals(SessionInfo.END_LOGOUT, entry.endReason());
        assertTrue(entry.durationMs() >= 0);
    }

    @Test
    @DisplayName("finish(null) retourne empty Optional")
    void finishNullSessionId() {
        UserSessionRegistry reg = new UserSessionRegistry();
        assertTrue(reg.finish(null, SessionInfo.END_LOGOUT, Instant.now()).isEmpty());
    }

    @Test
    @DisplayName("find(unknown) retourne empty Optional")
    void findUnknownSession() {
        UserSessionRegistry reg = new UserSessionRegistry();
        assertTrue(reg.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("listAll inclut actives + terminées")
    void listAllIncludesBoth() {
        UserSessionRegistry reg = new UserSessionRegistry();
        Instant t0 = Instant.now();
        SessionInfo s1 = session(UUID.randomUUID(), "a", t0, Duration.ofMinutes(30));
        SessionInfo s2 = session(UUID.randomUUID(), "b", t0, Duration.ofMinutes(30));
        reg.start(s1);
        reg.start(s2);
        reg.finish(s1.sessionId(), SessionInfo.END_LOGOUT, t0.plusSeconds(10));

        List<SessionInfo> all = reg.listAll(t0.plusSeconds(20));
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("size() retourne le nombre total d'entrées y compris terminées")
    void sizeIncludesAll() {
        UserSessionRegistry reg = new UserSessionRegistry();
        assertEquals(0, reg.size());
        Instant t0 = Instant.now();
        reg.start(session(UUID.randomUUID(), "a", t0, Duration.ofMinutes(30)));
        reg.start(session(UUID.randomUUID(), "b", t0, Duration.ofMinutes(30)));
        assertEquals(2, reg.size());
    }
}
