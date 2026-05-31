package fr.inra.oresing.rest.usecases.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Pilote le <b>mode maintenance</b> de l'instance : un simple fichier-drapeau,
 * lu par le reverse-proxy ( nginx, {@code if -f} par requête ) pour servir une
 * page de maintenance et bloquer l'accès aux services.
 *
 * <h2>Responsabilité unique</h2>
 * <p>Ce service ne fait QUE créer / supprimer / consulter le drapeau. Aucune
 * logique métier ( import, validation, données ) ici : c'est une action
 * d'exploitation, au même titre que vider un cache ou révoquer une session
 * ( cf {@code BuildCacheService}, sessions admin ). nginx reste le seul à
 * appliquer l'effet ( bloquer le trafic ) ; le backend n'est qu'un écrivain du
 * drapeau, car nginx ne peut pas écrire de fichier depuis une requête.
 *
 * <h2>Communication inter-conteneurs</h2>
 * <p>Le drapeau vit dans un répertoire <b>partagé</b> ( volume monté ) entre le
 * backend ( écriture ) et le proxy ( lecture seule ). La bascule est immédiate :
 * nginx teste l'existence du fichier à chaque requête, sans reload.
 *
 * <p>Chemin configurable via {@code openadom.maintenance.flag-path}.
 */
@Slf4j
@Service
public class MaintenanceModeService {

    private final Path flagPath;

    /**
     * Drapeau « accès admin autorisé en maintenance » , voisin du drapeau
     * principal. Présent => le reverse-proxy laisse passer les requêtes
     * porteuses du cookie de contournement ( {@link #getBypassSecret()} ) ;
     * absent => blocage total sauf routes exemptées.
     */
    private final Path adminBypassFlagPath;
    /**
     * Secret partagé backend <-> proxy ( même valeur via la variable
     * d'environnement {@code OPENADOM_MAINTENANCE_BYPASS_SECRET} ) . Le backend
     * le pose comme valeur du cookie {@code oa_maint_bypass} ; nginx compare le
     * cookie reçu à ce secret . Vide => contournement inopérant.
     */
    private final String bypassSecret;

    public MaintenanceModeService(
            @Value("${openadom.maintenance.flag-path:/maintenance/maintenance.flag}") String flagPath,
            @Value("${openadom.maintenance.bypass-secret:}") String bypassSecret) {
        this.flagPath = Path.of(flagPath);
        this.adminBypassFlagPath = this.flagPath.resolveSibling("maintenance-allow-admins.flag");
        this.bypassSecret = bypassSecret == null ? "" : bypassSecret.strip();
    }

    /** @return {@code true} si le mode maintenance est actif ( drapeau présent ). */
    public boolean isEnabled() {
        return Files.exists(flagPath);
    }

    /** @return {@code true} si l'accès admin ( cookie de contournement ) est autorisé. */
    public boolean isAdminBypassEnabled() {
        return Files.exists(adminBypassFlagPath);
    }

    /**
     * Jeton d'accès admin courant ( 6 caractères ) , régénéré à chaque activation
     * avec accès admin. Sert à construire un lien d'auto-récupération du cookie
     * et à invalider les anciens liens ( un nouveau cycle de maintenance change
     * le jeton ) . {@code null} si l'accès admin n'est pas autorisé.
     */
    public String getAdminAccessToken() {
        if (!isAdminBypassEnabled()) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(adminBypassFlagPath, StandardCharsets.UTF_8)) {
                if (line.startsWith("token=")) {
                    return line.substring("token=".length()).strip();
                }
            }
        } catch (IOException e) {
            log.warn("Lecture du jeton d'accès maintenance impossible : {}", e.getMessage());
        }
        return null;
    }

    /** @return {@code true} si {@code token} correspond au jeton d'accès courant. */
    public boolean isAccessTokenValid(String token) {
        String current = getAdminAccessToken();
        return current != null && !current.isBlank() && current.equals(token);
    }

    /**
     * Secret du cookie de contournement ( valeur à poser dans le cookie
     * {@code oa_maint_bypass} ) , ou chaîne vide si non configuré.
     */
    public String getBypassSecret() {
        return bypassSecret;
    }

    /**
     * Active la maintenance : crée le drapeau ( + le répertoire parent au besoin ).
     * Idempotent. Écrit un horodatage + une raison optionnelle, à titre de trace.
     *
     * @param allowAdmins {@code true} : pose aussi le drapeau d'accès admin
     *                    ( le proxy laisse passer le cookie de contournement ) ;
     *                    {@code false} : le retire ( blocage total ) .
     */
    public void enable(String reason, boolean allowAdmins) {
        try {
            Path parent = flagPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String payload = "enabled_at=" + Instant.now()
                    + (reason != null && !reason.isBlank() ? "\nreason=" + reason.strip() : "")
                    + "\nallow_admins=" + allowAdmins
                    + "\n";
            Files.writeString(flagPath, payload, StandardCharsets.UTF_8);
            setAdminBypass(allowAdmins);
            log.warn("Mode maintenance ACTIVÉ ( drapeau {} , accès admin={} )", flagPath, allowAdmins);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'activer le mode maintenance ( écriture " + flagPath + " )", e);
        }
    }

    /** Désactive la maintenance : supprime les deux drapeaux. Idempotent. */
    public void disable() {
        try {
            boolean removed = Files.deleteIfExists(flagPath);
            Files.deleteIfExists(adminBypassFlagPath);
            log.warn("Mode maintenance DÉSACTIVÉ ( drapeau {} , supprimé={} )", flagPath, removed);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de désactiver le mode maintenance ( suppression " + flagPath + " )", e);
        }
    }

    /**
     * Pose ( true ) ou retire ( false ) le drapeau d'accès admin. Idempotent.
     * À la pose, génère un nouveau jeton d'accès ( 6 caractères ) , stocké dans
     * le drapeau ( ainsi un nouveau cycle de maintenance invalide les anciens
     * liens d'auto-récupération du cookie ) .
     */
    private void setAdminBypass(boolean allow) throws IOException {
        if (allow) {
            if (bypassSecret.isEmpty()) {
                log.warn("Accès admin en maintenance demandé mais OPENADOM_MAINTENANCE_BYPASS_SECRET est vide "
                        + "- le cookie de contournement ne pourra pas être validé par le proxy.");
            }
            Files.writeString(adminBypassFlagPath,
                    "enabled_at=" + Instant.now() + "\ntoken=" + generateAccessToken() + "\n",
                    StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(adminBypassFlagPath);
        }
    }

    /** Jeton court ( 6 caractères alphanumériques ) , aléatoire sécurisé. */
    private static String generateAccessToken() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans I,O,0,1 ambigus
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
