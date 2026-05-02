package fr.inra.oresing.monitoring.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Blacklist in-memory de tokens JWT revoques par un kick admin .
 *
 * <p>Le format JWT etant stateless ( pas de claim {@code jti} cote
 * openADOM ) , la seule maniere de couper court a un token deja emis
 * est de l'inscrire dans une liste de revocation consultee a chaque
 * requete par {@code AuthorizationFilter} .
 *
 * <h2>Lifecycle d'une entree</h2>
 * <ul>
 *   <li>Ajoutee par {@code DashboardService.disconnectSession} ( admin
 *       clique sur le bouton "Deconnecter" dans oa-live ) .</li>
 *   <li>Purgee automatiquement quand le JWT atteint son {@code exp} -
 *       inutile de garder une entree pour un token deja invalide cote
 *       signature .</li>
 *   <li>Purgeable manuellement par un admin via DELETE /blacklist/{hash}
 *       ( ex. annulation d'un kick par erreur ) .</li>
 * </ul>
 *
 * <h2>Borne memoire</h2>
 * <p>LRU cap {@link #MAX_ENTRIES} = 100 . Au-dela , la plus ancienne
 * entree ( par {@code blacklistedAt} ) est evictee avant l'insert , avec
 * un log warn pour signaler l'eviction . Acceptable pour un dashboard
 * admin : quelques dizaines de kicks par jour au maximum , et la purge
 * automatique des tokens expires libere les entrees une fois le TTL
 * passe ( JWT TTL openADOM = 1h par defaut ) .
 *
 * <h2>Hash plutot que JWT brut</h2>
 * <p>On stocke le SHA-256 du token et non le token brut : un dump de
 * registry ne suffit pas a usurper l'identite ( meme si la fenetre
 * d'exploitation est limitee au TTL JWT ) . La comparaison reste O(1) .
 *
 * <p>Thread-safety : {@link #entries} est protege par synchronized .
 * Le volume reste petit ( cap 100 ) , la lecture d'un Set sous lock
 * dure quelques microsecondes - acceptable au prix de la simplicite .
 *
 * @author R.YAHIAOUI
 */
@Service
@Slf4j
public class JwtBlacklistRegistry {

    /** Capacite maximale ; au-dela on evicte la plus ancienne entree . */
    public static final int MAX_ENTRIES = 100;

    /**
     * LinkedHashMap insertion-order : la 1ere entree retournee par
     * {@code entrySet().iterator()} est la plus ancienne ( eviction
     * cible ) .
     */
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    /**
     * Ajoute un token a la blacklist . Idempotent : un re-ajout du meme
     * hash ne duplique pas l'entree , la nouvelle valeur ecrase l'ancienne
     * ( utile si l'admin re-kick une session relog avec le meme JWT ) .
     */
    public synchronized void add(String tokenHash, UUID userId, String userLogin,
                                  UUID sessionId, Instant blacklistedAt, Instant expiresAt) {
        if (tokenHash == null || tokenHash.isBlank()) return;

        purgeExpiredInternal(Instant.now());

        if (entries.size() >= MAX_ENTRIES && !entries.containsKey(tokenHash)) {
            Map.Entry<String, Entry> oldest = entries.entrySet().iterator().next();
            entries.remove(oldest.getKey());
            log.warn("JwtBlacklist : LRU cap reached ( {} ) , evicted oldest entry for user {} ( hash {}... )",
                    MAX_ENTRIES, oldest.getValue().userLogin(),
                    oldest.getKey().substring(0, Math.min(8, oldest.getKey().length())));
        }
        entries.put(tokenHash, new Entry(tokenHash, userId, userLogin, sessionId,
                blacklistedAt != null ? blacklistedAt : Instant.now(),
                expiresAt));
    }

    /**
     * Renvoie {@code true} si le {@code tokenHash} est blackliste et pas
     * encore expire . Lazy purge des entrees expirees au passage .
     */
    public synchronized boolean contains(String tokenHash) {
        if (tokenHash == null) return false;
        purgeExpiredInternal(Instant.now());
        return entries.containsKey(tokenHash);
    }

    /** Vue snapshot triee par blacklistedAt DESC ( plus recent en haut ) . */
    public synchronized List<Entry> list() {
        purgeExpiredInternal(Instant.now());
        return entries.values().stream()
                .sorted(Comparator.comparing(Entry::blacklistedAt).reversed())
                .toList();
    }

    /** Suppression manuelle ( admin annule un kick ) . Renvoie true si supprime . */
    public synchronized boolean remove(String tokenHash) {
        return entries.remove(tokenHash) != null;
    }

    /** Force purge des entrees expirees . Renvoie le nombre d'entrees supprimees . */
    public synchronized int purgeExpired() {
        return purgeExpiredInternal(Instant.now());
    }

    /**
     * Vide entierement la blacklist . Toutes les sessions revoquees
     * redeviennent valides ( jusqu'a leur expiration JWT naturelle ) .
     * Renvoie le nombre d'entrees supprimees .
     */
    public synchronized int clear() {
        int n = entries.size();
        entries.clear();
        if (n > 0) {
            log.info("JwtBlacklist : cleared {} entries by admin", n);
        }
        return n;
    }

    /** Taille courante , post-purge ( debug / metrics ) . */
    public synchronized int size() {
        purgeExpiredInternal(Instant.now());
        return entries.size();
    }

    private int purgeExpiredInternal(Instant now) {
        int before = entries.size();
        entries.values().removeIf(e -> e.expiresAt() != null && !now.isBefore(e.expiresAt()));
        int removed = before - entries.size();
        if (removed > 0) {
            log.debug("JwtBlacklist : purged {} expired entries", removed);
        }
        return removed;
    }

    /**
     * Calcule le SHA-256 hex d'un JWT . Utilitaire statique partage entre
     * le wiring login ( capture du token ) et le filter ( verif a chaque
     * requete ) .
     */
    public static String hash(String jwt) {
        if (jwt == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(jwt.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 garanti par toutes les JVM standard ; impossible
            // sans un environnement tronque . On loggue et renvoie null
            // pour ne pas planter le filter .
            log.error("SHA-256 unavailable on this JVM : {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Entree publique de la blacklist . Pas de JWT brut , juste hash + meta
     * pour l'audit admin .
     */
    public record Entry(
            String  tokenHash,
            UUID    userId,
            String  userLogin,
            UUID    sessionId,
            Instant blacklistedAt,
            Instant expiresAt) {

        /** Vue tronquee du hash pour les logs / UI ( les 12 premiers char ) . */
        public String hashPrefix() {
            return tokenHash == null ? "" : tokenHash.substring(0, Math.min(12, tokenHash.length()));
        }
    }
}
