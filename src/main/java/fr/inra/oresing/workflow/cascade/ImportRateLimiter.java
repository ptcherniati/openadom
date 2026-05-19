package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Configurable via {@code cascade.import.max-concurrent-per-user} ou
 * l'env var {@code CASCADE_IMPORT_MAX_CONCURRENT_PER_USER}.
 */
@Slf4j
@Service
public class ImportRateLimiter {

    private final Map<String, Semaphore> userSlots = new ConcurrentHashMap<>();
    private volatile int                 maxConcurrentPerUser;
    private final OpenadomMetrics       metrics;
    private final WorkflowLogWriter     logWriter;
    private final AuthenticationService authenticationService;

    public ImportRateLimiter(
            @Value("${cascade.import.max-concurrent-per-user:3}") final int maxConcurrentPerUser,
            OpenadomMetrics metrics,
            WorkflowLogWriter logWriter,
            AuthenticationService authenticationService) {
        this.maxConcurrentPerUser  = maxConcurrentPerUser;
        this.metrics               = metrics;
        this.logWriter             = logWriter;
        this.authenticationService = authenticationService;
        log.info("ImportRateLimiter ready : max {} imports concurrents par utilisateur",
                maxConcurrentPerUser);
    }

    /**
     * Mute le quota max a chaud . S'applique aux NOUVEAUX semaphores
     * crees ; les semaphores deja attaches a un user gardent l'ancienne
     * valeur jusqu'a leur drain . Reset le map userSlots pour forcer la
     * recreation au prochain acquire ( les imports en cours conservent
     * leur permit sur l'ancien semaphore via reference forte locale -
     * pas de fuite , release standard ) .
     */
    public synchronized void setMaxConcurrentPerUser(int n) {
        if (n < 1) throw new IllegalArgumentException("max must be >= 1");
        if (n == this.maxConcurrentPerUser) return;
        log.info("ImportRateLimiter : max {} -> {} ( reset user slots map )",
                this.maxConcurrentPerUser, n);
        this.maxConcurrentPerUser = n;
        this.userSlots.clear();
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
            logRejection(userId);
            throw new ImportRateLimitExceededException(userId, active, maxConcurrentPerUser);
        }
    }

    private void logRejection(String userId) {
        try {
            Instant now = Instant.now();
            logWriter.logAsync(new WorkflowLogEntry(
                    UUID.randomUUID(),
                    WorkflowLogEntry.TYPE_IMPORT,
                    UUID.fromString(userId),
                    resolveCurrentLogin(),
                    null,     // application et data_type inconnus au niveau du rate-limiter
                    null,
                    null,
                    now, now, Duration.ZERO,
                    WorkflowLogEntry.STATUS_RATE_LIMITED,
                    0L, 0L, 0, 0L,
                    List.of(),
                    null));
        } catch (IllegalArgumentException e) {
            log.warn("Format UUID invalide , skip log RATE_LIMITED [userId={}]", userId);
        }
    }

    /**
     * Best-effort resolution of the caller login from the current request
     * context. Returns null if no user is bound to the thread ( e.g. call
     * originating from a background task ) : in that case the dashboard
     * will degrade to showing only the UUID.
     */
    private String resolveCurrentLogin() {
        try {
            return authenticationService.getCurrentUserRoles().userLogin();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Libere le slot reserve par {@link #acquireOrThrow(String)}. A appeler
     * systematiquement dans un bloc {@code finally}.
     *
     * <p>when the user has no remaining active slot we evict the
     * Semaphore from the map. Without this purge , every login that ever
     * imported a file would keep a Semaphore for the JVM lifetime - a
     * confirmed slow leak. The eviction is atomic via {@code compute}
     * so a concurrent {@link #acquireOrThrow} cannot lose a permit.
     */
    public void release(String userId) {
        if (userId == null) {
            return;
        }
        userSlots.compute(userId, (uid, sem) -> {
            if (sem == null) {
                return null;
            }
            sem.release();
            // Evict only if the user currently holds zero permits ( all
            // released ). Concurrent acquireOrThrow that arrives during
            // this compute() will block on the lock , then either re-create
            // the semaphore ( evicted case ) or reuse the surviving one.
            return sem.availablePermits() >= maxConcurrentPerUser ? null : sem;
        });
    }

    @PreDestroy
    void shutdown() {
        userSlots.clear();
    }

    /**
     * Quota maximum d'imports concurrents par utilisateur.
     * Lecture seule , exposé pour le dashboard de configuration.
     */
    public synchronized int getMaxConcurrentPerUser() {
        return maxConcurrentPerUser;
    }

    /**
     * Snapshot des slots actuellement reservés par utilisateur.
     * Cle = userId , valeur = nombre de slots utilisés ( 0 .. max ).
     * Les utilisateurs sans slot actif ne sont pas inclus dans la map
     * pour ne pas faire grossir la reponse inutilement.
     */
    public Map<String, Integer> snapshotUsedSlots() {
        Map<String, Integer> snapshot = new java.util.HashMap<>();
        userSlots.forEach((userId, sem) -> {
            int used = maxConcurrentPerUser - sem.availablePermits();
            if (used > 0) {
                snapshot.put(userId, used);
            }
        });
        return snapshot;
    }
}