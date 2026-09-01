package com.territorial.user.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiSecretFilterTest {

    private final InternalApiSecretFilter filter = new InternalApiSecretFilter("test-secret");

    @Test
    void missingInternalTokenIsForbidden() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/wallets/bid-escrow");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void matchingInternalTokenContinuesFilterChain() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/internal/wallets/bid-escrow");
        request.addHeader(InternalApiSecretFilter.HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
