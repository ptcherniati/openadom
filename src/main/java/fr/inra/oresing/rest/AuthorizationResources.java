package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.model.OreSiRoleForUser;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OperationType;
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
    private OreSiApiRequestContext request;

    @Autowired
    private OreSiRepository repo;
    @GetMapping(value = "/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<LoginResult> getAuthorizations(){
        return authenticationService.getAuthorizations();
    }
    @GetMapping(value = "/{applicationNameOrId}/authorization/{dataType}/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationsResult getAuthorizationsForUser(@PathVariable(name = "applicationNameOrId") String applicationNameOrId, @PathVariable(name = "dataType") String dataType, @PathVariable(name = "userLoginOrId") String userLoginOrId){
        return authorizationService.getAuthorizationsForUser(applicationNameOrId, dataType,userLoginOrId);
    }

    @PostMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addAuthorization(@PathVariable(name = "nameOrId") String nameOrId, @PathVariable(name = "dataType") String dataType, @RequestBody CreateAuthorizationRequest authorization) {
        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        final Application application = repo.application().findApplication(nameOrId);
        final boolean isApplicationCreator = rolesForCurrentUser.getMemberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        List<OreSiAuthorization> authorizationsForCurrentUser = authorizationService.findUserAuthorizationsForApplicationAndDataType(application, dataType);
        if (!isApplicationCreator && !authorizationsForCurrentUser.stream().anyMatch(
                a->!a.getAuthorizations().get(OperationType.admin).isEmpty()
        )) {
            throw new NotApplicationCanSetRightsException(application.getName(), dataType);
        }
        Set<UUID> previousUsers = authorization.getUuid() == null ? new HashSet<>() : authorization.getUsersId();
        OreSiAuthorization oreSiAuthorization = authorizationService.addAuthorization(application, dataType, authorization, authorizationsForCurrentUser, isApplicationCreator);
        UUID authId = oreSiAuthorization.getId();
        if(authorization.getUuid()==null){
            authorizationService.createRoleForAuthorization(authorization, oreSiAuthorization);
        }
        final DatatypeUpdateRoleForManagement datatypeUpdateRoleForManagement = new DatatypeUpdateRoleForManagement(previousUsers, oreSiAuthorization, authorizationsForCurrentUser, isApplicationCreator);
        authorizationService.updateRoleForManagement(previousUsers, oreSiAuthorization);
        String uri = UriUtils.encodePath("/applications/" + authorization.getApplicationNameOrId() + "/dataType/" + authorization.getDataType() + "/authorization/" + authId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResult> getAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType, @PathVariable("authorizationId") UUID authorizationId) {
        final AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, dataType, request.getRequestUserId().toString());
        GetAuthorizationResult getAuthorizationResult = authorizationService.getAuthorization(new AuthorizationRequest(applicationNameOrId, dataType, authorizationId), authorizationsForUser);
        return ResponseEntity.ok(getAuthorizationResult);
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

    @GetMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResults> getAuthorizations(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType) {
        final AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, dataType, request.getRequestUserId().toString());
        ImmutableSet<GetAuthorizationResult> getAuthorizationResults = authorizationService.getAuthorizations(applicationNameOrId, dataType, authorizationsForUser);
        final GetAuthorizationResults getAuthorizationResultsWithOwnRights1 = new GetAuthorizationResults(getAuthorizationResults, authorizationsForUser);
        return ResponseEntity.ok(getAuthorizationResultsWithOwnRights1);
    }

    @GetMapping(value = "/applications/{nameOrId}/dataType/{dataType}/grantable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetGrantableResult> getGrantable(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType) {
        final AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, dataType, request.getRequestUserId().toString());
        GetGrantableResult getGrantableResult = authorizationService.getGrantable(applicationNameOrId, dataType, authorizationsForUser);
        return ResponseEntity.ok(getGrantableResult);
    }

    @DeleteMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> revokeAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("dataType") String dataType, @PathVariable("authorizationId") UUID authorizationId) {
        authorizationService.revoke(new AuthorizationRequest(applicationNameOrId, dataType, authorizationId));
        return ResponseEntity.noContent().build();
    }
}