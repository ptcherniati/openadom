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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pilote l'accès VPN <b>par service</b> du reverse-proxy : pour chaque service
 * exposé par nginx, un fichier-drapeau présent => ce service exige le VPN
 * ( ou une IP autorisée ) ; absent => service ouvert à tous.
 *
 * <h2>Responsabilité unique</h2>
 * <p>Ce service ne fait QUE créer / supprimer / consulter les drapeaux par
 * service. C'est une action d'exploitation, au même titre que le mode
 * maintenance ( cf {@link MaintenanceModeService} ) ou la purge des caches.
 * nginx reste le seul à appliquer l'effet ( tester l'IP cliente contre la
 * liste d'autorisation et renvoyer 403 -> page « VPN requis » ) ; le backend
 * n'est qu'un écrivain des drapeaux, car nginx ne peut pas écrire depuis une
 * requête.
 *
 * <h2>Communication inter-conteneurs</h2>
 * <p>Les drapeaux vivent dans un répertoire <b>partagé</b> ( volume monté )
 * entre le backend ( écriture ) et le proxy ( lecture seule ). La bascule est
 * immédiate : nginx teste l'existence du fichier à chaque requête, sans reload.
 *
 * <p>Le jeu de services connus est figé et DOIT rester aligné avec les
 * {@code location} de nginx et la liste {@code SERVICES} de l'entrypoint du
 * proxy. Toute clé inconnue est rejetée ( anti-traversal ).
 *
 * <p>Répertoire configurable via {@code openadom.vpn.gated-dir}.
 */
@Slf4j
@Service
public class VpnAccessService {

    /**
     * Services exposés par nginx, dans l'ordre d'affichage. Source de vérité
     * partagée avec nginx.conf ( un {@code location} par service ) et la liste
     * {@code SERVICES} de l'entrypoint du proxy.
     */
    public static final List<String> KNOWN_SERVICES = List.of(
            "frontend", "backend", "grafana", "pgadmin",
            "oa-live", "actuator", "shiny", "postgrest", "graphql");

    private final Path gatedDir;

    public VpnAccessService(
            @Value("${openadom.vpn.gated-dir:/vpn-gated}") String gatedDir) {
        this.gatedDir = Path.of(gatedDir);
    }

    /** @return état VPN de chaque service connu ( true = VPN requis ). */
    public Map<String, Boolean> status() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (String service : KNOWN_SERVICES) {
            states.put(service, Files.exists(flagOf(service)));
        }
        return states;
    }

    /**
     * Active ( {@code gated=true} ) ou désactive ( {@code false} ) l'exigence
     * VPN pour un service. Idempotent.
     */
    public void setGated(String service, boolean gated) {
        if (gated) {
            gate(service);
        } else {
            ungate(service);
        }
    }

    private void gate(String service) {
        Path flag = flagOf(service);
        try {
            Files.createDirectories(gatedDir);
            Files.writeString(flag, "gated_at=" + Instant.now() + "\n", StandardCharsets.UTF_8);
            log.warn("Accès VPN EXIGÉ pour le service '{}' ( drapeau {} )", service, flag);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de gater le service '" + service + "' ( écriture " + flag + " )", e);
        }
    }

    private void ungate(String service) {
        Path flag = flagOf(service);
        try {
            boolean removed = Files.deleteIfExists(flag);
            log.warn("Accès VPN OUVERT pour le service '{}' ( drapeau {} , supprimé={} )", service, flag, removed);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'ouvrir le service '" + service + "' ( suppression " + flag + " )", e);
        }
    }

    /**
     * Résout le drapeau d'un service en validant la clé contre la liste connue
     * ( anti-traversal : aucune valeur arbitraire ne touche le système de
     * fichiers ).
     */
    private Path flagOf(String service) {
        if (!KNOWN_SERVICES.contains(service)) {
            throw new IllegalArgumentException("Service VPN inconnu : '" + service
                    + "' ( connus : " + KNOWN_SERVICES + " )");
        }
        return gatedDir.resolve(service);
    }
}
