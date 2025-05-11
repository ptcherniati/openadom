package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUser;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
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

    protected final AuthenticationService authenticationService;
    private final OreSiApiRequestContext request;

    public AuthenticationResources(AuthenticationService authenticationService, OreSiApiRequestContext request) {
        this.authenticationService = authenticationService;
        this.request = request;
    }

    @Tag(name = "Sécurité", description = "Endpoints liés à la sécurité et à l’authentification")

    @Operation(
            summary = "Obtenir un token CSRF",
            description = "Renvoie le token CSRF à utiliser dans les requêtes POST/PUT/DELETE. Nécessite d’être authentifié.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token CSRF renvoyé"),
                    @ApiResponse(responseCode = "401", description = "Non authentifié")
            }
    )

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/csrf-token")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public OreSiRequestClient me() {
        return request.getRequestClient();
    }

    @Operation(
            summary = "Connexion utilisateur",
            description = "Authentifie un utilisateur et retourne un token JWT dans le cookie. " +
                    "Ce cookie est à passer dans tout appel au serveur",
            tags = {"authentication-resources"},
            parameters = {
                    @Parameter(
                            name = "login",
                            description = "Identifiant de l'utilisateur",
                            required = true,
                            in = ParameterIn.QUERY,
                            examples = {
                                    @ExampleObject(name = "Admin", value = "\"admin\""),
                                    @ExampleObject(name = "Utilisateur standard", value = "\"user123\"")
                            }
                    ),
                    @Parameter(
                            name = "password",
                            description = "Mot de passe de l'utilisateur",
                            required = true,
                            in = ParameterIn.QUERY,
                            schema = @Schema(type = "string", format = "password")
                    )
            }
    )
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginAdminResult login(final HttpServletResponse response, @RequestParam("login") final String login, @RequestParam("password") final String password) {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .filter(LoginAdminResult.class::isInstance)
                .map(LoginAdminResult.class::cast)
                .orElse(null);
    }

    @Operation(
            summary = "Déconnecter l'utilisateur courant",
            description = """
                    Invalide la session utilisateur et supprime les privilèges d'accès.
                    Efface les cookies d'authentification le cas échéant.
                    """,
            tags = {"Authentification"})
    @DeleteMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        return ResponseEntity
                .ok("{\"message\": \"Disconnected\"}");
    }

    @Operation(
            summary = "Créer un nouvel utilisateur",
            description = "Enregistre un nouvel utilisateur avec identifiant, mot de passe et email",
            tags = {"Utilisateurs"})
    @PreAuthorize("isAnonymous() || isAuthenticated()")
    @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, UUID>> createUser(
            @Parameter(
                    name = "login",
                    description = "Identifiant unique de connexion",
                    example = "\"jdupont\"",
                    required = true)
            @RequestParam("login") String login,

            @Parameter(
                    name = "password",
                    description = "Mot de passe en clair (sera hashé)",
                    example = "\"Secr3tP@ss\"",
                    required = true)
            @RequestParam("password") String password,

            @Parameter(
                    name = "email",
                    description = "Email principal de l'utilisateur",
                    example = "\"user@inrae.fr\"",
                    required = true)
            @RequestParam("email") final String email) throws AuthenticationFailure {
        final CreateUserResult createUserResult = authenticationService.createUser(login, password, email);
        try {
            authenticationService.sendEmailValidation(login);
        } catch (final AuthenticationFailure e) {
            switch (OreSiResources.getDefaultLocale().getLanguage()) {
                case "fr" -> throw new OreSiTechnicalException("Erreur lors de l'envoi de la mise à jour de validation");
                case "en" -> throw new OreSiTechnicalException("Error sending validation update");
                case null, default -> throw new OreSiTechnicalException("Error sending validation update");
            }

        }
        final String uri = UriUtils.encodePath("/users/" + createUserResult.userId().toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", createUserResult.userId()));
    }

    @Operation(
            summary = "Mettre à jour un utilisateur",
            description = """
                    Gère différentes opérations de mise à jour selon les paramètres fournis :
                    
                    1. **Activation de compte** (login + password + verificationKey)
                    2. **Changement d'email** (login + email → envoi d'une verificationKey)
                    3. **Réinitialisation de mot de passe** (login + email + verificationKey + newPassword + newPasswordConfirm)
                    4. **Modification mot de passe** (login + password + active)
                    Transitions d'état du compte : idle → pending → active""",
            tags = {"Utilisateurs"})
    @PreAuthorize("isAnonymous() || isAuthenticated()")
    @PutMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateUserResult> updateUser(
            final HttpServletResponse response/*,
            @RequestBody() final Mono<CreateUserRequest> createUserRequest*/
    ) throws AuthenticationFailure, NoSuchAlgorithmException, InvalidKeySpecException, JsonProcessingException {
        NotConnectedUser notConnectedUser = Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .filter(NotConnectedUser.class::isInstance)
                .map(NotConnectedUser.class::cast)
                .orElse(null);
        assert notConnectedUser != null;
        final OreSiUser oreSiUser = authenticationService.updateUser(notConnectedUser);
        final String uri = UriUtils.encodePath("/users/" + Optional.ofNullable(oreSiUser)
                        .map(OreSiUser::getId)
                        .map(UUID::toString)
                        .orElse(""),
                Charset.defaultCharset());

        return ResponseEntity.created(URI.create(uri)).body(CreateUserResult.of(Objects.requireNonNull(oreSiUser)));
    }

    @Operation(
            summary = "Récupérer un utilisateur par son login ou son ID",
            description = "Trouve un utilisateur selon son identifiant de connexion ou son UUID. Les champs sensibles comme le mot de passe ne sont pas retournés en production.",
            tags = {"Utilisateurs"})
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_USER_READER')")
    @GetMapping(value = "/users/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OreSiUser getByIdOrLogin(@PathVariable(name = "userLoginOrId") final String userLoginOrId) {
        return authenticationService.getByIdOrLogin(userLoginOrId);
    }
}