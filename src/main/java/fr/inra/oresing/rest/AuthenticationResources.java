package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    public LoginAdminResult login(final HttpServletResponse response, @RequestParam("login") final String login, @RequestParam("password") final String password) throws Throwable {
        final LoginAdminResult loginAdminResult = authenticationService.login(login, password);
        // l'authentification a fonctionné, on change dans le context
        final OreSiUserRole userRole = authenticationService.getUserRole(loginAdminResult.id());
        final OreSiUserRequestClient requestClient = OreSiUserRequestClient.of(loginAdminResult.id(), userRole);
        authHelper.refreshCookie(response, requestClient);
        request.setRequestClient(requestClient);
        return loginAdminResult;
    }

    @DeleteMapping("/logout")
    public ResponseEntity logout(HttpServletResponse response) {
        authHelper.invalidateCookie(response);
        request.reset();
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, UUID>> createUser(final HttpServletResponse response,
                                                        @RequestParam("login") final String login,
                                                        @RequestParam("password") final String password,
                                                        @RequestParam("email") final String email) throws AuthenticationFailure {
        final CreateUserResult createUserResult = authenticationService.createUser(login, password, email);
        try {
            authenticationService.sendEmailValidation(login, password);
        } catch (final AuthenticationFailure e) {
            switch (OreSiResources.getDefaultLocale().getLanguage()) {
                case "fr"-> throw new RuntimeException("Erreur lors de l'envoi de la mise à jour de validation");
                case "en"-> throw new RuntimeException("Error sending validation update");
                case null, default -> throw new RuntimeException("Error sending validation update");
            }

        }
        final String uri = UriUtils.encodePath("/users/" + createUserResult.userId().toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", createUserResult.userId()));
    }

    @PutMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateUserResult> updateUser(final HttpServletResponse response,
                                                       @RequestBody() final CreateUserRequest createUserRequest) throws AuthenticationFailure, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        final OreSiUser oreSiUser = authenticationService.updateUser(createUserRequest);
        final String uri = UriUtils.encodePath("/users/" + Optional.ofNullable(oreSiUser)
                        .map(OreSiUser::getId)
                        .map(UUID::toString)
                        .orElse(""),
                Charset.defaultCharset());

        return ResponseEntity.created(URI.create(uri)).body(CreateUserResult.of(Objects.requireNonNull(oreSiUser)));
    }

    @GetMapping(value = "/users/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OreSiUser getByIdOrLogin(@PathVariable(name = "userLoginOrId") final String userLoginOrId) {
        return authenticationService.getByIdOrLogin(userLoginOrId);
    }
}
