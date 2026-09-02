package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.map.LandTaxPolicy;
import com.territorial.auction.domain.map.dto.TaxLogResponse;
import com.territorial.auction.domain.map.dto.TaxStatusResponse;
import com.territorial.auction.domain.map.entity.LandTaxLog;
import com.territorial.auction.domain.map.entity.LandTaxLog.TaxStatus;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.repository.LandTaxLogRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.notification.NotificationType;
import com.territorial.auction.domain.notification.service.NotificationService;
import com.territorial.auction.domain.season.entity.SeasonPass;
import com.territorial.auction.domain.season.entity.UserSeasonPass;
import com.territorial.auction.domain.season.repository.UserSeasonPassRepository;
import com.territorial.auction.domain.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LandTaxServiceTest {

    @InjectMocks private LandTaxService landTaxService;

    @Mock private TerritoryRepository territoryRepository;
    @Mock private LandTaxLogRepository landTaxLogRepository;
    @Mock private UserSeasonPassRepository userSeasonPassRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUpRedis() {
        // getLandTaxLogs() 테스트는 Redis를 사용하지 않으므로 lenient로 선언
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(any())).thenReturn(null); // 기본 캐시 미스
    }

    // ─── 공통 픽스처 ─────────────────────────────────────────────────────────

    private UserSeasonPass activeSeasonPass(int taxExemptBonus) {
        SeasonPass seasonPass = SeasonPass.builder().taxExemptBonus(taxExemptBonus).build();

        return UserSeasonPass.builder()
                .seasonPass(seasonPass)
                .startedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    private LandTaxLog taxLog(Long id, int count, int gp, TaxStatus status) {
        LandTaxLog log =
                LandTaxLog.builder()
                        .user(null)
                        .territoryCount(count)
                        .gpCharged(gp)
                        .status(status)
                        .chargedAt(LocalDateTime.now().minusDays(1))
                        .build();
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }

    // ─── getLandTaxStatus() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getLandTaxStatus()")
    class GetLandTaxStatus {

        @Test
        @DisplayName("영토 없음 - 면제, 세금 0")
        void noTerritories_exempt() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            assertThat(response.territoryCount()).isEqualTo(0);
            assertThat(response.taxBreakdown().taxableCount()).isEqualTo(0);
            assertThat(response.taxBreakdown().dailyGP()).isEqualTo(0);
            assertThat(response.finalDailyGP()).isEqualTo(0);
        }

        @Test
        @DisplayName("영토 3개 이하 - 기본 면제 구간")
        void threeOrFewer_exempt() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(3L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            assertThat(response.territoryCount()).isEqualTo(3);
            assertThat(response.taxBreakdown().exemptCount())
                    .isEqualTo(LandTaxPolicy.BASE_EXEMPT_COUNT);
            assertThat(response.taxBreakdown().taxableCount()).isEqualTo(0);
            assertThat(response.taxBreakdown().dailyGP()).isEqualTo(0);
            assertThat(response.finalDailyGP()).isEqualTo(0);
        }

        @Test
        @DisplayName("영토 4개, 패스 없음 - 1단계 과세 (50 GP/일)")
        void fourTerritories_noPass_tier1Tax() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(4L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            // taxableCount = 4 - 3(exempt) = 1 → 1~3 taxable tier → 50 GP
            assertThat(response.taxBreakdown().taxableCount()).isEqualTo(1);
            assertThat(response.taxBreakdown().dailyGP()).isEqualTo(50);
            assertThat(response.seasonPassExemptBonus()).isEqualTo(0);
            assertThat(response.effectiveExemptCount()).isEqualTo(LandTaxPolicy.BASE_EXEMPT_COUNT);
            assertThat(response.finalDailyGP()).isEqualTo(50);
        }

        @Test
        @DisplayName("영토 7개, 패스 없음 - 2단계 과세 (150 GP/일)")
        void sevenTerritories_noPass_tier2Tax() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(7L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            // taxableCount = 7 - 3 = 4 → 4~7 taxable tier → 150 GP
            assertThat(response.taxBreakdown().taxableCount()).isEqualTo(4);
            assertThat(response.taxBreakdown().dailyGP()).isEqualTo(150);
            assertThat(response.finalDailyGP()).isEqualTo(150);
        }

        @Test
        @DisplayName("영토 11개, 패스 없음 - 3단계 과세 (400 GP/일)")
        void elevenTerritories_noPass_tier3Tax() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(11L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            // taxableCount = 11 - 3 = 8 → 8+ taxable tier → 400 GP
            assertThat(response.taxBreakdown().taxableCount()).isEqualTo(8);
            assertThat(response.taxBreakdown().dailyGP()).isEqualTo(400);
            assertThat(response.finalDailyGP()).isEqualTo(400);
        }

        @Test
        @DisplayName("영토 5개 + 유효한 시즌패스 - 패스 적용 시 면제")
        void fiveTerritories_withSeasonPass_finallyExempt() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(5L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(
                            Optional.of(activeSeasonPass(LandTaxPolicy.SEASON_PASS_EXEMPT_BONUS)));

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            // 패스 없이: taxableCount = 5-3 = 2 → dailyGP = 50
            assertThat(response.taxBreakdown().taxableCount()).isEqualTo(2);
            assertThat(response.taxBreakdown().dailyGP()).isEqualTo(50);

            // 패스 적용: effectiveExempt = 3+2 = 5, finalTaxable = 5-5 = 0 → finalDailyGP = 0
            assertThat(response.seasonPassExemptBonus())
                    .isEqualTo(LandTaxPolicy.SEASON_PASS_EXEMPT_BONUS);
            assertThat(response.effectiveExemptCount()).isEqualTo(5);
            assertThat(response.finalDailyGP()).isEqualTo(0);
        }

        @Test
        @DisplayName("영토 8개 + 유효한 시즌패스 - 세금 단계 낮아짐")
        void eightTerritories_withSeasonPass_lowerTier() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(8L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(
                            Optional.of(activeSeasonPass(LandTaxPolicy.SEASON_PASS_EXEMPT_BONUS)));

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            // 패스 없이: taxableCount = 8-3 = 5 → dailyGP = 150
            assertThat(response.taxBreakdown().dailyGP()).isEqualTo(150);

            // 패스 적용: effectiveExempt = 5, finalTaxable = 8-5 = 3 → 1~3 tier → finalDailyGP = 50
            assertThat(response.finalDailyGP()).isEqualTo(50);
        }

        @Test
        @DisplayName("만료된 시즌패스 - 패스 없음으로 처리")
        void expiredSeasonPass_treatedAsNoPass() {
            UserSeasonPass expiredPass = mock(UserSeasonPass.class);
            given(expiredPass.getExpiresAt()).willReturn(LocalDateTime.now().minusMinutes(1));

            given(territoryRepository.countByOwnerId(1L)).willReturn(5L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.of(expiredPass));

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            assertThat(response.seasonPassExemptBonus()).isEqualTo(0);
            assertThat(response.effectiveExemptCount()).isEqualTo(LandTaxPolicy.BASE_EXEMPT_COUNT);
            // 패스 없음 기준: taxableCount = 5-3 = 2 → finalDailyGP = 50
            assertThat(response.finalDailyGP()).isEqualTo(50);
            // 만료 패스의 SeasonPass는 조회하지 않아야 함
            then(expiredPass).should(never()).getSeasonPass();
        }

        @Test
        @DisplayName("nextChargeAt은 현재 시각 이후")
        void nextChargeAt_isInFuture() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);
            given(userSeasonPassRepository.findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .willReturn(Optional.empty());

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            assertThat(response.nextChargeAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Redis 캐시 히트 시 DB 조회 없이 캐시 값 반환")
        void cacheHit_returnsWithoutDbQuery() {
            TaxStatusResponse cached =
                    new TaxStatusResponse(
                            2,
                            new TaxStatusResponse.TaxBreakdown(2, 0, 0),
                            0,
                            3,
                            0,
                            LocalDateTime.now().plusHours(3));

            given(valueOperations.get("land_tax:expected:1")).willReturn(cached);

            TaxStatusResponse response = landTaxService.getLandTaxStatus(1L);

            assertThat(response).isEqualTo(cached);
            then(territoryRepository).should(never()).countByOwnerId(any());
        }
    }

    // ─── getLandTaxLogs() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getLandTaxLogs()")
    class GetLandTaxLogs {

        @Test
        @DisplayName("이력 없음 - totalCount=0, 빈 로그 리스트")
        void noLogs_returnsEmpty() {
            Pageable pageable = PageRequest.of(0, 20);
            given(landTaxLogRepository.findByUserIdOrderByChargedAtDesc(eq(1L), any()))
                    .willReturn(new PageImpl<>(Collections.emptyList()));

            TaxLogResponse response = landTaxService.getLandTaxLogs(1L, null, pageable);

            assertThat(response.totalCount()).isEqualTo(0);
            assertThat(response.logs()).isEmpty();
        }

        @Test
        @DisplayName("status=null이면 전체 이력을 chargedAt 내림차순으로 조회")
        void nullStatus_queriesAll() {
            Pageable pageable = PageRequest.of(0, 20);
            LandTaxLog log1 = taxLog(1L, 5, 50, TaxStatus.PAID);
            LandTaxLog log2 = taxLog(2L, 3, 0, TaxStatus.EXEMPT);
            given(landTaxLogRepository.findByUserIdOrderByChargedAtDesc(eq(1L), any()))
                    .willReturn(new PageImpl<>(List.of(log1, log2)));

            TaxLogResponse response = landTaxService.getLandTaxLogs(1L, null, pageable);

            assertThat(response.totalCount()).isEqualTo(2);
            assertThat(response.logs()).hasSize(2);
            then(landTaxLogRepository)
                    .should(never())
                    .findByUserIdAndStatusOrderByChargedAtDesc(any(), any(), any());
        }

        @Test
        @DisplayName("status=PAID이면 상태 필터 쿼리 호출")
        void paidStatusFilter_queriesWithStatusFilter() {
            Pageable pageable = PageRequest.of(0, 20);
            LandTaxLog paidLog = taxLog(1L, 5, 50, TaxStatus.PAID);
            given(
                            landTaxLogRepository.findByUserIdAndStatusOrderByChargedAtDesc(
                                    eq(1L), eq(TaxStatus.PAID), any()))
                    .willReturn(new PageImpl<>(List.of(paidLog)));

            TaxLogResponse response = landTaxService.getLandTaxLogs(1L, TaxStatus.PAID, pageable);

            assertThat(response.totalCount()).isEqualTo(1);
            assertThat(response.logs()).hasSize(1);
            then(landTaxLogRepository)
                    .should(never())
                    .findByUserIdOrderByChargedAtDesc(any(), any());
        }

        @Test
        @DisplayName("로그 필드 매핑 정확성")
        void logFields_mappedCorrectly() {
            Pageable pageable = PageRequest.of(0, 20);
            LocalDateTime chargedAt = LocalDateTime.of(2026, 4, 8, 0, 0, 0);
            LandTaxLog log =
                    LandTaxLog.builder()
                            .user(null)
                            .territoryCount(7)
                            .gpCharged(150)
                            .status(TaxStatus.PAID)
                            .chargedAt(chargedAt)
                            .build();
            ReflectionTestUtils.setField(log, "id", 42L);

            given(landTaxLogRepository.findByUserIdOrderByChargedAtDesc(eq(1L), any()))
                    .willReturn(new PageImpl<>(List.of(log)));

            TaxLogResponse response = landTaxService.getLandTaxLogs(1L, null, pageable);

            TaxLogResponse.TaxLogItem item = response.logs().get(0);
            assertThat(item.logId()).isEqualTo(42L);
            assertThat(item.territoryCount()).isEqualTo(7);
            assertThat(item.gpCharged()).isEqualTo(150);
            assertThat(item.status()).isEqualTo(TaxStatus.PAID);
            assertThat(item.chargedAt()).isEqualTo(chargedAt);
        }

        @Test
        @DisplayName("페이지 파라미터가 Repository에 전달됨")
        void pageParams_passedToRepository() {
            Pageable pageable = PageRequest.of(2, 5);
            given(landTaxLogRepository.findByUserIdOrderByChargedAtDesc(eq(1L), any()))
                    .willReturn(new PageImpl<>(Collections.emptyList()));

            landTaxService.getLandTaxLogs(1L, null, pageable);

            then(landTaxLogRepository)
                    .should()
                    .findByUserIdOrderByChargedAtDesc(eq(1L), eq(PageRequest.of(2, 5)));
        }
    }

    // ─── processUserTax() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("processUserTax()")
    class ProcessUserTax {

        @BeforeEach
        void setUp() {
            lenient()
                    .when(
                            userSeasonPassRepository
                                    .findTopByUserIdAndIsActiveTrueOrderByStartedAtDesc(1L))
                    .thenReturn(Optional.empty());
        }

        private GlobalVault vaultWith(int gp) {
            GlobalVault vault = mock(GlobalVault.class);
            lenient().when(vault.getStoredGp()).thenReturn(gp);
            return vault;
        }

        private Territory mockTerritory(String gradeStr) {
            TerritoryGrade grade = mock(TerritoryGrade.class);
            given(grade.getGrade()).willReturn(gradeStr);
            Territory territory = mock(Territory.class);
            given(territory.getGrade()).willReturn(grade);
            return territory;
        }

        @Test
        @DisplayName("영토 없음 → 조기 반환, 로그 미저장")
        void processUserTax_noTerritories_returnsEarly() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(0L);

            landTaxService.processUserTax(1L);

            then(landTaxLogRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("면제 구간 영토 수 → EXEMPT 로그 저장, 금고 조회 없음")
        void processUserTax_exempt_savesExemptLog() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(3L);

            landTaxService.processUserTax(1L);

            then(landTaxLogRepository).should().save(any(LandTaxLog.class));
            then(globalVaultRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("금고 GP 충분 → PAID 로그 저장 + 금고 차감 + 유예기간 키 삭제")
        void processUserTax_gpSufficient_savesPaidLog() {
            // taxableCount = 4-3 = 1 → taxAmount = 50
            given(territoryRepository.countByOwnerId(1L)).willReturn(4L);
            GlobalVault vault = vaultWith(100);
            // 잔액 조회는 findById(읽기), 차감은 findByIdWithLock(쓰기 — 갱신 유실 방지 락)
            given(globalVaultRepository.findById(1L)).willReturn(Optional.of(vault));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(territoryRepository.findAllOccupiedByOwnerId(eq(1L), any()))
                    .willReturn(new ArrayList<>());

            landTaxService.processUserTax(1L);

            then(vault).should().withdrawGp(50);
            then(landTaxLogRepository).should().save(any(LandTaxLog.class));
            then(redisTemplate).should().delete("land_tax:grace:1");
        }

        @Test
        @DisplayName("금고·영토 저장소 부족 + 유예기간 키 존재 → FAILED 로그만 저장, 알림·강제 경매 없음")
        void processUserTax_inGrace_savesFailedLogOnly() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(4L);
            given(territoryRepository.findAllOccupiedByOwnerId(eq(1L), any()))
                    .willReturn(new ArrayList<>());
            given(redisTemplate.hasKey("land_tax:grace:1")).willReturn(true);

            landTaxService.processUserTax(1L);

            then(landTaxLogRepository).should().save(any(LandTaxLog.class));
            then(notificationService).should(never()).sendNotification(any(), any(), any());
        }

        @Test
        @DisplayName("금고·영토 저장소 부족 + 첫 실패 → FAILED 로그 + 유예기간 키 설정 + TAX_FAIL_WARNING 알림")
        void processUserTax_firstFail_setsGraceAndSendsWarning() {
            given(territoryRepository.countByOwnerId(1L)).willReturn(4L);
            given(redisTemplate.hasKey("land_tax:grace:1")).willReturn(false);
            given(
                            landTaxLogRepository.existsByUserIdAndStatusAndChargedAtAfter(
                                    eq(1L), eq(TaxStatus.FAILED), any(LocalDateTime.class)))
                    .willReturn(false);

            landTaxService.processUserTax(1L);

            then(landTaxLogRepository).should().save(any(LandTaxLog.class));
            then(valueOperations)
                    .should()
                    .set(
                            eq("land_tax:grace:1"),
                            eq(1),
                            eq(Duration.ofHours(LandTaxPolicy.GRACE_PERIOD_HOURS)));
            then(notificationService)
                    .should()
                    .sendNotification(
                            eq(1L), eq(NotificationType.TAX_FAIL_WARNING), any(String.class));
        }

        @Test
        @DisplayName("금고·영토 저장소 부족 + 유예기간 만료 → EVICTED 로그 + 낮은 등급(D)부터 강제 경매 + TAX_EVICTION 알림")
        void processUserTax_graceExpired_selectiveEviction() {
            // taxableCount = 4-3 = 1 → taxAmount = 50
            given(territoryRepository.countByOwnerId(1L)).willReturn(4L);
            given(redisTemplate.hasKey("land_tax:grace:1")).willReturn(false);
            given(
                            landTaxLogRepository.existsByUserIdAndStatusAndChargedAtAfter(
                                    eq(1L), eq(TaxStatus.FAILED), any(LocalDateTime.class)))
                    .willReturn(true);

            Territory territoryA = mockTerritory("A"); // start price 5000
            Territory territoryD =
                    mockTerritory("D"); // start price 500 ≥ taxAmount 50 → stops here
            given(
                            territoryRepository.findAllOccupiedByOwnerId(
                                    eq(1L), eq(Territory.TerritoryStatus.OCCUPIED)))
                    .willReturn(new ArrayList<>(List.of(territoryA, territoryD)));

            landTaxService.processUserTax(1L);

            // D가 먼저 경매 전환되고, D 낙찰 대금(500) >= taxAmount(50)이므로 A는 건드리지 않음
            then(territoryD).should().release(any(LocalDateTime.class));
            then(territoryA).should(never()).release(any());
            then(landTaxLogRepository).should().save(any(LandTaxLog.class));
            then(notificationService)
                    .should()
                    .sendNotification(
                            eq(1L), eq(NotificationType.TAX_EVICTION), eq("1개 영토가 강제 경매 전환됐습니다."));
        }
    }
}
