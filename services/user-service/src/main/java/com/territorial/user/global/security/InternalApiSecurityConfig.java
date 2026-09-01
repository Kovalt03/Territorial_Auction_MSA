package com.territorial.user.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class InternalApiSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenProvider jwtTokenProvider,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            @Value("${internal-api.secret}") String secret)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exception ->
                                exception.authenticationEntryPoint(
                                        (request, response, authException) ->
                                                response.sendError(
                                                        HttpStatus.UNAUTHORIZED.value())))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers("/actuator/health", "/internal/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/signup",
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/refresh")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/auth/check-username",
                                                "/api/v1/auth/check-email",
                                                "/api/v1/auth/check-nickname")
                                        .permitAll()
                                        .requestMatchers("/api/v1/auth/logout", "/api/v1/users/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .addFilterBefore(
                        new InternalApiSecretFilter(secret),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
