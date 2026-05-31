package fr.inra.oresing.rest;

import fr.inra.oresing.rest.usecases.admin.UserAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoint d'administration du <b>blocage d'accès par utilisateur</b> ( global,
 * non lié à une application ). Bascule, par utilisateur, l'interdiction d'accès
 * à tous les services ( redirection page de maintenance ).
 *
 * <p>Action d'exploitation, séparée de la logique métier - au même titre que le
 * mode maintenance, l'accès VPN ou la purge des caches. Gardée
 * {@code SYSTEM_OPENADOM_ADMIN}.
 *
 * <p>La liste des utilisateurs ( login / email ) est servie par
 * {@code /api/v1/admin/users} ; ici on n'expose que l'ensemble des UUID
 * bloqués, que le front recoupe avec cette liste.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/user-access")
@SecurityRequirement(name = "Bearer Authentication")
public class UserAccessResources {

    private final UserAccessService userAccessService;

    public UserAccessResources(UserAccessService userAccessService) {
        this.userAccessService = userAccessService;
    }

    /** Demande de bascule du blocage d'un utilisateur. */
    public record BlockToggleRequest(boolean blocked) {
    }

    @Operation(summary = "UUID des utilisateurs dont l'accès est bloqué")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/blocked", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UUID> blocked() {
        return List.copyOf(userAccessService.blockedUserIds());
    }

    @Operation(summary = "Bloque / débloque l'accès d'un utilisateur")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PutMapping(value = "/blocked/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UUID> toggle(@PathVariable("userId") UUID userId, @RequestBody BlockToggleRequest request) {
        // Anti-lockout : un admin ne peut pas se bloquer lui-même.
        if (request.blocked() && userId.equals(currentUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Un administrateur ne peut pas bloquer son propre accès");
        }
        userAccessService.setBlocked(userId, request.blocked());
        return List.copyOf(userAccessService.blockedUserIds());
    }

    private UUID currentUserId() {
        try {
            return OreSiApiRequestContext.getRequestClient().id();
        } catch (RuntimeException e) {
            // Pas d'utilisateur authentifié résolu : pas de garde anti-lockout.
            return null;
        }
    }
}
