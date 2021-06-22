package fr.inra.oresing.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationResources {

    @Autowired
    private AuthorizationService authorizationService;

    @PostMapping(value = "/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateAuthorizationRequest> addAuthorization(@RequestBody CreateAuthorizationRequest authorization) {
        authorizationService.addAuthorization(authorization);
        return ResponseEntity.ok(authorization);
    }
}