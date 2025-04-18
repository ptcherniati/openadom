package fr.inra.oresing.rest.security;

import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class SecurityConfig implements ServiceContainerBean {

    public static final long MAX_AGE = 3600L;
    @Autowired
    private AuthorizationFilter authorizationFilter;
    @Value("${allowed.origin}")
    String allowedOrigin;
    private final AuthenticationProvider authenticationProvider;
    private ServiceContainer serviceContainer;

    public SecurityConfig(OreSiAuthorizationManager authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Primary
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .authenticationProvider(authenticationProvider)
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)// Optionnel : désactive la protection CSRF pour simplifier les tests d'API
                .formLogin(AbstractHttpConfigurer::disable) // Désactive le formulaire de login
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers(
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**",
                                        "/api/public/**",
                                        "/api-docs.yaml").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/login").hasAuthority(OreSiAuthorizationManager.ROLE_AUTHENTIFIED_USER.getAuthority())
                                .requestMatchers(HttpMethod.POST, "/api/v1/users").hasAuthority(OreSiAuthorizationManager.ROLE_UNAUTHENTIFIED_CREATE_USER.getAuthority())
                                .requestMatchers(HttpMethod.PUT, "/api/v1/users").hasAuthority(OreSiAuthorizationManager.ROLE_UNAUTHENTIFIED_UPDATE_USER.getAuthority())
                                .anyRequest().authenticated())
                .addFilterAfter(authorizationFilter, BasicAuthenticationFilter.class);
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

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }
}
