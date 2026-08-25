package com.territorial.auction.domain.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.map.dto.CollectTerritoryResponse;
import com.territorial.auction.domain.map.entity.BonusTile;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.entity.TerritoryGrade;
import com.territorial.auction.domain.map.entity.TerritoryProductionLog;
import com.territorial.auction.domain.map.repository.BonusTileRepository;
import com.territorial.auction.domain.map.repository.TerritoryProductionLogRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TerritoryIncomeServiceTest {

    @InjectMocks private TerritoryIncomeService territoryIncomeService;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BonusTileRepository bonusTileRepository;
    @Mock private TerritoryProductionLogRepository productionLogRepository;
    @Mock private UserRepository userRepository;

    @Mock
    private com.territorial.auction.domain.notification.service.NotificationService
            notificationService;

    private Territory territory;
    private User owner;

    @BeforeEach
    void setUp() {
        TerritoryGrade grade =
                TerritoryGrade.builder()
                        .grade("B")
                        .productionMultiplier(BigDecimal.ONE)
                        .auctionPriceMultiplier(BigDecimal.ONE)
                        .preBuiltCount(0)
                        .spawnRate(new BigDecimal("0.100"))
                        .gridSize(8)
                        .build();

        owner =
                User.builder()
                        .username("user1")
                        .email("user1@test.com")
                        .passwordHash("hash")
                        .nickname("테스터")
                        .build();
        ReflectionTestUtils.setField(owner, "id", 1L);

        territory = Territory.builder().coordX(5).coordY(5).grade(grade).build();
        ReflectionTestUtils.setField(territory, "id", 10L);
        // 이 테스트는 생산 공식을 검증한다 — 엔티티 기본값과 무관하게 base=1로 고정.
        ReflectionTestUtils.setField(territory, "baseProductionRate", 1);
        ReflectionTestUtils.setField(territory, "owner", owner);
        ReflectionTestUtils.setField(territory, "status", Territory.TerritoryStatus.OCCUPIED);
        ReflectionTestUtils.setField(territory, "occupiedUntil", LocalDateTime.now().plusDays(1));
    }

    // ─── 위치 저장소 픽스처 (성·저장소) ────────────────────────────────────────

    private BuildingInstance storageBuilding(int level, int storedGp) {
        BuildingType bt =
                BuildingType.builder()
                        .name("STORAGE")
                        .width(1)
                        .height(1)
                        .maxHp(60)
                        .baseCostGp(500)
                        .build();
        return locationStorage(bt, level, storedGp);
    }

    private BuildingInstance castleBuilding(int level, int storedGp) {
        BuildingType bt =
                BuildingType.builder()
                        .name("CASTLE")
                        .width(2)
                        .height(2)
                        .maxHp(100)
                        .baseCostGp(1000)
                        .build();
        return locationStorage(bt, level, storedGp);
    }

    private BuildingInstance locationStorage(BuildingType bt, int level, int storedGp) {
        BuildingInstance b =
                BuildingInstance.builder()
                        .buildingType(bt)
                        .territory(territory)
                        .posX(0)
                        .posY(0)
                        .hp(bt.getMaxHp())
                        .zone(2)
                        .build();
        ReflectionTestUtils.setField(b, "level", level);
        ReflectionTestUtils.setField(b, "storedGp", storedGp);
        return b;
    }

    @Nested
    class Collect {

        @Test
        @DisplayName("정상 수령 → creditedGp 적립 + BASE 로그 저장")
        void collect_success() {
            // given — base=1, grade=1.0, 보너스 없음, elapsed=60분 → 60GP
            ReflectionTestUtils.setField(
                    territory, "lastProducedAt", LocalDateTime.now().minusMinutes(60));

            BuildingInstance storage = storageBuilding(2, 0); // 용량 10,000

            given(territoryRepository.findByIdWithDetails(10L)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storage));
            given(bonusTileRepository.findByTerritoryId(10L)).willReturn(Optional.empty());
            given(
                            territoryRepository.countAdjacentOccupiedByOwner(
                                    5, 5, 1L, 10L, Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(0);
            given(userRepository.getReferenceById(1L)).willReturn(owner);

            // when
            CollectTerritoryResponse response = territoryIncomeService.collect(1L, 10L);

            // then
            assertThat(response.creditedGp()).isEqualTo(60);
            assertThat(response.productionRatePerMin()).isEqualTo(1);
            assertThat(response.storageCapacity()).isEqualTo(10_000);
            assertThat(storage.getStoredGp()).isEqualTo(60);
            // 보너스 없음 → BASE 로그 1건
            then(productionLogRepository).should(times(1)).save(any(TerritoryProductionLog.class));
        }

        @Test
        @DisplayName("성·저장소 합산 → 용량 합산, 적립은 저장소부터 채운다")
        void collect_castleAndStorage_fillsStorageFirst() {
            // given — 성 Lv1(5,000) + 저장소 Lv1(5,000), elapsed=60분 → 60GP
            ReflectionTestUtils.setField(
                    territory, "lastProducedAt", LocalDateTime.now().minusMinutes(60));

            BuildingInstance castle = castleBuilding(1, 0);
            BuildingInstance storage = storageBuilding(1, 0);

            given(territoryRepository.findByIdWithDetails(10L)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(castle, storage));
            given(bonusTileRepository.findByTerritoryId(10L)).willReturn(Optional.empty());
            given(
                            territoryRepository.countAdjacentOccupiedByOwner(
                                    5, 5, 1L, 10L, Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(0);
            given(userRepository.getReferenceById(1L)).willReturn(owner);

            // when
            CollectTerritoryResponse response = territoryIncomeService.collect(1L, 10L);

            // then
            assertThat(response.creditedGp()).isEqualTo(60);
            assertThat(response.storageCapacity()).isEqualTo(10_000);
            assertThat(storage.getStoredGp()).isEqualTo(60); // 저장소부터 채움
            assertThat(castle.getStoredGp()).isEqualTo(0);
        }

        @Test
        @DisplayName("저장소 없음 → STORAGE_NOT_FOUND 예외")
        void collect_noStorage_throwsStorageNotFound() {
            // given
            given(territoryRepository.findByIdWithDetails(10L)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of());

            // when / then
            assertThatThrownBy(() -> territoryIncomeService.collect(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.STORAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("소유자 불일치 → NOT_TERRITORY_OWNER 예외")
        void collect_notOwner_throwsNotTerritoryOwner() {
            // given
            given(territoryRepository.findByIdWithDetails(10L)).willReturn(Optional.of(territory));

            // when / then — userId 99L은 소유자 1L과 다름
            assertThatThrownBy(() -> territoryIncomeService.collect(99L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("점유 상태 아님 → TERRITORY_NOT_OCCUPIED 예외")
        void collect_notOccupied_throwsTerritoryNotOccupied() {
            // given — status만 IDLE로 변경, owner는 유지 → validateOwner 통과 후 validateOccupied 실패
            ReflectionTestUtils.setField(territory, "status", Territory.TerritoryStatus.IDLE);

            given(territoryRepository.findByIdWithDetails(10L)).willReturn(Optional.of(territory));

            // when / then
            assertThatThrownBy(() -> territoryIncomeService.collect(1L, 10L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_OCCUPIED);
        }

        @Test
        @DisplayName("저장소 용량 가득 → creditedGp=0, 로그 미저장")
        void collect_storageAtCapacity_creditedGpIsZero() {
            // given — 저장소 Lv1, 용량 5,000, storedGp 5,000 (가득)
            ReflectionTestUtils.setField(
                    territory, "lastProducedAt", LocalDateTime.now().minusMinutes(60));

            BuildingInstance storage = storageBuilding(1, 5_000);

            given(territoryRepository.findByIdWithDetails(10L)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storage));
            given(bonusTileRepository.findByTerritoryId(10L)).willReturn(Optional.empty());
            given(
                            territoryRepository.countAdjacentOccupiedByOwner(
                                    5, 5, 1L, 10L, Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(0);

            // when
            CollectTerritoryResponse response = territoryIncomeService.collect(1L, 10L);

            // then
            assertThat(response.creditedGp()).isEqualTo(0);
            assertThat(storage.getStoredGp()).isEqualTo(5_000);
            then(productionLogRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("경과 시간 1분 미만 → creditedGp=0")
        void collect_elapsedLessThanOneMinute_creditedGpIsZero() {
            // given — lastProducedAt 30초 전
            ReflectionTestUtils.setField(
                    territory, "lastProducedAt", LocalDateTime.now().minusSeconds(30));

            BuildingInstance storage = storageBuilding(2, 0);

            given(territoryRepository.findByIdWithDetails(10L)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storage));
            given(bonusTileRepository.findByTerritoryId(10L)).willReturn(Optional.empty());
            given(
                            territoryRepository.countAdjacentOccupiedByOwner(
                                    5, 5, 1L, 10L, Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(0);

            // when
            CollectTerritoryResponse response = territoryIncomeService.collect(1L, 10L);

            // then
            assertThat(response.creditedGp()).isEqualTo(0);
            assertThat(storage.getStoredGp()).isEqualTo(0);
        }
    }

    @Nested
    class CalculateEffectiveRate {

        @Test
        @DisplayName("인접 2칸 소유 → effectiveRate에 20% 보너스 적용")
        void calculateEffectiveRate_withAdjacentBonus() {
            // given — base=1, grade=1.0, adjacent=2 → rate = floor(1.0 × 1.2) = 1
            given(bonusTileRepository.findByTerritoryId(10L)).willReturn(Optional.empty());
            given(
                            territoryRepository.countAdjacentOccupiedByOwner(
                                    5, 5, 1L, 10L, Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(2);

            // when
            int rate = territoryIncomeService.calculateEffectiveRate(territory);

            // then
            assertThat(rate).isEqualTo(1);
        }

        @Test
        @DisplayName("BonusTile 배율 2.0 적용 → effectiveRate 2배")
        void calculateEffectiveRate_withBonusTile() {
            // given
            BonusTile bonusTile = mock(BonusTile.class);
            given(bonusTile.getMultiplier()).willReturn(new BigDecimal("2.00"));

            given(bonusTileRepository.findByTerritoryId(10L)).willReturn(Optional.of(bonusTile));
            given(
                            territoryRepository.countAdjacentOccupiedByOwner(
                                    5, 5, 1L, 10L, Territory.TerritoryStatus.OCCUPIED))
                    .willReturn(0);

            // when
            int rate = territoryIncomeService.calculateEffectiveRate(territory);

            // then — floor(1 × 1.0 × 2.0 × 1.0) = 2
            assertThat(rate).isEqualTo(2);
        }

        @Test
        @DisplayName("소유자 없음(비점유) → effectiveRate=0")
        void calculateEffectiveRate_noOwner_returnsZero() {
            // given
            ReflectionTestUtils.setField(territory, "owner", null);

            // when
            int rate = territoryIncomeService.calculateEffectiveRate(territory);

            // then
            assertThat(rate).isEqualTo(0);
        }
    }
}
