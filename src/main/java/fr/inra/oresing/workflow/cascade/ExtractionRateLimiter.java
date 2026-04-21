package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import fr.inrae.ore.cascade.core.ratelimit.UserRateLimiter;
import fr.inrae.ore.cascade.model.ratelimit.RateLimitConfig;
import fr.inrae.ore.cascade.model.ratelimit.RejectionPolicy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Limiteur de taux partagé par tous les endpoints d'extraction de données
 * (ZIP, CSV streaming, additionalFiles).
 *
 * <p>Encapsule le {@link UserRateLimiter} cascade en tant que primitive
 * standalone : pas de workflow cascade ni de Source/Sink, juste un
 * mécanisme d'acquisition/libération de slots par utilisateur pour
 * protéger le serveur contre la saturation sur les endpoints coûteux.
 *
 * <p>Introduit en phase 1c-bis, renommé en phase 1c-full de l'issue #62
 * pour refléter son usage étendu au-delà du seul endpoint ZIP.
 */
@Slf4j
@Service
public class ExtractionRateLimiter {

    private final int              maxConcurrentPerUser;
    private final long             acquireTimeoutSeconds;
    private final OpenadomMetrics  metrics;
    private UserRateLimiter        limiter;

    public ExtractionRateLimiter(
            @Value("${app.extraction.max-concurrent-per-user:5}") final int maxConcurrentPerUser,
            @Value("${app.extraction.acquire-timeout-seconds:0}") final long acquireTimeoutSeconds,
            OpenadomMetrics metrics) {
        this.maxConcurrentPerUser  = maxConcurrentPerUser;
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
        this.metrics               = metrics;
    }

    @PostConstruct
    void init() {
        RejectionPolicy policy = acquireTimeoutSeconds > 0
                ? RejectionPolicy.WAIT_WITH_TIMEOUT
                : RejectionPolicy.REJECT_IMMEDIATELY;

        RateLimitConfig config = new RateLimitConfig(
                maxConcurrentPerUser,
                acquireTimeoutSeconds,
                true,
                policy);

        this.limiter = UserRateLimiter.initialize(config);
        log.info("ExtractionRateLimiter ready : max {} extractions concurrentes par utilisateur, policy {}",
                maxConcurrentPerUser, policy);
    }

    /**
     * Tente de réserver un slot pour l'utilisateur. Lève
     * {@link ExtractionRateLimitExceededException} si le quota est atteint.
     *
     * @param userId identifiant utilisateur
     * @param type   zip / csv / additional_files ( tag metric )
     */
    public void acquireOrThrow(String userId, String type) {
        if (!limiter.tryAcquire(userId)) {
            int active = limiter.getActiveCount(userId);
            log.warn("Quota d'extractions atteint pour {} : {}/{}",
                    userId, active, maxConcurrentPerUser);
            metrics.recordExtractionRateLimited(type);
            throw new ExtractionRateLimitExceededException(userId, active, maxConcurrentPerUser);
        }
    }

    /**
     * Libère le slot réservé par {@link #acquireOrThrow(String)}. À appeler
     * systématiquement dans un bloc {@code finally}.
     */
    public void release(String userId) {
        limiter.release(userId);
    }
}
