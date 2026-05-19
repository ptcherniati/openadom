package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.LoginAdminResult;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUser;
import fr.inra.oresing.domain.exceptions.AuthenticationFailure;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.monitoring.session.SessionInfo;
import fr.inra.oresing.monitoring.session.UserSessionLogEntry;
import fr.inra.oresing.monitoring.session.UserSessionLogWriter;
import fr.inra.oresing.monitoring.session.UserSessionRegistry;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.security.JWTExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
    protected final JWTExtractor jwtExtractor;
    protected final UserSessionRegistry sessionRegistry;
    protected final UserSessionLogWriter sessionLogWriter;

    // #470 - Exposé au frontend via GET /api/v1/session/config pour que
    // SessionService aligne son timer d'inactivité sur le TTL serveur.
    @Value("${jwt.expiration:3600}")
    private int jwtExpirationSeconds;

    public AuthenticationResources(AuthenticationService authenticationService,
                                   JWTExtractor jwtExtractor,
                                   UserSessionRegistry sessionRegistry,
                                   UserSessionLogWriter sessionLogWriter) {
        this.authenticationService = authenticationService;
        this.jwtExtractor          = jwtExtractor;
        this.sessionRegistry       = sessionRegistry;
        this.sessionLogWriter      = sessionLogWriter;
    }

    @Operation(
            summary = "Configuration de session exposée au client",
            description = "Durée d'expiration du JWT ( en secondes ). Permet au frontend " +
                          "d'aligner son timer d'inactivité sur le TTL configuré côté serveur. " +
                          "Endpoint anonyme : appelable avant authentification.",
            tags = {"Authentication"})
    @GetMapping(value = "/session/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> sessionConfig() {
        return Map.of("jwtExpirationSeconds", jwtExpirationSeconds);
    }

    @Operation(
            summary = "Rafraîchir le JWT courant",
            description = "Génère un nouveau JWT pour l'utilisateur authentifié et le retourne dans " +
                          "l'en-tête Authorization de la réponse. Permet de prolonger la session " +
                          "avant une opération longue ou à la demande de l'utilisateur " +
                          "( bouton \"Rester connecté\" de l'avertissement d'inactivité ). " +
                          "Requiert un token encore valide ; un token expiré ne peut pas se rafraîchir lui-même.",
            tags = {"Authentication"})
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "Bearer Authentication")
    public Map<String, Object> refresh(HttpServletResponse response) {
        UUID userId = OreSiApiRequestContext.getRequestClient().id();
        jwtExtractor.refreshJwtInResponse(response, userId);
        return Map.of("status", "refreshed", "jwtExpirationSeconds", jwtExpirationSeconds);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    @SecurityRequirement(name = "Bearer Authentication")
    public OreSiRequestClient me() {
        return OreSiApiRequestContext.getRequestClient();
    }


    @Tag(name = "Sécurité", description = "Endpoints liés à la sécurité et à l’authentification")
    @Operation(
            summary = "Authentication and JWT retrieval",
            description = "Returns the JWT token if the user is authenticated. If authentication fails, returns NO_TOKEN.",
            tags = {"Authentication"},
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
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "JWT token successfully retrieved",
                    content = @Content(mediaType = "text/plain", schema = @Schema(implementation = String.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication failed"
            )
    })
    @GetMapping(value = "/login", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getCrsf(
            final HttpServletResponse response,
            @RequestParam("login") final String login,
            @RequestParam("password") final String password) {

        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(OreSiAuthenticationToken.class::isInstance)
                .map(OreSiAuthenticationToken.class::cast)
                .map(OreSiAuthenticationToken::getBearerJwt)
                .orElse("NO_TOKEN");
    }

    @Tag(name = "Sécurité", description = "Endpoints liés à la sécurité et à l’authentification")
    @Operation(
            summary = "Connexion utilisateur",
            description = "Authentifie un utilisateur et retourne un BEARER. " +
                          "Ce BEARER est à passer dans tout appel au serveur",
            tags = {"authentication-resources", "Authentication"},
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
    public LoginAdminResult login(final HttpServletRequest request,
                                  final HttpServletResponse response,
                                  @RequestParam("login") final String login,
                                  @RequestParam("password") final String password) {
        LoginAdminResult result = Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .filter(LoginAdminResult.class::isInstance)
                .map(LoginAdminResult.class::cast)
                .orElse(null);
        if (result != null) {
            registerSession(request, response, result);
        }
        return result;
    }

    @Operation(
            summary = "Déconnecter l'utilisateur courant",
            description = """
                    Invalide la session utilisateur et supprime les privilèges d'accès.
                    Efface les en-tête d'authentification le cas échéant.
                    """,
            tags = {"Authentification"})
    @DeleteMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        terminateCurrentUserSessions(SessionInfo.END_LOGOUT);
        return ResponseEntity
                .ok("{\"message\": \"Disconnected\"}");
    }

    /**
     * Cree et enregistre une nouvelle {@link SessionInfo} dans le registry
     * in-memory + l'index par user . La duree de vie effective est
     * {@code now + jwtExpirationSeconds} ; la session passera
     * {@code DISCONNECTED / JWT_EXPIRED} a expiration sans intervention .
     *
     * <p>Best effort : un echec ici ne doit jamais empecher le login .
     */
    private void registerSession(HttpServletRequest request,
                                 jakarta.servlet.http.HttpServletResponse response,
                                 LoginAdminResult result) {
        try {
            java.time.Instant now = java.time.Instant.now();
            UUID userId = result.id();
            String login = result.login();
            String ip    = resolveClientIp(request);
            String ua    = truncate(request.getHeader("User-Agent"), 500);
            // AuthorizationFilter.buildLoginAuthentication a deja appele
            // refreshJwtInResponse plus haut dans la chaine ; le JWT est
            // donc disponible dans le header de la reponse a ce stade .
            // On en extrait un hash SHA-256 stocke dans SessionInfo : le
            // bouton kick admin pourra ainsi blacklister le token sans
            // jamais avoir besoin du JWT brut .
            String jwt = response.getHeader(fr.inra.oresing.rest.security.JWTExtractor.AUTHORIZATION);
            if (jwt != null && jwt.startsWith(fr.inra.oresing.rest.security.JWTExtractor.BEARER_)) {
                jwt = jwt.substring(fr.inra.oresing.rest.security.JWTExtractor.BEARER_.length());
            }
            String tokenHash = fr.inra.oresing.monitoring.session.JwtBlacklistRegistry.hash(jwt);
            SessionInfo session = new SessionInfo(
                    UUID.randomUUID(), userId, login, ip, ua,
                    now,
                    now.plusSeconds(jwtExpirationSeconds),
                    null, null, tokenHash);
            sessionRegistry.start(session);
        } catch (RuntimeException ex) {
            // L'observabilite est best-effort : on log mais on ne casse
            // pas l'API .
            org.slf4j.LoggerFactory.getLogger(AuthenticationResources.class)
                    .warn("registerSession threw : {}", ex.getMessage());
        }
    }

    /**
     * Termine TOUTES les sessions ACTIVE du user authentifie courant
     * avec la {@code reason} fournie ( typiquement
     * {@code SessionInfo.END_LOGOUT} ) . Pour chaque session terminee ,
     * empile l'entry dans le {@link UserSessionLogWriter} pour
     * persistence async dans {@code oa_audit.user_session_log} .
     *
     * <p>Multi-onglets : un logout ferme toutes les sessions ACTIVE de
     * cet utilisateur cote dashboard . Les autres JWT du meme user
     * restent techniquement valides ( JWT stateless ) jusqu'a leur
     * expiration ; leur statut dashboard est aligne sur l'evenement
     * logout pour coherence d'audit .
     */
    private void terminateCurrentUserSessions(String reason) {
        try {
            UUID userId = OreSiApiRequestContext.getRequestClient().id();
            java.time.Instant now = java.time.Instant.now();
            sessionRegistry.listAll(now).stream()
                    .filter(s -> userId.equals(s.userId()))
                    .filter(s -> s.endTime() == null)
                    .forEach(s -> {
                        java.util.Optional<SessionInfo> finished =
                                sessionRegistry.finish(s.sessionId(), reason, now);
                        finished.ifPresent(f -> sessionLogWriter.logAsync(UserSessionLogEntry.fromSession(f)));
                    });
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(AuthenticationResources.class)
                    .warn("terminateCurrentUserSessions threw : {}", ex.getMessage());
        }
    }

    /**
     * Resolve l'IP du client en preferant {@code X-Forwarded-For} ( 1ere
     * valeur de la liste , correspondant au client d'origine derriere les
     * proxies / load-balancers ) , avec fallback sur
     * {@link HttpServletRequest#getRemoteAddr} .
     *
     * <p>Note securite : le proxy nginx du deployement openadom est
     * trusted ; en environnement non-trust on filtrerait par allowlist
     * de proxies connus avant d'accepter le header .
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Operation(
            summary = "Créer un nouvel utilisateur",
            description = "Enregistre un nouvel utilisateur avec identifiant, mot de passe et email",
            tags = {"Utilisateurs"})
    @PreAuthorize("isAnonymous() || isAuthenticated()")
    @PostMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "Bearer Authentication")
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
                case "fr" ->
                        throw new OreSiTechnicalException("Erreur lors de l'envoi de la mise à jour de validation");
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
    @SecurityRequirement(name = "Bearer Authentication")
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
        if (notConnectedUser == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
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
    @SecurityRequirement(name = "Bearer Authentication")
    public OreSiUser getByIdOrLogin(@PathVariable(name = "userLoginOrId") final String userLoginOrId) {
        return authenticationService.getByIdOrLogin(userLoginOrId);
    }
}