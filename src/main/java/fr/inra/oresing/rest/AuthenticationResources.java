package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForNotConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain.SYSTEM_USER_CONNECTED;

@RestController
@RequestMapping("/api/v1")
public class AuthenticationResources implements ServiceContainerBean {
    private ServiceContainer serviceContainer;

    @Autowired
    protected AuthenticationService authenticationService;

    @Autowired
    private OreSiApiRequestContext request;


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
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentification réussie",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LoginAdminResult.class),
                            examples = @ExampleObject(
                                    name = "ExempleAdmin",
                                    summary = "Réponse typique pour un administrateur OpenAdom",
                                    value = """
                                            {
                                              "id": "35157557-616a-46b8-aee3-487d7450ec23",
                                              "login": "admin_tech",
                                              "email": "admin@example.org",
                                              "state": "active",
                                              "authorizedForApplicationCreation": true,
                                              "openAdomAdmin": true,
                                              "authorizations": ["appl.*", "SI_.*", ".*"],
                                              "chartes": {
                                                "1fd78157-8233-47b0-80bb-f2ffcb6e7b97": "2025-03-11T12:00:00Z",
                                                "20ad5120-3a78-4026-944f-601297aced5f": "2025-03-10T09:30:45Z"
                                              },
                                              "currentUserRoles": {
                                                "applicationRoles": {
                                                  "1fd78157-8233-47b0-80bb-f2ffcb6e7b97": [
                                                    "applicationManager", 
                                                    "dataCurator",
                                                    "userManager"
                                                  ],
                                                  "20ad5120-3a78-4026-944f-601297aced5f": [
                                                    "auditor",
                                                    "reviewer"
                                                  ]
                                                },
                                                "userId": "35157557-616a-46b8-aee3-487d7450ec23",
                                                "userLogin": "admin_tech",
                                                "isOpenAdomAdmin": true,
                                                "isApplicationCreator": true,
                                                "memberOf": [
                                                  "1fd78157-8233-47b0-80bb-f2ffcb6e7b97_applicationManager",
                                                  "20ad5120-3a78-4026-944f-601297aced5f_auditor",
                                                  "openAdomAdmin"
                                                ],
                                                "isDataBaseSuper": false
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Requête invalide",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = LoginAdminResult.class)),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "params": {},
                                              "message": "BAD_LOGIN_PASSWORD",
                                              "localizedMessage": "BAD_LOGIN_PASSWORD"
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginAdminResult login(final HttpServletResponse response, @RequestParam("login") final String login, @RequestParam("password") final String password) throws Throwable {
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
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Déconnexion réussie",
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "Réponse vide",
                                    value = "{}"
                            ))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Non authentifié (si la protection est activée)")
    })
    @DeleteMapping("/logout")
    public void logout(HttpServletResponse response) {
        // repone envoyer par AuthorizationFilter
    }

    @Operation(
            summary = "Créer un nouvel utilisateur",
            description = "Enregistre un nouvel utilisateur avec identifiant, mot de passe et email",
            tags = {"Utilisateurs"})
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Utilisateur créé avec succès",
                    content = @Content(
                            schema = @Schema(
                                    type = "object",
                                    example = """
                                            { "userId": "550e8400-e29b-41d4-a716-446655440000" }"""))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Paramètres invalides ou manquants"),
            @ApiResponse(
                    responseCode = "409",
                    description = "L'utilisateur existe déjà")
    })
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
            authenticationService.sendEmailValidation(login, password);
        } catch (final AuthenticationFailure e) {
            switch (OreSiResources.getDefaultLocale().getLanguage()) {
                case "fr" -> throw new RuntimeException("Erreur lors de l'envoi de la mise à jour de validation");
                case "en" -> throw new RuntimeException("Error sending validation update");
                case null, default -> throw new RuntimeException("Error sending validation update");
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
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Utilisateur mis à jour avec succès",
                    content = @Content(
                            schema = @Schema(implementation = CreateUserResult.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Activation de compte",
                                            value = """ 
                                                    { 
                                                        "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                                                        "login": "user123", 
                                                        "email": "user@example.com",
                                                        "accountState": "active",
                                                        "chartes": { "key": "2025-03-11T16:27:00.000Z" }
                                                    }"""),
                                    @ExampleObject(
                                            name = "Changement d'email",
                                            value = """
                                                    { "accountState": "pending" }""")
                            })),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (champs manquants ou validation échouée)"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Échec d'authentification ou clé de vérification invalide")
    })
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
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Utilisateur trouvé",
                    content = @Content(
                            schema = @Schema(implementation = OreSiUser.class),
                            examples = @ExampleObject(
                                    name = "Réponse standard",
                                    value = """
                                            {
                                                "id": "550e8400-e29b-41d4-a716-446655440000",
                                                "login": "jdupont",
                                                "email": "j.dupont@inrae.fr",
                                                "authorizations": ["READ_DATA","WRITE_CONFIG"],
                                                "accountstate": "active",
                                                "chartes": {
                                                    "RGPD": "2025-03-11T16:27:00.000Z"
                                                }
                                            }"""
                            ))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur introuvable"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Format d'ID invalide")
    })
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_USER_READER')")
    @GetMapping(value = "/users/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OreSiUser getByIdOrLogin(@PathVariable(name = "userLoginOrId") final String userLoginOrId) {
        return authenticationService.getByIdOrLogin(userLoginOrId);
    }

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }
}
