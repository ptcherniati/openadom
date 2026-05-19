package fr.inra.oresing.rest.security;

import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.rest.authentication.evaluator.ApplicationPermissionEvaluator;
import fr.inra.oresing.rest.services.DefaultAuthorizationService;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    public static final String ACTUATOR = "/actuator";
    public static final String ADMIN = "/api/admin";
    public static final String POOLS = "/api/pools";
    public static final String UPLOAD = "/api/upload";
    public static final String STATUS = "/api/status";
    public static final String SWAGGER_UI = "/swagger-ui";
    public static final String API_DOCS = "/api-docs";
    public static final String API_PUBLIC = "/api/public";
    public static final String API_DOCS_YAML = "/api-docs.yaml";
    public static final String ERROR = "/error";
    public static final String LOGIN = "/login";
    public static final String USERS = "/users";
    public static final String ALL = "%s/**";
    public static final String API_V_1_LOGOUT = "/api/v1/logout";
    public static final String API_V_1_LOGIN = "/api/v1/login";
    public static final String API_V_1_USERS = "/api/v1/users";
    // #470 - Endpoint public exposant la durée d'expiration du JWT ,
    // pour que le frontend aligne son timer d'inactivité sur le TTL serveur.
    public static final String API_V_1_SESSION_CONFIG = "/api/v1/session/config";
    public static final String BASE = "/";
    public static final long MAX_AGE = 3600L;
    @Value("${allowed.origin}")
    String frontendOrigin;
    @Value("${springdoc.swagger-ui.server-url}")
    String swaggerUrl;

    /**/
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            PermissionEvaluator applicationPermissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(applicationPermissionEvaluator);
        return handler;
    }

    @Bean
    @Lazy
    public PermissionEvaluator applicationPermissionEvaluator(
            DefaultAuthorizationService authorizationService
    ) {
        return new ApplicationPermissionEvaluator(authorizationService);
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthorizationFilter authorizationFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers-> headers.contentSecurityPolicy(
                        csp->csp.policyDirectives("frame-ancestors %s;".formatted(frontendOrigin))))
                .formLogin(AbstractHttpConfigurer::disable) // Désactive le formulaire de login
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers(
                                        BASE,
                                        API_V_1_LOGOUT,
                                        API_V_1_SESSION_CONFIG,
                                        UPLOAD,
                                        ADMIN,
                                        POOLS,
                                        STATUS,
                                        ALL.formatted(ACTUATOR),
                                        ALL.formatted(SWAGGER_UI),
                                        ALL.formatted(API_DOCS),
                                        ALL.formatted(API_PUBLIC),
                                        API_DOCS_YAML,
                                        ERROR).permitAll()
                                .requestMatchers(HttpMethod.POST, API_V_1_LOGIN).hasAuthority(AuthorizationFilter.ROLE_AUTHENTIFIED_USER.getAuthority())
                                .requestMatchers(HttpMethod.GET, API_V_1_LOGIN).hasAuthority(AuthorizationFilter.ROLE_AUTHENTIFIED_USER.getAuthority())
                                .requestMatchers(HttpMethod.POST, API_V_1_USERS).hasAuthority(AuthorizationFilter.ROLE_UNAUTHENTIFIED_CREATE_USER.getAuthority())
                                .requestMatchers(HttpMethod.PUT, API_V_1_USERS).hasAuthority(AuthorizationFilter.ROLE_UNAUTHENTIFIED_UPDATE_USER.getAuthority())
                                .anyRequest().authenticated())
                .addFilterAfter(authorizationFilter, BasicAuthenticationFilter.class)
                .securityContext(security -> security
                        .securityContextRepository(new RequestAttributeSecurityContextRepository())
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Configuration pour toutes les origines
        configuration.setAllowedOrigins(List.of(swaggerUrl, frontendOrigin));
        configuration.setAllowedMethods(List.of("POST", "PUT", "DELETE", "GET", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("X-CSRF-TOKEN", "X-XSRF-TOKEN", "Content-Type", "Authorization", "Accept-Language"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(MAX_AGE);
        configuration.setExposedHeaders(List.of("Authorization", "X-XSRF-TOKEN", "Content-Disposition"));

        // Appliquer à toutes les routes
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public ServerRequestObservationConvention serverRequestObservationConvention(JWTExtractor jwtExtractor) {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
                KeyValues keyValues = super.getLowCardinalityKeyValues(context);

                // User ID Tag from JWT
                String userId = "none";
                HttpServletRequest request = context.getCarrier();
                String token = jwtExtractor.extractJwtCookie(request);
                if (token != null) {
                    try {
                        OreSiUserRequestClient user = jwtExtractor.getRequestClientFromJwt(token);
                        if (user != null && user.id() != null) {
                            userId = user.id().toString();
                        }
                    } catch (Exception e) {
                        // Ignore exceptions, userId will remain "none"
                    }
                }

                // Path Variables Tags
                Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (pathVariables == null) {
                    pathVariables = Collections.emptyMap();
                }
                String appName = pathVariables.getOrDefault("nameOrId", "none");
                String dataType = pathVariables.get("dataType");
                if (dataType == null) {
                    dataType = pathVariables.getOrDefault("dataName", "none");
                }

                return keyValues.and(
                    KeyValue.of("user_id", userId),
                    KeyValue.of("app_name", appName),
                    KeyValue.of("data_type", dataType)
                );
            }
        };
    }
}