package fr.inra.oresing.workflow.cascade.metrics;

import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.services.AuthorizationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Métriques Prometheus pour les 3 caches mémoire backend ( filterList ,
 * authorizationScopes , checkedFormatComponents ). Permet de tracer
 * l'occupation , les hits / miss , les invalidations dans le temps via
 * Grafana.
 *
 * <p>Les compteurs ( hit / miss / invalidate ) sont incrémentés depuis
 * les méthodes des services correspondants ( cf. recordHit / recordMiss
 * / recordInvalidation ) ; les gauges ( taille courante , capacité ) sont
 * lues à intervalle régulier par Micrometer via les accesseurs publics
 * exposés sur DataService et AuthorizationService.
 *
 * <p>Préfixe métrique : {@code oa_cache_*} ( cohérent avec les autres
 * métriques OpenADOM : {@code oa_import_*} , {@code oa_extraction_*} ).
 *
 * <p>Audit OA_FULL_REVIEW (8/5/26) - extrait dans son propre composant
 * pour éviter de polluer {@link OpenadomMetrics} qui est focalisé
 * cascade workflow.
 */
@Slf4j
@Component
public class OpenadomCacheMetrics {

    private static final String CACHE_PREFIX = "oa_cache";
    private static final String TAG_NAME = "name";

    private static final String FILTER_LIST = "filter_list";
    private static final String SCOPES = "authorization_scopes";
    private static final String CHECKED_FORMAT = "checked_format_components";

    private final MeterRegistry registry;
    private final DataService dataService;
    private final AuthorizationService authorizationService;

    private Counter filterListHit;
    private Counter filterListMiss;
    private Counter filterListInvalidate;
    private Counter scopesHit;
    private Counter scopesMiss;
    private Counter scopesInvalidate;
    private Counter checkedFormatHit;
    private Counter checkedFormatMiss;
    private Counter checkedFormatInvalidate;

    public OpenadomCacheMetrics(
            MeterRegistry registry,
            @Lazy DataService dataService,
            @Lazy AuthorizationService authorizationService) {
        this.registry = registry;
        this.dataService = dataService;
        this.authorizationService = authorizationService;
    }

    @PostConstruct
    void register() {
        try {
            // Gauges : taille courante des caches.
            registry.gauge(CACHE_PREFIX + "_size", Tags.of(TAG_NAME, FILTER_LIST),
                    dataService, DataService::getFilterListCacheSize);
            registry.gauge(CACHE_PREFIX + "_size", Tags.of(TAG_NAME, SCOPES),
                    authorizationService, AuthorizationService::getAuthorizationScopesCacheSize);
            registry.gauge(CACHE_PREFIX + "_size", Tags.of(TAG_NAME, CHECKED_FORMAT),
                    dataService, DataService::getCheckedFormatComponentsCacheSize);

            // Gauges : capacité max ( valeur statique mais utile dans le
            // dashboard pour calculer un % d'occupation ).
            registry.gauge(CACHE_PREFIX + "_max_entries", Tags.of(TAG_NAME, FILTER_LIST),
                    dataService, DataService::getFilterListCacheMaxEntries);
            registry.gauge(CACHE_PREFIX + "_max_entries", Tags.of(TAG_NAME, SCOPES),
                    authorizationService, AuthorizationService::getAuthorizationScopesCacheMaxEntries);
            registry.gauge(CACHE_PREFIX + "_max_entries", Tags.of(TAG_NAME, CHECKED_FORMAT),
                    dataService, DataService::getCheckedFormatComponentsCacheMaxEntries);

            // Counters : hits / miss / invalidations cumulés.
            filterListHit = Counter.builder(CACHE_PREFIX + "_hit_total")
                    .tag(TAG_NAME, FILTER_LIST).register(registry);
            filterListMiss = Counter.builder(CACHE_PREFIX + "_miss_total")
                    .tag(TAG_NAME, FILTER_LIST).register(registry);
            filterListInvalidate = Counter.builder(CACHE_PREFIX + "_invalidate_total")
                    .tag(TAG_NAME, FILTER_LIST).register(registry);

            scopesHit = Counter.builder(CACHE_PREFIX + "_hit_total")
                    .tag(TAG_NAME, SCOPES).register(registry);
            scopesMiss = Counter.builder(CACHE_PREFIX + "_miss_total")
                    .tag(TAG_NAME, SCOPES).register(registry);
            scopesInvalidate = Counter.builder(CACHE_PREFIX + "_invalidate_total")
                    .tag(TAG_NAME, SCOPES).register(registry);

            checkedFormatHit = Counter.builder(CACHE_PREFIX + "_hit_total")
                    .tag(TAG_NAME, CHECKED_FORMAT).register(registry);
            checkedFormatMiss = Counter.builder(CACHE_PREFIX + "_miss_total")
                    .tag(TAG_NAME, CHECKED_FORMAT).register(registry);
            checkedFormatInvalidate = Counter.builder(CACHE_PREFIX + "_invalidate_total")
                    .tag(TAG_NAME, CHECKED_FORMAT).register(registry);

            log.info("OpenadomCacheMetrics ready : 3 gauges de taille + 3 gauges de cap + 9 counters hit/miss/invalidate");
        } catch (Exception e) {
            log.warn("Échec enregistrement des métriques cache", e);
        }
    }

    public void recordFilterListHit() { if (filterListHit != null) filterListHit.increment(); }
    public void recordFilterListMiss() { if (filterListMiss != null) filterListMiss.increment(); }
    public void recordFilterListInvalidate() { if (filterListInvalidate != null) filterListInvalidate.increment(); }

    public void recordScopesHit() { if (scopesHit != null) scopesHit.increment(); }
    public void recordScopesMiss() { if (scopesMiss != null) scopesMiss.increment(); }
    public void recordScopesInvalidate() { if (scopesInvalidate != null) scopesInvalidate.increment(); }

    public void recordCheckedFormatHit() { if (checkedFormatHit != null) checkedFormatHit.increment(); }
    public void recordCheckedFormatMiss() { if (checkedFormatMiss != null) checkedFormatMiss.increment(); }
    public void recordCheckedFormatInvalidate() { if (checkedFormatInvalidate != null) checkedFormatInvalidate.increment(); }
}
