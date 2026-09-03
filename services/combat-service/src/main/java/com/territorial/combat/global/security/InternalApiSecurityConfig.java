package com.territorial.combat.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/building-types",
                                                "/api/v1/building-shop",
                                                "/api/v1/map/territories/*/buildings",
                                                "/api/v1/military/unit-types",
                                                "/api/v1/military/siege/target/*",
                                                "/api/v1/siege/events")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        new InternalApiSecretFilter(secret),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new GatewayRequestAuthenticationFilter(secret),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
