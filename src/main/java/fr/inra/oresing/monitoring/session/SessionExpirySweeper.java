package fr.inra.oresing.monitoring.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Scheduled task qui :
 *
 * <ol>
 *   <li>Detecte les sessions ACTIVE dont le JWT a expire et les persiste
 *       en {@code oa_metrics.user_session_log} avec
 *       {@code endReason = JWT_EXPIRED} . Sans ce sweeper , les
 *       expirations resteraient uniquement en RAM et n'apparaitraient
 *       jamais dans l'historique .</li>
 *   <li>Evicte du registry in-memory les sessions deja terminees plus
 *       anciennes que {@code retention} ( default 1h ) pour borner la
 *       RAM .</li>
 * </ol>
 *
 * <p>Frequence configurable via {@code app.session.sweep.fixed-rate-ms}
 * ( default 60s ) .
 *
 * @since Sprint Sessions ( monitoring )
 */
@Slf4j
@Component
public class SessionExpirySweeper {

    private final UserSessionRegistry  registry;
    private final UserSessionLogWriter logWriter;
    private final Duration             memoryRetention;

    public SessionExpirySweeper(
            UserSessionRegistry registry,
            UserSessionLogWriter logWriter,
            @Value("${app.session.memory-retention-minutes:60}") int memoryRetentionMinutes) {
        this.registry        = registry;
        this.logWriter       = logWriter;
        this.memoryRetention = Duration.ofMinutes(Math.max(1, memoryRetentionMinutes));
    }

    /**
     * Tick toutes les {@code app.session.sweep.fixed-rate-ms} ms ( default
     * 60s ) . Idempotent : appeler {@code finish()} sur une session deja
     * terminee est un no-op .
     */
    @Scheduled(fixedRateString = "${app.session.sweep.fixed-rate-ms:60000}",
               initialDelayString = "${app.session.sweep.initial-delay-ms:60000}")
    public void sweep() {
        Instant now = Instant.now();
        try {
            // Snapshot listAll() trigger detectJwtExpired() interne -> les
            // sessions actives expirees recoivent endTime + endReason .
            registry.listAll(now).stream()
                    .filter(s -> SessionInfo.END_JWT_EXPIRED.equals(s.endReason()))
                    .forEach(s -> {
                        // listAll a deja mute en RAM ; on persiste l'entry
                        // si elle vient juste de basculer ( best effort ;
                        // l'INSERT ON CONFLICT DO NOTHING garantit
                        // l'idempotence si on re-passe ) .
                        Optional<SessionInfo> snapshot = registry.find(s.sessionId());
                        snapshot.ifPresent(snap ->
                                logWriter.logAsync(UserSessionLogEntry.fromSession(snap)));
                    });

            int evicted = registry.evictTerminatedOlderThan(memoryRetention, now);
            if (evicted > 0) {
                log.debug("SessionExpirySweeper : {} entries terminees evictees ( retention {} )",
                        evicted, memoryRetention);
            }
        } catch (RuntimeException ex) {
            log.warn("SessionExpirySweeper : tick exception ( non-fatal ) : {}", ex.getMessage());
        }
    }
}
