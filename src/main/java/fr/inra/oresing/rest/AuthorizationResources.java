package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiRoleForUser;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.additionalfiles.AuthorizationsAdditionalFilesResult;
import fr.inra.oresing.domain.additionalfiles.OreSiAdditionalFileAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.PrivilegeAssessorDomainForSystem;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationAdminUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.model.authorization.*;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.services.AuthorizationService;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationResources implements ServiceContainerBean {

    @Autowired
    private HealthEndpoint healthEndpoint;

    private ServiceContainer serviceContainer;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OreSiApiRequestContext request;

    @Autowired
    private OreSiRepository repo;


    @GetMapping(value = "/authorizationForAdmin", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<LoginAdminResult> getAdminAuthorizationsForOpenAdom() {
        return serviceContainer.authenticationService().getAdminAuthorizations();
    }

    @GetMapping(value = "/{nameOrId}/authorizationAdminForApplication", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<LoginApplicationResult> getAdminAuthorizationsForApplication(@PathVariable("nameOrId") final String applicationNameOrId) {
        Application application = serviceContainer.applicationService().getApplication(applicationNameOrId);
        return serviceContainer.authenticationService().getApplicationAuthorizations(application);
    }

    @Operation(
            summary = "Créer une nouvelle autorisation",
            description = "Crée une nouvelle autorisation avec des permissions spécifiques pour différents types de données."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Détails de l'autorisation à créer ou à mettre à jour",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreateAuthorizationRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "Autorisation complexe",
                                    summary = "Exemple d'autorisation avec permissions générales et restrictions spécifiques",
                                    value = """
                                            {
                                              "uuid": null,
                                              "name": "atlantique extraction nivelle p1",
                                              "description": "une extraction sur nivelle",
                                              "authorizationForAll": {
                                                "sites": ["depot", "suppression"]
                                              },
                                              "authorizationsWithRestriction": {
                                                "pem": {
                                                  "operationTypes": ["extraction"],
                                                  "requiredAuthorizations": {
                                                    "projet": [
                                                      "projet_manche",
                                                      "projet_atlantique"
                                                    ],
                                                    "sites": [
                                                      "oir__p1",
                                                      "nivelle"
                                                    ]
                                                  },
                                                  "timeScope": {
                                                    "fromDay": "1984-01-02"
                                                  }
                                                }
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Autorisation dépôt sur sites",
                                    summary = "Autorisation pour le droit de dépôt sur sites",
                                    value = """
                                            {
                                              "uuid": null,
                                              "name": "Dépôt sur sites",
                                              "description": "Autorisation pour le dépôt sur tous les sites",
                                              "authorizationForAll": {
                                                "sites": ["depot"]
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Autorisation extraction PEM",
                                    summary = "Autorisation pour le droit d'extraction sur PEM",
                                    value = """
                                            {
                                              "uuid": null,
                                              "name": "Extraction PEM",
                                              "description": "Autorisation pour l'extraction des données PEM",
                                              "authorizationsWithRestriction": {
                                                "pem": {
                                                  "operationTypes": ["extraction"]
                                                }
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Autorisation extraction PEM avec période spécifique",
                                    summary = "Autorisation pour l'extraction des données PEM entre deux dates spécifiques",
                                    value = """
                                            {
                                              "uuid": null,
                                              "name": "Extraction PEM période spécifique",
                                              "description": "Extraction PEM du 01/01/1984 au 02/01/1984",
                                              "authorizationsWithRestriction": {
                                                "pem": {
                                                  "operationTypes": ["extraction"],
                                                  "timeScope": {
                                                    "fromDay": "1984-01-01",
                                                    "toDay": "1984-01-02"
                                                  }
                                                }
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Autorisation PEM bassin versant Nivelle",
                                    summary = "Autorisation pour les données PEM du bassin versant de Nivelle",
                                    value = """
                                            {
                                              "uuid": null,
                                              "name": "PEM Nivelle",
                                              "description": "Autorisation pour les données PEM du bassin versant de Nivelle",
                                              "authorizationsWithRestriction": {
                                                "pem": {
                                                  "operationTypes": ["read", "extraction"],
                                                  "requiredAuthorizations": {
                                                    "sites": ["nivelle"]
                                                  }
                                                }
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "Mise à jour d'une autorisation existante",
                                    summary = "Exemple de mise à jour d'une autorisation existante",
                                    value = """
                                            {
                                              "uuid": "b4a865cb-12a0-4688-b9d6-1385a65c52c1",
                                              "name": "Mise à jour autorisation",
                                              "description": "Mise à jour d'une autorisation existante",
                                              "authorizationForAll": {
                                                "sites": ["extraction"]
                                              },
                                              "authorizationsWithRestriction": {
                                                "pem": {
                                                  "operationTypes": ["depot"],
                                                  "requiredAuthorizations": {
                                                    "projet": ["projet_atlantique"]
                                                  }
                                                }
                                              }
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Autorisation créée ou mise à jour avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Réponse standard",
                                            value = """
                                                    { "message": "Autorisation créée avec succès", "id": "b4a865cb-12a0-4688-b9d6-1385a65c52c1" }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Application non trouvée")
    })
    @Parameters({
            @Parameter(name = "nameOrId", description = "Nom ou ID de l'application", required = true)
    })
    @PostMapping(value = "/applications/{nameOrId}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addAuthorization(
            @PathVariable(name = "nameOrId") final String nameOrId,
            @RequestBody final CreateAuthorizationRequest createAuthorizationRequest) {
        Application application = repo.application().findApplication(nameOrId);
        serviceContainer.authorizationService().getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.AUTHORIZATION_MANAGEMENT, application)
                .forAddAuthorization();
        List<AuthorizationRequestError> errors = new ArrayList<>();
        CreateAuthorizationRequest createAuthorizationRequestWithDependantAuthorization = serviceContainer.authorizationService()
                .createAuthorizationRequestWithDependantAuthorization(application, createAuthorizationRequest);
        serviceContainer.authorizationService().createAuthorizationRequestWithDependantAuthorization(application, createAuthorizationRequest);
        CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        List<UUID> userIds = userRepository.findAll().stream().map(OreSiUser::getId).toList();
        boolean isApplicationCreator = rolesForCurrentUser.memberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        final List<OreSiAuthorization> authorizationsForCurrentUser = serviceContainer.authorizationService().findUserAuthorizationsForApplication(application);
        AuthorizationRequest authorizationRequest = serviceContainer.authorizationService().createAuthorizationRequestToAuthorizationRequest(
                createAuthorizationRequestWithDependantAuthorization,
                application,
                userIds,
                authorizationsForCurrentUser,
                errors
        );
        if (!errors.isEmpty()) {
            final String uri = UriUtils.encodePath("/applications/authorization/null", Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", "null"));

        }
        final AuthorizationService.Authorizations oreSiAuthorizations = serviceContainer.authorizationService().addAuthorization(
                application,
                authorizationRequest,
                authorizationsForCurrentUser,
                isApplicationCreator);
        OreSiAuthorization oreSiAuthorization = oreSiAuthorizations.next();
        final UUID authId = oreSiAuthorization.getId();
        if (createAuthorizationRequest.uuid() == null) {
            final OreSiRightOnApplicationRole roleForAuthorization = serviceContainer.authorizationService().createRoleForAuthorization(authorizationRequest, oreSiAuthorization);
        }
        serviceContainer.authorizationService().updateRoleForManagement(oreSiAuthorizations.getPreviousUsers(), oreSiAuthorization);
        final String uri = UriUtils.encodePath("/applications/authorization/" + authId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResult> getAuthorizationById(
            @PathVariable("nameOrId") final String applicationNameOrId,
            @PathVariable("authorizationId") final UUID authorizationId) {
        AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        Application application = serviceContainer.authorizationService().getApplication(applicationNameOrId);
        final GetAuthorizationResult getAuthorizationResult = serviceContainer.authorizationService().getAuthorization(
                new AuthorizationRequest(
                        authorizationId,
                        "",
                        "",
                        application.getId(),
                        Set.of(),
                        null,
                        null
                ),
                authorizationsForUser);
        return ResponseEntity.ok(getAuthorizationResult);
    }

    @GetMapping(value = "/applications/{nameOrId}/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationResults> getAdminAuthorizationsForOpenAdom(@PathVariable("nameOrId") final String applicationNameOrId) {
        AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        final ImmutableSet<GetAuthorizationResult> getAuthorizationResults = serviceContainer.authorizationService().getAuthorizations(applicationNameOrId, authorizationsForUser);
        GetAuthorizationResults getAuthorizationResultsWithOwnRights1 = new GetAuthorizationResults(getAuthorizationResults, authorizationsForUser);
        return ResponseEntity.ok(getAuthorizationResultsWithOwnRights1);
    }

    @GetMapping(value = "/applications/{applicationNameOrId}/authorization/user/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationsResult getAuthorizationsForUser(@PathVariable(name = "applicationNameOrId") final String applicationNameOrId, @PathVariable(name = "userLoginOrId") final String userLoginOrId) {
        return serviceContainer.authorizationService().getAuthorizationsForUserAndPublic(applicationNameOrId, userLoginOrId);
    }

    @DeleteMapping(value = "/applications/{nameOrId}/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> revokeAuthorization(
            @PathVariable("nameOrId") final String applicationNameOrId,
            @PathVariable("authorizationId") final UUID authorizationId) {
        Application application = serviceContainer.authorizationService().getApplication(applicationNameOrId);
        ApplicationAdminUser applicationAdminUser = serviceContainer.authorizationService().getPrivilegeAssessorForApplication(
                        PrivilegeApplicationDomain.AUTHORIZATION_MANAGEMENT,
                        application
                )
                .forDeleteAuthorization();
        UUID revokeId = serviceContainer.authorizationService().revoke(
                applicationAdminUser,
                applicationNameOrId,
                new AuthorizationRequest(
                        authorizationId,
                        "",
                        "",
                        application.getId(),
                        Set.of(),
                        null,
                        null));
        return ResponseEntity.ok(revokeId);
    }

    @GetMapping(value = "/applications/{applicationNameOrId}/additionalFiles/authorization/{userLoginOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthorizationsAdditionalFilesResult getAdditionalFilesAuthorizationsForUser(@PathVariable(name = "applicationNameOrId") final String applicationNameOrId, @PathVariable(name = "userLoginOrId", required = false) String userLoginOrId) {
        String userLoginOrId1 = userLoginOrId == null || "null".equals(userLoginOrId) ? request.getRequestUserId().toString() : userLoginOrId;
        return serviceContainer.authorizationService().getAdditionalFilesAuthorizationsForUser(applicationNameOrId, userLoginOrId1);
    }


    @DeleteMapping(value = "/applications/{applicationNameOrId}/additionalFiles/authorization/{authorizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> revokeAdditionalFilesAuthorization(
            @PathVariable("applicationNameOrId") final String applicationNameOrId,
            @PathVariable("authorizationId") final String authorizationId) {
        UUID revokeId = serviceContainer.authorizationService().revokeAdditionalFiles(applicationNameOrId, UUID.fromString(authorizationId));
        return ResponseEntity.ok(revokeId.toString());
    }

    @PostMapping(value = "/applications/{nameOrId}/additionalFiles/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> addAdditionalFileAuthorization(@PathVariable(name = "nameOrId") final String nameOrId,
                                                                              @RequestBody final CreateAdditionalFileAuthorizationRequest authorization) {
        CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        Application application = repo.application().findApplication(nameOrId);
        boolean isApplicationCreator = rolesForCurrentUser.memberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        final List<OreSiAdditionalFileAuthorization> additionalFilesAuthorizationsForCurrentUser = serviceContainer.authorizationService().findUserAdditionalFilesAuthorizationsForApplicationAndDataType(application);
        if (!isApplicationCreator) {
            //TODO rights definition for additionnals
            //throw new NotApplicationCanManageReferenceRightsException(application.getName());
        }
        final Set<UUID> previousUsers = authorization.getUuid() == null ? new HashSet<>() : authorization.getUsersId();
        final OreSiAdditionalFileAuthorization oreSiAuthorization = serviceContainer.authorizationService().addAdditionalFileAuthorizations(application, authorization, additionalFilesAuthorizationsForCurrentUser, true);
        final UUID authId = oreSiAuthorization.getId();
        if (authorization.getUuid() == null) {
            OreSiRightOnApplicationRole roleForAuthorization = serviceContainer.authorizationService().createRoleForAuthorization(authorization, oreSiAuthorization);
        }
        serviceContainer.authorizationService().updateRoleForReferenceManagement(previousUsers, oreSiAuthorization);
        final String uri = UriUtils.encodePath("/applications/" + authorization.getApplicationNameOrId() + "/additionalFiles/authorization/" + authId.toString(), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("authorizationId", authId.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/additionalfiles/authorization", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAuthorizationAdditionalFilesResults> getAdditionalFilesAuthorizations(
            @PathVariable("nameOrId") final String applicationNameOrId,
            @RequestParam final MultiValueMap<String, String> params
    ) {
        AuthorizationsAdditionalFilesResult authorizationsForUser = getAdditionalFilesAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        final ImmutableSet<GetAuthorizationAdditionalFilesResult> getAuthorizationResults = serviceContainer.authorizationService().getAdditionalFilesuthorizations(applicationNameOrId, authorizationsForUser, params);
        final Set<GetGrantableResult.User> users = serviceContainer.authorizationService().getGrantableUsers()
                .stream()
                .filter(user -> !"_public_".equals(user.label()))
                .collect(Collectors.toSet());

        GetAuthorizationAdditionalFilesResults getAuthorizationResultsWithOwnRights1 = new GetAuthorizationAdditionalFilesResults(getAuthorizationResults, authorizationsForUser, users);
        return ResponseEntity.ok(getAuthorizationResultsWithOwnRights1);
    }


    @PutMapping(value = "/authorization/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add an authorization for a user",
            description = "This service allows adding a specific authorization for a given user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authorization successfully added"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "User or role not found")
    })
    public ResponseEntity<OreSiUser> addAuthorization(
            @Parameter(description = "The role to add", required = true,
                    examples = {
                            @ExampleObject(name = "openAdomAdmin", value = "openAdomAdmin", description = "OpenAdom administrator role"),
                            @ExampleObject(name = "applicationCreator", value = "applicationCreator", description = "Application creator role"),
                            @ExampleObject(name = "applicationManager", value = "applicationManager", description = "Application manager role"),
                            @ExampleObject(name = "userManager", value = "userManager", description = "User manager role")
                    }
            ) @PathVariable(name = "role") final String role,

            @Parameter(description = "The user's ID or login", required = true,
                    examples = {
                            @ExampleObject(name = "userId", value = "user123", description = "User ID"),
                            @ExampleObject(name = "userLogin", value = "john.doe", description = "User login")
                    }
            ) @RequestParam(name = "userIdOrLogin") final String userIdOrLogin,

            @Parameter(description = "The application name or ID (if applicable) for grant of applicationManager et userManager of the application",
                    examples = {
                            @ExampleObject(name = "applicationName", value = "SI_123", description = "Application name"),
                            @ExampleObject(name = "applicationId", value = "app-456", description = "Application ID")
                    }
            ) @RequestParam(name = "applicationNameOrId", required = false) final String applicationNameOrId,

            @Parameter(description = "The application pattern (if applicable) for grant of rôle applicationCreator",
                    examples = {
                            @ExampleObject(name = "applicationPattern", value = "SI_*", description = "Pattern for SI applications")
                    }
            ) @RequestParam(name = "applicationPattern", required = false) final List<String> applicationPattern
    ) throws JsonProcessingException {
        OreSiUser user = serviceContainer.authenticationService().getByIdOrLogin(userIdOrLogin);
        OreSiRoleForUser roleForUser = new OreSiRoleForUser(user.getId().toString(), role, "");
        if (Strings.isNullOrEmpty(applicationNameOrId)) {
            serviceContainer.authorizationService().getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_ADMINISTRATION)
                    .forAdministrationManagement()
                    .canManagerRightForRole(roleForUser);
            if (!CollectionUtils.isEmpty(applicationPattern)) {
                user.getAuthorizations().addAll(applicationPattern);
                userRepository.update(user);
                user = serviceContainer.authorizationService().addSystemRoleUser(roleForUser);
            }
        } else {
            Application application = serviceContainer.applicationService().getApplication(applicationNameOrId);
            serviceContainer.authorizationService().getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.APPLICATION_MANAGER, application)
                    .forManageAdministrator()
                    .canManagerRightOfUserForRole(user, roleForUser);
            user = serviceContainer.authorizationService().addApplicationRoleUser(roleForUser, application);
        }
        return ResponseEntity.ok(user);
    }

    @DeleteMapping(value = "/authorization/{role}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Remove an authorization for a user",
            description = "This service allows removing a specific authorization for a given user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authorization successfully removed"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "User or role not found")
    })
    public ResponseEntity<OreSiUser> deleteAuthorization(
            @Parameter(description = "The role to remove", required = true,
                    examples = {
                            @ExampleObject(name = "openAdomAdmin", value = "openAdomAdmin", description = "Remove OpenAdom administrator role"),
                            @ExampleObject(name = "applicationCreator", value = "applicationCreator", description = "Remove application creator role"),
                            @ExampleObject(name = "applicationManager", value = "applicationManager", description = "Remove application manager role"),
                            @ExampleObject(name = "userManager", value = "userManager", description = "Remove user manager role")
                    }
            ) @PathVariable(name = "role") final String role,

            @Parameter(description = "The user's ID or login", required = true,
                    examples = {
                            @ExampleObject(name = "userId", value = "user123", description = "User ID"),
                            @ExampleObject(name = "userLogin", value = "john.doe", description = "User login")
                    }
            ) @RequestParam(name = "userIdOrLogin") final String userIdOrLogin,

            @Parameter(description = "The application name or ID (if applicable) for revoke of applicationManager et userManager of the application",
                    examples = {
                            @ExampleObject(name = "applicationName", value = "SI_123", description = "Application name"),
                            @ExampleObject(name = "applicationId", value = "app-456", description = "Application ID")
                    }
            ) @RequestParam(name = "applicationNameOrId", required = false) final String applicationNameOrId,

            @Parameter(description = "The application pattern (if applicable) for revoke of an applicationCreator",
                    examples = {
                            @ExampleObject(name = "applicationPattern", value = "SI_*", description = "Pattern for SI applications")
                    }
            ) @RequestParam(name = "applicationPattern", required = false) final List<String> applicationPattern
    ) throws JsonProcessingException {
        OreSiUser user = serviceContainer.authenticationService().getByIdOrLogin(userIdOrLogin);
        OreSiRoleForUser roleForUser = new OreSiRoleForUser(user.getId().toString(), role, "");
        if (Strings.isNullOrEmpty(applicationNameOrId)) {
            serviceContainer.authorizationService().getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_ADMINISTRATION)
                    .forAdministrationManagement()
                    .canManagerRightForRole(roleForUser);
            if (!CollectionUtils.isEmpty(applicationPattern)) {
                applicationPattern.forEach(user.getAuthorizations()::remove);
                user = userRepository.update(user);
            }
            if (user.getAuthorizations().isEmpty()) {
                user = serviceContainer.authorizationService().deleteSystemRoleUser(roleForUser);
            }
        } else {
            Application application = serviceContainer.applicationService().getApplication(applicationNameOrId);
            serviceContainer.authorizationService().getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.APPLICATION_MANAGER, application)
                    .forManageAdministrator()
                    .canManagerRightOfUserForRole(user, roleForUser);
            user = serviceContainer.authorizationService().deleteApplicationRoleUser(
                    roleForUser,
                    application
            );
        }
        return ResponseEntity.ok(user);
    }

    @GetMapping(value = "/applications/{nameOrId}/grantable", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetGrantableResult> getGrantable(@PathVariable("nameOrId") final String applicationNameOrId) {
        AuthorizationsResult authorizationsForUser = getAuthorizationsForUser(applicationNameOrId, request.getRequestUserId().toString());
        final GetGrantableResult getGrantableResult = serviceContainer.authorizationService().getGrantable(applicationNameOrId, authorizationsForUser);
        return ResponseEntity.ok(getGrantableResult);
    }

    record Health(ConnectedUser connectedUser, HealthComponent health){};
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Health> getStatus() {
        ConnectedUser connectedUser = serviceContainer.authorizationService().getPrivilegeAssessorForSystem(PrivilegeSystemDomain.SYSTEM_USER_CONNECTED)
                .connectedUser();
        HealthComponent health = healthEndpoint.health();
        return ResponseEntity.ok().body(new Health(connectedUser,health));
    }

    public void setServiceContainer(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }
}