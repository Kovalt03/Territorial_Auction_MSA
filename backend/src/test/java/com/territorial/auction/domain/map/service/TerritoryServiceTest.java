package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TerritoryServiceTest {

    @InjectMocks private TerritoryService territoryService;

    @Mock private TerritoryRepository territoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

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
                        .zone1Radius(2)
                        .zone2Radius(4)
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

    @Test
    @DisplayName("combat context는 owner·좌표·영토 등급 격자 정보를 반환한다")
    void combatContext() {
        Territory t = territory(1L);
        User owner =
                User.builder()
                        .username("o")
                        .email("o@x")
                        .passwordHash("h")
                        .nickname("owner")
                        .build();
        ReflectionTestUtils.setField(owner, "id", 3L);
        t.occupy(owner, LocalDateTime.now().plusDays(3), LocalDateTime.now().plusHours(12));
        given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));

        var response = territoryService.getCombatContext(1L);

        assertThat(response.ownerId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo("OCCUPIED");
        assertThat(response.gridSize()).isEqualTo(10);
        assertThat(response.zone1Radius()).isEqualTo(2);
    }

    @Test
    @DisplayName("소유 영토 combat context 목록을 반환한다")
    void ownedCombatContexts() {
        Territory t = territory(1L);
        given(territoryRepository.findAllOccupiedByOwnerId(3L, Territory.TerritoryStatus.OCCUPIED))
                .willReturn(List.of(t));

        assertThat(territoryService.getOwnedCombatContexts(3L))
                .extracting("territoryId")
                .containsExactly(1L);
    }

    @Test
    @DisplayName("공성 인계는 현재 소유자가 일치할 때 새 점유자에게 이전한다")
    void takeoverFromSiege() {
        Territory t = territory(1L);
        User former =
                User.builder()
                        .username("f")
                        .email("f@x")
                        .passwordHash("h")
                        .nickname("former")
                        .build();
        ReflectionTestUtils.setField(former, "id", 2L);
        User attacker =
                User.builder()
                        .username("a")
                        .email("a@x")
                        .passwordHash("h")
                        .nickname("attacker")
                        .build();
        ReflectionTestUtils.setField(attacker, "id", 3L);
        t.occupy(former, LocalDateTime.now().plusDays(1), null);
        given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));
        given(userRepository.findById(3L)).willReturn(Optional.of(attacker));

        territoryService.takeOverFromSiege(1L, 3L, 2L);

        assertThat(t.getOwner()).isEqualTo(attacker);
        assertThat(t.getProtectedUntil()).isAfter(LocalDateTime.now());
        org.mockito.BDDMockito.then(messagingTemplate)
                .should()
                .convertAndSend(eq("/sub/map/update"), any(Object.class));
    }

    @Test
    @DisplayName("이미 반영된 공성 인계 재전달은 아무 변경 없이 종료한다")
    void takeoverDuplicateIsIdempotent() {
        Territory t = territory(1L);
        User attacker =
                User.builder()
                        .username("a")
                        .email("a@x")
                        .passwordHash("h")
                        .nickname("attacker")
                        .build();
        ReflectionTestUtils.setField(attacker, "id", 3L);
        t.occupy(attacker, LocalDateTime.now().plusDays(1), null);
        given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(t));

        territoryService.takeOverFromSiege(1L, 3L, 2L);

        org.mockito.BDDMockito.then(userRepository).should(never()).findById(3L);
        org.mockito.BDDMockito.then(messagingTemplate)
                .should(never())
                .convertAndSend(any(String.class), any(Object.class));
    }
}
