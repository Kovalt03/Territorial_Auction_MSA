package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.combat.client.CombatResourceClient;
import com.territorial.auction.domain.combat.client.CombatResourceClient.CreditIncomeResponse;
import com.territorial.auction.domain.combat.client.CombatResourceClient.TerritoryStorageView;
import com.territorial.auction.domain.map.dto.CollectTerritoryResponse;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.BonusTileRepository;
import com.territorial.auction.domain.map.repository.TerritoryProductionLogRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerritoryIncomeServiceTest {

    @InjectMocks private TerritoryIncomeService incomeService;

    @Mock private TerritoryRepository territoryRepository;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private BonusTileRepository bonusTileRepository;
    @Mock private TerritoryProductionLogRepository productionLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @Test
    void firstCollectionOnlyStartsClock() {
        Territory territory = territory(null);
        given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(territory));
        given(combatResourceClient.getTerritoryStorage(1L))
                .willReturn(new TerritoryStorageView(List.of(), 25, 100));

        CollectTerritoryResponse response = incomeService.collect(7L, 1L);

        assertThat(response.creditedGp()).isZero();
        assertThat(response.storedGp()).isEqualTo(25);
        then(territory).should().updateLastProducedAt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void creditsOnlyCapacityAcceptedByCombat() {
        LocalDateTime lastProducedAt = LocalDateTime.now().minusMinutes(10);
        Territory territory = territory(lastProducedAt);
        given(territoryRepository.findByIdWithDetails(1L)).willReturn(Optional.of(territory));
        given(bonusTileRepository.findByTerritoryId(1L)).willReturn(Optional.empty());
        given(
                        territoryRepository.countAdjacentOccupiedByOwner(
                                3, 4, 7L, 1L, Territory.TerritoryStatus.OCCUPIED))
                .willReturn(0);
        given(combatResourceClient.creditIncome(1L, 100, "TERRITORY_INCOME:1:" + lastProducedAt))
                .willReturn(new CreditIncomeResponse(80, 100, 100));

        CollectTerritoryResponse response = incomeService.collect(7L, 1L);

        assertThat(response.creditedGp()).isEqualTo(80);
        assertThat(response.storedGp()).isEqualTo(100);
        then(productionLogRepository).should().save(org.mockito.ArgumentMatchers.any());
    }

    private Territory territory(LocalDateTime lastProducedAt) {
        User owner = org.mockito.Mockito.mock(User.class);
        given(owner.getId()).willReturn(7L);
        TerritoryGrade grade = org.mockito.Mockito.mock(TerritoryGrade.class);
        given(grade.getProductionMultiplier()).willReturn(BigDecimal.ONE);
        Territory territory = org.mockito.Mockito.mock(Territory.class);
        given(territory.getId()).willReturn(1L);
        given(territory.getOwner()).willReturn(owner);
        given(territory.getStatus()).willReturn(Territory.TerritoryStatus.OCCUPIED);
        given(territory.getOccupiedUntil()).willReturn(LocalDateTime.now().plusDays(1));
        given(territory.getLastProducedAt()).willReturn(lastProducedAt);
        given(territory.getBaseProductionRate()).willReturn(10);
        given(territory.getGrade()).willReturn(grade);
        given(territory.getCoordX()).willReturn(3);
        given(territory.getCoordY()).willReturn(4);
        return territory;
    }
}
