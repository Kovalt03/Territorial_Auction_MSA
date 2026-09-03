package com.territorial.combat.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class GatewayRequestAuthenticationFilterTest {

    private static final String SECRET = "gateway-secret";
    private final GatewayRequestAuthenticationFilter filter =
            new GatewayRequestAuthenticationFilter(SECRET);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsPublicApiWithoutGatewayToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/island");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void authenticatesVerifiedGatewayUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/island");
        request.addHeader(GatewayRequestAuthenticationFilter.GATEWAY_HEADER, SECRET);
        request.addHeader(GatewayRequestAuthenticationFilter.USER_HEADER, "7");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(7L);
    }

    private FilterChain chain() {
        return (request, response) -> {};
    }
}
