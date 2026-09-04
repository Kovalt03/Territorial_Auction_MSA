package com.territorial.ranking.client;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.ranking.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 대륙 트로피 밴드 조회. 대륙은 공유 커널 map(모놀리식) 소유라 밴드 계산(minTrophyRequired ~ 다음 등급 minTrophyRequired)을 모놀리식에
 * 위임한다. 대륙은 정적 config라 저빈도 호출.
 */
@Component
public class ContinentBandClient {

    private final RestClient restClient;

    public ContinentBandClient(
            RestClient.Builder builder,
            @Value("${monolith.base-url}") String baseUrl,
            @Value("${internal-api.secret}") String internalApiSecret) {
        this.restClient =
                builder.baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Service-Token", internalApiSecret)
                        .defaultStatusHandler(
                                status -> status.value() == 404,
                                (req, res) -> {
                                    throw new CustomException(ErrorCode.CONTINENT_NOT_FOUND);
                                })
                        .build();
    }

    public TrophyBand getTrophyBand(Long continentId) {
        return restClient
                .get()
                .uri("/internal/continents/{id}/trophy-band", continentId)
                .retrieve()
                .body(TrophyBand.class);
    }

    // upper는 다음 등급 대륙의 minTrophyRequired(없으면 Integer.MAX_VALUE).
    public record TrophyBand(int lower, int upper) {}
}
