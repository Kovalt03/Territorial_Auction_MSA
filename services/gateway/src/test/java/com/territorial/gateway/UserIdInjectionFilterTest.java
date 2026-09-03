package com.territorial.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class UserIdInjectionFilterTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private final UserIdInjectionFilter filter = new UserIdInjectionFilter(SECRET);

    @Test
    void accessTokenInjectsVerifiedUserIdAndReplacesIncomingHeader() {
        ServerWebExchange result = filter(token("access"), "forged");

        assertThat(result.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("7");
    }

    @Test
    void refreshTokenDoesNotInjectUserId() {
        ServerWebExchange result = filter(token("refresh"), "forged");

        assertThat(result.getRequest().getHeaders().containsKey("X-User-Id")).isFalse();
    }

    @Test
    void removesIncomingGatewayServiceToken() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/military/units")
                        .header("X-Gateway-Service-Token", "forged")
                        .build();
        AtomicReference<ServerWebExchange> result = new AtomicReference<>();

        filter.filter(
                        MockServerWebExchange.from(request),
                        exchange -> {
                            result.set(exchange);
                            return Mono.empty();
                        })
                .block();

        assertThat(result.get().getRequest().getHeaders().containsKey("X-Gateway-Service-Token"))
                .isFalse();
    }

    private ServerWebExchange filter(String token, String incomingUserId) {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/v1/auctions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", incomingUserId)
                        .build();
        AtomicReference<ServerWebExchange> result = new AtomicReference<>();
        filter.filter(
                        MockServerWebExchange.from(request),
                        exchange -> {
                            result.set(exchange);
                            return Mono.empty();
                        })
                .block();
        return result.get();
    }

    private String token(String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject("7")
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(KEY)
                .compact();
    }
}
