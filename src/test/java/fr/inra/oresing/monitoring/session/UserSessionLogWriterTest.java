package fr.inra.oresing.monitoring.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link UserSessionLogWriter} — comportement fire-and-forget
 * de la queue bornée, flag {@code persistEnabled}.
 */
@Tag("domain.model")
@DisplayName("UserSessionLogWriter — writer asynchrone user_session_log")
class UserSessionLogWriterTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final Instant NOW     = Instant.now();

    private UserSessionLogEntry entry() {
        // UserSessionLogEntry(sessionId, userId, userLogin, ipAddress, userAgent,
        //                     loginTime, logoutTime, durationMs, endReason)
        return new UserSessionLogEntry(
                SESSION_ID, USER_ID, "alice", "127.0.0.1", "UA",
                NOW, NOW.plus(Duration.ofMinutes(30)),
                Duration.ofMinutes(30).toMillis(),
                SessionInfo.END_LOGOUT);
    }

    /** Crée un writer NON démarré (sans @PostConstruct). */
    private UserSessionLogWriter writer(UserSessionLogRepository repo, boolean persistEnabled) {
        return new UserSessionLogWriter(repo, persistEnabled, 10, 5, 2000L);
    }

    // ─── logAsync — persist enabled ──────────────────────────────────────────

    @Test
    @DisplayName("logAsync(null) est un no-op")
    void logAsyncNull() {
        UserSessionLogRepository repo = mock(UserSessionLogRepository.class);
        UserSessionLogWriter writer = writer(repo, true);
        assertThatCode(() -> writer.logAsync(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logAsync() avec persistEnabled=false ne remplit pas la queue")
    void logAsyncPersistDisabledDropsEntry() {
        UserSessionLogRepository repo = mock(UserSessionLogRepository.class);
        UserSessionLogWriter writer = writer(repo, false);

        writer.logAsync(entry());

        // Le repo ne doit jamais être appelé même si on force un flush
        // (pas de thread worker démarré sans @PostConstruct)
        verify(repo, never()).insertBatch(any());
    }

    @Test
    @DisplayName("logAsync() avec persistEnabled=true enfile l'entrée sans exception")
    void logAsyncPersistEnabled() {
        UserSessionLogRepository repo = mock(UserSessionLogRepository.class);
        UserSessionLogWriter writer = writer(repo, true);

        // La queue a capacité 10 → offre doit réussir sans exception
        assertThatCode(() -> writer.logAsync(entry())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("logAsync() : queue pleine → entrée droppée silencieusement")
    void logAsyncQueueFull() {
        UserSessionLogRepository repo = mock(UserSessionLogRepository.class);
        // Capacité de 1 — remplir puis déborder
        UserSessionLogWriter writer = new UserSessionLogWriter(repo, true, 1, 5, 2000L);

        UserSessionLogEntry e = entry();
        writer.logAsync(e);              // remplit la queue
        assertThatCode(() -> writer.logAsync(e)).doesNotThrowAnyException(); // overflow silencieux
    }
}
