package com.territorial.combat.domain.military.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UnitMoveSchedulerTest {

    @InjectMocks private UnitMoveScheduler scheduler;
    @Mock private UnitInstanceRepository repository;

    private UnitType type;

    @BeforeEach
    void setUp() {
        type =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(10)
                        .defensePower(10)
                        .costGp(100)
                        .foodCost(1)
                        .build();
        ReflectionTestUtils.setField(type, "id", 1L);
    }

    private UnitInstance transit(int quantity) {
        return UnitInstance.builder()
                .userId(1L)
                .unitType(type)
                .quantity(quantity)
                .homeTerritoryId(10L)
                .moveCompleteAt(LocalDateTime.now().minusSeconds(1))
                .build();
    }

    @Test
    @DisplayName("도착지 대기 스택이 있으면 수량을 병합하고 이동 스택을 삭제한다")
    void finalizeArrivedMoves_mergesExistingStack() {
        UnitInstance transit = transit(3);
        UnitInstance idle =
                UnitInstance.builder()
                        .userId(1L)
                        .unitType(type)
                        .quantity(2)
                        .homeTerritoryId(10L)
                        .build();
        given(repository.findArrivedInTransit(any())).willReturn(List.of(transit));
        given(repository.findReadyIdleAtTerritory(1L, 1L, 1, 10L)).willReturn(Optional.of(idle));

        scheduler.finalizeArrivedMoves();

        assertThat(idle.getQuantity()).isEqualTo(5);
        then(repository).should().delete(transit);
    }

    @Test
    @DisplayName("도착지 대기 스택이 없으면 이동 완료 표시만 해제한다")
    void finalizeArrivedMoves_finishesTransit() {
        UnitInstance transit = transit(3);
        given(repository.findArrivedInTransit(any())).willReturn(List.of(transit));
        given(repository.findReadyIdleAtTerritory(1L, 1L, 1, 10L)).willReturn(Optional.empty());

        scheduler.finalizeArrivedMoves();

        assertThat(transit.isInTransit()).isFalse();
    }
}
