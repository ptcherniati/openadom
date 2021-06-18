package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthenticationResources {

    @Autowired
    private AuthHelper authHelper;

    @Autowired
    protected AuthenticationService authenticationService;

    @Autowired
    private OreSiApiRequestContext request;

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResult login(HttpServletResponse response, @RequestParam("login") String login, @RequestParam("password") String password) throws Throwable {
        LoginResult loginResult = authenticationService.login(login, password);
        // l'authentification a fonctionné, on change dans le context
        OreSiUserRole userRole = authenticationService.getUserRole(loginResult.getId());
        OreSiUserRequestClient requestClient = OreSiUserRequestClient.of(loginResult.getId(), userRole);
        authHelper.refreshCookie(response, requestClient);
        request.setRequestClient(requestClient);
        return loginResult;
    }

    @DeleteMapping(value = "/logout")
    public ResponseEntity logout() {
        request.reset();
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, UUID>> createUser(HttpServletResponse response, @RequestParam("login") String login, @RequestParam("password") String password) {
        CreateUserResult createUserResult = authenticationService.createUser(login, password);
        String uri = UriUtils.encodePath("/users/" + createUserResult.getUserId().toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", createUserResult.getUserId()));
    }
}