package fr.inra.oresing.rest.security;

import fr.inra.oresing.rest.authentication.evaluator.ApplicationPermissionEvaluator;
import fr.inra.oresing.rest.services.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**/
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            AuthorizationService authorizationService) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(new ApplicationPermissionEvaluator(authorizationService));
        return handler;
    }

    @Bean
    public PermissionEvaluator applicationPermissionEvaluator(
            AuthorizationService authorizationService
    ) {
        return new ApplicationPermissionEvaluator(authorizationService);
    }

    public static final long MAX_AGE = 3600L;
    @Autowired
    private AuthorizationFilter authorizationFilter;
    @Value("${allowed.origin}")
    String allowedOrigin;


    /*@Bean
    @Primary
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .authenticationProvider(authenticationProvider)
                .build();
    }*/

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieClearingLogoutHandler cookies = new CookieClearingLogoutHandler(JWTExtractor.JWT_COOKIE_NAME);

        http
                .formLogin(AbstractHttpConfigurer::disable) // Désactive le formulaire de login
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/swagger-ui/**", "/api-docs/**", "/api/public/**")
                        .ignoringRequestMatchers("/api/v1/login", "/api/v1/users", "/api/v1/logout")
                )
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers(
                                        "/api/v1/logout",
                                        "/actuator/**",
                                        "/swagger-ui/**",
                                        "/api-docs/**",
                                        "/api/public/**",
                                        "/api-docs.yaml").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/login").hasAuthority(AuthorizationFilter.ROLE_AUTHENTIFIED_USER.getAuthority())
                                .requestMatchers(HttpMethod.POST, "/api/v1/users").hasAuthority(AuthorizationFilter.ROLE_UNAUTHENTIFIED_CREATE_USER.getAuthority())
                                .requestMatchers(HttpMethod.PUT, "/api/v1/users").hasAuthority(AuthorizationFilter.ROLE_UNAUTHENTIFIED_UPDATE_USER.getAuthority())
                                .anyRequest().authenticated())
                .addFilterAfter(authorizationFilter, BasicAuthenticationFilter.class)
                .securityContext(security -> security
                        .securityContextRepository(new RequestAttributeSecurityContextRepository())
                );
        ;
        return http.build();
    }


    @Configuration
    public class CorsConfig implements WebMvcConfigurer {

        private static final long MAX_AGE = 3600;

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/api/**")
                    .allowedOrigins(allowedOrigin)
                    .allowedMethods("POST", "PUT", "GET", "DELETE")
                    .allowCredentials(true)
                    .maxAge(MAX_AGE);
        }
    }
}
