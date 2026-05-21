package fr.inra.oresing.rest.data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter sliding-window pour l'endpoint {@code GET /filters} .
 * Empêche un utilisateur unique de saturer le pool Hikari ou le CPU
 * backend en spammant l'endpoint avec des refType différents
 * ( contournant le {@code singleflight} qui ne dédupe que par même
 * clé de cache ) .
 *
 * <p>Algorithme : fenêtre glissante de {@link #windowSeconds} secondes ,
 * compte des timestamps des appels passés par utilisateur , refuse si
 * > {@link #maxPerWindow} appels dans la fenêtre . Mémoire bornée
 * naturellement par éviction des timestamps obsolètes à chaque check .
 *
 * <p>Quota par défaut : 30 appels / 60 s par utilisateur . Configurable
 * via {@code openadom.ratelimit.filters.max-per-window} ( fréquence )
 * et {@code openadom.ratelimit.filters.window-seconds} ( durée fenêtre ) .
 * Désactivable en mettant {@code max-per-window} à 0 ou négatif .
 *
 * <p>Thread-safety : {@link ConcurrentHashMap} pour le map global +
 * synchronisation locale par-user sur la {@link Deque} pendant le check .
 * Le synchronized est très court ( O(window-size) , 30 entrées max ) .
 *
 * @author R.YAHIAOUI
 * @since 2026-05-21 ( resilience 10 users : anti-spam /filters )
 */
@Slf4j
@Service
public class FilterListRateLimiter {

    private final int maxPerWindow;
    private final int windowSeconds;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public FilterListRateLimiter(
            @Value("${openadom.ratelimit.filters.max-per-window:30}") int maxPerWindow,
            @Value("${openadom.ratelimit.filters.window-seconds:60}") int windowSeconds) {
        this.maxPerWindow = maxPerWindow;
        this.windowSeconds = windowSeconds;
        if (maxPerWindow > 0) {
            log.info("FilterListRateLimiter ready : max {} req / {}s par utilisateur",
                    maxPerWindow, windowSeconds);
        } else {
            log.info("FilterListRateLimiter disabled ( max-per-window = {} )", maxPerWindow);
        }
    }

    /**
     * Enregistre un appel /filters pour cet utilisateur . Lève
     * {@link FilterListRateLimitExceededException} ( -> HTTP 429 )
     * si le quota est dépassé dans la fenêtre glissante .
     *
     * @param userId identifiant utilisateur ( UUID String ) , {@code null}
     *               toléré ( cas anonymous : passe sans rate-limit )
     */
    public void acquireOrThrow(String userId) {
        if (maxPerWindow <= 0 || userId == null) return;
        final Instant now = Instant.now();
        final Instant cutoff = now.minusSeconds(windowSeconds);
        final Deque<Instant> queue = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (queue) {
            // Évince les hits sortis de la fenêtre ( garde uniquement
            // ceux postérieurs au cutoff ) .
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
                queue.pollFirst();
            }
            if (queue.size() >= maxPerWindow) {
                log.warn("Quota /filters dépassé pour user {} : {} req dans la fenêtre {}s",
                        userId, queue.size(), windowSeconds);
                throw new FilterListRateLimitExceededException(userId, queue.size(), maxPerWindow, windowSeconds);
            }
            queue.addLast(now);
        }
    }

    /**
     * Exception levée quand un utilisateur dépasse son quota /filters .
     * Mappée HTTP 429 par {@code OreSiNgExceptionHandler} ( pattern
     * identique à {@code ImportRateLimitExceededException} ) .
     */
    public static class FilterListRateLimitExceededException extends RuntimeException {
        private final String userId;
        private final int currentCount;
        private final int maxAllowed;
        private final int windowSeconds;

        public FilterListRateLimitExceededException(String userId, int currentCount, int maxAllowed, int windowSeconds) {
            super("Rate limit exceeded for /filters : user=" + userId
                    + " current=" + currentCount + "/" + maxAllowed
                    + " window=" + windowSeconds + "s");
            this.userId = userId;
            this.currentCount = currentCount;
            this.maxAllowed = maxAllowed;
            this.windowSeconds = windowSeconds;
        }

        public String getUserId() { return userId; }
        public int getCurrentCount() { return currentCount; }
        public int getMaxAllowed() { return maxAllowed; }
        public int getWindowSeconds() { return windowSeconds; }
    }
}
