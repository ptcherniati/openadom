package fr.inra.oresing.rest.users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     */
    @Operation(summary = "Grant a role to a user.")
    @PostMapping(
            value = "/{userId}/roles",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> grant(
            @PathVariable("userId") UUID userId,
            @RequestBody UserDTO.GrantRoleRequest request) {
        service.grantRole(userId, request.applicationId(), request.roleName());
        return ResponseEntity.ok(Map.of(
                "userId", userId.toString(),
                "applicationId", request.applicationId() == null ? null : request.applicationId().toString(),
                "roleName", request.roleName(),
                "granted", true));
    }

    /**
     * Revokes a role from the user. Body matches the grant payload for symmetry.
     */
    @Operation(summary = "Revoke a role from a user.")
    @DeleteMapping(
            value = "/{userId}/roles",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable("userId") UUID userId,
            @RequestBody UserDTO.RevokeRoleRequest request) {
        service.revokeRole(userId, request.applicationId(), request.roleName());
        return ResponseEntity.ok(Map.of(
                "userId", userId.toString(),
                "applicationId", request.applicationId() == null ? null : request.applicationId().toString(),
                "roleName", request.roleName(),
                "revoked", true));
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
