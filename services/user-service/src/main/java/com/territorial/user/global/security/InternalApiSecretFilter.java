package com.territorial.user.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

final class InternalApiSecretFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Service-Token";
    private final byte[] secret;

    InternalApiSecretFilter(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("INTERNAL_API_SECRET is required");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] provided =
                String.valueOf(request.getHeader(HEADER)).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(secret, provided)) {
            response.sendError(HttpStatus.FORBIDDEN.value());
            return;
        }
        chain.doFilter(request, response);
    }
}
