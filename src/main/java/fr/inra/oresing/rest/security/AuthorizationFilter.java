package fr.inra.oresing.rest.security;

import fr.inra.oresing.JwtCookieValue;
import fr.inra.oresing.OreSiRequestClient;
import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public class AuthorizationFilter extends OncePerRequestFilter implements ServiceContainerBean {
    private static final String HTTP_CORRELATION_ID = "X-Correlation-ID";
    public static final String JWT_COOKIE_NAME = "si-ore-jwt";
    private static final String AUTHORIZATION_ALREADY_DONE = "AUTHORIZATION_ALREADY_DONE";

    private ServiceContainer serviceContainer;
    private final OreSiApiRequestContext requestContext;
    private final JsonRowMapper mapper;
    private final SecretKey key;
    private final int jwtExpiration;

    @Autowired
    public AuthorizationFilter(
            JsonRowMapper mapper,
            OreSiApiRequestContext requestContext,
            JsonRowMapper jsonRowMapper,
            @Value("${jwt.expiration:3600}") int jwtExpiration,
            @Value("${jwt.secret:1234567890AZERTYUIOP}") String jwtSecret) {

        this.serviceContainer = serviceContainer;
        this.requestContext = requestContext;
        this.mapper = jsonRowMapper;
        this.jwtExpiration = jwtExpiration;
        String secureEnoughJwtSecret = StringUtils.rightPad(jwtSecret, 32, '0');
        this.key = Keys.hmacShaKeyFor(secureEnoughJwtSecret.getBytes());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/api/public") ||
                path.startsWith("/api-docs.yaml")) {
            chain.doFilter(request, response); // Skip le filtre
        }
        if (path.endsWith("/logout")) {
            return;
        }
        if (request.getAttribute(AUTHORIZATION_ALREADY_DONE) != null) {
            chain.doFilter(request, response);
        }
        request.setAttribute(AUTHORIZATION_ALREADY_DONE, true);
        try {
            OreSiAuthenticationToken token = buildAuthentication(request, response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(token);
            SecurityContextHolder.getContextHolderStrategy().setContext(
                   context
            );

        } catch (AuthenticationFailure e) {
            throw new RuntimeException(e);
        }
        chain.doFilter(request, response);
    }


    private OreSiAuthenticationToken buildAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationFailure, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (HttpMethod.OPTIONS.name().equals(method)) {
            return null;
        }
        if (HttpMethod.POST.name().equals(method) && path.endsWith("/login")) {
            return buildLoginAuthentication(request, response);
        } else if (HttpMethod.POST.equals(method) && path.endsWith("/users")) {
            return buildCreateUserAuthentication();
        } else if (HttpMethod.PUT.equals(method) && path.endsWith("/users")) {
            return buildUpdateUserAuthentication(request);
        } else {
            return handleJwtAuthentication(request, response);
        }
    }

    private OreSiAuthenticationToken buildLoginAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationFailure {
        String loginValue = request.getParameter("login");
        String passwordValue = request.getParameter("password");

        if (Strings.isNotEmpty(loginValue) && Strings.isNotEmpty(passwordValue)) {
            try {
                LoginAdminResult loginAdminResult = serviceContainer.authorizationService()
                        .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomain.SYSTEM_USER_NOT_CONNECTED)
                        .forLoginPassword(loginValue, passwordValue);
                refreshCookie(response, loginAdminResult.id());
                OreSiAuthenticationToken token = new OreSiAuthenticationToken(
                        loginAdminResult,
                        request.getRequestURI(),
                        List.of(OreSiAuthorizationManager.ROLE_AUTHENTIFIED_USER)
                );
                return token;
            } catch (AuthenticationFailure e) {
                throw new AuthenticationFailure("Échec technique", (OreSiUser) null);
            }
        }
        throw new AuthenticationFailure("Échec technique", (OreSiUser) null);
    }


    private OreSiAuthenticationToken buildCreateUserAuthentication() {
        return new OreSiAuthenticationToken(
                serviceContainer.authorizationService()
                        .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomain.SYSTEM_USER_NOT_CONNECTED)
                        .forCreateUser(),
                "",
                List.of(OreSiAuthorizationManager.ROLE_UNAUTHENTIFIED_CREATE_USER)
        );
    }

    public OreSiAuthenticationToken buildUpdateUserAuthentication(HttpServletRequest request) throws IOException, AuthenticationFailure {
        CreateUserRequest createUserRequest = (CreateUserRequest) mapper.readStream(request.getInputStream(), CreateUserRequest.class);
        NotConnectedUser updateUser = serviceContainer.authorizationService()
                .getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomain.SYSTEM_USER_NOT_CONNECTED)
                .forUpdateUser(createUserRequest);
        return new OreSiAuthenticationToken(
                updateUser,
                request.getRequestURI(),
                List.of(OreSiAuthorizationManager.ROLE_UNAUTHENTIFIED_UPDATE_USER)
        );
    }

    private OreSiAuthenticationToken handleJwtAuthentication(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String jwtCookie = extractJwtCookie(request);
        if (jwtCookie == null) {
            return null;
        }
        OreSiRequestClient requestClient = getRequestClientFromJwt(jwtCookie);
        refreshCookie(response, requestClient.id());
        return new OreSiAuthenticationToken(
                requestClient,
                request.getRequestURI(),
                List.of(OreSiAuthorizationManager.ROLE_AUTHENTIFIED_USER)
        );
    }

    /*private void handleCorrelation(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HTTP_CORRELATION_ID);
        requestContext.setClientCorrelationId(correlationId);
    }*/

    private String extractJwtCookie(HttpServletRequest request) {
        return Optional.ofNullable(request.getCookies())
                .map(Arrays::asList)
                .stream()
                .flatMap(List::stream)
                .filter(cookie -> cookie.getName().equals(JWT_COOKIE_NAME))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private OreSiUserRequestClient getRequestClientFromJwt(String token) throws IOException {
        String json = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();

        return ((JwtCookieValue) mapper.readValue(json, JwtCookieValue.class)).requestClient();
    }

    private void refreshCookie(HttpServletResponse response, UUID id) {
        OreSiUserRole userRole = serviceContainer.authenticationService()
                .getUserRole(id);
        OreSiUserRequestClient requestClient = OreSiUserRequestClient.of(id, userRole);
        Cookie cookie = newCookie(requestClient);
        try {
            response.addCookie(cookie);
        } catch (Exception e) {
            log.trace("pas grave");
        }
    }

    public String buildToken(OreSiUserRequestClient requestClient) {
        JwtCookieValue jwtCookieValue = new JwtCookieValue(requestClient);
        String json = mapper.toJson(jwtCookieValue);
        return Jwts.builder()
                .subject(json)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration * 1000L))
                .signWith(key)
                .compact();
    }

    private Cookie newCookie(final OreSiUserRequestClient requestClient) {
        final String json;
        final JwtCookieValue jwtCookieValue = new JwtCookieValue(requestClient);
        json = mapper.toJson(jwtCookieValue);
        return getCookie(json, jwtExpiration);
    }

    private Cookie getCookie(String json, int maxAge) {
        final Date issuedAt = new Date();
        final String token = buildToken(json, issuedAt, jwtExpiration);
        final Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    protected String buildToken(String json, Date issuedAt, int jwtExpiration) {
        return Jwts.builder()
                .subject(json)
                .issuedAt(issuedAt)
                .expiration(DateUtils.addSeconds(issuedAt, jwtExpiration))
                .signWith(key)
                .compact();
    }

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }
}
