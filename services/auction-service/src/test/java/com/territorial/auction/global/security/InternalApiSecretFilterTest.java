package com.territorial.auction.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiSecretFilterTest {
    private final InternalApiSecretFilter filter = new InternalApiSecretFilter("test-secret");

    @Test
    void rejectsInternalRequestWithoutSecret() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/auctions/active-count");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsInternalRequestWithMatchingSecret() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/internal/auctions/active-count");
        request.addHeader(InternalApiSecretFilter.HEADER, "test-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsPublicRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auctions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
