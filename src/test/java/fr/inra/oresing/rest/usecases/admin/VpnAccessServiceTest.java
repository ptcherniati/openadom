package fr.inra.oresing.rest.usecases.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de {@link VpnAccessService}, testé sur un répertoire temporaire
 * ( sans base ni Spring ) : le service ne fait que piloter un drapeau par service.
 */
class VpnAccessServiceTest {

    @TempDir
    Path tmp;

    private VpnAccessService serviceOn(Path dir) {
        return new VpnAccessService(dir.toString());
    }

    @Test
    void all_services_open_by_default() {
        VpnAccessService service = serviceOn(tmp.resolve("vpn-gated"));
        Map<String, Boolean> status = service.status();
        assertEquals(VpnAccessService.KNOWN_SERVICES.size(), status.size());
        assertTrue(status.values().stream().noneMatch(Boolean::booleanValue));
    }

    @Test
    void gate_then_ungate_one_service() {
        Path dir = tmp.resolve("vpn-gated");
        VpnAccessService service = serviceOn(dir);

        service.setGated("grafana", true);
        assertTrue(service.status().get("grafana"));
        assertTrue(Files.exists(dir.resolve("grafana")));
        // les autres restent ouverts
        assertFalse(service.status().get("frontend"));

        service.setGated("grafana", false);
        assertFalse(service.status().get("grafana"));
        assertFalse(Files.exists(dir.resolve("grafana")));
    }

    @Test
    void toggle_is_idempotent() {
        VpnAccessService service = serviceOn(tmp.resolve("vpn-gated"));
        service.setGated("backend", true);
        service.setGated("backend", true);
        assertTrue(service.status().get("backend"));
        service.setGated("backend", false);
        service.setGated("backend", false);
        assertFalse(service.status().get("backend"));
    }

    @Test
    void unknown_service_is_rejected() {
        VpnAccessService service = serviceOn(tmp.resolve("vpn-gated"));
        // anti-traversal : aucune clé arbitraire ne touche le FS
        assertThrows(IllegalArgumentException.class, () -> service.setGated("../etc/passwd", true));
        assertThrows(IllegalArgumentException.class, () -> service.setGated("unknown", true));
    }
}
