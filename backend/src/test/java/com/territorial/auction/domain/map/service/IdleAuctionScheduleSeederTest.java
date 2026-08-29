package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IdleAuctionScheduleSeederTest {

    @InjectMocks private IdleAuctionScheduleSeeder seeder;

    @Mock private TerritoryRepository territoryRepository;

    private Territory idleTerritory(long id) {
        Territory t = Territory.builder().coordX(1).coordY(2).build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    @Test
    @DisplayName("미편입 IDLE 영토에 워밍업 창(0~120분) 내 next_auction_at 부여")
    void seeds_nextAuctionAt() {
        Territory t = idleTerritory(1L);
        given(
                        territoryRepository.findAllByStatusAndNextAuctionAtIsNull(
                                Territory.TerritoryStatus.IDLE))
                .willReturn(List.of(t));
        LocalDateTime before = LocalDateTime.now();

        seeder.run(new DefaultApplicationArguments());

        assertThat(t.getNextAuctionAt()).isNotNull();
        assertThat(t.getNextAuctionAt()).isBetween(before, before.plusMinutes(121));
    }

    @Test
    @DisplayName("미편입 영토 없으면 아무 작업도 하지 않음")
    void skips_whenNone() {
        given(
                        territoryRepository.findAllByStatusAndNextAuctionAtIsNull(
                                Territory.TerritoryStatus.IDLE))
                .willReturn(List.of());

        seeder.run(new DefaultApplicationArguments());
        // 예외 없이 종료 — 상호작용 없음
    }
}
