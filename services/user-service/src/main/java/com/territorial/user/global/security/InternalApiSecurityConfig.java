package com.territorial.user.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class InternalApiSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Value("${internal-api.secret}") String secret) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers("/actuator/health", "/internal/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/auth/**")
                                        .permitAll()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new InternalApiSecretFilter(secret), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
