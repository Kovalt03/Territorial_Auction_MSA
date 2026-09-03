package com.territorial.combat.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.config.BuildingBalanceProperties;
import com.territorial.combat.domain.building.dto.InventoryResponse;
import com.territorial.combat.domain.building.dto.IslandResponse;
import com.territorial.combat.domain.building.dto.MoveBuildingRequest;
import com.territorial.combat.domain.building.dto.MoveBuildingResponse;
import com.territorial.combat.domain.building.dto.PlaceBuildingRequest;
import com.territorial.combat.domain.building.dto.PlaceBuildingResponse;
import com.territorial.combat.domain.building.dto.PlaceFromInventoryRequest;
import com.territorial.combat.domain.building.dto.PlaceFromInventoryResponse;
import com.territorial.combat.domain.building.dto.RepairAllResponse;
import com.territorial.combat.domain.building.dto.RepairBuildingResponse;
import com.territorial.combat.domain.building.dto.StoreBuildingResponse;
import com.territorial.combat.domain.building.dto.TerritoryBuildingResponse;
import com.territorial.combat.domain.building.dto.UpgradeBuildingResponse;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.entity.IslandGrade;
import com.territorial.combat.domain.building.port.BuildingNotificationPort;
import com.territorial.combat.domain.building.port.SeasonBenefitPort;
import com.territorial.combat.domain.building.port.SeasonBenefitPort.SeasonBenefit;
import com.territorial.combat.domain.building.port.TerritoryContextPort;
import com.territorial.combat.domain.building.port.TerritoryContextPort.TerritoryContext;
import com.territorial.combat.domain.building.port.WalletPort;
import com.territorial.combat.domain.building.port.WalletPort.WalletSnapshot;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.building.repository.IslandGradeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    @InjectMocks private BuildingService buildingService;

    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BuildingTypeRepository buildingTypeRepository;

    @Mock
    private com.territorial.combat.domain.building.repository.BuildingLevelSpecRepository
            buildingLevelSpecRepository;

    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private IslandGradeRepository islandGradeRepository;
    @Mock private TerritoryContextPort territoryContextPort;
    @Mock private CombatUserSnapshotRepository userSnapshotRepository;
    @Mock private WalletPort walletPort;
    @Mock private SeasonBenefitPort seasonBenefitPort;
    @Mock private BuildingNotificationPort notificationPort;

    @Mock
    private com.territorial.combat.domain.building.repository.BuildingCastleLimitRepository
            buildingCastleLimitRepository;

    @Mock private BuildingBalanceProperties balanceProperties;

    @org.junit.jupiter.api.BeforeEach
    void stubLevelSpecsEmpty() {
        org.mockito.Mockito.lenient()
                .when(
                        buildingLevelSpecRepository.findByBuildingType_IdAndLevel(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.lenient()
                .when(
                        buildingLevelSpecRepository.findAllByBuildingType_IdIn(
                                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        org.mockito.Mockito.lenient()
                .when(seasonBenefitPort.findActiveBenefit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(SeasonBenefit.none());
    }

    // ─── 공통 픽스처 ───────────────────────────────────────────────────────────

    private Long sampleUser(Long id) {
        return id;
    }

    // Lv2 STORAGE 건물 — 용량 10,000. 위치 저장소 스텁으로 사용한다.
    private BuildingInstance storageWithGp(int gp) {
        BuildingInstance b =
                BuildingInstance.builder()
                        .buildingType(storage())
                        .posX(0)
                        .posY(0)
                        .hp(60)
                        .zone(2)
                        .build();
        ReflectionTestUtils.setField(b, "level", 2);
        ReflectionTestUtils.setField(b, "storedGp", gp);
        return b;
    }

    private TerritoryContext gradeA() {
        return new TerritoryContext(10L, null, 10, 2, 4);
    }

    private TerritoryContext territoryOwnedBy(Long ownerId, TerritoryContext grade) {
        return new TerritoryContext(
                10L, ownerId, grade.gridSize(), grade.zone1Radius(), grade.zone2Radius());
    }

    private BuildingType castle() {
        BuildingType bt =
                BuildingType.builder()
                        .name("CASTLE")
                        .width(2)
                        .height(2)
                        .maxHp(100)
                        .baseCostGp(1000)
                        .zoneRestriction(1)
                        .build();
        ReflectionTestUtils.setField(bt, "id", 1L);
        return bt;
    }

    private BuildingType storage() {
        BuildingType bt =
                BuildingType.builder()
                        .name("STORAGE")
                        .width(1)
                        .height(1)
                        .maxHp(60)
                        .baseCostGp(500)
                        .zoneRestriction(null)
                        .build();
        ReflectionTestUtils.setField(bt, "id", 2L);
        return bt;
    }

    // 건설 시간이 지정된 건물 타입 — 배치 시 buildCompleteAt 이 설정된다.
    private BuildingType storageWithBuildTime(int seconds) {
        BuildingType bt = storage();
        ReflectionTestUtils.setField(bt, "buildTimeSeconds", seconds);
        return bt;
    }

    private BuildingInstance islandBuilding(BuildingType bt, HomeIsland island, long id) {
        BuildingInstance b =
                BuildingInstance.builder()
                        .buildingType(bt)
                        .island(island)
                        .posX(5)
                        .posY(5)
                        .hp(bt.getMaxHp())
                        .zone(2)
                        .build();
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }

    private BuildingInstance underConstruction(BuildingType bt, HomeIsland island, long id) {
        BuildingInstance b = islandBuilding(bt, island, id);
        b.startConstruction(LocalDateTime.now().plusMinutes(10));
        return b;
    }

    private BuildingInstance placedInstance(
            BuildingType bt, TerritoryContext territory, int posX, int posY) {
        org.mockito.Mockito.lenient()
                .when(territoryContextPort.findById(territory.territoryId()))
                .thenReturn(Optional.of(territory));
        BuildingInstance bi =
                BuildingInstance.builder()
                        .buildingType(bt)
                        .territoryId(territory.territoryId())
                        .posX(posX)
                        .posY(posY)
                        .hp(bt.getMaxHp())
                        .zone(1)
                        .build();
        ReflectionTestUtils.setField(bi, "id", 100L);
        ReflectionTestUtils.setField(bi, "level", 1);
        return bi;
    }

    private HomeIsland sampleIsland(Long userId) {
        HomeIsland island = HomeIsland.builder().userId(userId).build();
        ReflectionTestUtils.setField(island, "id", 1L);
        return island;
    }

    private IslandGrade islandGrade(String name, int gridSize, int z1, int z2, int castleLvl) {
        return IslandGrade.builder()
                .name(name)
                .gridSize(gridSize)
                .zone1Radius(z1)
                .zone2Radius(z2)
                .castleLevelRequired(castleLvl)
                .build();
    }

    private HomeIsland islandWithGrade(Long userId, IslandGrade grade) {
        HomeIsland island = HomeIsland.builder().userId(userId).islandGrade(grade).build();
        ReflectionTestUtils.setField(island, "id", 1L);
        return island;
    }

    // ─── findTerritoryBuildings() ──────────────────────────────────────────────

    @Nested
    @DisplayName("findTerritoryBuildings()")
    class FindTerritoryBuildings {

        @Test
        @DisplayName("영토 건물 목록 정상 반환")
        void success() {
            TerritoryContext territory = territoryOwnedBy(sampleUser(1L), gradeA());
            BuildingType bt = storage();
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);

            given(territoryContextPort.findById(10L)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findByTerritoryId(10L)).willReturn(List.of(bi));

            TerritoryBuildingResponse response = buildingService.findTerritoryBuildings(10L);

            assertThat(response.buildings()).hasSize(1);
            assertThat(response.buildings().get(0).posX()).isEqualTo(0);
        }

        @Test
        @DisplayName("존재하지 않는 영토 → TERRITORY_NOT_FOUND")
        void territory_not_found() {
            given(territoryContextPort.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> buildingService.findTerritoryBuildings(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
        }
    }

    // ─── placeOnTerritory() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("placeOnTerritory()")
    class PlaceOnTerritory {

        @Test
        @DisplayName("영토 점유자가 건물 배치 성공")
        void success() {
            Long user = sampleUser(1L);
            TerritoryContext grade = gradeA();
            TerritoryContext territory = territoryOwnedBy(user, grade);
            BuildingType bt = storage();

            given(territoryContextPort.findById(10L)).willReturn(Optional.of(territory));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByTerritoryId(10L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(2000)));
            given(buildingInstanceRepository.save(any()))
                    .willAnswer(
                            inv -> {
                                BuildingInstance saved = inv.getArgument(0);
                                ReflectionTestUtils.setField(saved, "id", 200L);
                                return saved;
                            });

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            PlaceBuildingResponse response = buildingService.placeOnTerritory(1L, 10L, req);

            assertThat(response.buildingId()).isEqualTo(200L);
            assertThat(response.posX()).isEqualTo(0);
        }

        @Test
        @DisplayName("영토 점유자가 아님 → NOT_TERRITORY_OWNER")
        void not_owner() {
            Long owner = sampleUser(1L);
            Long other = sampleUser(2L);
            TerritoryContext territory = territoryOwnedBy(owner, gradeA());

            given(territoryContextPort.findById(10L)).willReturn(Optional.of(territory));

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeOnTerritory(2L, 10L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("GP 부족 → INSUFFICIENT_GP")
        void insufficient_gp() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // baseCostGp=500

            given(territoryContextPort.findById(10L)).willReturn(Optional.of(territory));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByTerritoryId(10L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(100)));

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeOnTerritory(1L, 10L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_GP);
        }

        @Test
        @DisplayName("겹치는 위치 배치 → INVALID_POSITION")
        void overlapping_position() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // 1x1

            BuildingInstance existing = placedInstance(bt, territory, 0, 0);

            given(territoryContextPort.findById(10L)).willReturn(Optional.of(territory));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByTerritoryId(10L)).willReturn(List.of(existing));

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0); // same position
            assertThatThrownBy(() -> buildingService.placeOnTerritory(1L, 10L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_POSITION);
        }

        @Test
        @DisplayName("Castle을 zone1이 아닌 곳에 배치 → ZONE_RESTRICTION_VIOLATED")
        void zone_restriction_violated() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA()); // gridSize=10
            BuildingType castleType = castle(); // zoneRestriction=1

            given(territoryContextPort.findById(10L)).willReturn(Optional.of(territory));
            given(buildingTypeRepository.findById(1L)).willReturn(Optional.of(castleType));
            given(buildingInstanceRepository.findByTerritoryId(10L))
                    .willReturn(Collections.emptyList());

            // posX=0, posY=0 is in zone 2-3, castle needs zone 1
            PlaceBuildingRequest req = new PlaceBuildingRequest(1L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeOnTerritory(1L, 10L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ZONE_RESTRICTION_VIOLATED);
        }
    }

    // ─── upgrade() ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upgrade()")
    class Upgrade {

        @Test
        @DisplayName("건설 중인 건물은 업그레이드 불가 → BUILDING_UNDER_CONSTRUCTION")
        void under_construction_cannot_upgrade() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingInstance bi = placedInstance(storage(), territory, 0, 0);
            bi.startConstruction(LocalDateTime.now().plusMinutes(5));

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));

            assertThatThrownBy(() -> buildingService.upgrade(1L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_UNDER_CONSTRUCTION);
        }

        @Test
        @DisplayName("레벨 1 → 2 업그레이드 성공 → newLevel=2, nextLevel=3, upgradeCost=500")
        void success() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // baseCostGp=500
            BuildingInstance bi = placedInstance(bt, territory, 0, 0); // level=1

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(2000)));

            UpgradeBuildingResponse response = buildingService.upgrade(1L, 100L);

            assertThat(response.newLevel()).isEqualTo(2);
            assertThat(response.nextLevel()).isEqualTo(3);
            assertThat(response.maxLevel()).isEqualTo(3);
            assertThat(response.upgradeCost()).isEqualTo(500); // baseCostGp(500) × level(1)
            assertThat(bi.getHp()).isEqualTo(120); // maxHp(60) × newLevel(2)
        }

        @Test
        @DisplayName("레벨 2 → 3 업그레이드 성공 → nextLevel=null (최대 레벨)")
        void success_toMaxLevel() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // baseCostGp=500
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);
            ReflectionTestUtils.setField(bi, "level", 2);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(2000)));

            UpgradeBuildingResponse response = buildingService.upgrade(1L, 100L);

            assertThat(response.newLevel()).isEqualTo(3);
            assertThat(response.nextLevel()).isNull();
            assertThat(response.upgradeCost()).isEqualTo(1000); // baseCostGp(500) × level(2)
        }

        @Test
        @DisplayName("최대 레벨(3) 도달 → BUILDING_MAX_LEVEL")
        void max_level_reached() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage();
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);
            ReflectionTestUtils.setField(bi, "level", 3);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));

            assertThatThrownBy(() -> buildingService.upgrade(1L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_MAX_LEVEL);
        }

        @Test
        @DisplayName("건물 없음 → BUILDING_NOT_FOUND")
        void not_found() {
            given(buildingInstanceRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> buildingService.upgrade(1L, 999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_NOT_FOUND);
        }

        @Test
        @DisplayName("점유자 아님 → NOT_TERRITORY_OWNER")
        void not_owner() {
            Long owner = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(owner, gradeA());
            BuildingType bt = storage();
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));

            assertThatThrownBy(() -> buildingService.upgrade(2L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("GP 부족 → INSUFFICIENT_GP")
        void insufficient_gp() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // baseCostGp=500, level1 upgrade cost=500
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(100)));

            assertThatThrownBy(() -> buildingService.upgrade(1L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_GP);
        }
    }

    // ─── repair() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("repair()")
    class Repair {

        @org.junit.jupiter.api.BeforeEach
        void stubRepairRate() {
            org.mockito.Mockito.lenient().when(balanceProperties.repairGpPerHp()).thenReturn(2);
        }

        @Test
        @DisplayName("손상 건물 수리 시작 → GP 선차감 + 수리 타이머 설정(즉시 풀피 아님)")
        void success() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // maxHp=60
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);
            ReflectionTestUtils.setField(bi, "hp", 0); // 손상 60

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(2000)));

            RepairBuildingResponse response = buildingService.repair(1L, 100L);

            // 손상 60 × 2 GP/HP = 120 차감. HP는 아직 안 오르고(완료 시), 수리 타이머·플래그 설정.
            assertThat(response.hp()).isEqualTo(0);
            assertThat(response.gpRemaining()).isEqualTo(2000 - 120);
            assertThat(response.buildCompleteAt()).isNotNull();
            assertThat(bi.isRepairing()).isTrue();
            assertThat(bi.isUnderConstruction(java.time.LocalDateTime.now())).isTrue();
        }

        @Test
        @DisplayName("파괴되지 않은 손상 건물도 수리 시작 → 손상분 GP 차감")
        void damaged_notDestroyed() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // maxHp=60
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);
            ReflectionTestUtils.setField(bi, "hp", 40); // 손상 20

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(1000)));

            RepairBuildingResponse response = buildingService.repair(1L, 100L);

            assertThat(response.gpRemaining()).isEqualTo(1000 - 40); // (60-40) × 2
            assertThat(bi.isRepairing()).isTrue();
        }

        @Test
        @DisplayName("레벨2 건물 수리 시작 → 손상 = baseMaxHp×2 기준 GP 차감")
        void success_level2() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // maxHp=60
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);
            ReflectionTestUtils.setField(bi, "level", 2);
            ReflectionTestUtils.setField(bi, "hp", 0);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(2000)));

            RepairBuildingResponse response = buildingService.repair(1L, 100L);

            // fullHp = 60 × 2 = 120 → 손상 120 × 2 GP = 240 차감
            assertThat(response.gpRemaining()).isEqualTo(2000 - 240);
            assertThat(bi.isRepairing()).isTrue();
        }

        @Test
        @DisplayName("풀피 건물 수리 시도 → BUILDING_ALREADY_FULL_HP")
        void full_hp() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage(); // maxHp=60
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);
            ReflectionTestUtils.setField(bi, "hp", bt.getMaxHp()); // 풀피

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));

            assertThatThrownBy(() -> buildingService.repair(1L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_ALREADY_FULL_HP);
        }

        @Test
        @DisplayName("수리 완료(finishConstruction) → 파괴·수리 플래그 해제")
        void finishRepair_clearsDestroyedAndRepairing() {
            BuildingInstance bi =
                    placedInstance(storage(), territoryOwnedBy(sampleUser(1L), gradeA()), 0, 0);
            ReflectionTestUtils.setField(bi, "isDestroyed", true);
            ReflectionTestUtils.setField(bi, "hp", 0);
            bi.startRepair(java.time.LocalDateTime.now().minusSeconds(1)); // 이미 완료 시각

            bi.finishConstruction();

            assertThat(bi.isDestroyed()).isFalse();
            assertThat(bi.isRepairing()).isFalse();
            assertThat(bi.isUnderConstruction(java.time.LocalDateTime.now())).isFalse();
        }
    }

    @Nested
    @DisplayName("repairAll()")
    class RepairAll {

        @Test
        @DisplayName("영토 전체 수리도 territory port 소유권으로 판정한다")
        void territoryUsesOwnerFromContextPort() {
            TerritoryContext territory = territoryOwnedBy(1L, gradeA());
            BuildingInstance damaged = placedInstance(storage(), territory, 0, 0);
            ReflectionTestUtils.setField(damaged, "hp", 40);
            given(buildingInstanceRepository.findByTerritoryId(10L)).willReturn(List.of(damaged));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(1000)));
            given(balanceProperties.repairGpPerHp()).willReturn(2);

            RepairAllResponse response = buildingService.repairAll(1L, "TERRITORY", 10L);

            assertThat(response.repairedCount()).isEqualTo(1);
            assertThat(response.totalCost()).isEqualTo(40);
            assertThat(response.gpRemaining()).isEqualTo(960);
            assertThat(damaged.isRepairing()).isTrue();
        }
    }

    // ─── getIsland() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getIsland()")
    class GetIsland {

        @Test
        @DisplayName("섬 정보 정상 반환")
        void success() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());

            IslandResponse response = buildingService.getIsland(1L);

            assertThat(response.islandId()).isEqualTo(1L);
            assertThat(response.buildings()).isEmpty();
        }

        @Test
        @DisplayName("섬 없음 → ISLAND_NOT_FOUND")
        void not_found() {
            given(homeIslandRepository.findByUserId(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> buildingService.getIsland(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ISLAND_NOT_FOUND);
        }
    }

    // ─── placeOnIsland() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("placeOnIsland()")
    class PlaceOnIsland {

        @Test
        @DisplayName("섬 건물 배치 성공 — 패스 없음, 슬롯 1개, 기존 건물 없음")
        void success() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(2000)));
            given(buildingInstanceRepository.save(any()))
                    .willAnswer(
                            inv -> {
                                BuildingInstance saved = inv.getArgument(0);
                                ReflectionTestUtils.setField(saved, "id", 201L);
                                return saved;
                            });

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            PlaceBuildingResponse response = buildingService.placeOnIsland(1L, req);

            assertThat(response.buildingId()).isEqualTo(201L);
        }

        @Test
        @DisplayName("GP 부족 → INSUFFICIENT_GP")
        void insufficient_gp() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(10)));

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeOnIsland(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_GP);
        }

        // 섬 D등급: gridSize=10, zone1Radius=2 → Zone1은 (3..6, 3..6)
        @Test
        @DisplayName("2×2 성이 Zone1을 벗어나 걸치면 → ZONE_RESTRICTION_VIOLATED")
        void castle_footprint_must_fit_in_zone() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(1L)).willReturn(Optional.of(castle()));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());

            // 원점 (6,6)은 Zone1이지만 (7,7)까지 차지하므로 Zone2를 침범한다
            PlaceBuildingRequest req = new PlaceBuildingRequest(1L, 6, 6);
            assertThatThrownBy(() -> buildingService.placeOnIsland(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ZONE_RESTRICTION_VIOLATED);
        }

        @Test
        @DisplayName("섬에 성이 이미 있으면 → CASTLE_ALREADY_EXISTS")
        void only_one_castle_per_island() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(1L)).willReturn(Optional.of(castle()));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.existsCastleOnIsland(1L)).willReturn(true);

            // (3,3)~(4,4) 는 모두 Zone1 — Zone 제약은 통과하고 성 중복에서 걸린다
            PlaceBuildingRequest req = new PlaceBuildingRequest(1L, 3, 3);
            assertThatThrownBy(() -> buildingService.placeOnIsland(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CASTLE_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("성 레벨 상한에 도달한 건물 → BUILDING_LIMIT_EXCEEDED")
        void building_limit_exceeded() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.findCastleLevelByIslandId(1L))
                    .willReturn(Optional.of(1));
            given(buildingCastleLimitRepository.findByBuildingType_IdAndCastleLevel(2L, 1))
                    .willReturn(
                            Optional.of(
                                    com.territorial.combat.domain.building.entity
                                            .BuildingCastleLimit.builder()
                                            .buildingType(bt)
                                            .castleLevel(1)
                                            .maxCount(2)
                                            .build()));
            given(buildingInstanceRepository.countByIslandIdAndBuildingTypeId(1L, 2L))
                    .willReturn(2L); // 이미 2개 → 상한 도달

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeOnIsland(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("상한이 설정되지 않은 건물은 개수 제한 없음")
        void no_limit_configured_allows_placement() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingCastleLimitRepository.findByBuildingType_IdAndCastleLevel(2L, 1))
                    .willReturn(Optional.empty());
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(2000)));
            given(buildingInstanceRepository.save(any()))
                    .willAnswer(
                            inv -> {
                                BuildingInstance saved = inv.getArgument(0);
                                ReflectionTestUtils.setField(saved, "id", 210L);
                                return saved;
                            });

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            assertThat(buildingService.placeOnIsland(1L, req).buildingId()).isEqualTo(210L);
        }

        @Test
        @DisplayName("장인 1명이 이미 건설 중 → BUILDER_SLOT_FULL")
        void builder_slot_full_no_pass() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.countUnderConstructionByOwnerId(eq(1L), any(), any()))
                    .willReturn(1L); // builderCount = 1, 건설 중 = 1 → full

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeOnIsland(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDER_SLOT_FULL);
        }

        @Test
        @DisplayName("완성된 건물은 장인 슬롯을 점유하지 않는다 → 배치 성공")
        void completed_buildings_do_not_occupy_slot() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(
                            List.of(
                                    islandBuilding(bt, island, 97L),
                                    islandBuilding(bt, island, 98L)));
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(2000)));
            given(buildingInstanceRepository.save(any()))
                    .willAnswer(
                            inv -> {
                                BuildingInstance saved = inv.getArgument(0);
                                ReflectionTestUtils.setField(saved, "id", 203L);
                                return saved;
                            });

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            assertThat(buildingService.placeOnIsland(1L, req).buildingId()).isEqualTo(203L);
        }

        @Test
        @DisplayName("건설 시간 지정 → buildCompleteAt 설정, 미지정 → 즉시 완성")
        void build_time_sets_complete_at() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(9000)));
            given(buildingInstanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            given(buildingTypeRepository.findById(2L))
                    .willReturn(Optional.of(storageWithBuildTime(120)));
            buildingService.placeOnIsland(1L, new PlaceBuildingRequest(2L, 0, 0));

            ArgumentCaptor<BuildingInstance> captor =
                    ArgumentCaptor.forClass(BuildingInstance.class);
            then(buildingInstanceRepository).should().save(captor.capture());
            assertThat(captor.getValue().isUnderConstruction(LocalDateTime.now())).isTrue();

            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(storage()));
            buildingService.placeOnIsland(1L, new PlaceBuildingRequest(2L, 1, 1));
            then(buildingInstanceRepository).should(times(2)).save(captor.capture());
            assertThat(captor.getValue().getBuildCompleteAt()).isNull();
        }

        @Test
        @DisplayName("시즌 패스 보유 시 장인 2명 → 1개 건설 중이어도 배치 성공")
        void season_pass_extra_slot_allows_second_building() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.countUnderConstructionByOwnerId(eq(1L), any(), any()))
                    .willReturn(1L); // builderCount = 2, 건설 중 = 1 → OK
            given(seasonBenefitPort.findActiveBenefit(1L)).willReturn(new SeasonBenefit(0, 1));
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(2000)));
            given(buildingInstanceRepository.save(any()))
                    .willAnswer(
                            inv -> {
                                BuildingInstance saved = inv.getArgument(0);
                                ReflectionTestUtils.setField(saved, "id", 202L);
                                return saved;
                            });

            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 0, 0);
            PlaceBuildingResponse response = buildingService.placeOnIsland(1L, req);

            assertThat(response.buildingId()).isEqualTo(202L);
        }
    }

    // ─── getInventory() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getInventory()")
    class GetInventory {

        @Test
        @DisplayName("보관함 아이템 정상 반환")
        void success() {
            Long user = sampleUser(1L);
            BuildingType bt = storage();
            BuildingInstance stored =
                    BuildingInstance.builder()
                            .buildingType(bt)
                            .ownerId(user)
                            .posX(-1)
                            .posY(-1)
                            .hp(bt.getMaxHp())
                            .zone(0)
                            .build();
            ReflectionTestUtils.setField(stored, "id", 300L);

            given(buildingInstanceRepository.findStoredByOwnerId(1L)).willReturn(List.of(stored));

            InventoryResponse response = buildingService.getInventory(1L);

            assertThat(response.items()).hasSize(1);
        }

        @Test
        @DisplayName("보관함 비어있음 → 빈 목록 반환")
        void empty_inventory() {
            given(buildingInstanceRepository.findStoredByOwnerId(1L))
                    .willReturn(Collections.emptyList());

            InventoryResponse response = buildingService.getInventory(1L);

            assertThat(response.items()).isEmpty();
        }
    }

    // ─── store() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("store()")
    class Store {

        @Test
        @DisplayName("건설 중인 건물은 보관 불가 → BUILDING_UNDER_CONSTRUCTION")
        void under_construction_cannot_be_stored() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingInstance bi = placedInstance(storage(), territory, 2, 2);
            bi.startConstruction(LocalDateTime.now().plusMinutes(5));

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));

            assertThatThrownBy(() -> buildingService.store(1L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_UNDER_CONSTRUCTION);
        }

        @Test
        @DisplayName("건물 보관 성공")
        void success() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage();
            BuildingInstance bi = placedInstance(bt, territory, 2, 2);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(userSnapshotRepository.existsById(1L)).willReturn(true);

            StoreBuildingResponse response = buildingService.store(1L, 100L);

            assertThat(response.buildingId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Castle은 보관 불가 → CASTLE_CANNOT_BE_STORED")
        void castle_cannot_be_stored() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingInstance bi = placedInstance(castle(), territory, 4, 4);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));

            assertThatThrownBy(() -> buildingService.store(1L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CASTLE_CANNOT_BE_STORED);
        }

        @Test
        @DisplayName("점유자 아님 → NOT_TERRITORY_OWNER")
        void not_owner() {
            Long owner = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(owner, gradeA());
            BuildingInstance bi = placedInstance(storage(), territory, 0, 0);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));

            assertThatThrownBy(() -> buildingService.store(2L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }
    }

    // ─── move() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("move()")
    class Move {

        @Test
        @DisplayName("건물 이동 성공")
        void success() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = storage();
            BuildingInstance bi = placedInstance(bt, territory, 0, 0);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findByTerritoryId(10L)).willReturn(List.of(bi));

            MoveBuildingRequest req = new MoveBuildingRequest(3, 3);
            MoveBuildingResponse response = buildingService.move(1L, 100L, req);

            assertThat(response.posX()).isEqualTo(3);
            assertThat(response.posY()).isEqualTo(3);
        }

        @Test
        @DisplayName("범위 밖 이동 → INVALID_POSITION")
        void out_of_bounds() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA()); // gridSize=10
            BuildingInstance bi = placedInstance(storage(), territory, 0, 0);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(bi));
            given(buildingInstanceRepository.findByTerritoryId(10L)).willReturn(List.of(bi));

            MoveBuildingRequest req = new MoveBuildingRequest(15, 15); // out of 10x10 grid
            assertThatThrownBy(() -> buildingService.move(1L, 100L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_POSITION);
        }
    }

    // ─── placeFromInventory() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("placeFromInventory()")
    class PlaceFromInventory {

        @Test
        @DisplayName("보관함 건물 배치 성공")
        void success() {
            Long user = sampleUser(1L);
            TerritoryContext grade = gradeA();
            TerritoryContext territory = territoryOwnedBy(user, grade);
            BuildingType bt = storage();
            BuildingInstance stored =
                    BuildingInstance.builder()
                            .buildingType(bt)
                            .ownerId(user)
                            .posX(-1)
                            .posY(-1)
                            .hp(bt.getMaxHp())
                            .zone(0)
                            .build();
            ReflectionTestUtils.setField(stored, "id", 300L);

            given(buildingInstanceRepository.findByIdWithLock(300L))
                    .willReturn(Optional.of(stored));
            given(territoryContextPort.findById(10L)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findByTerritoryId(10L))
                    .willReturn(Collections.emptyList());

            PlaceFromInventoryRequest req = new PlaceFromInventoryRequest(10L, 0, 0);
            PlaceFromInventoryResponse response = buildingService.placeFromInventory(1L, 300L, req);

            assertThat(response.buildingId()).isEqualTo(300L);
            assertThat(response.posX()).isEqualTo(0);
        }

        @Test
        @DisplayName("보관함에 없는 아이템 → BUILDING_NOT_FOUND")
        void not_in_inventory() {
            PlaceFromInventoryRequest req = new PlaceFromInventoryRequest(10L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeFromInventory(1L, 999L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_NOT_FOUND);
        }
    }

    // ─── upgrade() — 섬 성 레벨업 시 IslandGrade 연동 ─────────────────────────

    @Nested
    @DisplayName("건설 시간 / 섬 확장")
    class ConstructionTime {

        private BuildingType castleWithUpgradeTime(Integer seconds) {
            BuildingType bt = castle();
            ReflectionTestUtils.setField(bt, "upgradeTimeSeconds", seconds);
            return bt;
        }

        private BuildingInstance castleAt(BuildingType bt, HomeIsland island, int x, int y) {
            BuildingInstance bi =
                    BuildingInstance.builder()
                            .buildingType(bt)
                            .island(island)
                            .posX(x)
                            .posY(y)
                            .hp(bt.getMaxHp())
                            .zone(1)
                            .build();
            ReflectionTestUtils.setField(bi, "id", 100L);
            return bi;
        }

        private BuildingInstance farmlandAt(HomeIsland island, int x, int y) {
            BuildingType bt =
                    BuildingType.builder()
                            .name("FARMLAND")
                            .width(1)
                            .height(1)
                            .maxHp(50)
                            .baseCostGp(300)
                            .zoneRestriction(-2) // Zone2 이상에만 배치 가능
                            .build();
            ReflectionTestUtils.setField(bt, "id", 7L);
            BuildingInstance bi =
                    BuildingInstance.builder()
                            .buildingType(bt)
                            .island(island)
                            .posX(x)
                            .posY(y)
                            .hp(50)
                            .zone(2)
                            .build();
            ReflectionTestUtils.setField(bi, "id", 101L);
            return bi;
        }

        @Test
        @DisplayName("업그레이드 시간이 있으면 레벨은 그대로, 완료 예정 시각만 설정된다")
        void upgrade_defers_level_until_complete() {
            Long user = sampleUser(1L);
            HomeIsland island = islandWithGrade(user, islandGrade("D", 10, 2, 4, 1));
            BuildingInstance castle = castleAt(castleWithUpgradeTime(600), island, 3, 3);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(castle));
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(5000)));

            UpgradeBuildingResponse response = buildingService.upgrade(1L, 100L);

            assertThat(castle.getLevel()).isEqualTo(1); // 아직 안 오름
            assertThat(castle.getUpgradeToLevel()).isEqualTo(2);
            assertThat(castle.isUnderConstruction(LocalDateTime.now())).isTrue();
            assertThat(response.newLevel()).isEqualTo(2); // 도달할 레벨
            assertThat(response.buildCompleteAt()).isNotNull();
        }

        @Test
        @DisplayName("장인이 모두 작업 중이면 업그레이드 불가 → BUILDER_SLOT_FULL")
        void upgrade_requires_free_builder() {
            Long user = sampleUser(1L);
            HomeIsland island = islandWithGrade(user, islandGrade("D", 10, 2, 4, 1));
            BuildingInstance castle = castleAt(castleWithUpgradeTime(600), island, 3, 3);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(castle));
            given(buildingInstanceRepository.countUnderConstructionByOwnerId(eq(1L), any(), any()))
                    .willReturn(1L);

            assertThatThrownBy(() -> buildingService.upgrade(1L, 100L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDER_SLOT_FULL);
        }

        @Test
        @DisplayName("시즌 패스 건설 시간 20% 감소 → 600초 업그레이드가 480초")
        void season_pass_reduces_build_time() {
            Long user = sampleUser(1L);
            HomeIsland island = islandWithGrade(user, islandGrade("D", 10, 2, 4, 1));
            BuildingInstance castle = castleAt(castleWithUpgradeTime(600), island, 3, 3);
            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(castle));
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(5000)));
            given(seasonBenefitPort.findActiveBenefit(1L)).willReturn(new SeasonBenefit(20, 0));

            LocalDateTime before = LocalDateTime.now();
            buildingService.upgrade(1L, 100L);

            long seconds =
                    java.time.Duration.between(before, castle.getBuildCompleteAt()).getSeconds();
            assertThat(seconds).isBetween(475L, 480L);
        }

        @Test
        @DisplayName("성 업그레이드 완료 → 섬 확장 + 중심 기준 재배치 + 규칙 위반 건물 자동 보관")
        void island_expansion_recenters_and_stores_violators() {
            Long user = sampleUser(1L);
            IslandGrade dGrade = islandGrade("D", 10, 2, 4, 1);
            IslandGrade bGrade = islandGrade("B", 16, 3, 6, 2);
            HomeIsland island = islandWithGrade(user, dGrade);
            BuildingInstance castle = castleAt(castle(), island, 3, 3); // 업그레이드 시간 없음 → 즉시
            BuildingInstance farmland = farmlandAt(island, 2, 2); // D에서는 Zone2

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(castle));
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(5000)));
            given(islandGradeRepository.findByCastleLevelRequired(2))
                    .willReturn(Optional.of(bGrade));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(List.of(castle, farmland));

            buildingService.upgrade(1L, 100L);

            assertThat(castle.getLevel()).isEqualTo(2);
            assertThat(island.getGridSize()).isEqualTo(16);
            // 그리드가 10→16, 중심 기준으로 +3 이동
            assertThat(castle.getPosX()).isEqualTo(6);
            assertThat(castle.getPosY()).isEqualTo(6);
            // 농지는 재배치 후 Zone1이 되어 규칙(Zone2 이상)을 어기므로 보관함으로
            assertThat(farmland.isInInventory()).isTrue();
            assertThat(farmland.getOwnerId()).isEqualTo(user);
            then(notificationPort).should().notifyIslandExpanded(1L, 1);
        }
    }

    @Nested
    @DisplayName("upgrade() — 섬 성 레벨업 & IslandGrade 연동")
    class UpgradeCastleOnIsland {

        private BuildingType castleWithGpProduction() {
            BuildingType bt =
                    BuildingType.builder()
                            .name("CASTLE")
                            .width(2)
                            .height(2)
                            .maxHp(100)
                            .baseCostGp(1000)
                            .zoneRestriction(1)
                            .gpProductionRate(10)
                            .build();
            ReflectionTestUtils.setField(bt, "id", 1L);
            return bt;
        }

        private BuildingInstance castleOnIsland(BuildingType bt, HomeIsland island) {
            BuildingInstance bi =
                    BuildingInstance.builder()
                            .buildingType(bt)
                            .island(island)
                            .posX(3)
                            .posY(3)
                            .hp(bt.getMaxHp())
                            .zone(1)
                            .build();
            ReflectionTestUtils.setField(bi, "id", 100L);
            ReflectionTestUtils.setField(bi, "level", 1);
            return bi;
        }

        @Test
        @DisplayName("성 Lv1→2 업그레이드 → islandGrade가 B등급으로 변경됨")
        void castle_upgrade_changes_island_grade() {
            Long user = sampleUser(1L);
            IslandGrade dGrade = islandGrade("D", 10, 2, 4, 1);
            IslandGrade bGrade = islandGrade("B", 15, 4, 7, 2);
            HomeIsland island = islandWithGrade(user, dGrade);
            BuildingType bt = castleWithGpProduction();
            BuildingInstance castle = castleOnIsland(bt, island);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(castle));
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(5000)));
            given(islandGradeRepository.findByCastleLevelRequired(2))
                    .willReturn(Optional.of(bGrade));

            buildingService.upgrade(1L, 100L);

            assertThat(island.getIslandGrade()).isEqualTo(bGrade);
            assertThat(island.getGridSize()).isEqualTo(15);
            assertThat(island.getGrade()).isEqualTo("B");
            assertThat(island.getZone1Radius()).isEqualTo(4);
            assertThat(island.getZone2Radius()).isEqualTo(7);
        }

        @Test
        @DisplayName("IslandGrade 조회 실패 시 기존 등급 유지")
        void castle_upgrade_grade_not_found_keeps_current() {
            Long user = sampleUser(1L);
            IslandGrade dGrade = islandGrade("D", 10, 2, 4, 1);
            HomeIsland island = islandWithGrade(user, dGrade);
            BuildingType bt = castleWithGpProduction();
            BuildingInstance castle = castleOnIsland(bt, island);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(castle));
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(5000)));
            given(islandGradeRepository.findByCastleLevelRequired(2)).willReturn(Optional.empty());

            buildingService.upgrade(1L, 100L);

            assertThat(island.getIslandGrade()).isEqualTo(dGrade);
            assertThat(island.getGridSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("영토 건물 성 업그레이드 → IslandGrade 조회 없음")
        void territory_castle_upgrade_skips_island_grade() {
            Long user = sampleUser(1L);
            TerritoryContext territory = territoryOwnedBy(user, gradeA());
            BuildingType bt = castleWithGpProduction();
            BuildingInstance castle = placedInstance(bt, territory, 4, 4);

            given(buildingInstanceRepository.findById(100L)).willReturn(Optional.of(castle));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storageWithGp(5000)));

            buildingService.upgrade(1L, 100L);

            // IslandGrade repo should never be called for territory buildings
            org.mockito.BDDMockito.then(islandGradeRepository).shouldHaveNoInteractions();
        }
    }

    // ─── calculateIslandZone (간접 검증 via placeOnIsland) ────────────────────

    @Nested
    @DisplayName("IslandGrade 존 경계 검증")
    class IslandZoneBoundary {

        @Test
        @DisplayName("D등급(10x10) 존1 반경=2: center=5, posX=4,posY=4 → zone1")
        void d_grade_zone1_boundary() {
            Long user = sampleUser(1L);
            IslandGrade dGrade = islandGrade("D", 10, 2, 4, 1);
            HomeIsland island = islandWithGrade(user, dGrade);
            BuildingType bt = storage();

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(2L)).willReturn(Optional.of(bt));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());
            given(buildingInstanceRepository.findStorageBuildingsByIslandIdWithLock(1L))
                    .willReturn(List.of(storageWithGp(2000)));
            given(buildingInstanceRepository.save(any()))
                    .willAnswer(
                            inv -> {
                                BuildingInstance saved = inv.getArgument(0);
                                ReflectionTestUtils.setField(saved, "id", 201L);
                                return saved;
                            });

            // center=5, dist=|4-5|=1 ≤ zone1Radius(2) → zone1
            PlaceBuildingRequest req = new PlaceBuildingRequest(2L, 4, 4);
            PlaceBuildingResponse response = buildingService.placeOnIsland(1L, req);

            assertThat(response.buildingId()).isEqualTo(201L);
        }

        @Test
        @DisplayName("B등급(15x15) Castle을 zone1이 아닌 곳 배치 → ZONE_RESTRICTION_VIOLATED")
        void b_grade_castle_outside_zone1_rejected() {
            Long user = sampleUser(1L);
            IslandGrade bGrade = islandGrade("B", 15, 4, 7, 2);
            HomeIsland island = islandWithGrade(user, bGrade);
            BuildingType castleType = castle(); // zoneRestriction=1

            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingTypeRepository.findById(1L)).willReturn(Optional.of(castleType));
            given(buildingInstanceRepository.findByIslandId(1L))
                    .willReturn(Collections.emptyList());

            // center=7, posX=0 → dist=7 > zone1Radius(4) → zone2/3, not zone1
            PlaceBuildingRequest req = new PlaceBuildingRequest(1L, 0, 0);
            assertThatThrownBy(() -> buildingService.placeOnIsland(1L, req))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ZONE_RESTRICTION_VIOLATED);
        }
    }

    @Nested
    @DisplayName("rushConstruction()")
    class RushConstruction {

        @Test
        @DisplayName("건설 중 건물 즉시 완료 → 남은 시간 비례 AP 차감 + 건설 완료")
        void rush_success() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingInstance building = underConstruction(storage(), island, 50L); // 남은 ~10분
            given(buildingInstanceRepository.findById(50L)).willReturn(Optional.of(building));
            given(walletPort.spend(eq(1L), anyInt(), anyString()))
                    .willReturn(new WalletSnapshot(400));

            var res = buildingService.rushConstruction(1L, 50L);

            // 10분 남음 → 올림(600/60)=10분 × 10 AP = 100
            assertThat(res.apSpent()).isEqualTo(100);
            assertThat(res.apRemaining()).isEqualTo(400);
            assertThat(building.getBuildCompleteAt()).isNull();
        }

        @Test
        @DisplayName("건설 중이 아니면 → BUILDING_NOT_UNDER_CONSTRUCTION")
        void rush_notUnderConstruction() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingInstance building =
                    islandBuilding(storage(), island, 51L); // buildCompleteAt 없음
            given(buildingInstanceRepository.findById(51L)).willReturn(Optional.of(building));

            assertThatThrownBy(() -> buildingService.rushConstruction(1L, 51L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BUILDING_NOT_UNDER_CONSTRUCTION);
        }

        @Test
        @DisplayName("AP 부족 → INSUFFICIENT_AP")
        void rush_insufficientAp() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            BuildingInstance building = underConstruction(storage(), island, 52L);
            given(buildingInstanceRepository.findById(52L)).willReturn(Optional.of(building));
            willThrow(new CustomException(ErrorCode.INSUFFICIENT_AP))
                    .given(walletPort)
                    .spend(eq(1L), anyInt(), anyString());

            assertThatThrownBy(() -> buildingService.rushConstruction(1L, 52L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        }
    }

    @Nested
    @DisplayName("activateProductionBoost()")
    class ActivateProductionBoost {

        @Test
        @DisplayName("부스터 발동 → 500 AP 차감 + 종료 시각 설정")
        void boost_success() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(walletPort.spend(eq(1L), anyInt(), anyString()))
                    .willReturn(new WalletSnapshot(500));

            var res = buildingService.activateProductionBoost(1L);

            assertThat(res.apSpent()).isEqualTo(500);
            assertThat(res.apRemaining()).isEqualTo(500);
            assertThat(res.multiplier()).isEqualTo(2);
            assertThat(island.getProductionBoostUntil()).isNotNull();
        }

        @Test
        @DisplayName("이미 활성 중 → PRODUCTION_BOOST_ALREADY_ACTIVE")
        void boost_alreadyActive() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            island.activateProductionBoost(java.time.LocalDateTime.now().plusHours(1));
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));

            assertThatThrownBy(() -> buildingService.activateProductionBoost(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PRODUCTION_BOOST_ALREADY_ACTIVE);
        }

        @Test
        @DisplayName("AP 부족 → INSUFFICIENT_AP")
        void boost_insufficientAp() {
            Long user = sampleUser(1L);
            HomeIsland island = sampleIsland(user);
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            willThrow(new CustomException(ErrorCode.INSUFFICIENT_AP))
                    .given(walletPort)
                    .spend(eq(1L), anyInt(), anyString());

            assertThatThrownBy(() -> buildingService.activateProductionBoost(1L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        }
    }
}
