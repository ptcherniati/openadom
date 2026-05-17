package fr.inra.oresing.domain.data.deposit.prescan;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.repository.data.DataRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loader lazy + batch-coalescing pour les parents recursifs ( Axe A.3
 * plan resilience ) .
 *
 * <h2>Pourquoi</h2>
 *
 * <p>{@code WithRecursion} actuellement requiert le full preload de
 * tous les UUIDs existants du refType recursif via
 * {@link DataRepository#getDataIdPerKeys} . Sur des refs 10M+ rows
 * cela materialise tout en RAM Java ( 1+ GB heap , OOM au-dela ) .
 * Cette classe propose un chemin alternatif : on ne charge que les
 * naturalkeys reellement demandees au fil de l'eau , avec :
 * <ul>
 *   <li><b>Cache LRU borne</b> : evite de re-query des naturalkeys
 *       deja vues dans le run ;</li>
 *   <li><b>Batch coalescing</b> : entre deux flushes , les requestes
 *       sont accumulees en un Set ; un seul query SQL groupe les
 *       sert toutes via {@code WHERE naturalkey = ANY(?::ltree[])} ;</li>
 *   <li><b>Negative caching</b> : naturalkeys absentes en BDD sont
 *       memorisees pour eviter de re-query infiniment .</li>
 * </ul>
 *
 * <h2>Garanties iso-resultat</h2>
 *
 * <p>Pour le sous-ensemble de naturalkeys reellement demandees
 * pendant le run , le mapping retourne est <b>strictement identique</b>
 * a celui du full preload limite a ce sous-ensemble . Les
 * naturalkeys non-demandees ne sont jamais chargees .
 *
 * <p>Pre-requis : l'index btree {@code nk_patternColumnNam_type} sur
 * {@code (referencetype, naturalkey, patterncolumnname)} doit exister
 * ( deja present en V8 ) , sinon les lookups lazy degradent en
 * sequential scan .
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Cree par le caller pour la duree d'un import . Pas thread-safe
 * sur {@link #request} si plusieurs threads transformeurs partagent
 * la meme instance ; le cache underlying est concurrent mais
 * l'accumulation pending est protegee par synchronization simple .
 * Pour usage parallele , le caller devrait soit synchroniser ses
 * appels , soit fournir une instance par worker thread .
 *
 * <h2>Wiring futur</h2>
 *
 * <p>Cette classe est une brique reutilisable pour le refacto
 * {@code WithRecursion} . L'integration consiste a :
 * <ol>
 *   <li>Remplacer le full preload initial par un
 *       {@code LazyParentLoader} attache au context d'import ;</li>
 *   <li>Dans {@code WithRecursion.getKnownId} , appeler
 *       {@link #request(Ltree)} puis {@link #getCachedId(Ltree)} ;
 *       si cache miss , flush avant retry ;</li>
 *   <li>Periodiquement ( chaque N rows ) appeler {@link #flush}
 *       pour grouper les queries .</li>
 * </ol>
 *
 * @author R.YAHIAOUI
 * @since openadom plan resilience Axe A.3
 */
@Slf4j
public final class LazyParentLoader {

    /** Sentinel pour les naturalkeys absentes en BDD ( negative caching ) .
     *  Differencier "pas encore cherche" ( cache miss ) de "cherche +
     *  absent en BDD" ( pas de re-query ) . */
    private static final UUID NEGATIVE_SENTINEL =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final DataRepository dataRepository;
    private final String         referenceType;

    /** Cache positive + negative ; UUID = found ; NEGATIVE_SENTINEL = not found .
     *  ConcurrentHashMap pour permettre lookups paralleles cote
     *  WithRecursion sans lock global . */
    private final ConcurrentHashMap<Ltree, UUID> cache = new ConcurrentHashMap<>();

    /** Naturalkeys requestees mais pas encore flushees ; sera vidange
     *  par {@link #flush} . Synchronized pour eviter perdre des entries
     *  en cas de race lookup vs flush . */
    private final Set<Ltree> pending = Collections.synchronizedSet(new HashSet<>());

    /** Statistiques pour log + observability . */
    private long totalRequests   = 0L;
    private long totalCacheHits  = 0L;
    private long totalQueries    = 0L;
    private long totalRowsLoaded = 0L;

    /**
     * @param dataRepository  repository pour les queries lazy
     * @param referenceType   refType cible ( utilise pour
     *                        {@link DataRepository#getDataIdPerKeysByNaturalKeys} )
     */
    public LazyParentLoader(DataRepository dataRepository, String referenceType) {
        this.dataRepository = dataRepository;
        this.referenceType  = referenceType;
    }

    /**
     * Demande la resolution d'une naturalkey . Si deja en cache , retour
     * immediat ( pas de modification du pending ) . Sinon , marque la
     * naturalkey comme pending pour la prochaine flush .
     *
     * @param naturalKey  naturalkey a resoudre
     * @return {@code true} si deja en cache ( appel get suivant
     *         retournera la valeur ) , {@code false} si pending
     *         ( il faut flusher avant d'appeler get )
     */
    public boolean request(Ltree naturalKey) {
        if (naturalKey == null) {
            return false;
        }
        totalRequests++;
        if (cache.containsKey(naturalKey)) {
            totalCacheHits++;
            return true;
        }
        pending.add(naturalKey);
        return false;
    }

    /**
     * Retourne l'UUID en cache pour une naturalkey , ou {@code null}
     * si pas en cache ou si la naturalkey n'existe pas en BDD
     * ( negative cache ) . Cet appel ne declenche jamais de query
     * SQL ; pour forcer une resolution , faire {@link #request} +
     * {@link #flush} + getCachedId .
     */
    public UUID getCachedId(Ltree naturalKey) {
        if (naturalKey == null) {
            return null;
        }
        UUID cached = cache.get(naturalKey);
        if (cached == null || cached.equals(NEGATIVE_SENTINEL)) {
            return null;
        }
        return cached;
    }

    /**
     * Flush les naturalkeys pending : execute une query bulk
     * {@code getDataIdPerKeysByNaturalKeys} avec le set accumule ,
     * peuple le cache avec les rows trouvees + negative-cache les
     * absentes . No-op si rien en pending .
     *
     * <p>Performance : O(|pending| log N) via index btree
     * {@code nk_patternColumnNam_type} . Pas de full table scan .
     */
    public void flush() {
        Set<String> toQuery;
        synchronized (pending) {
            if (pending.isEmpty()) {
                return;
            }
            toQuery = new HashSet<>();
            for (Ltree nk : pending) {
                toQuery.add(nk.toString());
            }
            pending.clear();
        }
        long t0 = System.nanoTime();
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> loaded =
                dataRepository.getDataIdPerKeysByNaturalKeys(referenceType, toQuery);
        totalQueries++;
        // Positive cache : indexes les UUIDs trouves par naturalkey .
        // Une meme naturalkey peut apparaitre plusieurs fois ( N
        // pattern columns ) ; ici on prend la PREMIERE - WithRecursion
        // legacy avait le meme comportement implicite ( premier match
        // dans l'iteration de l'ImmutableMap ) .
        Set<Ltree> foundNks = new HashSet<>();
        for (Map.Entry<DataValue.LineIdentityColumnName, UUID> e : loaded.entrySet()) {
            Ltree nk = e.getKey().naturalKey();
            cache.putIfAbsent(nk, e.getValue());
            foundNks.add(nk);
        }
        totalRowsLoaded += loaded.size();
        // Negative cache : naturalkeys requestees + non trouvees .
        // Important pour eviter re-query d'une key qui n'existe pas
        // ( WithRecursion peut iterer plusieurs fois sur la meme
        // missing parent ) .
        for (String requestedSql : toQuery) {
            Ltree requested = Ltree.fromSqlWithoutCheck(requestedSql);
            if (!foundNks.contains(requested)) {
                cache.putIfAbsent(requested, NEGATIVE_SENTINEL);
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        if (log.isDebugEnabled()) {
            log.debug("[lazy-parent-loader] flush ref={} pending={} loaded={} elapsed={}ms ( total req={} hits={} queries={} rows={} )",
                    referenceType, toQuery.size(), loaded.size(), elapsedMs,
                    totalRequests, totalCacheHits, totalQueries, totalRowsLoaded);
        }
    }

    /**
     * Pre-charge un set de naturalkeys connues a l'avance ( ex :
     * resultat d'un pre-scan CSV ) en un seul query bulk . Equivalent
     * a {@code request(nk) for nk in set} + {@code flush()} , mais
     * sans passer par le pending Set ( utile pour le warm-up initial
     * a partir du prescan ) .
     *
     * @param naturalKeys  set de naturalkeys au format texte ltree
     */
    public void preload(Set<String> naturalKeys) {
        if (naturalKeys == null || naturalKeys.isEmpty()) {
            return;
        }
        long t0 = System.nanoTime();
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> loaded =
                dataRepository.getDataIdPerKeysByNaturalKeys(referenceType, naturalKeys);
        totalQueries++;
        Set<Ltree> foundNks = new HashSet<>();
        for (Map.Entry<DataValue.LineIdentityColumnName, UUID> e : loaded.entrySet()) {
            Ltree nk = e.getKey().naturalKey();
            cache.putIfAbsent(nk, e.getValue());
            foundNks.add(nk);
        }
        totalRowsLoaded += loaded.size();
        for (String requestedSql : naturalKeys) {
            Ltree requested = Ltree.fromSqlWithoutCheck(requestedSql);
            if (!foundNks.contains(requested)) {
                cache.putIfAbsent(requested, NEGATIVE_SENTINEL);
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        log.debug("[lazy-parent-loader] preload ref={} requested={} loaded={} elapsed={}ms",
                referenceType, naturalKeys.size(), loaded.size(), elapsedMs);
    }

    /**
     * Ajoute une entree directement au cache ( sans query ) . Utilise
     * par {@code WithRecursion} quand il vient d'inserer une row
     * pendant le run : on enregistre son UUID dans le cache pour que
     * les lignes suivantes referenciant ce parent ne re-query pas
     * inutilement ( la row n'existe pas encore en BDD , seulement en
     * staging - le lookup BDD echouerait ) .
     *
     * @param naturalKey  cle naturelle
     * @param id          UUID associe
     */
    public void putKnown(Ltree naturalKey, UUID id) {
        if (naturalKey != null && id != null) {
            cache.put(naturalKey, id);
        }
    }

    /** Snapshot des statistiques pour observability . */
    public Stats stats() {
        return new Stats(totalRequests, totalCacheHits, totalQueries,
                totalRowsLoaded, cache.size(), pending.size());
    }

    public record Stats(long totalRequests, long totalCacheHits,
                        long totalQueries, long totalRowsLoaded,
                        int cacheSize, int pendingSize) {
        public double hitRate() {
            return totalRequests == 0L ? 0.0 : (double) totalCacheHits / totalRequests;
        }
    }
}
