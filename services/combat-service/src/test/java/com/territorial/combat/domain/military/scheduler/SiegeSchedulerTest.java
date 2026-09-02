package com.territorial.combat.domain.military.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.territorial.combat.domain.military.entity.SiegeEvent;
import com.territorial.combat.domain.military.repository.SiegeEventRepository;
import com.territorial.combat.domain.military.service.SiegeResolutionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SiegeSchedulerTest {

    @InjectMocks private SiegeScheduler scheduler;
    @Mock private SiegeEventRepository siegeEventRepository;
    @Mock private SiegeResolutionService siegeResolutionService;

    @Test
    @DisplayName("한 공성 정산 실패가 다음 만료 공성 처리를 막지 않는다")
    void continuesAfterOneFailure() {
        SiegeEvent first = event(1L);
        SiegeEvent second = event(2L);
        given(
                        siegeEventRepository.findPendingToResolve(
                                org.mockito.ArgumentMatchers.eq(SiegeEvent.SiegeStatus.PENDING),
                                any(LocalDateTime.class)))
                .willReturn(List.of(first, second));
        willThrow(new IllegalStateException("broken"))
                .given(siegeResolutionService)
                .resolveOneSiege(first);

        scheduler.resolveExpiredSieges();

        then(siegeResolutionService).should().resolveOneSiege(first);
        then(siegeResolutionService).should().resolveOneSiege(second);
    }

    private SiegeEvent event(Long id) {
        SiegeEvent event =
                SiegeEvent.builder()
                        .attackerId(1L)
                        .defenderId(2L)
                        .targetTerritoryId(10L)
                        .attackZone(3)
                        .siegeStartAt(LocalDateTime.now().minusMinutes(6))
                        .resolveAt(LocalDateTime.now().minusMinutes(1))
                        .build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
