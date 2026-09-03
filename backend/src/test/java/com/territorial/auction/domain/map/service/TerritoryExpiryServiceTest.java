package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.territorial.auction.domain.map.dto.MapUpdateBroadcast;
import com.territorial.auction.domain.map.entity.Continent;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.event.TerritoryLostEvent;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.global.client.SeasonQueryClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TerritoryExpiryServiceTest {

    @InjectMocks private TerritoryExpiryService territoryExpiryService;

    @Mock private TerritoryRepository territoryRepository;
    @Mock private SeasonQueryClient seasonQueryClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        // afterCommit 브로드캐스트 등록을 위해 활성 동기화 필요
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** afterCommit 브로드캐스트는 실제 커밋이 없으면 안 뜨므로 수동으로 콜백을 실행한다. */
    private void flushAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(
                        org.springframework.transaction.support.TransactionSynchronization
                                ::afterCommit);
    }

    private Territory occupiedTerritory(long id) {
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
        User owner =
                User.builder().username("o").email("o@x").passwordHash("h").nickname("주인").build();
        ReflectionTestUtils.setField(owner, "id", 10L);
        ReflectionTestUtils.setField(t, "owner", owner);
        ReflectionTestUtils.setField(t, "status", Territory.TerritoryStatus.OCCUPIED);
        ReflectionTestUtils.setField(t, "occupiedUntil", LocalDateTime.now().minusHours(1));
        return t;
    }

    @Test
    @DisplayName("만료 영토 없으면 아무 작업도 하지 않음")
    void release_none() {
        given(territoryRepository.findAllExpiredOccupied(any(), any())).willReturn(List.of());

        territoryExpiryService.releaseExpiredTerritories();

        verify(eventPublisher, never()).publishEvent(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("만료 영토 → IDLE 전환 + TerritoryLostEvent + map 브로드캐스트")
    void release_single() {
        Territory t = occupiedTerritory(1L);
        given(territoryRepository.findAllExpiredOccupied(any(), any())).willReturn(List.of(t));
        given(seasonQueryClient.getActiveSeason()).willReturn(Optional.empty());

        territoryExpiryService.releaseExpiredTerritories();
        flushAfterCommit();

        assertThat(t.getStatus()).isEqualTo(Territory.TerritoryStatus.IDLE);
        assertThat(t.getOwner()).isNull();
        verify(eventPublisher).publishEvent(any(TerritoryLostEvent.class));
        verify(messagingTemplate)
                .convertAndSend(eq("/sub/map/update"), any(MapUpdateBroadcast.class));
    }

    @Test
    @DisplayName("만료 영토 여러 개 모두 release")
    void release_multiple() {
        given(territoryRepository.findAllExpiredOccupied(any(), any()))
                .willReturn(List.of(occupiedTerritory(1L), occupiedTerritory(2L)));
        given(seasonQueryClient.getActiveSeason()).willReturn(Optional.empty());

        territoryExpiryService.releaseExpiredTerritories();
        flushAfterCommit();

        verify(eventPublisher, times(2)).publishEvent(any(TerritoryLostEvent.class));
        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/sub/map/update"), any(MapUpdateBroadcast.class));
    }
}
