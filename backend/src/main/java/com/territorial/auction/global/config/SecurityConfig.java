package com.territorial.auction.global.config;

import com.territorial.auction.global.security.CustomUserDetailsService;
import com.territorial.auction.global.security.InternalApiSecretFilter;
import com.territorial.auction.global.security.jwt.JwtAuthenticationFilter;
import com.territorial.auction.global.security.jwt.JwtTokenProvider;
import com.territorial.auction.global.security.oauth2.CustomOAuth2UserService;
import com.territorial.auction.global.security.oauth2.OAuth2FailureHandler;
import com.territorial.auction.global.security.oauth2.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final CustomUserDetailsService customUserDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Value("${internal-api.secret}") String internalApiSecret)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exceptions ->
                                exceptions.authenticationEntryPoint(
                                        (request, response, ex) -> {
                                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                            response.setContentType(
                                                    "application/json;charset=UTF-8");
                                            response.getWriter()
                                                    .write(
                                                            "{\"status\":\"UNAUTHORIZED\","
                                                                    + "\"message\":\"인증이 필요합니다.\"}");
                                        }))
                .authorizeHttpRequests(
                        // spotless:off
                        auth ->
                                auth.requestMatchers("/api/v1/auth/logout").authenticated()
                                    .requestMatchers("/api/v1/auth/**", "/oauth2/**", "/login/**").permitAll()
                                    .requestMatchers("/ws/**").permitAll()
                                    .requestMatchers("/actuator/**").permitAll()
                                    .requestMatchers("/internal/**").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/images/**").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/announcement").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/map/**").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/continents/**").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/auctions/my-bids").authenticated()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/auctions", "/api/v1/auctions/**").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/rankings/territory-hold").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/rankings/auction-spend").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/rankings/continent/**").permitAll()
                                    .requestMatchers(HttpMethod.GET, "/api/v1/siege/events").permitAll()
                                    .requestMatchers("/api/v1/military/**").authenticated()
                                    .requestMatchers("/api/v1/siege/**").authenticated()
                                    .requestMatchers("/api/v1/users/me/wishlist/**").authenticated()
                                    .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                                    .anyRequest().authenticated())
                        // spotless:on
                .oauth2Login(
                        oauth2 ->
                                oauth2.userInfoEndpoint(
                                                endpoint ->
                                                        endpoint.userService(
                                                                customOAuth2UserService))
                                        .successHandler(oAuth2SuccessHandler)
                                        .failureHandler(oAuth2FailureHandler))
                .addFilterBefore(
                        new InternalApiSecretFilter(internalApiSecret),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, stringRedisTemplate),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
