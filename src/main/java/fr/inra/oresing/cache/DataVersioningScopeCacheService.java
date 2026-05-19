package fr.inra.oresing.cache;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.DataVersioningScopeCacheRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Orchestre le cache materialise des dropdowns de scope ( ecran
 * DataVersioningView ) :
 *
 * <ul>
 *   <li>Si le flag {@code openadom.cache.data-versioning-scope.enabled}
 *       est {@code true} : lookup table {@code <app>.data_versioning_scope_cache} ,
 *       miss = compute via le supplier passe par le caller + INSERT
 *       lazy + retour ; hit = lecture pre-calculee instantanee .</li>
 *   <li>Si {@code false} : bypass complet , chaque appel re-execute le
 *       supplier ( etat legacy , pour debug ou rollback rapide ) .</li>
 * </ul>
 *
 * <p>L'invalidation se fait sur 3 chemins :
 * <ol>
 *   <li>Triggers SQL statement-level sur {@code referencevalue} ( cf.
 *       migration V8 ) : INSERT/DELETE de data ;</li>
 *   <li>{@link #invalidateByUserId} : grant / revoke d'un scope a un
 *       user ;</li>
 *   <li>{@link #invalidateAllForApp} : YAML edit ( config application
 *       changee ) , admin invalidate-caches .</li>
 * </ol>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class DataVersioningScopeCacheService {

    private final OreSiRepository repository;

    @Value("${openadom.cache.data-versioning-scope.enabled:true}")
    private boolean enabled;

    @Value("${openadom.cache.data-versioning-scope.max-entries-per-app:5000}")
    private int maxEntriesPerApp;

    public DataVersioningScopeCacheService(OreSiRepository repository) {
        this.repository = repository;
    }

    @jakarta.annotation.PostConstruct
    void logConfig() {
        log.info("DataVersioningScopeCacheService : enabled={} maxEntriesPerApp={}",
                enabled, maxEntriesPerApp);
    }

    /**
     * Retourne les valeurs distinctes visibles pour le couple ( refType ,
     * column , user ) . Hit si l'entree existe en table , miss = compute
     * via {@code freshSupplier} + INSERT lazy + retour . Si le cache
     * est desactive , le supplier est appele systematiquement et rien
     * n'est ecrit en table .
     *
     * @param application    application cible
     * @param refType        referencetype demande
     * @param column         colonne demandee ( CSV-encoded comme dans
     *                       l'API legacy de findDataColumn )
     * @param userId         id de l'utilisateur appelant ( pour scope-aware
     *                       caching - cf. SET ROLE Postgres )
     * @param freshSupplier  recompute la donnee fraiche ( miss path )
     * @return liste des valeurs visibles , jamais null ( liste vide si rien )
     */
    public List<List<String>> getOrCompute(Application application,
                                           String refType,
                                           String column,
                                           UUID userId,
                                           Supplier<List<List<String>>> freshSupplier) {
        if (!enabled || userId == null) {
            return Optional.ofNullable(freshSupplier.get()).orElse(List.of());
        }

        DataVersioningScopeCacheRepository repo = repository.getRepository(application).dataVersioningScopeCache();

        Optional<List<List<String>>> cached = repo.find(refType, column, userId);
        if (cached.isPresent()) {
            log.debug("dataVersioningScopeCache hit  for ({}, {}, {}, user={})",
                    application.getName(), refType, column, userId);
            return cached.get();
        }
        log.debug("dataVersioningScopeCache miss for ({}, {}, {}, user={}) , computing",
                application.getName(), refType, column, userId);

        List<List<String>> fresh = Optional.ofNullable(freshSupplier.get()).orElse(List.of());

        // Plafond defensif : si la table depasse maxEntriesPerApp pour cette
        // application , on n'INSERT plus ( degrade en bypass-cache pour
        // l'entree manquante ) jusqu'au prochain trigger SQL d'invalidation
        // ou hook applicatif . Evite l'explosion par un user qui rotate
        // ses scopes a haute frequence .
        if (repo.count() < maxEntriesPerApp) {
            try {
                repo.put(refType, column, userId, fresh);
            } catch (RuntimeException e) {
                // Best-effort : un echec d'insert ne casse pas le flow .
                log.warn("dataVersioningScopeCache put failed for {}::{}::{} user={} : {}",
                        application.getName(), refType, column, userId, e.getMessage());
            }
        } else {
            log.debug("dataVersioningScopeCache cap reached ({} entries) for app={} ; skipping put",
                    maxEntriesPerApp, application.getName());
        }
        return fresh;
    }

    public void invalidateByReferenceType(Application application, String referenceType) {
        if (application == null || referenceType == null) return;
        try {
            repository.getRepository(application).dataVersioningScopeCache()
                    .invalidateByReferenceType(referenceType);
        } catch (RuntimeException e) {
            log.warn("invalidateByReferenceType failed for {}::{}: {}",
                    application.getName(), referenceType, e.getMessage());
        }
    }

    public void invalidateByUserId(Application application, UUID userId) {
        if (application == null || userId == null) return;
        try {
            repository.getRepository(application).dataVersioningScopeCache()
                    .invalidateByUserId(userId);
        } catch (RuntimeException e) {
            log.warn("invalidateByUserId failed for {}::{}: {}",
                    application.getName(), userId, e.getMessage());
        }
    }

    public void invalidateAllForApp(Application application) {
        if (application == null) return;
        try {
            int n = repository.getRepository(application).dataVersioningScopeCache().invalidateAll();
            log.info("dataVersioningScopeCache.invalidateAll for {} : {} rows", application.getName(), n);
        } catch (RuntimeException e) {
            log.warn("invalidateAll failed for {} : {}", application.getName(), e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
    public int getMaxEntriesPerApp() { return maxEntriesPerApp; }
}
