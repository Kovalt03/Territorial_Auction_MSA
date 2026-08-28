package com.territorial.auction.domain.map.service;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 경매 순환에 아직 편입되지 않은 IDLE 영토(nextAuctionAt=null)에 시간차를 둔 예약을 부여한다.
// 한 번 예약되면(=non-null) 다시 건드리지 않으므로 재기동 시 idempotent.
// 영토 스케줄링이므로 map 도메인이 소유(경매 '생성'은 auction-service가 territory.auction-ready 이벤트로 담당).
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class IdleAuctionScheduleSeeder implements ApplicationRunner {

    // 초기 편입을 이 시간(분) 안에 무작위로 분산 → 첫 경매가 한꺼번에 열리지 않게 한다.
    private static final int WARMUP_WINDOW_MINUTES = 120;

    private final TerritoryRepository territoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Territory> unscheduled =
                territoryRepository.findAllByStatusAndNextAuctionAtIsNull(
                        Territory.TerritoryStatus.IDLE);
        if (unscheduled.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int windowSeconds = WARMUP_WINDOW_MINUTES * 60;
        Random random = new Random();
        for (Territory territory : unscheduled) {
            territory.scheduleNextAuction(now.plusSeconds(random.nextInt(windowSeconds)));
        }
        log.info("IDLE 영토 경매 순환 편입 완료. 건수={}", unscheduled.size());
    }
}
