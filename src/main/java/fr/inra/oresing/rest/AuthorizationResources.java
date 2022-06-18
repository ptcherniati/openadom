package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.model.OreSiRoleForUser;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.persistence.roles.CurrentUserRoles;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;

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
    private OreSiRepository repo;
    @GetMapping(value = "/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<LoginResult> getAuthorizations(){
        return authenticationService.getAuthorizations();
    }

    @PostMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addAuthorization(@PathVariable(name = "nameOrId") String nameOrId, @PathVariable(name = "dataType") String dataType, @RequestBody CreateAuthorizationRequest authorization) {
        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        final Application application = repo.application().findApplication(nameOrId);
        final boolean isApplicationCreator = rolesForCurrentUser.getMemberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        final boolean canChangeRoles= isApplicationCreator;
        if (!canChangeRoles) {
            throw new NotApplicationCanSetRightsException(application.getName(), dataType);
        }
        Set<UUID> previousUsers = authorization.getUuid() == null ? new HashSet<>() : authorization.getUsersId();
        OreSiAuthorization oreSiAuthorization = authorizationService.addAuthorization(application, dataType, authorization, isApplicationCreator);
        UUID authId = oreSiAuthorization.getId();
        OreSiRightOnApplicationRole roleForAuthorization = authorizationService.createRoleForAuthorization(authorization, oreSiAuthorization);
        List<OreSiAuthorization> authorizationsForCurrentUser = authorizationService.findUserAuthorizationsForApplicationAndDataType(application, dataType);
        final DatatypeUpdateRoleForManagement datatypeUpdateRoleForManagement = new DatatypeUpdateRoleForManagement(previousUsers, oreSiAuthorization, authorizationsForCurrentUser, isApplicationCreator);
        authorizationService.updateRoleForManagement(previousUsers, oreSiAuthorization);
        String uri = UriUtils.encodePath("/applications/" + authorization.getApplicationNameOrId() + "/dataType/" + authorization.getDataType() + "/authorization/" + authId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResult> getAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType, @PathVariable("authorizationId") UUID authorizationId) {
        GetAuthorizationResult getAuthorizationResult = authorizationService.getAuthorization(new AuthorizationRequest(applicationNameOrId, dataType, authorizationId));
        return ResponseEntity.ok(getAuthorizationResult);
    }

    @DeleteMapping(value = "/authorization/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OreSiUser> deleteAuthorization(
            @PathVariable(name = "role", required = true) String role,
            @RequestParam(name = "userId", required = true) String userId,
            @RequestParam(name = "applicationPattern", required = false) String applicationPattern)
            throws NotSuperAdminException, NotApplicationCreatorRightsException {
        final OreSiRoleForUser roleForUser = new OreSiRoleForUser(userId, role, applicationPattern);
        OreSiUser user = authorizationService.deleteRoleUser(roleForUser);
        return ResponseEntity.ok(user);
    }

    @PutMapping(value = "/authorization/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OreSiUser> addAuthorization(
            @PathVariable(name = "role", required = true) String role,
            @RequestParam(name = "userId", required = true) String userId,
            @RequestParam(name = "applicationPattern", required = false) String applicationPattern)
            throws NotSuperAdminException, NotApplicationCreatorRightsException {
        final OreSiRoleForUser roleForUser = new OreSiRoleForUser(userId, role, applicationPattern);
        OreSiUser user = authorizationService.addRoleUser(roleForUser);
        return ResponseEntity.ok(user);
    }

    @GetMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImmutableSet<GetAuthorizationResult>> getAuthorizations(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType) {
        ImmutableSet<GetAuthorizationResult> getAuthorizationResults = authorizationService.getAuthorizations(applicationNameOrId, dataType);
        return ResponseEntity.ok(getAuthorizationResults);
    }

    @GetMapping(value = "/applications/{nameOrId}/dataType/{dataType}/grantable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetGrantableResult> getGrantable(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType) {
        GetGrantableResult getGrantableResult = authorizationService.getGrantable(applicationNameOrId, dataType);
        return ResponseEntity.ok(getGrantableResult);
    }

    @DeleteMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> revokeAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType, @PathVariable("authorizationId") UUID authorizationId) {
        authorizationService.revoke(new AuthorizationRequest(applicationNameOrId, dataType, authorizationId));
        return ResponseEntity.noContent().build();
    }
}