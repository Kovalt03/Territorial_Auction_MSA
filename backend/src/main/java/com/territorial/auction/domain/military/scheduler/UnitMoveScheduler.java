package com.territorial.auction.domain.military.scheduler;

import com.territorial.auction.domain.military.entity.UnitInstance;
import com.territorial.auction.domain.military.repository.UnitInstanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnitMoveScheduler {

    private final UnitInstanceRepository unitInstanceRepository;

    /** 이동 완료 시각이 지난 유닛을 도착지 대기 스택으로 편입한다. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void finalizeArrivedMoves() {
        List<UnitInstance> arrived =
                unitInstanceRepository.findArrivedInTransit(LocalDateTime.now());
        for (UnitInstance transit : arrived) {
            mergeIntoHomeIdle(transit);
        }
        if (!arrived.isEmpty()) {
            log.info("유닛 이동 완료 정산. 건수={}", arrived.size());
        }
    }

    private void mergeIntoHomeIdle(UnitInstance transit) {
        Long userId = transit.getUser().getId();
        Long typeId = transit.getUnitType().getId();
        Optional<UnitInstance> readyIdle =
                transit.getHomeTerritory() != null
                        ? unitInstanceRepository.findReadyIdleAtTerritory(
                                userId,
                                typeId,
                                transit.getLevel(),
                                transit.getHomeTerritory().getId())
                        : unitInstanceRepository.findReadyIdleAtIsland(
                                userId,
                                typeId,
                                transit.getLevel(),
                                transit.getHomeIsland().getId());
        if (readyIdle.isPresent()) {
            readyIdle.get().addQuantity(transit.getQuantity());
            unitInstanceRepository.delete(transit);
        } else {
            transit.finishMove();
        }
    }
}
