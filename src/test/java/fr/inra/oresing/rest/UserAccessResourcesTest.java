package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.usecases.admin.UserAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contrat de {@link UserAccessResources} : délégation au service + garde
 * anti-lockout ( un admin ne peut pas se bloquer lui-même ) .
 */
class UserAccessResourcesTest {

    private UserAccessService service;
    private UserAccessResources resources;

    @BeforeEach
    void setUp() {
        service = mock(UserAccessService.class);
        resources = new UserAccessResources(service);
        SecurityContextHolder.clearContext();
        OreSiApiRequestContext.setAuthenticationToken(null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Pose le contexte d'authentification courant sur {@code userId}. */
    private void authenticatedAs(UUID userId) {
        OreSiAuthenticationToken token = new OreSiAuthenticationToken(
                new OreSiUserRequestClient(userId, null), "", List.of());
        OreSiApiRequestContext.setAuthenticationToken(token);
    }

    @Test
    void blocked_returns_service_list() {
        UUID a = UUID.randomUUID();
        when(service.blockedUserIds()).thenReturn(Set.of(a));
        assertEquals(List.of(a), resources.blocked());
    }

    @Test
    void toggle_block_other_user_delegates() {
        UUID target = UUID.randomUUID();
        authenticatedAs(UUID.randomUUID()); // admin différent de la cible
        when(service.blockedUserIds()).thenReturn(Set.of(target));

        List<UUID> result = resources.toggle(target, new UserAccessResources.BlockToggleRequest(true));

        verify(service).setBlocked(target, true);
        assertEquals(List.of(target), result);
    }

    @Test
    void toggle_block_self_is_rejected_with_400() {
        UUID me = UUID.randomUUID();
        authenticatedAs(me);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resources.toggle(me, new UserAccessResources.BlockToggleRequest(true)));

        assertEquals(400, ex.getStatusCode().value());
        verify(service, never()).setBlocked(me, true); // jamais appelé : refus avant
    }

    @Test
    void toggle_unblock_self_is_allowed() {
        UUID me = UUID.randomUUID();
        authenticatedAs(me);
        when(service.blockedUserIds()).thenReturn(Set.of());

        // Se débloquer soi-même n'est pas un lockout -> autorisé.
        resources.toggle(me, new UserAccessResources.BlockToggleRequest(false));

        verify(service).setBlocked(me, false);
    }
}
