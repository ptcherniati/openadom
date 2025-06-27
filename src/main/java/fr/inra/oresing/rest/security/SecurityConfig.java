package fr.inra.oresing.rest.security;

import fr.inra.oresing.rest.authentication.evaluator.ApplicationPermissionEvaluator;
import fr.inra.oresing.rest.services.AuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    public static final String ACTUATOR = "/actuator";
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
            AuthorizationService authorizationService
    ) {
        return new ApplicationPermissionEvaluator(authorizationService);
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthorizationFilter authorizationFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable) // Désactive le formulaire de login
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(BASE, ALL.formatted(SWAGGER_UI), ALL.formatted(API_DOCS), ALL.formatted(API_PUBLIC))
                        .ignoringRequestMatchers(API_V_1_LOGIN, API_V_1_USERS, API_V_1_LOGOUT)
                )
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers(
                                        BASE,
                                        API_V_1_LOGOUT,
                                        ALL.formatted(ACTUATOR),
                                        ALL.formatted(SWAGGER_UI),
                                        ALL.formatted(API_DOCS),
                                        ALL.formatted(API_PUBLIC),
                                        API_DOCS_YAML,
                                        ERROR).permitAll()
                                .requestMatchers(HttpMethod.POST, API_V_1_LOGIN).hasAuthority(AuthorizationFilter.ROLE_AUTHENTIFIED_USER.getAuthority())
                                .requestMatchers(HttpMethod.POST, API_V_1_USERS).hasAuthority(AuthorizationFilter.ROLE_UNAUTHENTIFIED_CREATE_USER.getAuthority())
                                .requestMatchers(HttpMethod.PUT, API_V_1_USERS).hasAuthority(AuthorizationFilter.ROLE_UNAUTHENTIFIED_UPDATE_USER.getAuthority())
                                .anyRequest().authenticated())
                .addFilterAfter(authorizationFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), AuthorizationFilter.class)
                .securityContext(security -> security
                        .securityContextRepository(new RequestAttributeSecurityContextRepository())
                );
        return http.build();
    }

    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws IOException, ServletException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                response.setHeader(csrfToken.getHeaderName(), csrfToken.getToken());
            }
            filterChain.doFilter(request, response);
        }
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

        // Appliquer à toutes les routes
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
/*
    @Configuration
    public class CorsConfig implements WebMvcConfigurer {

        private static final long MAX_AGE = 3600;

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            // Configuration CORS pour les endpoints API
            registry.addMapping("/api/**")
                    .allowedOrigins(swaggerUrl, frontendOrigin)
                    .allowedMethods(
                            HttpMethod.POST.name(),
                            HttpMethod.PUT.name(),
                            HttpMethod.DELETE.name(),
                            HttpMethod.GET.name(),
                            HttpMethod.OPTIONS.name()
                    )
                    .allowedHeaders("X-CSRF-TOKEN", "X-XSRF-TOKEN", "Content-Type", "Authorization", "Accept-Language")
                    .allowCredentials(true)
                    .maxAge(MAX_AGE);

            // Configuration CORS spécifique pour la racine
            registry.addMapping(BASE)
                    .allowedOrigins(swaggerUrl, frontendOrigin)
                    .allowedMethods(HttpMethod.GET.name(),
                            HttpMethod.OPTIONS.name())
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(MAX_AGE);
        }
    }*/
}