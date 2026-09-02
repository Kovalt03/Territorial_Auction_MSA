package com.territorial.auction.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiSecretFilterTest {

    private final InternalApiSecretFilter filter = new InternalApiSecretFilter("test-secret");

    @Test
    void rejectsMissingInternalToken() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/territories/1/combat-context");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void acceptsMatchingInternalToken() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/territories/1/combat-context");
        request.addHeader(InternalApiSecretFilter.HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void ignoresPublicPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
