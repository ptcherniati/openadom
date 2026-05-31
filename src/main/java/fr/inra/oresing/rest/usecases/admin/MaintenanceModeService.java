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

    public MaintenanceModeService(
            @Value("${openadom.maintenance.flag-path:/maintenance/maintenance.flag}") String flagPath) {
        this.flagPath = Path.of(flagPath);
    }

    /** @return {@code true} si le mode maintenance est actif ( drapeau présent ). */
    public boolean isEnabled() {
        return Files.exists(flagPath);
    }

    /**
     * Active la maintenance : crée le drapeau ( + le répertoire parent au besoin ).
     * Idempotent. Écrit un horodatage + une raison optionnelle, à titre de trace.
     */
    public void enable(String reason) {
        try {
            Path parent = flagPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String payload = "enabled_at=" + Instant.now()
                    + (reason != null && !reason.isBlank() ? "\nreason=" + reason.strip() : "")
                    + "\n";
            Files.writeString(flagPath, payload, StandardCharsets.UTF_8);
            log.warn("Mode maintenance ACTIVÉ ( drapeau {} )", flagPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'activer le mode maintenance ( écriture " + flagPath + " )", e);
        }
    }

    /** Désactive la maintenance : supprime le drapeau. Idempotent. */
    public void disable() {
        try {
            boolean removed = Files.deleteIfExists(flagPath);
            log.warn("Mode maintenance DÉSACTIVÉ ( drapeau {} , supprimé={} )", flagPath, removed);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de désactiver le mode maintenance ( suppression " + flagPath + " )", e);
        }
    }
}
