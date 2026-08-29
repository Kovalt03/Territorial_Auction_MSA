package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TerritoryServiceTest {

    @InjectMocks private TerritoryService territoryService;

    @Mock private TerritoryRepository territoryRepository;
    @Mock private UserRepository userRepository;

    private Territory territory(long id) {
        Continent c = Continent.builder().name("북부").themeColor("#000").displayName("북부").build();
        ReflectionTestUtils.setField(c, "id", 1L);
        TerritoryGrade g =
                TerritoryGrade.builder()
                        .grade("A")
                        .productionMultiplier(BigDecimal.ONE)
                        .auctionPriceMultiplier(BigDecimal.ONE)
                        .preBuiltCount(0)
                        .spawnRate(BigDecimal.ONE)
                        .gridSize(10)
                        .build();
        Territory t = Territory.builder().coordX(1).coordY(2).continent(c).grade(g).build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    @Test
    @DisplayName("occupy — 낙찰자 소유로 점유 상태 전환")
    void occupy_success() {
        Territory t = territory(1L);
        User winner =
                User.builder().username("w").email("w@x").passwordHash("h").nickname("낙찰자").build();
        ReflectionTestUtils.setField(winner, "id", 3L);
        given(territoryRepository.findById(1L)).willReturn(Optional.of(t));
        given(userRepository.getReferenceById(3L)).willReturn(winner);

        territoryService.occupy(
                1L, 3L, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusHours(12));

        assertThat(t.getStatus()).isEqualTo(Territory.TerritoryStatus.OCCUPIED);
        assertThat(t.getOwner()).isEqualTo(winner);
    }

    @Test
    @DisplayName("occupy — 없는 영토 → TERRITORY_NOT_FOUND")
    void occupy_notFound() {
        given(territoryRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                territoryService.occupy(
                                        9L, 3L, LocalDateTime.now(), LocalDateTime.now()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
    }

    @Test
    @DisplayName("release — IDLE 전환 + 재경매 예약")
    void release_success() {
        Territory t = territory(1L);
        ReflectionTestUtils.setField(t, "status", Territory.TerritoryStatus.OCCUPIED);
        given(territoryRepository.findById(1L)).willReturn(Optional.of(t));
        LocalDateTime next = LocalDateTime.now().plusHours(1);

        territoryService.release(1L, next);

        assertThat(t.getStatus()).isEqualTo(Territory.TerritoryStatus.IDLE);
        assertThat(t.getNextAuctionAt()).isEqualTo(next);
    }

    @Test
    @DisplayName("release — 없는 영토 → TERRITORY_NOT_FOUND")
    void release_notFound() {
        given(territoryRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> territoryService.release(9L, LocalDateTime.now()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
    }
}
