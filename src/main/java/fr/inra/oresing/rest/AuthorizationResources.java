package fr.inra.oresing.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationResources {

    @Autowired
    private AuthorizationService authorizationService;

    @PostMapping(value = "/applications/{nameOrId}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addAuthorization(@RequestBody CreateAuthorizationRequest authorization) {
        UUID authorizationId = authorizationService.addAuthorization(authorization);
        String uri = UriUtils.encodePath("/applications/" + authorization.getApplicationNameOrId() + "/authorization/" + authorizationId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authorizationId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResult> getAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("authorizationId") UUID authorizationId) {
        GetAuthorizationResult getAuthorizationResult = authorizationService.getAuthorization(new AuthorizationRequest(applicationNameOrId, authorizationId));
        return ResponseEntity.ok(getAuthorizationResult);
    }

    @DeleteMapping(value = "/applications/{nameOrId}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> revokeAuthorization(@PathVariable("nameOrId") String applicationNameOrId, @PathVariable("authorizationId") UUID authorizationId) {
        authorizationService.revoke(new AuthorizationRequest(applicationNameOrId, authorizationId));
        return ResponseEntity.noContent().build();
    }
}