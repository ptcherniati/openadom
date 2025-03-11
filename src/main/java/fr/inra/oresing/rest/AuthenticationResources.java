package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
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


    @Operation(
            summary = "Connexion utilisateur",
            description = "Authentifie un utilisateur et retourne un token JWT dans le cookie. Ce cookie est à passer dans tout appel au serveur",
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
