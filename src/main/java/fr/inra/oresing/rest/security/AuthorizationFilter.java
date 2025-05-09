package fr.inra.oresing.rest.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.persistence.BinaryFileInfos;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.data.publication.AuthorizationPublicationService;
import fr.inra.oresing.rest.data.publication.StoreFile;
import fr.inra.oresing.rest.exceptions.OreExceptionHandler;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
public class AuthorizationFilter extends GenericFilterBean implements ServiceContainerBean {
    public static final GrantedAuthority ROLE_AUTHENTIFIED_USER = new SimpleGrantedAuthority("ROLE_AUTHENTIFIED_USER");
    public static final GrantedAuthority ROLE_UNAUTHENTIFIED_UPDATE_USER = new SimpleGrantedAuthority("ROLE_UNAUTHENTIFIED_UPDATE_USER");
    public static final GrantedAuthority ROLE_UNAUTHENTIFIED_CREATE_USER = new SimpleGrantedAuthority("ROLE_UNAUTHENTIFIED_CREATE_USER");
    private static final String AUTHORIZATION_ALREADY_DONE = "AUTHORIZATION_ALREADY_DONE";
    private final OreSiApiRequestContext requestContext;
    private static JsonRowMapper<OreSiUserRequestClient> mapper;
    private final OreExceptionHandler exceptionHandler;
    private final JWTExtractor JWTExtractor;
    private ServiceContainer serviceContainer;

    @Autowired
    public AuthorizationFilter(
            OreSiApiRequestContext requestContext,
            JsonRowMapper<OreSiUserRequestClient> jsonRowMapper,
            @Value("${jwt.expiration:3600}") int jwtExpiration,
            @Value("${jwt.secret:1234567890AZERTYUIOP}") String jwtSecret,
            OreExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        this.requestContext = requestContext;
        mapper = jsonRowMapper;
        this.JWTExtractor = new JWTExtractor(
                mapper,
                jwtExpiration,
                jwtSecret
        );
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getRequestURI();
        OreSiAuthenticationToken authenticationToken = requestContext.getAuthenticationToken();
        if (authenticationToken != null) {
            chain.doFilter(request, response);
            return;
        }
        if (response.isCommitted()) {
            return;
        }
        if (path.endsWith("/logout")) {
            JWTExtractor.clearSession(request, response, false);
            chain.doFilter(request, response);
            return;
        }
        if (
                path.equals("/") ||
                        path.startsWith("/actuator") ||
                        path.startsWith("/swagger-ui") ||
                        path.startsWith("/api-docs") ||
                        path.startsWith("/api/public") ||
                        path.startsWith("/api-docs.yaml") ||
                        path.equals("/error")) {
            chain.doFilter(request, response); // Skip le filtre
            return;
        }
        if (request.getAttribute(AUTHORIZATION_ALREADY_DONE) != null) {
            chain.doFilter(request, response);
        }
        request.setAttribute(AUTHORIZATION_ALREADY_DONE, true);
        try {
            OreSiAuthenticationToken token = buildAuthentication(request, response, request.isSecure());
            requestContext.setAuthenticationToken(token);
        } catch (AuthenticationFailure e) {
            ResponseEntity<AuthenticationFailure> handle = exceptionHandler.handle(e);
            response.setStatus(handle.getStatusCode().value());
            response.setContentType("application/json");
            String body = mapper.toJson(handle.getBody());
            response.getWriter().write(body);
            response.getWriter().flush();
            return;
        }
        chain.doFilter(request, response);
    }


    private OreSiAuthenticationToken buildAuthentication(HttpServletRequest request, HttpServletResponse response, boolean isSecureEnvironnement) throws AuthenticationFailure, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.OPTIONS.name().equals(method)) {
            return null;
        }
        if (HttpMethod.POST.name().equals(method) && path.endsWith("/login")) {
            return buildLoginAuthentication(request, response, isSecureEnvironnement);
        } else if (HttpMethod.POST.name().equals(method) && path.endsWith("/users")) {
            return buildCreateUserAuthentication();
        } else if (HttpMethod.PUT.name().equals(method) && path.endsWith("/users")) {
            return buildUpdateUserAuthentication(request);
        } else {
            OreSiAuthenticationToken oreSiAuthenticationToken = handleJwtAuthentication(request, response, isSecureEnvironnement);
            requestContext.setAuthenticationToken(oreSiAuthenticationToken); //premier stockage pour certaines méthodes
            if (oreSiAuthenticationToken == null) {
                return null;
            }
            Optional.ofNullable(path)
                    .map(p -> p.split("/"))
                    .map(Arrays::asList)
                    .filter(list -> list.size() > 4 && "applications".equals(list.get(3)))
                    .map(list -> list.get(4))
                    .ifPresent(oreSiAuthenticationToken::setApplicationName);
            Optional.ofNullable(path)
                    .map(p -> p.split("/"))
                    .map(Arrays::asList)
                    .filter(list -> list.size() > 3 && "applications".equals(list.get(3)))
                    .filter(list -> list.size() > 6 && List.of("data", "synthesis", "filesOnRepository").contains(list.get(5)))
                    .map(list -> list.get(6))
                    .or(() -> {
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
                                    .map(fileId -> {
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

                                    });
                        } catch (IllegalArgumentException e) {
                            if (AuthorizationPublicationService.DATA_NAME_CAN_T_BE_NULL.equals(e.getMessage())) {
                                optionalUUID
                                        .flatMap(fileId -> serviceContainer.binaryFileService().getFile(oreSiAuthenticationToken.getApplicationName(), fileId))
                                        .ifPresent(binaryFile -> {
                                            oreSiAuthenticationToken.setBinaryFile(binaryFile);
                                            dataNameOpt
                                                    .ifPresent(oreSiAuthenticationToken::setDataName);

                                            ;
                                        });
                            }
                            return Optional.empty();
                        }
                    })
                    .ifPresent(dataName -> {
                        addFilleOrUUID(request, oreSiAuthenticationToken, dataName, path);
                        oreSiAuthenticationToken.setDataName(dataName);
                    });

            return oreSiAuthenticationToken;
        }
    }

    private void addFilleOrUUID(HttpServletRequest request, OreSiAuthenticationToken oreSiAuthenticationToken, String dataName, String path) {
        if (HttpMethod.POST.name().equals(request.getMethod()) && "/api/v1/applications/%1$s/data/%2$s".formatted(oreSiAuthenticationToken.getApplicationName(), dataName).equals(path)) {
            String params = request.getParameter("params");
            Optional.ofNullable(params)
                    .filter(obj -> true)
                    .filter(Predicate.not("undefined"::equals))
                    .map(json -> {
                        try {
                            return new ObjectMapper().readValue(params, FileOrUUID.class);
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

    private OreSiAuthenticationToken addFileOrUUIDIfCreateData(OreSiAuthenticationToken authenticationToken, String dataName) {
        return authenticationToken;
    }

    private OreSiAuthenticationToken buildLoginAuthentication(HttpServletRequest request, HttpServletResponse response, boolean isSecureEnvironnement) throws AuthenticationFailure {
        String loginValue = request.getParameter("login");
        String passwordValue = request.getParameter("password");

        if (Strings.isNotEmpty(loginValue) && Strings.isNotEmpty(passwordValue)) {
            try {
                LoginAdminResult loginAdminResult = serviceContainer.authorizationService()
                        .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomainEnum.SYSTEM_USER_NOT_CONNECTED)
                        .forLoginPassword(loginValue, passwordValue);
                JWTExtractor.refreshJwtInResponse(response, loginAdminResult.id(), isSecureEnvironnement);
                return new OreSiAuthenticationToken(
                        loginAdminResult,
                        request.getRequestURI(),
                        List.of(ROLE_AUTHENTIFIED_USER)
                );
            } catch (AuthenticationFailure e) {
                throw new AuthenticationFailure("Échec technique", (OreSiUser) null);
            }
        }
        throw new AuthenticationFailure("Échec technique", (OreSiUser) null);
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
        CreateUserRequest createUserRequest = mapper.readStream(request.getInputStream(), CreateUserRequest.class);
        NotConnectedUser updateUser = serviceContainer.authorizationService()
                .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomainEnum.SYSTEM_USER_NOT_CONNECTED)
                .forUpdateUser(createUserRequest);
        return new OreSiAuthenticationToken(
                updateUser,
                request.getRequestURI(),
                List.of(ROLE_UNAUTHENTIFIED_UPDATE_USER)
        );
    }

    private OreSiAuthenticationToken handleJwtAuthentication(HttpServletRequest request, HttpServletResponse response, boolean isSecureEnvironnement) throws IOException {
        String jwtCookie = JWTExtractor.extractJwtCookie(request);
        if (jwtCookie == null) {
            return null;
        }
        OreSiRequestClient requestClient = JWTExtractor.getRequestClientFromJwt(jwtCookie);
        JWTExtractor.refreshJwtInResponse(response, requestClient.id(), isSecureEnvironnement);
        return new OreSiAuthenticationToken(
                requestClient,
                request.getRequestURI(),
                List.of(ROLE_AUTHENTIFIED_USER)
        );
    }

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {
        JWTExtractor.setSetGetUserRole(serviceContainer.authenticationService()::getUserRole);
        this.serviceContainer = serviceContainer;
    }
}