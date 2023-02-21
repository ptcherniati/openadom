package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.model.*;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OperationType;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.persistence.roles.CurrentUserRoles;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.rest.exceptions.authentication.NotApplicationCanManageReferenceRightsException;
import fr.inra.oresing.rest.exceptions.authentication.NotApplicationCanSetRightsException;
import fr.inra.oresing.rest.exceptions.authentication.NotApplicationCreatorRightsException;
import fr.inra.oresing.rest.exceptions.authentication.NotSuperAdminException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationResources {

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OreSiApiRequestContext request;

    @Autowired
    private OreSiRepository repo;

    @GetMapping(value = "/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<LoginResult> getAuthorizations() {
        return authenticationService.getAuthorizations();
    }

    @PostMapping(value = "/applications/{nameOrId}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addAuthorization(
            @PathVariable(name = "nameOrId") String nameOrId,
            @RequestBody CreateAuthorizationRequest authorization) {
        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        final Application application = repo.application().findApplication(nameOrId);
        final boolean isApplicationCreator = rolesForCurrentUser.getMemberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        List<OreSiAuthorization> authorizationsForCurrentUser = authorizationService.findUserAuthorizationsForApplication(application);
        if (!isApplicationCreator && !authorizationsForCurrentUser.stream().anyMatch(
                a -> !a.getAuthorizations().values().stream().anyMatch(auth -> auth.get(OperationType.admin).isEmpty())
        )) {
            throw new NotApplicationCanSetRightsException(application.getName());
        }
        Set<UUID> previousUsers = authorization.getUuid() == null ? new HashSet<>() : authorization.getUsersId();
        OreSiAuthorization oreSiAuthorization = authorizationService.addAuthorization(application, authorization, authorizationsForCurrentUser, isApplicationCreator);
        UUID authId = oreSiAuthorization.getId();
        if (authorization.getUuid() == null) {
            final OreSiRightOnApplicationRole roleForAuthorization = authorizationService.createRoleForAuthorization(authorization, oreSiAuthorization);
        }
        final DatatypeUpdateRoleForManagement datatypeUpdateRoleForManagement = new DatatypeUpdateRoleForManagement(previousUsers, oreSiAuthorization, authorizationsForCurrentUser, isApplicationCreator);
        authorizationService.updateRoleForManagement(previousUsers, oreSiAuthorization);
        String uri = UriUtils.encodePath("/applications/authorization/" + authId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResult> getAuthorizationById(
            @PathVariable("nameOrId") String applicationNameOrId,
            @PathVariable("authorizationId") UUID authorizationId) {
        final AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        GetAuthorizationResult getAuthorizationResult = authorizationService.getAuthorization(new AuthorizationRequest(applicationNameOrId, authorizationId), authorizationsForUser);
        return ResponseEntity.ok(getAuthorizationResult);
    }

    @GetMapping(value = "/applications/{nameOrId}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResults> getAuthorizations(@PathVariable("nameOrId") String applicationNameOrId) {
        final AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        ImmutableSet<GetAuthorizationResult> getAuthorizationResults = authorizationService.getAuthorizations(applicationNameOrId, authorizationsForUser);
        final GetAuthorizationResults getAuthorizationResultsWithOwnRights1 = new GetAuthorizationResults(getAuthorizationResults, authorizationsForUser);
        return ResponseEntity.ok(getAuthorizationResultsWithOwnRights1);
    }

    @GetMapping(value = "/applications/{applicationNameOrId}/authorization/user/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationsResult getAuthorizationsForUser(@PathVariable(name = "applicationNameOrId") String applicationNameOrId, @PathVariable(name = "userLoginOrId") String userLoginOrId) {
        return authorizationService.getAuthorizationsForUser(applicationNameOrId, userLoginOrId);
    }

    @DeleteMapping(value = "/applications/{nameOrId}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> revokeAuthorization(
            @PathVariable("nameOrId") String applicationNameOrId,
            @PathVariable("authorizationId") UUID authorizationId) {
        final UUID revokeId = authorizationService.revoke(applicationNameOrId, new AuthorizationRequest(applicationNameOrId, authorizationId));
        return ResponseEntity.ok(revokeId);
    }

    @PostMapping(value = "/applications/{nameOrId}/references/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addReferenceAuthorization(@PathVariable(name = "nameOrId") String nameOrId, @RequestBody CreateReferenceAuthorizationRequest authorization) {
        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        final Application application = repo.application().findApplication(nameOrId);
        final boolean isApplicationCreator = rolesForCurrentUser.getMemberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        List<OreSiReferenceAuthorization> referencesAuthorizationsForCurrentUser = authorizationService.findUserReferencesAuthorizationsForApplicationAndDataType(application);
        if (!isApplicationCreator && referencesAuthorizationsForCurrentUser.stream().noneMatch(
                a -> CollectionUtils.isEmpty(a.getReferences().get(OperationType.admin))
        )) {
            throw new NotApplicationCanManageReferenceRightsException(application.getName());
        }
        Set<UUID> previousUsers = authorization.getUuid() == null ? new HashSet<>() : authorization.getUsersId();
        OreSiReferenceAuthorization oreSiAuthorization = authorizationService.addReferenceAuthorizations(application, authorization, referencesAuthorizationsForCurrentUser, isApplicationCreator);
        UUID authId = oreSiAuthorization.getId();
        if (authorization.getUuid() == null) {
            final OreSiRightOnApplicationRole roleForAuthorization = authorizationService.createRoleForAuthorization(authorization, oreSiAuthorization);
        }
        authorizationService.updateRoleForReferenceManagement(previousUsers, oreSiAuthorization);
        String uri = UriUtils.encodePath("/applications/" + authorization.getApplicationNameOrId() + "/references/authorization/" + authId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/references/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationReferencesResults> getReferencesAuthorizations(
            @PathVariable("nameOrId") String applicationNameOrId,
            @RequestParam MultiValueMap<String, String> params
    ) {
        final AuthorizationsReferencesResult authorizationsForUser = getReferencesAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        ImmutableSet<GetAuthorizationReferencesResult> getAuthorizationResults = authorizationService.getReferencesAuthorizations(applicationNameOrId, authorizationsForUser, params);
        Set<GetGrantableResult.User> users = authorizationService.getGrantableUsers()
                .stream()
                .filter(user -> !"_public_".equals(user.getLabel()))
                .collect(Collectors.toSet());

        final GetAuthorizationReferencesResults getAuthorizationResultsWithOwnRights1 = new GetAuthorizationReferencesResults(getAuthorizationResults, authorizationsForUser, users);
        return ResponseEntity.ok(getAuthorizationResultsWithOwnRights1);
    }

    @GetMapping(value = "/applications/{applicationNameOrId}/references/authorization/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationsReferencesResult getReferencesAuthorizationsForUser(@PathVariable(name = "applicationNameOrId") String applicationNameOrId, @PathVariable(name = "userLoginOrId", required = false) String userLoginOrId) {
        userLoginOrId = userLoginOrId == null || "null".equals(userLoginOrId) ? request.getRequestUserId().toString() : userLoginOrId;
        return authorizationService.getReferencesAuthorizationsForUser(applicationNameOrId, userLoginOrId);
    }


    @DeleteMapping(value = "/applications/{applicationNameOrId}/references/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> revokeReferencesAuthorization(
            @PathVariable("applicationNameOrId") String applicationNameOrId,
            @PathVariable("authorizationId") String authorizationId) {
        final UUID revokeId = authorizationService.revokeReferencesAuthorization(applicationNameOrId, UUID.fromString(authorizationId));
        return ResponseEntity.ok(revokeId.toString());
    }

    @PutMapping(value = "/authorization/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OreSiUser> addAuthorization(
            @PathVariable(name = "role", required = true) String role,
            @RequestParam(name = "userIdOrLogin", required = true) String userIdOrLogin,
            @RequestParam(name = "applicationPattern", required = false) String applicationPattern)
            throws NotSuperAdminException, NotApplicationCreatorRightsException {
        OreSiUser user = authenticationService.getByIdOrLogin(userIdOrLogin);
        final OreSiRoleForUser roleForUser = new OreSiRoleForUser(user.getId().toString(), role, applicationPattern);
        user = authorizationService.addRoleUser(roleForUser);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping(value = "/authorization/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OreSiUser> deleteAuthorization(
            @PathVariable(name = "role", required = true) String role,
            @RequestParam(name = "userIdOrLogin", required = true) String userIdOrLogin,
            @RequestParam(name = "applicationPattern", required = false) String applicationPattern)
            throws NotSuperAdminException, NotApplicationCreatorRightsException {
        OreSiUser user = authenticationService.getByIdOrLogin(userIdOrLogin);
        final OreSiRoleForUser roleForUser = new OreSiRoleForUser(user.getId().toString(), role, applicationPattern);
        user = authorizationService.deleteRoleUser(roleForUser);
        return ResponseEntity.ok(user);
    }

    @GetMapping(value = "/applications/{nameOrId}/grantable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetGrantableResult> getGrantable(@PathVariable("nameOrId") String applicationNameOrId) {
        final AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        GetGrantableResult getGrantableResult = authorizationService.getGrantable(applicationNameOrId, authorizationsForUser);
        return ResponseEntity.ok(getGrantableResult);
    }
}