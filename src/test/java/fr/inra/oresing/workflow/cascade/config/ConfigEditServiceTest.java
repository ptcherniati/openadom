package fr.inra.oresing.workflow.cascade.config;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link ConfigEditService} — sans contexte Spring.
 *
 * <p>Couvre : applyPatch (succès, admin guard, champ inconnu, read-only,
 * BLOCKING rule), applyPatch avec poolParallelism nested map,
 * schema(), auditList(), snapshot().
 */
@ExtendWith(MockitoExtension.class)
@Tag("core.config")
@Tag("domain.model")
@DisplayName("ConfigEditService — tests unitaires")
class ConfigEditServiceTest {

    @Mock private AuthenticationService authenticationService;

    private ImportProperties     props;
    private ConfigFieldRegistry  registry;
    private ConfigChangeAudit    audit;
    private ConfigEditService    service;
    
    @BeforeEach
    void setUp() {
        props = new ImportProperties();
        audit = new ConfigChangeAudit();

        ConfigFieldRegistryTest.FakePoolReloader reloader =
                new ConfigFieldRegistryTest.FakePoolReloader();

        fr.inra.oresing.workflow.cascade.ImportRateLimiter importRl =
                new fr.inra.oresing.workflow.cascade.ImportRateLimiter(
                        3, null, null, null);
        fr.inra.oresing.workflow.cascade.ExtractionRateLimiter extractionRl =
                new fr.inra.oresing.workflow.cascade.ExtractionRateLimiter(
                        5, 0L, null, null, null);

        registry = new ConfigFieldRegistry(props, new PublishProperties(), reloader, importRl, extractionRl,
                Optional.empty());
        registry.registerAll();

        service = new ConfigEditService(registry, audit, authenticationService,
                List.of(new fr.inra.oresing.workflow.cascade.config.rules.StagingIgnoredOnMergeFileRule(),
                        new fr.inra.oresing.workflow.cascade.config.rules.DirectCopyPerConnectionStickyWarning()));
    }

    /** Configure le mock pour un utilisateur admin. */
    private void asAdmin() {
        OreSiUser user = new OreSiUser();
        user.setLogin("admin");
        CurrentUserRoles adminRoles = new CurrentUserRoles(List.of("openAdomAdmin"), false, user);
        when(authenticationService.getCurrentUserRoles()).thenReturn(adminRoles);
        lenient().when(authenticationService.getCurrentUser()).thenReturn(user);
    }

    /** Configure le mock pour un utilisateur NON-admin. */
    private void asRegularUser() {
        OreSiUser user = new OreSiUser();
        user.setLogin("alice");
        CurrentUserRoles regularRoles = new CurrentUserRoles(List.of("someRole"), false, user);
        when(authenticationService.getCurrentUserRoles()).thenReturn(regularRoles);
    }

    // ─── applyPatch — cas nominal ─────────────────────────────────────────

    @Nested
    @DisplayName("applyPatch() — admin")
    class ApplyPatchAdmin {

        @Test
        @DisplayName("patch chunkSizeLines : changed=true, snapshot mis à jour")
        void patchIntField() {
            asAdmin();
            ConfigEditService.PatchResult result = service.applyPatch(
                    Map.of("chunkSizeLines", 5000));

            assertEquals(1, result.changes().size());
            assertEquals("chunkSizeLines", result.changes().get(0).field());
            assertEquals("5000", result.changes().get(0).newValue());
            assertEquals(5000, result.snapshot().get("chunkSizeLines"));
        }

        @Test
        @DisplayName("patch avec même valeur : aucun changement")
        void patchSameValueNoChange() {
            asAdmin();
            // chunkSizeLines défaut = 1000
            ConfigEditService.PatchResult result = service.applyPatch(
                    Map.of("chunkSizeLines", 1000));

            assertTrue(result.changes().isEmpty(), "No change expected for same value");
        }

        @Test
        @DisplayName("patch field null ignoré")
        void patchNullFieldIgnored() {
            asAdmin();
            Map<String, Object> patch = new java.util.HashMap<>();
            patch.put("chunkSizeLines", null);
            ConfigEditService.PatchResult result = service.applyPatch(patch);

            assertTrue(result.changes().isEmpty(), "null value should be ignored");
        }

        @Test
        @DisplayName("patch poolParallelism nested map")
        void patchNestedPoolParallelism() {
            asAdmin();
            ConfigEditService.PatchResult result = service.applyPatch(
                    Map.of("poolParallelism", Map.of("source", 8, "transform", 6)));

            assertFalse(result.changes().isEmpty());
            assertTrue(result.changes().stream()
                    .anyMatch(c -> c.field().equals("pool.source")));
        }

        @Test
        @DisplayName("snapshot() retourne les valeurs courantes")
        void snapshotReturnsCurrentValues() {
            Map<String, Object> snap = service.snapshot();
            assertNotNull(snap);
            assertTrue(snap.containsKey("chunkSizeLines"));
        }
    }

    // ─── applyPatch — accès refusé ──────────────────────────────────────────

    @Test
    @DisplayName("applyPatch() lève AccessDeniedException pour non-admin")
    void patchDeniedForNonAdmin() {
        asRegularUser();
        Map<String, Object> patch = Map.of("chunkSizeLines", 2000);
        assertThrows(AccessDeniedException.class, () -> service.applyPatch(patch));
    }

    // ─── applyPatch — rejets ──────────────────────────────────────────────

    @Test
    @DisplayName("applyPatch() avec champ inconnu lève NoSuchElementException")
    void patchUnknownFieldThrows() {
        asAdmin();
        Map<String, Object> patch = Map.of("nonExistentField", 42);
        assertThrows(java.util.NoSuchElementException.class, () -> service.applyPatch(patch));
    }

    @Test
    @DisplayName("applyPatch() sur champ read-only lève UnsupportedOperationException")
    void patchReadOnlyFieldThrows() {
        asAdmin();
        Map<String, Object> patch = Map.of("virtualThreads", true);
        assertThrows(UnsupportedOperationException.class, () -> service.applyPatch(patch));
    }

    @Test
    @DisplayName("applyPatch() avec règle BLOCKING lève IllegalArgumentException")
    void patchBlockedByRule() {
        asAdmin();
        // On crée un service avec une règle BLOCKING qui bloque tout
        ConsistencyRule alwaysBlocking = new ConsistencyRule() {
            @Override public String code() { return "ALWAYS_BLOCK"; }
            @Override public Severity severity() { return Severity.BLOCKING; }
            @Override public java.util.Optional<String> check(EffectiveConfig c) {
                return java.util.Optional.of("Toujours bloqué (test)");
            }
        };
        ConfigEditService blockedService = new ConfigEditService(
                registry, audit, authenticationService, List.of(alwaysBlocking));
        Map<String, Object> patch = Map.of("chunkSizeLines", 2000);
        assertThrows(IllegalArgumentException.class, () -> blockedService.applyPatch(patch));
    }

    // ─── schema() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("schema() retourne les métadonnées des champs")
    void schemaReturnsMeta() {
        List<ConfigFieldRegistry.FieldMeta> schema = service.schema();
        assertNotNull(schema);
        assertFalse(schema.isEmpty());
        assertTrue(schema.stream().anyMatch(f -> "chunkSizeLines".equals(f.name())));
    }

    // ─── auditList() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("auditList() retourne la liste des entrées d'audit (admin)")
    void auditListAdmin() {
        asAdmin();
        service.applyPatch(Map.of("chunkSizeLines", 2000));
        List<ConfigChangeAudit.Entry> entries = service.auditList();
        assertFalse(entries.isEmpty());
    }

    @Test
    @DisplayName("auditList() lève AccessDeniedException pour non-admin")
    void auditListDeniedForNonAdmin() {
        asRegularUser();
        assertThrows(AccessDeniedException.class, () -> service.auditList());
    }

    // ─── Change record ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Change record : field/oldValue/newValue accessibles")
    void changeRecord() {
        ConfigEditService.Change c = new ConfigEditService.Change("myField", "old", "new");
        assertEquals("myField", c.field());
        assertEquals("old", c.oldValue());
        assertEquals("new", c.newValue());
    }

    // ─── PatchResult record ──────────────────────────────────────────────────

    @Test
    @DisplayName("PatchResult record : snapshot/changes/warnings accessibles")
    void patchResultRecord() {
        ConfigEditService.PatchResult r = new ConfigEditService.PatchResult(
                Map.of("a", 1), List.of(), List.of("warn"));
        assertEquals(1, r.snapshot().get("a"));
        assertTrue(r.changes().isEmpty());
        assertEquals(List.of("warn"), r.warnings());
    }
}