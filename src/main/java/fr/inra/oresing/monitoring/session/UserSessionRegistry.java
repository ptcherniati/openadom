package fr.inra.oresing.monitoring.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Registry in-memory des sessions utilisateurs authentifiees .
 *
 * <p>Pattern identique a
 * {@link fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry}
 * adapte au domaine sessions :
 *
 * <ul>
 *   <li>{@link #start(SessionInfo)} appele au login OK .</li>
 *   <li>{@link #finish(UUID, String, Instant)} appele au logout explicite
 *       ou par detection lazy ( {@link #listActive(Instant)} marque
 *       JWT_EXPIRED les sessions dont {@code expiresAt <= now} ) .</li>
 *   <li>{@link #listActive(Instant)} et {@link #listAll(Instant)}
 *       retournent une vue triee par loginTime DESC ( les plus recentes
 *       en premier ) .</li>
 * </ul>
 *
 * <p>Thread-safety : tout est gere via {@link ConcurrentHashMap} ; les
 * mutations ne se chevauchent pas pour une meme {@code sessionId} et les
 * listings sont weakly-consistent ( normal pour un dashboard live ) .
 *
 * <p>Pas de cleanup periodique : les entrees DISCONNECTED restent dans
 * la map jusqu'au prochain {@link #evictTerminatedOlderThan(java.time.Duration, Instant)}
 * appele par un scheduled task ( ou jamais dans Sprint S.1 - acceptable
 * pour un volume de sessions courant , ~ centaines max ) .
 */
@Slf4j
@Service
public class UserSessionRegistry {

    private final ConcurrentMap<UUID, SessionInfo> bySessionId = new ConcurrentHashMap<>();

    /** Enregistre une nouvelle session . Idempotent par sessionId . */
    public void start(SessionInfo session) {
        if (session == null || session.sessionId() == null) {
            return;
        }
        bySessionId.put(session.sessionId(), session);
        log.debug("Session registered : {} / {}", session.userLogin(), session.sessionId());
    }

    /**
     * Termine une session avec la raison fournie . No-op si la session
     * n'existe plus ou est deja terminee .
     */
    public Optional<SessionInfo> finish(UUID sessionId, String reason, Instant endTime) {
        if (sessionId == null) return Optional.empty();
        SessionInfo[] terminated = new SessionInfo[1];
        bySessionId.computeIfPresent(sessionId, (id, cur) -> {
            if (cur.endTime() != null) {
                return cur;                                      // already finished
            }
            terminated[0] = cur.withEnd(endTime != null ? endTime : Instant.now(), reason);
            return terminated[0];
        });
        return Optional.ofNullable(terminated[0]);
    }

    /** Lookup direct par sessionId . */
    public Optional<SessionInfo> find(UUID sessionId) {
        return Optional.ofNullable(bySessionId.get(sessionId));
    }

    /** Nombre total d'entries en memoire ( actives + terminees ) . */
    public int size() {
        return bySessionId.size();
    }

    /**
     * Lazy detection : passe les sessions dont {@code expiresAt <= now} en
     * JWT_EXPIRED . Appele a chaque listing pour eviter d'avoir un thread
     * scheduled dedie .
     */
    private void detectJwtExpired(Instant now) {
        bySessionId.replaceAll((id, cur) -> {
            if (cur.endTime() == null
                    && cur.expiresAt() != null
                    && !now.isBefore(cur.expiresAt())) {
                return cur.withEnd(cur.expiresAt(), SessionInfo.END_JWT_EXPIRED);
            }
            return cur;
        });
    }

    /**
     * Liste des sessions ACTIVE au moment {@code now} , triees par
     * loginTime DESC ( plus recentes en premier ) .
     */
    public List<SessionInfo> listActive(Instant now) {
        detectJwtExpired(now);
        return bySessionId.values().stream()
                .filter(s -> s.isActive(now))
                .sorted(Comparator.comparing(SessionInfo::loginTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Liste de toutes les sessions ( actives + terminees ) encore
     * presentes dans le registry , triees par loginTime DESC .
     */
    public List<SessionInfo> listAll(Instant now) {
        detectJwtExpired(now);
        return bySessionId.values().stream()
                .sorted(Comparator.comparing(SessionInfo::loginTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Evicte les sessions terminees ( endTime != null ) plus anciennes
     * que {@code retention} . Permet de borner la RAM si beaucoup de
     * logins / logouts . Appelable depuis un scheduled task externe .
     */
    public int evictTerminatedOlderThan(java.time.Duration retention, Instant now) {
        Instant cutoff = now.minus(retention);
        int[] removed = { 0 };
        bySessionId.entrySet().removeIf(e -> {
            SessionInfo s = e.getValue();
            boolean stale = s.endTime() != null && s.endTime().isBefore(cutoff);
            if (stale) removed[0]++;
            return stale;
        });
        if (removed[0] > 0) {
            log.debug("UserSessionRegistry : evicted {} terminated sessions older than {}", removed[0], retention);
        }
        return removed[0];
    }
}
