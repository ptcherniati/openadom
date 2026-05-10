package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link AuthorizationsForApplicationUser}.
 * Ce record concentre toute la logique d'autorisation effective (canRead, canWrite, canDelete,
 * getAuthorizations) — son comportement est validé ici en isolation.
 */
@Tag("core.auth")
@DisplayName("Tests unitaires de AuthorizationsForApplicationUser")
class AuthorizationsForApplicationUserTest {

    private static final String DATA_NAME = "dataName";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AuthorizationParsed authWith(OperationType... types) {
        AuthorizationParsed auth = mock(AuthorizationParsed.class);
        when(auth.operationTypes()).thenReturn(Set.of(types));
        return auth;
    }

    private AuthorizationsForApplicationUser build(
            boolean isApplicationManager,
            boolean isUserManager,
            Map<String, List<AuthorizationParsed>> userAuths,
            Map<String, AuthorizationParsed> publicAuths) {
        return new AuthorizationsForApplicationUser(
                null, null,
                isApplicationManager,
                isUserManager,
                userAuths,
                publicAuths
        );
    }

    // ── canRead ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("canRead(dataName)")
    class CanReadTests {

        @Test
        @DisplayName("isApplicationManager=true → true")
        void canRead_applicationManager_returnsTrue() {
            var user = build(true, false, Map.of(), Map.of());
            assertTrue(user.canRead(DATA_NAME));
        }

        @Test
        @DisplayName("isUserManager=true → true")
        void canRead_userManager_returnsTrue() {
            var user = build(false, true, Map.of(), Map.of());
            assertTrue(user.canRead(DATA_NAME));
        }

        @Test
        @DisplayName("authorization utilisateur avec OperationType.depot → true")
        void canRead_userAuthWithDepot_returnsTrue() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.depot))),
                    Map.of());
            assertTrue(user.canRead(DATA_NAME));
        }

        @Test
        @DisplayName("authorization publique avec OperationType.extraction → true")
        void canRead_publicAuthWithExtraction_returnsTrue() {
            var user = build(false, false,
                    Map.of(),
                    Map.of(DATA_NAME, authWith(OperationType.extraction)));
            assertTrue(user.canRead(DATA_NAME));
        }

        @Test
        @DisplayName("aucune authorization → false")
        void canRead_noAuth_returnsFalse() {
            var user = build(false, false, Map.of(), Map.of());
            assertFalse(user.canRead(DATA_NAME));
        }
    }

    // ── canWrite ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("canWrite(dataName, toPublish)")
    class CanWriteTests {

        @Test
        @DisplayName("dépôt (toPublish=false) avec auth depot → true")
        void canWrite_depot_authDepot_returnsTrue() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.depot))),
                    Map.of());
            assertTrue(user.canWrite(DATA_NAME, false));
        }

        @Test
        @DisplayName("dépôt (toPublish=false) avec auth publication uniquement → false")
        void canWrite_depot_authPublicationOnly_returnsFalse() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.publication))),
                    Map.of());
            assertFalse(user.canWrite(DATA_NAME, false));
        }

        @Test
        @DisplayName("publication (toPublish=true) avec auth publication → true")
        void canWrite_publication_authPublication_returnsTrue() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.publication))),
                    Map.of());
            assertTrue(user.canWrite(DATA_NAME, true));
        }

        @Test
        @DisplayName("publication (toPublish=true) avec auth depot uniquement → false")
        void canWrite_publication_authDepotOnly_returnsFalse() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.depot))),
                    Map.of());
            assertFalse(user.canWrite(DATA_NAME, true));
        }

        @Test
        @DisplayName("isApplicationManager=true ignore les auths → true (dépôt)")
        void canWrite_applicationManager_alwaysTrue() {
            var user = build(true, false, Map.of(), Map.of());
            assertTrue(user.canWrite(DATA_NAME, false));
            assertTrue(user.canWrite(DATA_NAME, true));
        }
    }

    // ── canDelete ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("canDelete(dataName, isRepository)")
    class CanDeleteTests {

        @Test
        @DisplayName("isRepository=false, auth publication → true")
        void canDelete_notRepo_authPublication_returnsTrue() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.publication))),
                    Map.of());
            assertTrue(user.canDelete(DATA_NAME, false));
        }

        @Test
        @DisplayName("isRepository=true, auth delete seul → true")
        void canDelete_repo_authDelete_returnsTrue() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.delete))),
                    Map.of());
            assertTrue(user.canDelete(DATA_NAME, true));
        }

        @Test
        @DisplayName("isRepository=false, auth delete seul → false (règle exige publication quand !isRepository)")
        void canDelete_notRepo_authDeleteOnly_returnsFalse() {
            // Comportement contre-intuitif documenté : sans repositoty, la suppression
            // requiert OperationType.publication (pas delete), car seule la publication
            // permet de "dépublier" les données.
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.delete))),
                    Map.of());
            assertFalse(user.canDelete(DATA_NAME, false));
        }

        @Test
        @DisplayName("isApplicationManager=true → true quelle que soit la config")
        void canDelete_applicationManager_alwaysTrue() {
            var user = build(true, false, Map.of(), Map.of());
            assertTrue(user.canDelete(DATA_NAME, false));
            assertTrue(user.canDelete(DATA_NAME, true));
        }

        @Test
        @DisplayName("isUserManager=true → true quelle que soit la config")
        void canDelete_userManager_alwaysTrue() {
            var user = build(false, true, Map.of(), Map.of());
            assertTrue(user.canDelete(DATA_NAME, false));
            assertTrue(user.canDelete(DATA_NAME, true));
        }
    }

    // ── getAuthorizations ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAuthorizations(dataName, actions)")
    class GetAuthorizationsTests {

        @Test
        @DisplayName("filtre retourne seulement les auths dont l'operationType correspond")
        void getAuthorizations_filtersOnOperationType() {
            AuthorizationParsed depotAuth = authWith(OperationType.depot);
            AuthorizationParsed publicationAuth = authWith(OperationType.publication);
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(depotAuth, publicationAuth)),
                    Map.of());

            List<AuthorizationParsed> result = user.getAuthorizations(DATA_NAME, Set.of(OperationType.depot));

            assertEquals(1, result.size());
            assertTrue(result.contains(depotAuth));
            assertFalse(result.contains(publicationAuth));
        }

        @Test
        @DisplayName("inclut aussi l'authorization publique si elle correspond")
        void getAuthorizations_includesPublicAuthIfMatches() {
            AuthorizationParsed publicAuth = authWith(OperationType.depot);
            var user = build(false, false,
                    Map.of(),
                    Map.of(DATA_NAME, publicAuth));

            List<AuthorizationParsed> result = user.getAuthorizations(DATA_NAME, Set.of(OperationType.depot));

            assertEquals(1, result.size());
            assertTrue(result.contains(publicAuth));
        }

        @Test
        @DisplayName("retourne liste vide si aucune auth ne correspond")
        void getAuthorizations_noMatch_returnsEmpty() {
            var user = build(false, false,
                    Map.of(DATA_NAME, List.of(authWith(OperationType.publication))),
                    Map.of());

            List<AuthorizationParsed> result = user.getAuthorizations(DATA_NAME, Set.of(OperationType.depot));

            assertTrue(result.isEmpty());
        }
    }
}