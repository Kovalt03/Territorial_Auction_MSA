package com.territorial.gateway;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 모든 요청에서 유입 X-User-Id를 제거(위조 방지)하고, 유효한 Bearer JWT가 있으면 subject(userId)를 X-User-Id로 주입한다. 내부
 * 서비스(auction 등)는 이 헤더를 신뢰한다. 모놀리식은 X-User-Id를 무시하고 JWT를 직접 검증한다.
 */
@Component
public class UserIdInjectionFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String GATEWAY_TOKEN_HEADER = "X-Gateway-Service-Token";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey key;

    public UserIdInjectionFilter(@Value("${jwt.secret}") String secret) {
        this.key =
                io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = extractUserId(request);

        ServerHttpRequest.Builder mutated = request.mutate();
        mutated.headers(
                headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(GATEWAY_TOKEN_HEADER);
                });
        if (userId != null) {
            mutated.header(USER_ID_HEADER, userId);
        }
        return chain.filter(exchange.mutate().request(mutated.build()).build());
    }

    private String extractUserId(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        try {
            var claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(auth.substring(7))
                            .getPayload();
            if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                return null;
            }
            return claims.getSubject();
        } catch (Exception e) {
            return null; // 유효하지 않은 토큰 → X-User-Id 미주입(보호 엔드포인트는 서비스가 거부)
        }
    }

    @Override
    public int getOrder() {
        return -1; // 라우팅 전에 적용
    }
}
