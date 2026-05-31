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
        return new MaintenanceModeService(flag.toString());
    }

    @Test
    void disabled_by_default_then_enable_then_disable() {
        Path flag = tmp.resolve("maintenance.flag");
        MaintenanceModeService service = serviceOn(flag);

        assertFalse(service.isEnabled());
        service.enable("bench");
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

        service.enable(null);
        assertTrue(service.isEnabled());
        assertTrue(Files.exists(flag));
    }

    @Test
    void enable_and_disable_are_idempotent() {
        Path flag = tmp.resolve("maintenance.flag");
        MaintenanceModeService service = serviceOn(flag);

        service.enable("once");
        service.enable("twice"); // ne doit pas échouer
        assertTrue(service.isEnabled());

        service.disable();
        service.disable(); // suppression d'un drapeau déjà absent : no-op
        assertFalse(service.isEnabled());
    }
}
