package fr.inra.oresing.monitoring.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link SessionExpirySweeper}.
 *
 * <p>Couvre : sweep nominal (JWT expirées persistées), absorption d'exception,
 * eviction des sessions terminées.
 */
@Tag("domain.model")
@DisplayName("SessionExpirySweeper — détection JWT expirés et éviction mémoire")
class SessionExpirySweeperTest {

    private static SessionInfo activeSession(UUID sessionId, Instant loginTime, Instant expiresAt) {
        return new SessionInfo(
                sessionId, UUID.randomUUID(), "alice", "127.0.0.1", "UA",
                loginTime, expiresAt, null, null, null);
    }

    private static SessionInfo expiredSession(UUID sessionId, Instant loginTime, Instant expiresAt) {
        return new SessionInfo(
                sessionId, UUID.randomUUID(), "alice", "127.0.0.1", "UA",
                loginTime, expiresAt, Instant.now(), SessionInfo.END_JWT_EXPIRED, null);
    }

    // ─── sweep() nominal ────────────────────────────────────────────────────

    @Test
    @DisplayName("sweep() : aucune session expirée → logWriter non appelé")
    void sweepNoExpired() {
        UserSessionRegistry  registry  = mock(UserSessionRegistry.class);
        UserSessionLogWriter logWriter = mock(UserSessionLogWriter.class);

        when(registry.listAll(any())).thenReturn(List.of());
        when(registry.evictTerminatedOlderThan(any(), any())).thenReturn(0);

        SessionExpirySweeper sweeper = new SessionExpirySweeper(registry, logWriter, 60);
        sweeper.sweep();

        verify(logWriter, never()).logAsync(any());
    }

    @Test
    @DisplayName("sweep() : session JWT_EXPIRED → logAsync appelé avec l'entry")
    void sweepExpiredPersisted() {
        UserSessionRegistry  registry  = mock(UserSessionRegistry.class);
        UserSessionLogWriter logWriter = mock(UserSessionLogWriter.class);

        UUID sessionId = UUID.randomUUID();
        Instant login  = Instant.now().minus(Duration.ofHours(2));
        Instant expiry = Instant.now().minus(Duration.ofHours(1));

        SessionInfo expired = expiredSession(sessionId, login, expiry);
        when(registry.listAll(any())).thenReturn(List.of(expired));
        when(registry.find(sessionId)).thenReturn(Optional.of(expired));
        when(registry.evictTerminatedOlderThan(any(), any())).thenReturn(0);

        SessionExpirySweeper sweeper = new SessionExpirySweeper(registry, logWriter, 60);
        sweeper.sweep();

        verify(logWriter).logAsync(any(UserSessionLogEntry.class));
    }

    @Test
    @DisplayName("sweep() : session active non expirée → logAsync non appelé")
    void sweepActiveNotLogged() {
        UserSessionRegistry  registry  = mock(UserSessionRegistry.class);
        UserSessionLogWriter logWriter = mock(UserSessionLogWriter.class);

        UUID sessionId = UUID.randomUUID();
        Instant login  = Instant.now().minus(Duration.ofMinutes(10));
        Instant expiry = Instant.now().plus(Duration.ofHours(1));

        SessionInfo active = activeSession(sessionId, login, expiry);
        when(registry.listAll(any())).thenReturn(List.of(active));
        when(registry.evictTerminatedOlderThan(any(), any())).thenReturn(0);

        SessionExpirySweeper sweeper = new SessionExpirySweeper(registry, logWriter, 60);
        sweeper.sweep();

        verify(logWriter, never()).logAsync(any());
    }

    @Test
    @DisplayName("sweep() : registry.find() retourne empty → logAsync non appelé")
    void sweepExpiredButRegistryFindEmpty() {
        UserSessionRegistry  registry  = mock(UserSessionRegistry.class);
        UserSessionLogWriter logWriter = mock(UserSessionLogWriter.class);

        UUID sessionId = UUID.randomUUID();
        Instant login  = Instant.now().minus(Duration.ofHours(2));
        Instant expiry = Instant.now().minus(Duration.ofHours(1));

        SessionInfo expired = expiredSession(sessionId, login, expiry);
        when(registry.listAll(any())).thenReturn(List.of(expired));
        when(registry.find(sessionId)).thenReturn(Optional.empty());
        when(registry.evictTerminatedOlderThan(any(), any())).thenReturn(0);

        SessionExpirySweeper sweeper = new SessionExpirySweeper(registry, logWriter, 60);
        sweeper.sweep();

        verify(logWriter, never()).logAsync(any());
    }

    // ─── absorption d'exception ──────────────────────────────────────────────

    @Test
    @DisplayName("sweep() : exception du registry → absorbée (ne casse pas le scheduler)")
    void sweepSwallowsException() {
        UserSessionRegistry  registry  = mock(UserSessionRegistry.class);
        UserSessionLogWriter logWriter = mock(UserSessionLogWriter.class);

        when(registry.listAll(any())).thenThrow(new RuntimeException("DB down"));

        SessionExpirySweeper sweeper = new SessionExpirySweeper(registry, logWriter, 60);
        assertThatCode(sweeper::sweep).doesNotThrowAnyException();
    }

    // ─── éviction ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sweep() : evictTerminatedOlderThan est toujours appelé")
    void sweepCallsEvict() {
        UserSessionRegistry  registry  = mock(UserSessionRegistry.class);
        UserSessionLogWriter logWriter = mock(UserSessionLogWriter.class);

        when(registry.listAll(any())).thenReturn(List.of());
        when(registry.evictTerminatedOlderThan(any(), any())).thenReturn(3);

        SessionExpirySweeper sweeper = new SessionExpirySweeper(registry, logWriter, 60);
        sweeper.sweep();

        verify(registry).evictTerminatedOlderThan(any(Duration.class), any(Instant.class));
    }

    @Test
    @DisplayName("constructeur : memoryRetentionMinutes <= 0 est borné à 1 min")
    void constructorClampsRetentionToMin() {
        UserSessionRegistry  registry  = mock(UserSessionRegistry.class);
        UserSessionLogWriter logWriter = mock(UserSessionLogWriter.class);
        // Ne doit pas lever d'exception
        assertThatCode(() -> new SessionExpirySweeper(registry, logWriter, 0))
                .doesNotThrowAnyException();
    }
}
