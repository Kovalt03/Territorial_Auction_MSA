package com.territorial.auction.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.service.AuctionCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** map의 territory-auction-ready(Kafka) 이벤트를 구독해 경매를 생성한다. durable 경로. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCreateSubscriber {

    public static final String TOPIC = "territory-auction-ready";

    private final ObjectMapper objectMapper;
    private final AuctionCreationService auctionCreationService;

    @KafkaListener(topics = TOPIC, groupId = "auction-territory-ready")
    public void handle(@Payload String json) {
        try {
            TerritoryAuctionReadyEvent event =
                    objectMapper.readValue(json, TerritoryAuctionReadyEvent.class);
            auctionCreationService.createAuction(event);
        } catch (Exception e) {
            log.error("[AuctionCreateSubscriber] 이벤트 처리 실패: {}", json, e);
            throw new IllegalStateException("territory-auction-ready 처리 실패", e);
        }
    }
}
