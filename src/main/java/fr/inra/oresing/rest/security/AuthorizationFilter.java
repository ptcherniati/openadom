package fr.inra.oresing.rest.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.data.publication.AuthorizationPublicationService;
import fr.inra.oresing.rest.data.publication.StoreFile;
import fr.inra.oresing.rest.exceptions.OreExceptionHandler;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AuthorizationFilter extends GenericFilterBean {
    public static final GrantedAuthority ROLE_AUTHENTIFIED_USER = new SimpleGrantedAuthority("ROLE_AUTHENTIFIED_USER");
    public static final GrantedAuthority ROLE_UNAUTHENTIFIED_UPDATE_USER = new SimpleGrantedAuthority("ROLE_UNAUTHENTIFIED_UPDATE_USER");
    public static final GrantedAuthority ROLE_UNAUTHENTIFIED_CREATE_USER = new SimpleGrantedAuthority("ROLE_UNAUTHENTIFIED_CREATE_USER");
    public static final String APPLICATIONS = "applications";
    public static final String DATA = "data";
    public static final String SYNTHESIS = "synthesis";
    public static final String FILES_ON_REPOSITORY = "filesOnRepository";
    public static final String PARAMS = "params";
    public static final String JS_UNDEFINED = "undefined";
    public static final String LOGIN_PARAMETER = "login";
    public static final String PASSWORD_PARAMETER = "password";
    private static final String AUTHORIZATION_ALREADY_DONE = "AUTHORIZATION_ALREADY_DONE";
    public static final String BAD_LOGIN_PASSWORD = "BAD_LOGIN_PASSWORD";
    private final JsonRowMapper<?> mapper;
    private final OreExceptionHandler exceptionHandler;
    private final JWTExtractor jWTExtractor;
    private final ServiceContainer serviceContainer;

    /**
     * Repository utilisé pour persister le {@link SecurityContext} sous
     * forme d'attribut de requête. Doit être de la même classe que celui
     * configuré dans {@link SecurityConfig} ( cf.
     * {@code .securityContextRepository(new RequestAttributeSecurityContextRepository())} ).
     *
     * <p><b>Pourquoi</b> : sans ça , l'authentification posée par ce filtre
     * dans le {@code SecurityContextHolder} ( ThreadLocal ) n'est pas
     * persistée pour l'{@code ASYNC} dispatch des contrôleurs renvoyant
     * un {@code Flux<>}. Le {@link org.springframework.security.web.context.SecurityContextHolderFilter}
     * de Spring Security re-tourne au dispatch ASYNC , recharge depuis
     * le repository , trouve un contexte vide , et l'{@code AnonymousAuthenticationFilter}
     * marque la requête anonyme → 403 → /error → 500 ( "Failed to write
     * request" parce que le {@code Content-Type: application/x-ndjson}
     * du Flux est déjà fixé et Spring n'a pas de converter NDJSON pour
     * les ProblemDetail ).
     */
    private final RequestAttributeSecurityContextRepository securityContextRepository =
            new RequestAttributeSecurityContextRepository();

    private final fr.inra.oresing.monitoring.session.JwtBlacklistRegistry jwtBlacklist;

    @Autowired
    public AuthorizationFilter(
            ServiceContainer serviceContainer,
            JsonRowMapper<?> jsonRowMapper,
            JWTExtractor jWTExtractor,
            OreExceptionHandler exceptionHandler,
            fr.inra.oresing.monitoring.session.JwtBlacklistRegistry jwtBlacklist) {
        this.exceptionHandler = exceptionHandler;
        this.mapper = jsonRowMapper;
        this.jWTExtractor = jWTExtractor;
        this.serviceContainer = serviceContainer;
        this.jwtBlacklist = jwtBlacklist;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI();
        OreSiAuthenticationToken authenticationToken = OreSiApiRequestContext.getAuthenticationToken();
        if (authenticationToken != null) {
            // #62 - Idempotent : si une passe précédente a déjà rempli le
            // SecurityContextHolder mais n'a pas pu sauver dans le
            // request-attribute repository , on rattrape ici pour que
            // l'ASYNC dispatch retrouve l'authentification.
            saveSecurityContextToRequest(request, response);
            chain.doFilter(request, response);
            return;
        }
        if (response.isCommitted()) {
            return;
        }
        if (path.endsWith("/logout")) {
            // Sprint Sessions / monitoring : on tente l'authentification
            // AVANT de cleaner la session afin que
            // OreSiApiRequestContext.getRequestClient() soit disponible
            // dans AuthenticationResources.logout() ; sans ca le registry
            // de sessions ne pourrait pas marquer la session comme
            // DISCONNECTED ( bug : dashboard montrait toujours l'user en
            // ACTIVE apres logout ) .
            //
            // Logout reste idempotent : si le JWT est absent / invalide /
            // expire , l'auth echoue silencieusement et on poursuit avec
            // clearSession + chain.doFilter -> le controller renvoie OK
            // ( comportement historique ) .
            try {
                OreSiAuthenticationToken token = buildAuthentication(request, response);
                if (token != null) {
                    OreSiApiRequestContext.setAuthenticationToken(token);
                    saveSecurityContextToRequest(request, response);
                }
            } catch (Exception ignored) {
                /* logout idempotent : on continue meme sans auth valide */
            }
            jWTExtractor.clearSession(request, response);
            chain.doFilter(request, response);
            return;
        }
        if (
                path.equals("/") ||
                path.startsWith(SecurityConfig.ADMIN) ||
                path.startsWith(SecurityConfig.POOLS) ||
                path.startsWith(SecurityConfig.UPLOAD) ||
                path.startsWith(SecurityConfig.STATUS) ||
                path.startsWith(SecurityConfig.ACTUATOR) ||
                path.startsWith(SecurityConfig.SWAGGER_UI) ||
                path.startsWith(SecurityConfig.API_DOCS) ||
                path.startsWith(SecurityConfig.API_PUBLIC) ||
                path.startsWith(SecurityConfig.API_DOCS_YAML) ||
                // #470 - Endpoint anonyme exposant la configuration de session ;
                // appelé par le frontend à l'init ( avant tout login ) , donc
                // sans token. Sans ce skip , le filtre tente de parser un
                // "Bearer null" envoyé par le Fetcher et répond 401 ( traité
                // alors par le listener "disconnected" comme une perte de
                // session , d'où la boucle /login?returnUrl=/login ).
                path.equals(SecurityConfig.API_V_1_SESSION_CONFIG) ||
                path.equals(SecurityConfig.ERROR)) {
            chain.doFilter(request, response); // Skip le filtre
            return;
        }
        if (request.getAttribute(AUTHORIZATION_ALREADY_DONE) != null) {
            chain.doFilter(request, response);
        }
        request.setAttribute(AUTHORIZATION_ALREADY_DONE, true);
        try {
            OreSiAuthenticationToken token = buildAuthentication(request, response);
            OreSiApiRequestContext.setAuthenticationToken(token);
            // #62 - Persiste le SecurityContext en tant qu'attribut de requête
            // pour que l'ASYNC dispatch ( contrôleurs Flux<>) retrouve
            // l'authentification quand le SecurityContextHolderFilter
            // re-charge depuis le repository ; sinon : anonymous -> 403 -> 500.
            saveSecurityContextToRequest(request, response);
        } catch (AuthenticationFailure e) {
            ResponseEntity<String> handle = exceptionHandler.handle(e);
            response.setStatus(handle.getStatusCode().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String body = mapper.toJson(handle.getBody());
            response.getWriter().write(body);
            response.getWriter().flush();
            return;
        } catch (BadCredentialsException | AuthenticationCredentialsNotFoundException e) {
            // #470 - Les exceptions JWT levées par le filtre n'atteignent
            // pas @ExceptionHandler ( applicable uniquement aux controllers ) ;
            // on écrit la réponse 401 directement ici pour renvoyer au
            // frontend le contrat stable { code , message } au lieu du
            // 500 par défaut Spring.
            writeJsonAuthError(response, e);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Sauvegarde le {@link SecurityContext} courant ( fraîchement rempli
     * par {@link OreSiApiRequestContext#setAuthenticationToken} ) dans
     * un attribut de la requête , via le {@link RequestAttributeSecurityContextRepository}.
     *
     * <p>Indispensable pour que l'authentification survive à l'{@code ASYNC}
     * dispatch des contrôleurs renvoyant un {@code Flux<>} ; sans ça , le
     * {@link org.springframework.security.web.context.SecurityContextHolderFilter}
     * recharge un contexte vide depuis le repository pendant l'async
     * dispatch , l'{@link org.springframework.security.web.authentication.AnonymousAuthenticationFilter}
     * marque la requête anonyme , et la chaîne tombe en 403 puis 500.
     */
    private void saveSecurityContextToRequest(HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null && context.getAuthentication() != null) {
            securityContextRepository.saveContext(context, request, response);
        }
    }

    private void writeJsonAuthError(HttpServletResponse response, AuthenticationException ex) throws IOException {
        String code;
        if (ex.getCause() instanceof io.jsonwebtoken.ExpiredJwtException) {
            code = "TOKEN_EXPIRED";
        } else if (ex.getMessage() != null && ex.getMessage().contains("revoked by admin")) {
            code = "TOKEN_REVOKED";
        } else {
            code = "TOKEN_INVALID";
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage().replace("\"", "\\\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format("{\"code\":\"%s\",\"message\":\"%s\"}", code, message));
        response.getWriter().flush();
    }


    private OreSiAuthenticationToken buildAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationFailure, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.OPTIONS.name().equals(method)) {
            return null;
        }
        if (List.of(HttpMethod.POST.name(), HttpMethod.GET.name()).contains(method) && path.endsWith(SecurityConfig.LOGIN)) {
            return buildLoginAuthentication(request, response);
        }
        if (HttpMethod.POST.name().equals(method) && path.endsWith(SecurityConfig.USERS)) {
            return buildCreateUserAuthentication();
        }
        if (HttpMethod.PUT.name().equals(method) && path.endsWith(SecurityConfig.USERS)) {
            return buildUpdateUserAuthentication(request);
        }

        OreSiAuthenticationToken oreSiAuthenticationToken = handleJwtAuthentication(request);
        OreSiApiRequestContext.setAuthenticationToken(oreSiAuthenticationToken); //premier stockage pour certaines méthodes
        if (oreSiAuthenticationToken == null) {
            return null;
        }
        Optional.ofNullable(path)
                .map(p -> p.split("/"))
                .map(Arrays::asList)
                .filter(list -> list.size() > 4 && APPLICATIONS.equals(list.get(3)))
                .map(list -> list.get(4))
                .ifPresent(oreSiAuthenticationToken::setApplicationName);
        Optional.ofNullable(path)
                .map(p -> p.split("/"))
                .map(Arrays::asList)
                .filter(list -> list.size() > 3 && APPLICATIONS.equals(list.get(3)))
                .filter(list -> list.size() > 6 && List.of(DATA, SYNTHESIS, FILES_ON_REPOSITORY).contains(list.get(5)))
                .map(list -> list.get(6))
                .or(() -> getWithFileId(oreSiAuthenticationToken, path))
                .ifPresent(dataName -> {
                    oreSiAuthenticationToken.setDataName(dataName);
                    addFileOrUUID(request, oreSiAuthenticationToken, dataName, path);
                    oreSiAuthenticationToken.setDataName(dataName);
                });

        return oreSiAuthenticationToken;
    }

    private Optional<String> getWithFileId(OreSiAuthenticationToken oreSiAuthenticationToken, String path) {
        Pattern pattern = Pattern
                .compile("/api/v1/applications/(%s)/file/(.*)".formatted(oreSiAuthenticationToken.getApplicationName()));
        final Optional<UUID> optionalUUID = Optional.ofNullable(path)
                .map(pattern::matcher)
                .map(m -> m.matches() ? m.group(2) : null)
                .map(UUID::fromString);
        final Optional<String> dataNameOpt = Optional.ofNullable(path)
                .map(pattern::matcher)
                .map(m -> m.matches() ? m.group(1) : null);
        try {
            return optionalUUID
                    .map(fileId -> extractFileOrUUIDAndFindDataName(fileId, oreSiAuthenticationToken));
        } catch (IllegalArgumentException e) {
            extractBinaryFileAndFindDataName(oreSiAuthenticationToken, e, optionalUUID, dataNameOpt);
            return Optional.empty();
        }
    }

    private void extractBinaryFileAndFindDataName(OreSiAuthenticationToken oreSiAuthenticationToken, IllegalArgumentException e, Optional<UUID> optionalUUID, Optional<String> dataNameOpt) {
        if (AuthorizationPublicationService.DATA_NAME_NOT_FOUND.equals(e.getMessage())) {
            optionalUUID
                    .flatMap(fileId -> serviceContainer.binaryFileService().getFile(oreSiAuthenticationToken.getApplicationName(), fileId))
                    .ifPresent(binaryFile -> {
                        oreSiAuthenticationToken.setBinaryFile(binaryFile);
                        dataNameOpt
                                .ifPresent(oreSiAuthenticationToken::setDataName);

                    });
        }
    }

    private String extractFileOrUUIDAndFindDataName(UUID fileId, OreSiAuthenticationToken oreSiAuthenticationToken) {
        Application applicationOrApplicationAccordingToRights = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(oreSiAuthenticationToken.getApplicationName());
        return Optional.ofNullable(serviceContainer.versioningService()
                        .getStoreFile(applicationOrApplicationAccordingToRights,
                                null,
                                FileOrUUID.forUUID(fileId),
                                null,
                                null))
                .map(oreSiAuthenticationToken::setStoreFile)
                .map(StoreFile::builder)
                .map(AuthorizationPublicationService::getDataName)
                .orElse(null);
    }

    private void addFileOrUUID(HttpServletRequest request, OreSiAuthenticationToken oreSiAuthenticationToken, String dataName, String path) {
        if (HttpMethod.POST.name().equals(request.getMethod()) && "/api/v1/applications/%1$s/data/%2$s".formatted(oreSiAuthenticationToken.getApplicationName(), dataName).equals(path)) {
            String params = request.getParameter(PARAMS);
            Optional.ofNullable(params)
                    .filter(Predicate.not(JS_UNDEFINED::equals))
                    .map(json -> {
                        try {
                            return mapper.getJsonMapper().readValue(json, FileOrUUID.class);
                        } catch (JsonProcessingException e) {
                            throw new BadFileOrUUIDQuery(e.getMessage());
                        }
                    })
                    .ifPresent(oreSiAuthenticationToken::setFileOrUUID);
            return;
        }
        Matcher matcher = Pattern.compile("/api/v1/applications/%s/file/(.*)".formatted(oreSiAuthenticationToken.getApplicationName())).matcher(path);
        if (HttpMethod.DELETE.name().equals(request.getMethod()) && matcher.matches()) {
            UUID fileId = UUID.fromString(matcher.group(1));
            serviceContainer.binaryFileService().getFile(oreSiAuthenticationToken.getApplicationName(), fileId)
                    .map(BinaryFile::getParams)
                    .map(BinaryFileInfos::binaryFiledataset)
                    .map(binaryFileDataset -> new FileOrUUID(
                            fileId,
                            binaryFileDataset,
                            false
                    ))
                    .ifPresent(oreSiAuthenticationToken::setFileOrUUID);
        }
    }

    private OreSiAuthenticationToken buildLoginAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationFailure {
        String loginValue = request.getParameter(LOGIN_PARAMETER);
        String passwordValue = request.getParameter(PASSWORD_PARAMETER);

        if (Strings.isNotEmpty(loginValue) && Strings.isNotEmpty(passwordValue)) {
            LoginAdminResult loginAdminResult = serviceContainer.authorizationService()
                    .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomainEnum.SYSTEM_USER_NOT_CONNECTED)
                    .forLoginPassword(loginValue, passwordValue);
            final String jwt = jWTExtractor.refreshJwtInResponse(response, loginAdminResult.id());
            final OreSiAuthenticationToken oreSiAuthenticationToken = new OreSiAuthenticationToken(
                    loginAdminResult,
                    request.getRequestURI(),
                    List.of(ROLE_AUTHENTIFIED_USER)
            );
            oreSiAuthenticationToken.setJwtToken(jwt);
            return oreSiAuthenticationToken;
        }
        throw new AuthenticationFailure(BAD_LOGIN_PASSWORD, (OreSiUser) null);
    }


    private OreSiAuthenticationToken buildCreateUserAuthentication() {
        return new OreSiAuthenticationToken(
                serviceContainer.authorizationService()
                        .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomainEnum.SYSTEM_USER_NOT_CONNECTED)
                        .forCreateUser(),
                "",
                List.of(ROLE_UNAUTHENTIFIED_CREATE_USER)
        );
    }

    public OreSiAuthenticationToken buildUpdateUserAuthentication(HttpServletRequest request) throws IOException, AuthenticationFailure {
        CreateUserRequest createUserRequest = mapper.getJsonMapper().readValue(request.getInputStream(), CreateUserRequest.class);
        NotConnectedUser updateUser = serviceContainer.authorizationService()
                .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomainEnum.SYSTEM_USER_NOT_CONNECTED)
                .forUpdateUser(createUserRequest);
        return new OreSiAuthenticationToken(
                updateUser,
                request.getRequestURI(),
                List.of(ROLE_UNAUTHENTIFIED_UPDATE_USER)
        );
    }

    private OreSiAuthenticationToken handleJwtAuthentication(HttpServletRequest request) throws IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String jwtToken = authHeader.substring(7);
        // Revocation check : un JWT inscrit dans la blacklist par un kick
        // admin est rejete . On lance BadCredentialsException avec un
        // message stable que writeJsonAuthError mappe en TOKEN_REVOKED ;
        // le frontend ( interceptor axios global ) declenche alors la
        // redirection vers la page de login .
        String tokenHash = fr.inra.oresing.monitoring.session.JwtBlacklistRegistry.hash(jwtToken);
        if (tokenHash != null && jwtBlacklist.contains(tokenHash)) {
            throw new BadCredentialsException("Token revoked by admin kick");
        }
        OreSiRequestClient requestClient = jWTExtractor.getRequestClientFromJwt(jwtToken);
        return new OreSiAuthenticationToken(
                requestClient,
                request.getRequestURI(),
                List.of(ROLE_AUTHENTIFIED_USER)
        );
    }
}
