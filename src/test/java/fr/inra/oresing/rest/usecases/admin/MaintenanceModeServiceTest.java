package fr.inra.oresing.rest.usecases.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de {@link MaintenanceModeService}, testé sur un répertoire temporaire
 * ( sans base ni Spring ) : le service ne fait que piloter un fichier-drapeau.
 */
class MaintenanceModeServiceTest {

    @TempDir
    Path tmp;

    private MaintenanceModeService serviceOn(Path flag) {
        return new MaintenanceModeService(flag.toString(), "test-secret");
    }

    private Path adminFlagOf(Path flag) {
        return flag.resolveSibling("maintenance-allow-admins.flag");
    }

    @Test
    void disabled_by_default_then_enable_then_disable() {
        Path flag = tmp.resolve("maintenance.flag");
        MaintenanceModeService service = serviceOn(flag);

        assertFalse(service.isEnabled());
        service.enable("bench", false, null);
        assertTrue(service.isEnabled());
        assertTrue(Files.exists(flag));
        service.disable();
        assertFalse(service.isEnabled());
        assertFalse(Files.exists(flag));
    }

    @Test
    void enable_creates_missing_parent_directories() {
        Path flag = tmp.resolve("nested/dir/maintenance.flag");
        MaintenanceModeService service = serviceOn(flag);

        service.enable(null, false, null);
        assertTrue(service.isEnabled());
        assertTrue(Files.exists(flag));
    }

    @Test
    void enable_and_disable_are_idempotent() {
        Path flag = tmp.resolve("maintenance.flag");
        MaintenanceModeService service = serviceOn(flag);

        service.enable("once", false, null);
        service.enable("twice", false, null); // ne doit pas échouer
        assertTrue(service.isEnabled());

        service.disable();
        service.disable(); // suppression d'un drapeau déjà absent : no-op
        assertFalse(service.isEnabled());
    }

    @Test
    void allow_admins_creates_then_clears_the_bypass_flag() {
        Path flag = tmp.resolve("maintenance.flag");
        MaintenanceModeService service = serviceOn(flag);

        // Activation avec accès admin : le drapeau de contournement est posé.
        service.enable("upgrade", true, null);
        assertTrue(service.isAdminBypassEnabled());
        assertTrue(Files.exists(adminFlagOf(flag)));

        // Réactivation sans accès admin : le drapeau de contournement est retiré.
        service.enable("upgrade", false, null);
        assertFalse(service.isAdminBypassEnabled());
        assertFalse(Files.exists(adminFlagOf(flag)));

        // Activation admin puis désactivation totale : les deux drapeaux partent.
        service.enable("upgrade", true, null);
        assertTrue(service.isAdminBypassEnabled());
        service.disable();
        assertFalse(service.isEnabled());
        assertFalse(service.isAdminBypassEnabled());
    }

    @Test
    void bypass_secret_is_exposed() {
        Path flag = tmp.resolve("maintenance.flag");
        assertTrue("test-secret".equals(serviceOn(flag).getBypassSecret()));
    }

    @Test
    void service_scope_subset_then_all_then_cleared() {
        Path flag = tmp.resolve("maintenance.flag");
        MaintenanceModeService service = serviceOn(flag);

        // Sous-ensemble : seuls backend + grafana sont gatés.
        service.enable("bench", false, java.util.List.of("backend", "grafana"));
        assertTrue(service.gatedServices().equals(java.util.Set.of("backend", "grafana")));

        // null = tous les services connus.
        service.enable("bench", false, null);
        assertTrue(service.gatedServices().equals(new java.util.LinkedHashSet<>(ProxyServices.NAMES)));

        // Réduction : un service hors ensemble est rouvert.
        service.enable("bench", false, java.util.List.of("frontend"));
        assertTrue(service.gatedServices().equals(java.util.Set.of("frontend")));

        // Nom inconnu ignoré ( pas de path traversal, jamais écrit ) .
        service.enable("bench", false, java.util.List.of("../../etc/passwd", "backend"));
        assertTrue(service.gatedServices().equals(java.util.Set.of("backend")));

        // Désactivation : plus aucun service gaté.
        service.disable();
        assertTrue(service.gatedServices().isEmpty());
    }
}
