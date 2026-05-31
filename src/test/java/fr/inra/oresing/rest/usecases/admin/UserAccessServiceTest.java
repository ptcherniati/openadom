package fr.inra.oresing.rest.usecases.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de {@link UserAccessService} , testé sur un répertoire temporaire
 * ( sans base ni Spring ) : le service ne fait que piloter des fichiers-drapeaux
 * nommés d'après l'UUID utilisateur.
 */
class UserAccessServiceTest {

    @TempDir
    Path tmp;

    private UserAccessService serviceOn(Path dir) {
        // flag-path bidon ( non utilisé ici ) + blocked-dir explicite.
        return new UserAccessService(tmp.resolve("maintenance.flag").toString(), dir.toString());
    }

    @Test
    void not_blocked_by_default_then_block_then_unblock() {
        UserAccessService service = serviceOn(tmp.resolve("blocked-users"));
        UUID user = UUID.randomUUID();

        assertFalse(service.isBlocked(user));
        service.setBlocked(user, true);
        assertTrue(service.isBlocked(user));
        assertEquals(java.util.Set.of(user), service.blockedUserIds());

        service.setBlocked(user, false);
        assertFalse(service.isBlocked(user));
        assertTrue(service.blockedUserIds().isEmpty());
    }

    @Test
    void block_and_unblock_are_idempotent() {
        UserAccessService service = serviceOn(tmp.resolve("blocked-users"));
        UUID user = UUID.randomUUID();

        service.setBlocked(user, true);
        service.setBlocked(user, true); // ne doit pas échouer
        assertTrue(service.isBlocked(user));

        service.setBlocked(user, false);
        service.setBlocked(user, false); // suppression d'un drapeau absent : no-op
        assertFalse(service.isBlocked(user));
    }

    @Test
    void blocked_ids_lists_only_valid_uuid_files() throws Exception {
        Path dir = tmp.resolve("blocked-users");
        UserAccessService service = serviceOn(dir);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        service.setBlocked(a, true);
        service.setBlocked(b, true);

        // Fichier parasite au nom non-UUID : ignoré ( robustesse ).
        Files.writeString(dir.resolve("not-a-uuid.txt"), "x");

        assertEquals(java.util.Set.of(a, b), service.blockedUserIds());
    }

    @Test
    void blocked_ids_empty_when_dir_absent() {
        UserAccessService service = serviceOn(tmp.resolve("does-not-exist"));
        assertTrue(service.blockedUserIds().isEmpty());
        assertFalse(service.isBlocked(UUID.randomUUID()));
    }

    @Test
    void null_user_is_never_blocked() {
        UserAccessService service = serviceOn(tmp.resolve("blocked-users"));
        assertFalse(service.isBlocked(null));
    }
}
