package com.territorial.combat.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class GatewayRequestAuthenticationFilter extends OncePerRequestFilter {

    static final String GATEWAY_HEADER = "X-Gateway-Service-Token";
    static final String USER_HEADER = "X-User-Id";

    private final byte[] secret;

    GatewayRequestAuthenticationFilter(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String gatewayToken = request.getHeader(GATEWAY_HEADER);
        if (gatewayToken == null
                || !MessageDigest.isEqual(secret, gatewayToken.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String value = request.getHeader(USER_HEADER);
        if (value != null) {
            try {
                long userId = Long.parseLong(value);
                if (userId <= 0) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                SecurityContextHolder.getContext()
                        .setAuthentication(
                                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (NumberFormatException exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
