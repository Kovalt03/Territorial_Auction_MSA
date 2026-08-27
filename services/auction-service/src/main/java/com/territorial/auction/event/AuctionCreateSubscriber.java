package com.territorial.auction.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.service.AuctionCreationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/** map의 territory.auction-ready 이벤트를 구독해 경매를 생성한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCreateSubscriber {

    public static final String TOPIC = "territory.auction-ready";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final AuctionCreationService auctionCreationService;

    @PostConstruct
    public void subscribe() {
        redissonClient
                .getTopic(TOPIC)
                .addListener(
                        String.class,
                        (channel, json) -> {
                            try {
                                TerritoryAuctionReadyEvent event =
                                        objectMapper.readValue(
                                                json, TerritoryAuctionReadyEvent.class);
                                auctionCreationService.createAuction(event);
                            } catch (Exception e) {
                                log.error("[AuctionCreateSubscriber] 이벤트 처리 실패: {}", json, e);
                            }
                        });
        log.info("[AuctionCreateSubscriber] 구독 시작: {}", TOPIC);
    }
}
