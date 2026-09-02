package com.territorial.combat.domain.military.scheduler;

import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
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

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void finalizeArrivedMoves() {
        List<UnitInstance> arrived =
                unitInstanceRepository.findArrivedInTransit(LocalDateTime.now());
        arrived.forEach(this::mergeIntoHomeIdle);
        if (!arrived.isEmpty()) {
            log.info("유닛 이동 완료 정산. 건수={}", arrived.size());
        }
    }

    private void mergeIntoHomeIdle(UnitInstance transit) {
        Long userId = transit.getUserId();
        Long typeId = transit.getUnitType().getId();
        Optional<UnitInstance> readyIdle =
                transit.getHomeTerritoryId() != null
                        ? unitInstanceRepository.findReadyIdleAtTerritory(
                                userId, typeId, transit.getLevel(), transit.getHomeTerritoryId())
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
