package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Limiteur de taux appliqué aux imports de fichiers (POST multipart).
 *
 * <p>Contrairement à {@link ExtractionRateLimiter} qui s'appuie sur le
 * singleton cascade {@code UserRateLimiter}, celui-ci utilise une
 * implementation locale a base de {@link Semaphore} par utilisateur.
 * Raison : cascade UserRateLimiter est un singleton global. Partager
 * le meme pool de slots entre imports et extractions impacterait
 * l'UX ( un utilisateur avec 5 exports en cours ne pourrait plus
 * importer ). Quotas isolés = experience utilisateur correcte.
 *
 * <p>Quota par defaut : 3 imports concurrents par utilisateur.
 * Configurable via {@code app.import.max-concurrent-per-user} ou
 * l'env var {@code APP_IMPORT_MAX_CONCURRENT_PER_USER}.
 */
@Slf4j
@Service
public class ImportRateLimiter {

    private final Map<String, Semaphore> userSlots = new ConcurrentHashMap<>();
    private final int              maxConcurrentPerUser;
    private final OpenadomMetrics  metrics;

    public ImportRateLimiter(
            @Value("${app.import.max-concurrent-per-user:3}") final int maxConcurrentPerUser,
            OpenadomMetrics metrics) {
        this.maxConcurrentPerUser = maxConcurrentPerUser;
        this.metrics              = metrics;
        log.info("ImportRateLimiter ready : max {} imports concurrents par utilisateur",
                maxConcurrentPerUser);
    }

    /**
     * Tente de reserver un slot. Leve {@link ImportRateLimitExceededException}
     * si le quota est atteint (429 Too Many Requests cote HTTP).
     */
    public void acquireOrThrow(String userId) {
        Semaphore sem = userSlots.computeIfAbsent(
                userId, k -> new Semaphore(maxConcurrentPerUser, true));
        if (!sem.tryAcquire()) {
            int active = maxConcurrentPerUser - sem.availablePermits();
            log.warn("Quota d'imports atteint pour {} : {}/{}",
                    userId, active, maxConcurrentPerUser);
            metrics.recordImportRateLimited();
            throw new ImportRateLimitExceededException(userId, active, maxConcurrentPerUser);
        }
    }

    /**
     * Libere le slot reserve par {@link #acquireOrThrow(String)}. A appeler
     * systematiquement dans un bloc {@code finally}.
     */
    public void release(String userId) {
        Semaphore sem = userSlots.get(userId);
        if (sem != null) {
            sem.release();
        }
    }

    @PreDestroy
    void shutdown() {
        userSlots.clear();
    }
}
