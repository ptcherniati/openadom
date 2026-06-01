package fr.inra.oresing.rest.usecases.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Pilote le <b>blocage d'accès par utilisateur</b> : un utilisateur bloqué se
 * voit refuser l'accès à <b>tous</b> les services et est redirigé vers la page
 * de maintenance.
 *
 * <h2>Responsabilité unique</h2>
 * <p>Comme {@link MaintenanceModeService} ( maintenance globale / par service )
 * et {@link VpnAccessService} ( exigence VPN par service ) , ce service ne fait
 * QUE créer / supprimer / consulter des fichiers-drapeaux. C'est une action
 * d'exploitation ; l'application de l'effet est répartie sur deux couches qui
 * lisent le <b>même</b> jeu de drapeaux ( source unique de vérité ) :
 * <ul>
 *   <li><b>nginx</b> ( UX ) : si la requête porte le cookie {@code oa_uid}
 *       d'un utilisateur bloqué , il renvoie la page de maintenance sur
 *       n'importe quel service ( y compris grafana / pgadmin , que le backend
 *       ne voit pas ) ;</li>
 *   <li><b>le filtre d'authentification backend</b> ( autoritaire ) : il relit
 *       l'identifiant depuis le <b>JWT validé</b> ( jamais depuis le cookie ,
 *       donc non contournable ) et rejette les appels {@code /api} d'un
 *       utilisateur bloqué.</li>
 * </ul>
 *
 * <h2>Clé = UUID utilisateur</h2>
 * <p>Le drapeau est nommé d'après l'{@link UUID} de l'utilisateur ( porté par
 * le JWT et recopié dans le cookie {@code oa_uid} au login ). L'UUID est un nom
 * de fichier sûr ( hex + tirets ) : pas de sanitisation ni de risque de
 * <i>path traversal</i> à l'écriture, et nginx peut le tester tel quel.
 *
 * <h2>Communication inter-conteneurs</h2>
 * <p>Les drapeaux vivent dans un répertoire <b>partagé</b> ( volume monté )
 * entre le backend ( écriture ) et le proxy ( lecture seule ) , voisin des
 * drapeaux de maintenance. La bascule est immédiate : nginx et le backend
 * testent l'existence du fichier à chaque requête, sans reload.
 *
 * <p>Répertoire dérivé du drapeau de maintenance ( même volume partagé ) ;
 * surchargeable via {@code openadom.access.blocked-dir}.
 */
@Slf4j
@Service
public class UserAccessService {

    /**
     * TTL du cache mémoire de {@link #isBlocked} ( ms ) . {@code isBlocked} est
     * appelé sur le chemin chaud du filtre d'authentification ( chaque requête
     * /api ) : on évite un stat filesystem par requête. Effet immédiat sur
     * {@code block}/{@code unblock} ( invalidation synchrone ) ; le TTL ne sert
     * qu'à rattraper d'éventuelles modifications hors-bande des drapeaux.
     */
    private static final long CACHE_TTL_MS = 5_000;

    private final Path blockedDir;

    private volatile Set<UUID> cachedBlocked = Set.of();
    private volatile long cachedAtMs = 0L;

    public UserAccessService(
            @Value("${openadom.maintenance.flag-path:/maintenance/maintenance.flag}") String flagPath,
            @Value("${openadom.access.blocked-dir:}") String blockedDir) {
        this.blockedDir = (blockedDir == null || blockedDir.isBlank())
                ? Path.of(flagPath).resolveSibling("blocked-users")
                : Path.of(blockedDir);
    }

    /** @return l'ensemble des UUID d'utilisateurs actuellement bloqués. */
    public Set<UUID> blockedUserIds() {
        if (!Files.isDirectory(blockedDir)) {
            return Set.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(blockedDir)) {
            files.forEach(p -> parseUuid(p.getFileName().toString()).ifPresent(ids::add));
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lister les utilisateurs bloqués ( " + blockedDir + " )", e);
        }
        return ids;
    }

    /**
     * @return true si l'utilisateur {@code userId} est bloqué. Lecture servie
     * par un cache mémoire ( cf. {@link #CACHE_TTL_MS} ) pour ne pas faire un
     * stat filesystem à chaque requête /api.
     */
    public boolean isBlocked(UUID userId) {
        return userId != null && currentlyBlocked().contains(userId);
    }

    /** Snapshot caché des UUID bloqués, rechargé à expiration du TTL. */
    private Set<UUID> currentlyBlocked() {
        if (System.currentTimeMillis() - cachedAtMs > CACHE_TTL_MS) {
            reloadCache();
        }
        return cachedBlocked;
    }

    private synchronized void reloadCache() {
        // Double-check : un autre thread a pu recharger pendant l'attente du lock.
        if (System.currentTimeMillis() - cachedAtMs <= CACHE_TTL_MS) {
            return;
        }
        cachedBlocked = blockedUserIds();
        cachedAtMs = System.currentTimeMillis();
    }

    /** Force le rechargement au prochain {@link #isBlocked} ( effet immédiat ). */
    private void invalidateCache() {
        cachedAtMs = 0L;
    }

    /**
     * Bloque ( {@code blocked=true} ) ou débloque ( {@code false} ) un
     * utilisateur. Idempotent.
     */
    public void setBlocked(UUID userId, boolean blocked) {
        if (blocked) {
            block(userId);
        } else {
            unblock(userId);
        }
    }

    private void block(UUID userId) {
        Path flag = flagOf(userId);
        try {
            Files.createDirectories(blockedDir);
            Files.writeString(flag, "blocked_at=" + Instant.now() + "\n", StandardCharsets.UTF_8);
            invalidateCache();
            log.warn("Accès BLOQUÉ pour l'utilisateur {} ( drapeau {} )", userId, flag);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de bloquer l'utilisateur " + userId + " ( écriture " + flag + " )", e);
        }
    }

    private void unblock(UUID userId) {
        Path flag = flagOf(userId);
        try {
            boolean removed = Files.deleteIfExists(flag);
            invalidateCache();
            log.warn("Accès RÉTABLI pour l'utilisateur {} ( drapeau {} , supprimé={} )", userId, flag, removed);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de débloquer l'utilisateur " + userId + " ( suppression " + flag + " )", e);
        }
    }

    private Path flagOf(UUID userId) {
        // userId est un UUID -> nom de fichier sûr ; resolve reste dans blockedDir.
        return blockedDir.resolve(userId.toString());
    }

    private static java.util.Optional<UUID> parseUuid(String name) {
        try {
            return java.util.Optional.of(UUID.fromString(name));
        } catch (IllegalArgumentException ignored) {
            // Fichier au nom non-UUID dans le répertoire : ignoré ( robustesse ).
            return java.util.Optional.empty();
        }
    }
}
