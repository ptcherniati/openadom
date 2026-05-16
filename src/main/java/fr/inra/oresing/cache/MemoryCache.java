package fr.inra.oresing.cache;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Cache mémoire générique , LRU borné en nombre d'entrées + TTL optionnel.
 *
 * <p>Audit OA_FULL_REVIEW (8/5/26) - extrait pour mutualiser le pattern
 * que filterListCache / scopesCache / checkedFormatComponentsCache
 * répétaient avec des variations mineures :
 * <ul>
 *   <li>{@link ConcurrentHashMap} thread-safe ;
 *   <li>éviction LRU sur l'âge ( plus ancien {@code timestamp} d'abord )
 *       quand on dépasse {@code maxEntries} ;
 *   <li>TTL en minutes ; si {@code <= 0} les entrées vivent jusqu'à
 *       invalidation explicite ( utile en prod où l'on fait confiance
 *       aux hooks d'écriture ) ;
 *   <li>API d'invalidation simple ( clé , prédicat , clear ) +
 *       observabilité ( {@code size()} , {@code maxEntries()} ).
 * </ul>
 *
 * <p>Design : wraps les valeurs dans un record {@code Entry<V>} pour
 * cacher la gestion du timestamp aux callers. Le caller manipule
 * uniquement les valeurs métier ( {@code V} ).
 *
 * <p>Hors scope : pas de pondération en octets ( utile uniquement côté
 * frontend où la mémoire heap browser est plus contrainte ; cf.
 * {@code EtagCache} dans le frontend ).
 *
 * @param <K> type de clé ( typiquement {@link String} )
 * @param <V> type de valeur ( typiquement un record )
 */
@Slf4j
public class MemoryCache<K, V> {

    /**
     * Entrée du cache : ( valeur métier , timestamp d'insertion en ms ).
     * Le timestamp sert à la fois pour le TTL et pour l'éviction LRU.
     */
    public record Entry<V>(V value, long timestamp) {}

    private final String name;
    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
    private volatile int maxEntries;
    private volatile long ttlMinutes;

    /**
     * @param name        nom du cache , utilisé en log uniquement
     * @param maxEntries  capacité max ; >= 1
     * @param ttlMinutes  TTL en minutes ; {@code <= 0} = pas d'expiration auto
     */
    public MemoryCache(String name, int maxEntries, long ttlMinutes) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries doit être >= 1 , reçu : " + maxEntries);
        }
        this.name = name;
        this.maxEntries = maxEntries;
        this.ttlMinutes = ttlMinutes;
    }

    /**
     * Lookup. Retourne la valeur si présente et non expirée ; sinon
     * {@code null}. La présence d'une entrée expirée la laisse intacte
     * en place ( elle sera évincée naturellement à la prochaine écriture ).
     */
    public V get(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null) return null;
        if (isExpired(entry)) return null;
        return entry.value();
    }

    /**
     * Stocke une nouvelle valeur sous {@code key}. Évince l'entrée la
     * plus ancienne si la capacité max est atteinte.
     */
    public void put(K key, V value) {
        if (map.size() >= maxEntries && !map.containsKey(key)) {
            map.entrySet().stream()
                    .min(Comparator.comparingLong(e -> e.getValue().timestamp()))
                    .ifPresent(oldest -> map.remove(oldest.getKey()));
        }
        map.put(key, new Entry<>(value, System.currentTimeMillis()));
    }

    /** Retire une entrée par clé. */
    public void invalidate(K key) {
        if (key != null) map.remove(key);
    }

    /**
     * Retire toutes les entrées dont la clé satisfait {@code predicate}.
     * Pratique pour invalider toutes les entrées d'une application en
     * filtrant sur la clé composite.
     */
    public int invalidateMatching(Predicate<K> predicate) {
        int sizeBefore = map.size();
        map.keySet().removeIf(predicate);
        int removed = sizeBefore - map.size();
        if (removed > 0) log.info("MemoryCache[{}] : {} entrées invalidées", name, removed);
        return removed;
    }

    /** Vide le cache. */
    public void invalidateAll() {
        int sizeBefore = map.size();
        map.clear();
        if (sizeBefore > 0) log.info("MemoryCache[{}] : cache entier vidé ( {} entrées )", name, sizeBefore);
    }

    /** Nombre d'entrées actuellement présentes ( y compris expirées non encore évincées ). */
    public int size() {
        return map.size();
    }

    public int maxEntries() {
        return maxEntries;
    }

    public long ttlMinutes() {
        return ttlMinutes;
    }

    public String name() {
        return name;
    }

    /**
     * Reconfigure les caps à chaud ( ex: après reload de config ). N'évince
     * pas immédiatement les entrées en surplus ; l'éviction LRU se fait
     * naturellement aux prochaines écritures.
     */
    public void reconfigure(int newMaxEntries, long newTtlMinutes) {
        if (newMaxEntries < 1) {
            throw new IllegalArgumentException("maxEntries doit être >= 1");
        }
        this.maxEntries = newMaxEntries;
        this.ttlMinutes = newTtlMinutes;
    }

    /**
     * Une entrée est expirée si {@code ttlMinutes > 0} et que son âge
     * dépasse le TTL. Si {@code ttlMinutes <= 0} , jamais expirée.
     */
    private boolean isExpired(Entry<V> entry) {
        if (ttlMinutes <= 0) return false;
        long ageMs = System.currentTimeMillis() - entry.timestamp();
        return ageMs >= TimeUnit.MINUTES.toMillis(ttlMinutes);
    }

    /** Snapshot des entrées ( pour les tests , pas pour usage applicatif ). */
    public Map<K, Entry<V>> snapshot() {
        return Map.copyOf(map);
    }

    /**
     * Estime la taille mémoire occupée par les valeurs du cache en
     * sérialisant chaque entrée en JSON et en sommant les bytes. C'est
     * une approximation ( la sérialisation JSON ne reflète pas
     * exactement la footprint heap JVM : overhead objets , compressed
     * oops , déduplication de strings , etc. ) mais donne un indicateur
     * fiable de tendance pour l'observabilité admin .
     *
     * <p>Coût : O ( N entries * size ( value ) ) en CPU . Ne pas appeler
     * dans le hot path . Utilisable depuis un endpoint admin dédié avec
     * un éventuel TTL côté caller pour éviter le hammering .
     *
     * @param mapper      Jackson mapper utilisé pour la sérialisation
     * @param valueExtractor extracteur de la valeur métier depuis l'entrée ;
     *                    permet aux types {@code V} qui ne sont pas
     *                    directement sérialisables ( ex . holders avec
     *                    field timestamp ) de fournir une vue projetée
     * @return total approximatif en octets ; 0 si cache vide
     */
    public long estimateSizeBytes(com.fasterxml.jackson.databind.ObjectMapper mapper,
                                  java.util.function.Function<V, Object> valueExtractor) {
        long total = 0L;
        long expiredSkipped = 0L;
        for (Map.Entry<K, Entry<V>> e : map.entrySet()) {
            if (isExpired(e.getValue())) {
                expiredSkipped++;
                continue;
            }
            try {
                Object projected = valueExtractor == null
                        ? e.getValue().value()
                        : valueExtractor.apply(e.getValue().value());
                if (projected == null) continue;
                total += mapper.writeValueAsBytes(projected).length;
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                log.debug("estimateSizeBytes : serialization failed for cache {} entry , skipping : {}",
                        name, ex.getMessage());
            }
        }
        if (expiredSkipped > 0) {
            log.debug("estimateSizeBytes : skipped {} expired entries in cache {}", expiredSkipped, name);
        }
        return total;
    }

    /** Variante sans extracteur : sérialise directement la valeur métier. */
    public long estimateSizeBytes(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return estimateSizeBytes(mapper, null);
    }
}
