package fr.inra.oresing.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estime la taille memoire des caches JVM via serialisation Jackson .
 *
 * <p>Strategie :
 * <ul>
 *   <li>resultat memoise pendant {@code ttlMinutes} ( aligne sur la
 *       config commune {@code OPENADOM_CACHE_SIZES_TTL_MINUTES} ,
 *       defaut 360 min = 6h ) ;</li>
 *   <li>refresh force ( {@code force=true} ) ignore le TTL et recompute
 *       ; cas d'usage : admin clique " Mesurer maintenant " apres une
 *       purge / preload pour voir l'etat instantane ;</li>
 *   <li>lock simple pour eviter le hammering concurrent ( 2 admins
 *       cliquent en meme temps -&gt; un seul compute , l'autre attend ) ;</li>
 *   <li>aucune intrusion dans les hot paths existants ( cache reads /
 *       writes inchanges ) .</li>
 * </ul>
 */
@Slf4j
@Component
public class CacheSizeEstimator {

    public record SizeReport(Map<String, Long> bytesByCache, Instant computedAt, long durationMs) {}

    @Value("${openadom.cache.sizes.ttl-minutes:360}")
    private long ttlMinutes;

    // Instance dediee a l'estimation : pas d'autowire Spring car le
    // projet n'expose pas de bean ObjectMapper global ( chaque service
    // instancie le sien , cf . pattern dans WorkflowLogRepository ,
    // IntegrityService , etc . ) . Une seule instance partagee suffit
    // ici car la serialisation est read-only et thread-safe .
    private final ObjectMapper mapper = new ObjectMapper();
    private final ServiceContainer serviceContainer;
    private final ReentrantLock computeLock = new ReentrantLock();
    private final AtomicReference<SizeReport> lastReportRef = new AtomicReference<>();

    public CacheSizeEstimator(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }

    /**
     * Renvoie le rapport ; recompute si {@code force=true} ou si le
     * cache memoire interne est expire .
     */
    public SizeReport getReport(boolean force) {
        SizeReport last = lastReportRef.get();
        if (!force && last != null && !isExpired(last)) {
            return last;
        }
        computeLock.lock();
        try {
            // Double-check apres acquisition du lock ( un autre thread a
            // pu calculer pendant l'attente ) .
            last = lastReportRef.get();
            if (!force && last != null && !isExpired(last)) {
                return last;
            }
            SizeReport computed = compute();
            lastReportRef.set(computed);
            return computed;
        } finally {
            computeLock.unlock();
        }
    }

    /** TTL configure en minutes ; <= 0 = jamais d'expiration . */
    public long ttlMinutes() {
        return ttlMinutes;
    }

    /** Invalide le rapport memoise ; le prochain {@link #getReport} recomputera . */
    public void invalidate() {
        lastReportRef.set(null);
    }

    private boolean isExpired(SizeReport report) {
        if (ttlMinutes <= 0) return false;
        return report.computedAt().isBefore(Instant.now().minus(Duration.ofMinutes(ttlMinutes)));
    }

    private SizeReport compute() {
        long t0 = System.currentTimeMillis();
        Map<String, Long> bytes = new LinkedHashMap<>();
        // L'ordre suit celui de CacheAdminResources.cacheStats pour
        // simplifier le merge cote frontend .
        bytes.put("filterList",              safeEstimate(serviceContainer.dataService()::estimateFilterListCacheSizeBytes));
        bytes.put("authorizationScopes",     safeEstimate(serviceContainer.authorizationService()::estimateAuthorizationScopesCacheSizeBytes));
        bytes.put("checkedFormatComponents", safeEstimate(serviceContainer.dataService()::estimateCheckedFormatComponentsCacheSizeBytes));
        bytes.put("referencedFiles",         safeEstimate(((fr.inra.oresing.rest.binaryFile.BinaryFileService) serviceContainer.binaryFileService())::estimateReferencedFilesCacheSizeBytes));
        long durationMs = System.currentTimeMillis() - t0;
        log.info("CacheSizeEstimator compute : {} ms , total = {} bytes",
                durationMs,
                bytes.values().stream().mapToLong(Long::longValue).sum());
        return new SizeReport(bytes, Instant.now(), durationMs);
    }

    private long safeEstimate(java.util.function.Function<ObjectMapper, Long> estimator) {
        try {
            return estimator.apply(mapper);
        } catch (RuntimeException ex) {
            log.warn("Cache size estimation failed : {}", ex.getMessage());
            return 0L;
        }
    }
}