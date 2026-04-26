package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import fr.inrae.ore.cascade.core.ratelimit.UserRateLimiter;
import fr.inrae.ore.cascade.model.ratelimit.RateLimitConfig;
import fr.inrae.ore.cascade.model.ratelimit.RejectionPolicy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    private final int                   maxConcurrentPerUser;
    private final long                  acquireTimeoutSeconds;
    private final OpenadomMetrics       metrics;
    private final WorkflowLogWriter     logWriter;
    private final AuthenticationService authenticationService;
    private UserRateLimiter             limiter;

    public ExtractionRateLimiter(
            @Value("${cascade.extraction.max-concurrent-per-user:5}") final int maxConcurrentPerUser,
            @Value("${cascade.extraction.acquire-timeout-seconds:0}") final long acquireTimeoutSeconds,
            OpenadomMetrics metrics,
            WorkflowLogWriter logWriter,
            AuthenticationService authenticationService) {
        this.maxConcurrentPerUser  = maxConcurrentPerUser;
        this.acquireTimeoutSeconds = acquireTimeoutSeconds;
        this.metrics               = metrics;
        this.logWriter             = logWriter;
        this.authenticationService = authenticationService;
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
            logRejection(userId, type);
            throw new ExtractionRateLimitExceededException(userId, active, maxConcurrentPerUser);
        }
    }

    private void logRejection(String userId, String type) {
        try {
            String workflowType = switch (type) {
                case "zip"              -> WorkflowLogEntry.TYPE_EXTRACT_ZIP;
                case "csv"              -> WorkflowLogEntry.TYPE_EXTRACT_CSV;
                case "charte"           -> WorkflowLogEntry.TYPE_EXTRACT_CHARTE;
                case "additional_files" -> WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES;
                default                 -> "EXTRACT_" + type.toUpperCase();
            };
            Instant now = Instant.now();
            logWriter.logAsync(new WorkflowLogEntry(
                    UUID.randomUUID(),
                    workflowType,
                    UUID.fromString(userId),
                    resolveCurrentLogin(),
                    null, null, null,
                    now, now, Duration.ZERO,
                    WorkflowLogEntry.STATUS_RATE_LIMITED,
                    0L, 0L, 0, 0L,
                    List.of(),
                    null));
        } catch (IllegalArgumentException e) {
            log.warn("Format UUID invalide , skip log RATE_LIMITED [userId={} type={}]", userId, type);
        }
    }

    /**
     * Best-effort resolution of the caller login from the current request
     * context. Returns null if no user is bound to the thread : the
     * dashboard will then fall back to showing the UUID.
     */
    private String resolveCurrentLogin() {
        try {
            return authenticationService.getCurrentUserRoles().userLogin();
        } catch (RuntimeException e) {
            return null;
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
