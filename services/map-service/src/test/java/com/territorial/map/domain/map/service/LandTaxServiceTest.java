package com.territorial.map.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.territorial.map.client.CombatResourceClient;
import com.territorial.map.client.NotificationClient;
import com.territorial.map.client.SeasonQueryClient;
import com.territorial.map.domain.map.dto.TaxStatusResponse;
import com.territorial.map.domain.map.entity.LandTaxLog;
import com.territorial.map.domain.map.entity.LandTaxLog.TaxStatus;
import com.territorial.map.domain.map.entity.Territory;
import com.territorial.map.domain.map.repository.LandTaxLogRepository;
import com.territorial.map.domain.map.repository.TerritoryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LandTaxServiceTest {

    @Mock private TerritoryRepository territoryRepository;
    @Mock private LandTaxLogRepository landTaxLogRepository;
    @Mock private SeasonQueryClient seasonQueryClient;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private NotificationClient notificationClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private LandTaxService landTaxService;

    private static final Long USER_ID = 7L;

    @DisplayName("현황 조회 — 캐시 미스 시 보유 수·면제 구간으로 과세액을 계산하고 캐시에 적재한다")
    @Test
    void getLandTaxStatus_cacheMiss_computesAndCaches() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(territoryRepository.countByOwnerId(USER_ID)).thenReturn(5L);
        when(seasonQueryClient.getTaxExemptBonus(USER_ID)).thenReturn(0);

        TaxStatusResponse response = landTaxService.getLandTaxStatus(USER_ID);

        // 보유 5, 기본 면제 3 → 과세 2개 → 50 GP
        assertThat(response.territoryCount()).isEqualTo(5);
        assertThat(response.taxBreakdown().exemptCount()).isEqualTo(3);
        assertThat(response.taxBreakdown().taxableCount()).isEqualTo(2);
        assertThat(response.taxBreakdown().dailyGP()).isEqualTo(50);
        assertThat(response.effectiveExemptCount()).isEqualTo(3);
        assertThat(response.finalDailyGP()).isEqualTo(50);
        verify(valueOps).set(anyString(), eq(response), any());
    }

    @DisplayName("과세 처리 — 보유 영토가 없으면 아무 로그도 남기지 않고 종료한다")
    @Test
    void processUserTax_zeroTerritories_noLog() {
        when(territoryRepository.countByOwnerId(USER_ID)).thenReturn(0L);

        landTaxService.processUserTax(USER_ID);

        verify(landTaxLogRepository, never()).save(any());
        verify(combatResourceClient, never()).chargeTax(any(), org.mockito.ArgumentMatchers.anyInt(), anyList(), anyString());
    }

    @DisplayName("과세 처리 — 면제 범위 내면 과세액 0으로 EXEMPT 로그만 남긴다")
    @Test
    void processUserTax_withinExemption_savesExemptLog() {
        when(territoryRepository.countByOwnerId(USER_ID)).thenReturn(3L);
        when(seasonQueryClient.getTaxExemptBonus(USER_ID)).thenReturn(0);

        landTaxService.processUserTax(USER_ID);

        LandTaxLog saved = captureSavedLog();
        assertThat(saved.getStatus()).isEqualTo(TaxStatus.EXEMPT);
        assertThat(saved.getGpCharged()).isEqualTo(0);
        assertThat(saved.getTerritoryCount()).isEqualTo(3);
        verify(combatResourceClient, never()).chargeTax(any(), org.mockito.ArgumentMatchers.anyInt(), anyList(), anyString());
    }

    @DisplayName("과세 처리 — 금고/저장소에서 정상 수금되면 PAID 로그 + 납부 알림")
    @Test
    void processUserTax_paid_savesPaidLogAndNotifies() {
        when(territoryRepository.countByOwnerId(USER_ID)).thenReturn(5L);
        when(seasonQueryClient.getTaxExemptBonus(USER_ID)).thenReturn(0);
        Territory t = org.mockito.Mockito.mock(Territory.class);
        when(t.getId()).thenReturn(11L);
        when(territoryRepository.findAllOccupiedByOwnerId(
                        USER_ID, Territory.TerritoryStatus.OCCUPIED))
                .thenReturn(List.of(t));
        when(combatResourceClient.chargeTax(eq(USER_ID), eq(50), anyList(), anyString()))
                .thenReturn(true);

        landTaxService.processUserTax(USER_ID);

        LandTaxLog saved = captureSavedLog();
        assertThat(saved.getStatus()).isEqualTo(TaxStatus.PAID);
        assertThat(saved.getGpCharged()).isEqualTo(50);
        verify(notificationClient).sendNotification(eq(USER_ID), eq("TAX_CHARGED"), anyString());
    }

    private LandTaxLog captureSavedLog() {
        ArgumentCaptor<LandTaxLog> captor = ArgumentCaptor.forClass(LandTaxLog.class);
        verify(landTaxLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
