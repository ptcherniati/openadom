package fr.inra.oresing.rest.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable()) // Optionnel : désactive la protection CSRF pour simplifier les tests d'API
                .formLogin(login -> login.disable()) // Désactive le formulaire de login
                .httpBasic(basic -> basic.disable()); // Désactive l'authentification Basic
        return http.build();
    }
}
