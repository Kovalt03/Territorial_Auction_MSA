package com.territorial.auction.domain.map.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.event.TerritoryAuctionReadyEvent;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 순환에 편입된 IDLE 영토를 스캔해 startBidding + territory.auction-ready 이벤트를 발행한다. 경매 '생성'은
 * auction-service가 이 이벤트를 구독해 담당(모놀리식은 생성하지 않음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerritoryAuctionReadyPublisher {

    public static final String TOPIC = "territory-auction-ready";

    private final TerritoryRepository territoryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void publishReadyTerritories() {
        LocalDateTime now = LocalDateTime.now();
        List<Territory> ready =
                territoryRepository.findAllReadyForAuction(Territory.TerritoryStatus.IDLE, now);
        for (Territory territory : ready) {
            territory.startBidding();
            publishFor(territory);
        }
    }

    /** 특정 영토의 경매 생성을 즉시 요청(관리자 강제 시작 등). 호출 측이 startBidding을 먼저 수행한다. */
    public void publishFor(Territory territory) {
        String grade = territory.getGrade() != null ? territory.getGrade().getGrade() : "D";
        publish(
                new TerritoryAuctionReadyEvent(
                        territory.getId(),
                        territory.getCoordX(),
                        territory.getCoordY(),
                        territory.getContinent().getDisplayName(),
                        territory.getContinent().getId(),
                        grade));
        log.info("[TerritoryAuctionReady] 발행 territoryId={}", territory.getId());
    }

    private void publish(TerritoryAuctionReadyEvent event) {
        try {
            // 경매 생성 트리거 — durable. auction-service가 Kafka로 구독.
            kafkaTemplate.send(
                    TOPIC,
                    String.valueOf(event.territoryId()),
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("[TerritoryAuctionReady] 직렬화 실패 territoryId={}", event.territoryId(), e);
        }
    }
}
