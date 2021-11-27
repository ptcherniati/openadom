package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationResources {

    @Autowired
    private AuthorizationService authorizationService;

    @PostMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addAuthorization(@RequestBody CreateAuthorizationRequest authorization) {
        Set<UUID> previousUsers = authorization.getUuid()==null?new HashSet<>():authorization.getUsersId();
        OreSiAuthorization oreSiAuthorization = authorizationService.addAuthorization(authorization);
        UUID authId = oreSiAuthorization.getId();
            OreSiRightOnApplicationRole roleForAuthorization = null;
        if(authorization.getUuid()==null){
             roleForAuthorization = authorizationService.createRoleForAuthorization(authorization, oreSiAuthorization);
        }

        authorizationService.updateRoleForManagement(previousUsers, oreSiAuthorization);
        String uri = UriUtils.encodePath("/applications/" + authorization.getApplicationNameOrId() + "/dataType/" + authorization.getDataType() + "/authorization/" + authId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/dataType/{dataType}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResult> getAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("authorizationId") UUID authorizationId) {
        GetAuthorizationResult getAuthorizationResult = authorizationService.getAuthorization(new AuthorizationRequest(applicationNameOrId, authorizationId));
        return ResponseEntity.ok(getAuthorizationResult);
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
    public ResponseEntity<?> revokeAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("authorizationId") UUID authorizationId) {
        authorizationService.revoke(new AuthorizationRequest(applicationNameOrId, authorizationId));
        return ResponseEntity.noContent().build();
    }
}