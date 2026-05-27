package fr.inra.oresing.rest.users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST surface for the oa-live "Users" admin tab.
 *
 * <p>All endpoints are guarded by {@link UserRolesService} which enforces
 * the fine-grained authorization rules (openAdomAdmin vs application manager
 * scope). Controllers stay thin: parse input, delegate, map the outcome to an
 * HTTP response.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "Bearer Authentication")
public class UsersResources {

    private final UserRolesService service;

    @Autowired
    public UsersResources(UserRolesService service) {
        this.service = service;
    }

    /**
     * Lists users matching the optional filters. The service performs the
     * authorization check and trims the result set to what the caller may see.
     */
    @Operation(summary = "List users for the admin Users tab.")
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<UserDTO.UserSummary>> listUsers(
            @RequestParam(name = "login", required = false) String login,
            @RequestParam(name = "appId", required = false) String appId,
            @RequestParam(name = "role",  required = false) String role,
            @RequestParam(name = "state", required = false) String state) {

        UUID appUuid = parseUuidOrNull(appId);
        List<UserDTO.UserSummary> rows = service.listUsers(login, appUuid, role, state);
        return ResponseEntity.ok(rows);
    }

    /**
     * Returns the detail panel payload for a single user, or 404 if the user
     * does not exist or is not visible to the current caller.
     */
    @Operation(summary = "Detail view of a user: apps + global roles + authorization counts.")
    @GetMapping(value = "/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDTO.UserDetail> getDetail(@PathVariable("userId") UUID userId) {
        Optional<UserDTO.UserDetail> detail = service.findDetail(userId);
        return detail.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Grants a role to the user. Empty {@code applicationId} means "global role".
     *
     * <p>Le champ {@code granted} dans la reponse reflete l'etat reel cote PG :
     * {@code true} si la membership a ete creee , {@code false} si l'utilisateur
     * etait deja membre ( no-op idempotent ) . Le client peut alors afficher
     * un toast contextuel "Role attribue" vs "Role deja attribue" plutot que
     * de presumer un succes systematique .
     */
    @Operation(summary = "Grant a role to a user.")
    @PostMapping(
            value = "/{userId}/roles",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> grant(
            @PathVariable("userId") UUID userId,
            @RequestBody UserDTO.GrantRoleRequest request) {
        boolean granted = service.grantRole(userId, request.applicationId(), request.roleName());
        // Map.of rejects null values ( NPE ) and a global grant has a null
        // applicationId by design , so build the response with a LinkedHashMap
        // that tolerates nulls and preserves the field order .
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("userId", userId.toString());
        body.put("applicationId", request.applicationId() == null ? null : request.applicationId().toString());
        body.put("roleName", request.roleName());
        body.put("granted", granted);
        return ResponseEntity.ok(body);
    }

    /**
     * Revokes a role from the user. Body matches the grant payload for symmetry.
     *
     * <p>Le champ {@code revoked} reflete l'etat reel cote PG : {@code true}
     * si la membership a ete supprimee , {@code false} si l'utilisateur n'etait
     * pas membre ( e.g. base portee avec un format de role obsolete , role
     * deja revoque par un autre admin , etc ) . Le client doit afficher une
     * erreur dans ce dernier cas plutot que de pretendre que la revocation
     * a fonctionne .
     */
    @Operation(summary = "Revoke a role from a user.")
    @DeleteMapping(
            value = "/{userId}/roles",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable("userId") UUID userId,
            @RequestBody UserDTO.RevokeRoleRequest request) {
        boolean revoked = service.revokeRole(userId, request.applicationId(), request.roleName());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("userId", userId.toString());
        body.put("applicationId", request.applicationId() == null ? null : request.applicationId().toString());
        body.put("roleName", request.roleName());
        body.put("revoked", revoked);
        return ResponseEntity.ok(body);
    }

    // ---------------------------------------------------------------- //
    //  helpers                                                         //
    // ---------------------------------------------------------------- //

    private static UUID parseUuidOrNull(String maybeUuid) {
        if (maybeUuid == null || maybeUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(maybeUuid);
        } catch (IllegalArgumentException ex) {
            // The list endpoint accepts a blank / null filter; an invalid UUID
            // is treated as "no app filter" rather than failing the request.
            return null;
        }
    }
}