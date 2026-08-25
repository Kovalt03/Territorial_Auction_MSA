package com.territorial.auction.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.HomeIsland;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository.MilitaryLocationSummary;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.military.LocationType;
import com.territorial.auction.domain.military.dto.AttackTokenResponse;
import com.territorial.auction.domain.military.dto.DeclareSiegeRequest;
import com.territorial.auction.domain.military.dto.DeployUnitRequest;
import com.territorial.auction.domain.military.dto.MoveUnitRequest;
import com.territorial.auction.domain.military.dto.MoveUnitResponse;
import com.territorial.auction.domain.military.dto.ProduceUnitRequest;
import com.territorial.auction.domain.military.dto.ProduceUnitResponse;
import com.territorial.auction.domain.military.dto.RecallUnitRequest;
import com.territorial.auction.domain.military.dto.ScoutTerritoryResponse;
import com.territorial.auction.domain.military.dto.UnitListResponse;
import com.territorial.auction.domain.military.dto.UnitTypeCatalogResponse;
import com.territorial.auction.domain.military.entity.AttackToken;
import com.territorial.auction.domain.military.entity.SiegeEvent;
import com.territorial.auction.domain.military.entity.SiegeStructureType;
import com.territorial.auction.domain.military.entity.UnitInstance;
import com.territorial.auction.domain.military.entity.UnitType;
import com.territorial.auction.domain.military.event.GarrisonBuildingDestroyedEvent;
import com.territorial.auction.domain.military.event.TerritoryLostEvent;
import com.territorial.auction.domain.military.repository.AttackTokenRepository;
import com.territorial.auction.domain.military.repository.SiegeEventRepository;
import com.territorial.auction.domain.military.repository.SiegeForceRepository;
import com.territorial.auction.domain.military.repository.SiegeResultRepository;
import com.territorial.auction.domain.military.repository.UnitInstanceRepository;
import com.territorial.auction.domain.military.repository.UnitTypeRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class MilitaryServiceTest {

    @InjectMocks private MilitaryService militaryService;

    @Mock private AttackTokenRepository attackTokenRepository;
    @Mock private UnitInstanceRepository unitInstanceRepository;
    @Mock private UnitTypeRepository unitTypeRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private SiegeEventRepository siegeEventRepository;
    @Mock private SiegeForceRepository siegeForceRepository;
    @Mock private SiegeResultRepository siegeResultRepository;
    @Mock private UserRepository userRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;

    @Mock
    private com.territorial.auction.domain.notification.service.NotificationService
            notificationService;

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private com.territorial.auction.global.config.BalanceConfig balanceConfig;

    @Mock
    private com.territorial.auction.domain.military.repository.SiegeStructureRepository
            siegeStructureRepository;

    @Mock
    private com.territorial.auction.domain.building.repository.GlobalVaultRepository
            globalVaultRepository;

    private static final long TERR_ID = 10L;
    private static final long ISLAND_ID = 1L;

    private User attacker;
    private User defender;
    private UnitType unitType;
    private AttackToken attackToken;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        attacker = user(1L, "attacker1", "공격자");
        defender = user(2L, "defender1", "방어자");
        unitType =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(10)
                        .defensePower(8)
                        .costGp(100)
                        .foodCost(2)
                        .level(1)
                        .build();
        ReflectionTestUtils.setField(unitType, "id", 1L);
        attackToken = AttackToken.builder().user(attacker).build();
        ReflectionTestUtils.setField(attackToken, "normalCount", 3);
        ReflectionTestUtils.setField(attackToken, "precisionCount", 1);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // --- fixtures ---

    private User user(Long id, String username, String nickname) {
        User u =
                User.builder()
                        .username(username)
                        .email(username + "@e.com")
                        .passwordHash("hash")
                        .nickname(nickname)
                        .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Territory ownedTerritory() {
        Territory t = Territory.builder().coordX(3).coordY(4).build();
        ReflectionTestUtils.setField(t, "id", TERR_ID);
        ReflectionTestUtils.setField(t, "owner", attacker);
        ReflectionTestUtils.setField(t, "status", Territory.TerritoryStatus.OCCUPIED);
        ReflectionTestUtils.setField(t, "occupiedUntil", LocalDateTime.now().plusDays(1));
        return t;
    }

    private HomeIsland ownedIsland() {
        HomeIsland island = HomeIsland.builder().user(attacker).build();
        ReflectionTestUtils.setField(island, "id", ISLAND_ID);
        return island;
    }

    private com.territorial.auction.domain.building.entity.GlobalVault vault(int gp) {
        var v =
                com.territorial.auction.domain.building.entity.GlobalVault.builder()
                        .user(attacker)
                        .build();
        ReflectionTestUtils.setField(v, "storedGp", gp);
        return v;
    }

    // Lv2 STORAGE — 용량 10,000. GP·식량 저장 스텁으로 사용.
    private BuildingInstance storage(int gp, int food) {
        BuildingType bt =
                BuildingType.builder().name("STORAGE").width(1).height(1).maxHp(60).build();
        BuildingInstance b =
                BuildingInstance.builder().buildingType(bt).posX(0).posY(0).hp(60).zone(2).build();
        ReflectionTestUtils.setField(b, "level", 2);
        ReflectionTestUtils.setField(b, "storedGp", gp);
        ReflectionTestUtils.setField(b, "storedFood", food);
        return b;
    }

    private UnitInstance idleAtTerritory(int qty, Territory home) {
        UnitInstance inst =
                UnitInstance.builder()
                        .user(attacker)
                        .unitType(unitType)
                        .quantity(qty)
                        .homeTerritory(home)
                        .build();
        ReflectionTestUtils.setField(inst, "id", 100L);
        return inst;
    }

    private UnitInstance deployedFromTerritory(int qty, Territory home, Territory deployed) {
        UnitInstance inst = idleAtTerritory(qty, home);
        ReflectionTestUtils.setField(inst, "id", 101L);
        inst.deployTo(deployed, null);
        return inst;
    }

    // 영토 위치를 소유자로 확인하고 병영·성·주거지·저장소를 성공 경로로 스텁한다.
    private void stubTerritoryLocation(Territory territory, int gp, int food, int currentUnits) {
        given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));
        given(
                        buildingInstanceRepository.findMilitaryLocationSummaryByTerritoryId(
                                eq(TERR_ID), any()))
                .willReturn(militaryLocationSummary(1, 1, 0));
        given(unitInstanceRepository.sumQuantityByHomeTerritoryId(TERR_ID))
                .willReturn(currentUnits);
    }

    private MilitaryLocationSummary militaryLocationSummary(
            int barracksLevel, int castleLevel, int residenceCapacity) {
        return new MilitaryLocationSummary() {
            @Override
            public Integer getMaxBarracksLevel() {
                return barracksLevel;
            }

            @Override
            public Integer getCastleLevel() {
                return castleLevel;
            }

            @Override
            public Integer getResidenceCapacity() {
                return residenceCapacity;
            }
        };
    }

    // ==========================================================
    // GetAttackTokens
    // ==========================================================

    @Nested
    @DisplayName("GetAttackTokens")
    class GetAttackTokens {

        @Test
        @DisplayName("공격권 레코드 있음 → normalCount, precisionCount 반환")
        void success() {
            given(attackTokenRepository.findByUserId(1L)).willReturn(Optional.of(attackToken));
            AttackTokenResponse response = militaryService.getAttackTokens(1L);
            assertThat(response.normalCount()).isEqualTo(3);
            assertThat(response.precisionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("공격권 레코드 없음 → (0, 0) 반환")
        void empty() {
            given(attackTokenRepository.findByUserId(1L)).willReturn(Optional.empty());
            AttackTokenResponse response = militaryService.getAttackTokens(1L);
            assertThat(response.normalCount()).isEqualTo(0);
            assertThat(response.precisionCount()).isEqualTo(0);
        }
    }

    // ==========================================================
    // ProduceUnit — 생산 위치 스코핑
    // ==========================================================

    @Nested
    @DisplayName("ProduceUnit")
    class ProduceUnit {

        private ProduceUnitRequest req(int quantity) {
            return new ProduceUnitRequest(1L, quantity, 1, TERR_ID, LocationType.TERRITORY);
        }

        @Test
        @DisplayName("병영·GP·식량 충분 + 대기 스택 없음 → 새 인스턴스 save + 위치 저장소 차감")
        void success_newStack() {
            Territory territory = ownedTerritory();
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            stubTerritoryLocation(territory, 5000, 500, 0);
            BuildingInstance storage = storage(5000, 500);
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(TERR_ID))
                    .willReturn(List.of(storage));
            given(unitInstanceRepository.findReadyIdleAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(Optional.empty());
            given(userRepository.findById(1L)).willReturn(Optional.of(attacker));

            ProduceUnitResponse response = militaryService.produceUnit(1L, req(3));

            // GP 300, 식량 6 차감 후 남은 GP 4700
            assertThat(response.gpRemaining()).isEqualTo(4700);
            assertThat(storage.getStoredGp()).isEqualTo(4700);
            assertThat(storage.getStoredFood()).isEqualTo(494);
            then(unitInstanceRepository).should().save(any(UnitInstance.class));
        }

        @Test
        @DisplayName("대기 스택 이미 존재 → addQuantity, save 미호출")
        void success_existingStack() {
            Territory territory = ownedTerritory();
            UnitInstance idle = idleAtTerritory(3, territory);
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            stubTerritoryLocation(territory, 5000, 500, 3); // 성 Lv1 슬롯 5, 현재 3 + 2 = 5 OK
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(TERR_ID))
                    .willReturn(List.of(storage(5000, 500)));
            given(unitInstanceRepository.findReadyIdleAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(Optional.of(idle));

            militaryService.produceUnit(1L, req(2));

            assertThat(idle.getQuantity()).isEqualTo(5);
            then(unitInstanceRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("위치에 병영 없음 → NO_BARRACKS")
        void noBarracks() {
            Territory territory = ownedTerritory();
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));
            given(
                            buildingInstanceRepository.findMilitaryLocationSummaryByTerritoryId(
                                    eq(TERR_ID), any()))
                    .willReturn(militaryLocationSummary(0, 1, 0));

            assertThatThrownBy(() -> militaryService.produceUnit(1L, req(1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NO_BARRACKS);
        }

        @Test
        @DisplayName("위치 병영 레벨 부족 → BARRACKS_LEVEL_INSUFFICIENT")
        void barracksLevelInsufficient() {
            Territory territory = ownedTerritory();
            ReflectionTestUtils.setField(unitType, "level", 3);
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));
            given(
                            buildingInstanceRepository.findMilitaryLocationSummaryByTerritoryId(
                                    eq(TERR_ID), any()))
                    .willReturn(militaryLocationSummary(1, 1, 0));

            assertThatThrownBy(() -> militaryService.produceUnit(1L, req(1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.BARRACKS_LEVEL_INSUFFICIENT);
        }

        @Test
        @DisplayName("위치 유닛 슬롯 초과 → UNIT_CAPACITY_EXCEEDED")
        void capacityExceeded() {
            Territory territory = ownedTerritory();
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            // 성 Lv1 슬롯 5, 주거지 0 → 용량 5. 현재 5 + 1 > 5
            stubTerritoryLocation(territory, 5000, 500, 5);

            assertThatThrownBy(() -> militaryService.produceUnit(1L, req(1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNIT_CAPACITY_EXCEEDED);
        }

        @Test
        @DisplayName("위치 저장소 GP 부족 → INSUFFICIENT_GP")
        void insufficientGp() {
            Territory territory = ownedTerritory();
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            stubTerritoryLocation(territory, 100, 500, 0);
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(TERR_ID))
                    .willReturn(List.of(storage(100, 500))); // GP 100 < 300

            assertThatThrownBy(() -> militaryService.produceUnit(1L, req(3)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_GP);
        }

        @Test
        @DisplayName("위치 저장소 식량 부족 → FOOD_INSUFFICIENT")
        void insufficientFood() {
            Territory territory = ownedTerritory();
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            stubTerritoryLocation(territory, 5000, 2, 0);
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(TERR_ID))
                    .willReturn(List.of(storage(5000, 2))); // 식량 2 < 6

            assertThatThrownBy(() -> militaryService.produceUnit(1L, req(3)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FOOD_INSUFFICIENT);
        }
    }

    // ==========================================================
    // DeployUnit — 출발 위치 대기 스택에서 배치
    // ==========================================================

    @Nested
    @DisplayName("DeployUnit")
    class DeployUnit {

        @org.junit.jupiter.api.BeforeEach
        void stubCastleGarrisonCap() {
            org.mockito.Mockito.lenient()
                    .when(
                            balanceConfig.getInt(
                                    com.territorial.auction.global.config.BalanceConfig
                                            .KEY_GARRISON_CAP_CASTLE,
                                    com.territorial.auction.domain.military.MilitaryPolicy
                                            .GARRISON_CAP_CASTLE))
                    .thenReturn(5);
        }

        private DeployUnitRequest req(int quantity) {
            return new DeployUnitRequest(
                    TERR_ID, 50L, 1L, quantity, 1, TERR_ID, LocationType.TERRITORY);
        }

        // 주둔 대상 성(레벨1 → 수용량 5), 대상 영토 소속
        private BuildingInstance garrisonCastle(Territory territory) {
            BuildingType castleType =
                    BuildingType.builder().name("CASTLE").width(1).height(1).maxHp(300).build();
            BuildingInstance castle =
                    BuildingInstance.builder()
                            .territory(territory)
                            .buildingType(castleType)
                            .posX(0)
                            .posY(0)
                            .hp(300)
                            .zone(1)
                            .build();
            ReflectionTestUtils.setField(castle, "id", 50L);
            ReflectionTestUtils.setField(castle, "level", 1);
            return castle;
        }

        @Test
        @DisplayName("소유 영토 + 주둔 건물 + 대기 유닛 충분 → subtract + 배치 스택 save")
        void success() {
            Territory territory = ownedTerritory();
            UnitInstance idle = idleAtTerritory(10, territory);
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findById(50L))
                    .willReturn(Optional.of(garrisonCastle(territory)));
            given(unitInstanceRepository.sumQuantityByDeployedBuildingId(50L)).willReturn(0);
            given(unitInstanceRepository.findReadyIdleAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(Optional.of(idle));
            given(unitInstanceRepository.findDeployedFromTerritory(1L, 1L, 1, TERR_ID, 50L))
                    .willReturn(Optional.empty());
            given(userRepository.findById(1L)).willReturn(Optional.of(attacker));

            militaryService.deployUnit(1L, req(4));

            assertThat(idle.getQuantity()).isEqualTo(6);
            then(unitInstanceRepository).should().save(any(UnitInstance.class));
        }

        @Test
        @DisplayName("영토 소유자 아님 → NOT_TERRITORY_OWNER")
        void notOwner() {
            Territory territory = Territory.builder().coordX(3).coordY(4).build();
            ReflectionTestUtils.setField(territory, "id", TERR_ID);
            ReflectionTestUtils.setField(territory, "owner", defender);
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));

            assertThatThrownBy(() -> militaryService.deployUnit(1L, req(1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("출발지 대기 유닛 부족 → INSUFFICIENT_UNITS")
        void insufficient() {
            Territory territory = ownedTerritory();
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));
            given(buildingInstanceRepository.findById(50L))
                    .willReturn(Optional.of(garrisonCastle(territory)));
            given(unitInstanceRepository.sumQuantityByDeployedBuildingId(50L)).willReturn(0);
            given(unitInstanceRepository.findReadyIdleAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(Optional.of(idleAtTerritory(2, territory)));

            assertThatThrownBy(() -> militaryService.deployUnit(1L, req(5)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_UNITS);
        }
    }

    // ==========================================================
    // RecallUnit — 배치 유닛을 귀속지 대기 스택으로 회수
    // ==========================================================

    @Nested
    @DisplayName("RecallUnit")
    class RecallUnit {

        private RecallUnitRequest req(int quantity) {
            return new RecallUnitRequest(TERR_ID, 1L, quantity, 1);
        }

        @Test
        @DisplayName("배치 유닛 충분 → subtract + 귀속지 대기 스택으로 병합")
        void success() {
            Territory territory = ownedTerritory();
            UnitInstance deployed = deployedFromTerritory(10, territory, territory);
            UnitInstance homeIdle = idleAtTerritory(1, territory);
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));
            given(unitInstanceRepository.findDeployedAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(List.of(deployed));
            given(unitInstanceRepository.findReadyIdleAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(Optional.of(homeIdle));

            militaryService.recallUnit(1L, req(4));

            assertThat(deployed.getQuantity()).isEqualTo(6);
            assertThat(homeIdle.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("영토 소유자 아님 → NOT_TERRITORY_OWNER")
        void notOwner() {
            Territory territory = Territory.builder().coordX(3).coordY(4).build();
            ReflectionTestUtils.setField(territory, "id", TERR_ID);
            ReflectionTestUtils.setField(territory, "owner", defender);
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));

            assertThatThrownBy(() -> militaryService.recallUnit(1L, req(1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("배치 유닛 부족 → INSUFFICIENT_UNITS")
        void insufficient() {
            Territory territory = ownedTerritory();
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));
            given(unitInstanceRepository.findDeployedAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(List.of(deployedFromTerritory(2, territory, territory)));

            assertThatThrownBy(() -> militaryService.recallUnit(1L, req(5)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_UNITS);
        }
    }

    // ==========================================================
    // MoveUnit — 위치 간 이동 (GP 비용 + 이동 시간)
    // ==========================================================

    @Nested
    @DisplayName("MoveUnit")
    class MoveUnit {

        // MoveUnitRequest 시그니처: (unitTypeId, quantity, sourceId, sourceType, destId, destType)
        private MoveUnitRequest moveReq(int quantity) {
            return new MoveUnitRequest(
                    1L,
                    quantity,
                    1,
                    TERR_ID,
                    LocationType.TERRITORY,
                    ISLAND_ID,
                    LocationType.ISLAND);
        }

        @Test
        @DisplayName("영토→섬 이동 → 출발지 GP 차감 + 이동중 스택 save + moveCompleteAt 설정")
        void success() {
            Territory source = ownedTerritory();
            HomeIsland dest = ownedIsland();
            UnitInstance idle = idleAtTerritory(10, source);
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(source));
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(dest));
            given(unitInstanceRepository.findReadyIdleAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(Optional.of(idle));
            // 도착지(섬) 슬롯 확인
            given(unitInstanceRepository.sumQuantityByHomeIslandId(ISLAND_ID)).willReturn(0);
            given(buildingInstanceRepository.findCastleLevelByIslandId(ISLAND_ID))
                    .willReturn(Optional.of(1));
            given(buildingInstanceRepository.sumResidenceCapacityByIslandId(eq(ISLAND_ID), any()))
                    .willReturn(0);
            BuildingInstance storage = storage(5000, 0);
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(TERR_ID))
                    .willReturn(List.of(storage));
            given(userRepository.findById(1L)).willReturn(Optional.of(attacker));

            MoveUnitResponse response = militaryService.moveUnit(1L, moveReq(4));

            // 이동 비용 = 4 × 10 = 40, 남은 GP 4960
            assertThat(response.movedCount()).isEqualTo(4);
            assertThat(response.gpRemaining()).isEqualTo(4960);
            assertThat(response.moveCompleteAt()).isNotNull();
            assertThat(idle.getQuantity()).isEqualTo(6);
            assertThat(storage.getStoredGp()).isEqualTo(4960);
            then(unitInstanceRepository).should().save(any(UnitInstance.class));
        }

        @Test
        @DisplayName("출발지 = 도착지 → INVALID_INPUT")
        void sameLocation() {
            Territory territory = ownedTerritory();
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(territory));

            MoveUnitRequest sameReq =
                    new MoveUnitRequest(
                            1L,
                            2,
                            1,
                            TERR_ID,
                            LocationType.TERRITORY,
                            TERR_ID,
                            LocationType.TERRITORY);
            assertThatThrownBy(() -> militaryService.moveUnit(1L, sameReq))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("출발지 대기 유닛 부족 → INSUFFICIENT_UNITS")
        void insufficient() {
            Territory source = ownedTerritory();
            HomeIsland dest = ownedIsland();
            given(territoryRepository.findById(TERR_ID)).willReturn(Optional.of(source));
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(dest));
            given(unitInstanceRepository.findReadyIdleAtTerritory(1L, 1L, 1, TERR_ID))
                    .willReturn(Optional.of(idleAtTerritory(2, source)));

            assertThatThrownBy(() -> militaryService.moveUnit(1L, moveReq(5)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_UNITS);
        }
    }

    // ==========================================================
    // DeclareSiege — 대기 유닛 가용량 합산 검증 (핵심 경로)
    // ==========================================================

    @Nested
    @DisplayName("DeclareSiege")
    class DeclareSiege {

        private Territory targetTerritory() {
            Territory t = Territory.builder().coordX(5).coordY(6).build();
            ReflectionTestUtils.setField(t, "id", 20L);
            ReflectionTestUtils.setField(t, "owner", defender);
            ReflectionTestUtils.setField(t, "status", Territory.TerritoryStatus.OCCUPIED);
            ReflectionTestUtils.setField(t, "occupiedUntil", LocalDateTime.now().minusHours(1));
            return t;
        }

        // 대상 (5,6) 인접 타일에 주둔지 1개 → 수용량 10 ≥ 병력 3
        private List<DeclareSiegeRequest.StructureEntry> structures() {
            return List.of(
                    new DeclareSiegeRequest.StructureEntry(SiegeStructureType.STAGING, 5, 7));
        }

        private DeclareSiegeRequest req() {
            // 최외곽 Zone 3 — 진입 전제 없음(공략은 외곽→중심). 병력: 유닛타입 1L × 3
            return new DeclareSiegeRequest(
                    20L,
                    null,
                    3,
                    List.of(new DeclareSiegeRequest.ForceEntry(1L, 3, 1)),
                    structures());
        }

        @Test
        @DisplayName("모든 조건 통과 → SiegeEvent 저장 + 병력 커밋 + 응답 반환")
        void success() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());
            given(attackTokenRepository.findByUserIdWithLock(1L))
                    .willReturn(Optional.of(attackToken));
            given(userRepository.findById(1L)).willReturn(Optional.of(attacker));
            // 병력 커밋: 유닛 타입 조회 + 대기 풀 검증·차감
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(10);
            given(unitInstanceRepository.findReadyIdleByUserIdAndUnitTypeIdAndLevel(1L, 1L, 1))
                    .willReturn(List.of(idleAtTerritory(10, target)));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(1000)));
            given(siegeEventRepository.save(any(SiegeEvent.class)))
                    .willAnswer(
                            inv -> {
                                SiegeEvent s = inv.getArgument(0);
                                ReflectionTestUtils.setField(s, "id", 99L);
                                return s;
                            });

            var response = militaryService.declareSiege(1L, req());

            assertThat(response.siegeId()).isEqualTo(99L);
            then(siegeEventRepository).should().save(any(SiegeEvent.class));
            then(siegeForceRepository).should().save(any());
        }

        @Test
        @DisplayName("정밀 대상 건물이 공격 구역과 다른 존 → SIEGE_TARGET_BUILDING_INVALID")
        void precisionTargetWrongZone() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(10);
            given(attackTokenRepository.findByUserIdWithLock(1L))
                    .willReturn(Optional.of(attackToken));

            // Zone 3 공격인데 대상 건물은 Zone 1 → 무효
            BuildingInstance wrongZoneBuilding =
                    BuildingInstance.builder()
                            .territory(target)
                            .buildingType(
                                    BuildingType.builder()
                                            .name("CASTLE")
                                            .width(1)
                                            .height(1)
                                            .maxHp(200)
                                            .baseCostGp(100)
                                            .build())
                            .posX(0)
                            .posY(0)
                            .hp(200)
                            .zone(1)
                            .build();
            ReflectionTestUtils.setField(wrongZoneBuilding, "id", 77L);
            given(buildingInstanceRepository.findById(77L))
                    .willReturn(Optional.of(wrongZoneBuilding));

            DeclareSiegeRequest precisionReq =
                    new DeclareSiegeRequest(
                            20L,
                            77L,
                            3,
                            List.of(new DeclareSiegeRequest.ForceEntry(1L, 3, 1)),
                            structures());

            assertThatThrownBy(() -> militaryService.declareSiege(1L, precisionReq))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SIEGE_TARGET_BUILDING_INVALID);
        }

        @Test
        @DisplayName("Zone 1 공격은 바깥 Zone 2 클리어 전제 → 미클리어 시 ZONE_NOT_CLEARED")
        void zone1RequiresOuterCleared() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            // 이전 클리어 이력 없음 → Zone 2가 클리어되지 않아 Zone 1 진입 불가
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());

            DeclareSiegeRequest zone1 =
                    new DeclareSiegeRequest(
                            20L,
                            null,
                            1,
                            List.of(new DeclareSiegeRequest.ForceEntry(1L, 3, 1)),
                            structures());
            assertThatThrownBy(() -> militaryService.declareSiege(1L, zone1))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ZONE_NOT_CLEARED);
        }

        @Test
        @DisplayName("보호 기간 중(protectedUntil 미래) → TERRITORY_PROTECTED")
        void protectedPeriod() {
            Territory target = targetTerritory();
            ReflectionTestUtils.setField(
                    target, "protectedUntil", LocalDateTime.now().plusHours(6));
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));

            assertThatThrownBy(() -> militaryService.declareSiege(1L, req()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_PROTECTED);
        }

        @Test
        @DisplayName("대기 유닛 부족 → INSUFFICIENT_UNITS")
        void insufficientUnits() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(1);

            assertThatThrownBy(() -> militaryService.declareSiege(1L, req()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_UNITS);
        }

        @Test
        @DisplayName("자기 영토 공격 → CANNOT_ATTACK_OWN_TERRITORY")
        void ownTerritory() {
            Territory target = targetTerritory();
            ReflectionTestUtils.setField(target, "owner", attacker);
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));

            assertThatThrownBy(() -> militaryService.declareSiege(1L, req()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_ATTACK_OWN_TERRITORY);
        }

        @Test
        @DisplayName("주둔지 없는 공성 건물 편성 → SIEGE_STAGING_REQUIRED")
        void stagingRequired() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(10);

            DeclareSiegeRequest noStaging =
                    new DeclareSiegeRequest(
                            20L,
                            null,
                            3,
                            List.of(new DeclareSiegeRequest.ForceEntry(1L, 3, 1)),
                            List.of(
                                    new DeclareSiegeRequest.StructureEntry(
                                            SiegeStructureType.TOWER, 5, 7)));

            assertThatThrownBy(() -> militaryService.declareSiege(1L, noStaging))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SIEGE_STAGING_REQUIRED);
        }

        @Test
        @DisplayName("병력이 주둔지 수용량 초과 → SIEGE_FORCE_EXCEEDS_CAPACITY")
        void forceExceedsCapacity() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(20);

            // 주둔지 1개 → 수용량 10, 병력 11 → 초과
            DeclareSiegeRequest tooMany =
                    new DeclareSiegeRequest(
                            20L,
                            null,
                            3,
                            List.of(new DeclareSiegeRequest.ForceEntry(1L, 11, 1)),
                            structures());

            assertThatThrownBy(() -> militaryService.declareSiege(1L, tooMany))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SIEGE_FORCE_EXCEEDS_CAPACITY);
        }

        @Test
        @DisplayName("대상 영토에 인접하지 않은 좌표 → SIEGE_STRUCTURE_PLACEMENT_INVALID")
        void placementInvalid() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(10);

            // 대상 (5,6)에서 먼 (0,0)
            DeclareSiegeRequest farAway =
                    new DeclareSiegeRequest(
                            20L,
                            null,
                            3,
                            List.of(new DeclareSiegeRequest.ForceEntry(1L, 3, 1)),
                            List.of(
                                    new DeclareSiegeRequest.StructureEntry(
                                            SiegeStructureType.STAGING, 0, 0)));

            assertThatThrownBy(() -> militaryService.declareSiege(1L, farAway))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SIEGE_STRUCTURE_PLACEMENT_INVALID);
        }

        @Test
        @DisplayName("금고 GP 부족 → INSUFFICIENT_GP")
        void vaultInsufficient() {
            Territory target = targetTerritory();
            given(territoryRepository.findById(20L)).willReturn(Optional.of(target));
            given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                    .willReturn(List.of());
            given(attackTokenRepository.findByUserIdWithLock(1L))
                    .willReturn(Optional.of(attackToken));
            given(userRepository.findById(1L)).willReturn(Optional.of(attacker));
            given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(10);
            given(unitInstanceRepository.findReadyIdleByUserIdAndUnitTypeIdAndLevel(1L, 1L, 1))
                    .willReturn(List.of(idleAtTerritory(10, target)));
            given(siegeEventRepository.save(any(SiegeEvent.class)))
                    .willAnswer(
                            inv -> {
                                SiegeEvent s = inv.getArgument(0);
                                ReflectionTestUtils.setField(s, "id", 99L);
                                return s;
                            });
            // 주둔지 1개 비용 500 > 금고 100
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault(100)));

            assertThatThrownBy(() -> militaryService.declareSiege(1L, req()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_GP);
        }
    }

    @Nested
    @DisplayName("ScoutTerritory")
    class ScoutTerritory {

        private UnitType scoutType() {
            UnitType scout =
                    UnitType.builder()
                            .name("SCOUT")
                            .attackPower(6)
                            .defensePower(6)
                            .costGp(80)
                            .foodCost(1)
                            .level(1)
                            .build();
            ReflectionTestUtils.setField(scout, "id", 9L);
            return scout;
        }

        private Territory enemyTerritory() {
            Territory t = Territory.builder().coordX(5).coordY(6).build();
            ReflectionTestUtils.setField(t, "id", 30L);
            ReflectionTestUtils.setField(t, "owner", defender);
            ReflectionTestUtils.setField(t, "status", Territory.TerritoryStatus.OCCUPIED);
            return t;
        }

        private UnitInstance stack(UnitType type, User owner, int qty) {
            UnitInstance inst =
                    UnitInstance.builder().user(owner).unitType(type).quantity(qty).build();
            ReflectionTestUtils.setField(inst, "id", 200L + qty);
            return inst;
        }

        @Test
        @DisplayName("정찰 성공 → SCOUT 1기 소모 + 방어 총 병력 수만 반환")
        void success() {
            Territory target = enemyTerritory();
            UnitType scout = scoutType();
            given(territoryRepository.findById(30L)).willReturn(Optional.of(target));
            given(unitTypeRepository.findByName("SCOUT")).willReturn(Optional.of(scout));
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 9L, 1)).willReturn(3);
            given(unitInstanceRepository.findReadyIdleByUserIdAndUnitTypeIdAndLevel(1L, 9L, 1))
                    .willReturn(List.of(stack(scout, attacker, 3)));
            given(unitInstanceRepository.findByUserIdAndDeployedTerritoryId(2L, 30L))
                    .willReturn(
                            List.of(stack(unitType, defender, 4), stack(unitType, defender, 3)));

            ScoutTerritoryResponse response = militaryService.scoutTerritory(1L, 30L);

            assertThat(response.territoryId()).isEqualTo(30L);
            assertThat(response.defenderTotalUnits()).isEqualTo(7);
        }

        @Test
        @DisplayName("SCOUT 유닛 없음 → SCOUT_UNIT_REQUIRED")
        void noScoutUnit() {
            Territory target = enemyTerritory();
            given(territoryRepository.findById(30L)).willReturn(Optional.of(target));
            given(unitTypeRepository.findByName("SCOUT")).willReturn(Optional.of(scoutType()));
            given(unitInstanceRepository.sumReadyIdleQuantity(1L, 9L, 1)).willReturn(0);

            assertThatThrownBy(() -> militaryService.scoutTerritory(1L, 30L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SCOUT_UNIT_REQUIRED);
        }

        @Test
        @DisplayName("자기 영토 정찰 → SCOUT_INVALID_TARGET")
        void ownTerritory() {
            Territory target = enemyTerritory();
            ReflectionTestUtils.setField(target, "owner", attacker);
            given(territoryRepository.findById(30L)).willReturn(Optional.of(target));

            assertThatThrownBy(() -> militaryService.scoutTerritory(1L, 30L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SCOUT_INVALID_TARGET);
        }
    }

    // ==========================================================
    // GetUnitList — 위치별 그룹핑
    // ==========================================================

    @Nested
    @DisplayName("GetTerritoryGarrison")
    class GetTerritoryGarrison {

        @Test
        @DisplayName("영토 배치 유닛을 타입별 합계로 반환")
        void groupsByType() {
            Territory territory = ownedTerritory();
            given(unitInstanceRepository.findByUserIdAndDeployedTerritoryId(1L, TERR_ID))
                    .willReturn(
                            List.of(idleAtTerritory(3, territory), idleAtTerritory(2, territory)));

            var res = militaryService.getTerritoryGarrison(1L, TERR_ID);

            assertThat(res).hasSize(1);
            assertThat(res.get(0).unitTypeId()).isEqualTo(1L);
            assertThat(res.get(0).deployedCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("배치 유닛 없으면 빈 목록")
        void empty() {
            given(unitInstanceRepository.findByUserIdAndDeployedTerritoryId(1L, TERR_ID))
                    .willReturn(List.of());
            assertThat(militaryService.getTerritoryGarrison(1L, TERR_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("GetUnitList")
    class GetUnitList {

        @Test
        @DisplayName("소유 영토 + 홈 아일랜드별로 유닛·수용량·저장 식량을 그룹핑")
        void grouping() {
            Territory territory = ownedTerritory();
            HomeIsland island = ownedIsland();
            UnitInstance idle = idleAtTerritory(7, territory);

            given(unitInstanceRepository.findByUserId(1L)).willReturn(List.of(idle));
            given(territoryRepository.findByOwnerId(1L)).willReturn(List.of(territory));
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(island));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryId(TERR_ID))
                    .willReturn(List.of(storage(0, 120)));
            given(buildingInstanceRepository.findCastleLevelByTerritoryId(TERR_ID))
                    .willReturn(Optional.of(1));
            given(buildingInstanceRepository.sumResidenceCapacityByTerritoryId(eq(TERR_ID), any()))
                    .willReturn(0);
            given(buildingInstanceRepository.findStorageBuildingsByIslandId(ISLAND_ID))
                    .willReturn(List.of());
            given(buildingInstanceRepository.findCastleLevelByIslandId(ISLAND_ID))
                    .willReturn(Optional.of(1));
            given(buildingInstanceRepository.sumResidenceCapacityByIslandId(eq(ISLAND_ID), any()))
                    .willReturn(0);

            UnitListResponse response = militaryService.getUnitList(1L);

            assertThat(response.locations()).hasSize(2);
            UnitListResponse.LocationUnits terr =
                    response.locations().stream()
                            .filter(l -> l.locationType().equals("TERRITORY"))
                            .findFirst()
                            .orElseThrow();
            assertThat(terr.storedFood()).isEqualTo(120);
            assertThat(terr.unitCapacity()).isEqualTo(5);
            assertThat(terr.units()).hasSize(1);
            assertThat(terr.units().get(0).idleCount()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("handleTerritoryLost()")
    class HandleTerritoryLost {

        // 성 Lv1 → 유닛 슬롯 5, 주거지 0 → 섬 수용량 5
        private void stubEmptyIslandCapacity() {
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(ownedIsland()));
            given(buildingInstanceRepository.findCastleLevelByIslandId(ISLAND_ID))
                    .willReturn(Optional.of(1));
            given(
                            buildingInstanceRepository.sumResidenceCapacityByIslandId(
                                    eq(ISLAND_ID), any(LocalDateTime.class)))
                    .willReturn(0);
            given(unitInstanceRepository.sumQuantityByHomeIslandId(ISLAND_ID)).willReturn(0);
            given(unitInstanceRepository.findReadyIdleAtIsland(1L, 1L, 1, ISLAND_ID))
                    .willReturn(Optional.empty());
            given(userRepository.findById(1L)).willReturn(Optional.of(attacker));
        }

        @Test
        @DisplayName("수용량 이내 → 유닛 전량 섬 대기 스택으로 퇴각, 원 스택 삭제")
        void withinCapacity_retreatsAll() {
            Territory lost = ownedTerritory();
            UnitInstance homed = idleAtTerritory(3, lost);
            given(unitInstanceRepository.findByOwnerAndTerritoryAssociation(1L, TERR_ID))
                    .willReturn(new java.util.ArrayList<>(List.of(homed)));
            stubEmptyIslandCapacity();

            militaryService.handleTerritoryLost(new TerritoryLostEvent(TERR_ID, 1L));

            ArgumentCaptor<UnitInstance> captor = ArgumentCaptor.forClass(UnitInstance.class);
            then(unitInstanceRepository).should().save(captor.capture());
            assertThat(captor.getValue().getQuantity()).isEqualTo(3);
            assertThat(captor.getValue().getHomeIsland().getId()).isEqualTo(ISLAND_ID);
            then(unitInstanceRepository).should().delete(homed);
        }

        @Test
        @DisplayName("섬 슬롯 초과분 소멸 → 수용량(5)까지만 퇴각")
        void overflow_destroysExcess() {
            Territory lost = ownedTerritory();
            UnitInstance homed = idleAtTerritory(8, lost);
            given(unitInstanceRepository.findByOwnerAndTerritoryAssociation(1L, TERR_ID))
                    .willReturn(new java.util.ArrayList<>(List.of(homed)));
            stubEmptyIslandCapacity();

            militaryService.handleTerritoryLost(new TerritoryLostEvent(TERR_ID, 1L));

            ArgumentCaptor<UnitInstance> captor = ArgumentCaptor.forClass(UnitInstance.class);
            then(unitInstanceRepository).should().save(captor.capture());
            assertThat(captor.getValue().getQuantity()).isEqualTo(5); // 8 중 5만 수용, 3 소멸
            then(unitInstanceRepository).should().delete(homed);
        }

        @Test
        @DisplayName("섬 없음 → 유닛 전부 소멸")
        void noIsland_annihilates() {
            Territory lost = ownedTerritory();
            List<UnitInstance> units = new java.util.ArrayList<>(List.of(idleAtTerritory(3, lost)));
            given(unitInstanceRepository.findByOwnerAndTerritoryAssociation(1L, TERR_ID))
                    .willReturn(units);
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.empty());

            militaryService.handleTerritoryLost(new TerritoryLostEvent(TERR_ID, 1L));

            then(unitInstanceRepository).should().deleteAll(units);
            then(unitInstanceRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("연관 유닛 없음 → 아무 동작 없음")
        void noUnits_noop() {
            given(unitInstanceRepository.findByOwnerAndTerritoryAssociation(1L, TERR_ID))
                    .willReturn(List.of());

            militaryService.handleTerritoryLost(new TerritoryLostEvent(TERR_ID, 1L));

            then(homeIslandRepository).should(never()).findByUserId(any());
        }
    }

    @Nested
    @DisplayName("handleGarrisonBuildingDestroyed()")
    class HandleGarrisonBuildingDestroyed {

        private static final long BUILDING_ID = 50L;

        @Test
        @DisplayName("주둔 유닛 있음 → 홈 아일랜드로 퇴각(원 스택 삭제)")
        void retreatsGarrison() {
            UnitInstance garrison = idleAtTerritory(3, ownedTerritory());
            given(unitInstanceRepository.findByDeployedBuildingId(BUILDING_ID))
                    .willReturn(new java.util.ArrayList<>(List.of(garrison)));
            // 섬 수용량 5(성 Lv1)
            given(homeIslandRepository.findByUserId(1L)).willReturn(Optional.of(ownedIsland()));
            given(buildingInstanceRepository.findCastleLevelByIslandId(ISLAND_ID))
                    .willReturn(Optional.of(1));
            given(
                            buildingInstanceRepository.sumResidenceCapacityByIslandId(
                                    eq(ISLAND_ID), any(LocalDateTime.class)))
                    .willReturn(0);
            given(unitInstanceRepository.sumQuantityByHomeIslandId(ISLAND_ID)).willReturn(0);
            given(unitInstanceRepository.findReadyIdleAtIsland(1L, 1L, 1, ISLAND_ID))
                    .willReturn(Optional.empty());
            given(userRepository.findById(1L)).willReturn(Optional.of(attacker));

            militaryService.handleGarrisonBuildingDestroyed(
                    new GarrisonBuildingDestroyedEvent(1L, BUILDING_ID));

            ArgumentCaptor<UnitInstance> captor = ArgumentCaptor.forClass(UnitInstance.class);
            then(unitInstanceRepository).should().save(captor.capture());
            assertThat(captor.getValue().getQuantity()).isEqualTo(3);
            assertThat(captor.getValue().getHomeIsland().getId()).isEqualTo(ISLAND_ID);
            then(unitInstanceRepository).should().delete(garrison);
        }

        @Test
        @DisplayName("주둔 유닛 없음 → 아무 동작 없음")
        void noGarrison_noop() {
            given(unitInstanceRepository.findByDeployedBuildingId(BUILDING_ID))
                    .willReturn(List.of());

            militaryService.handleGarrisonBuildingDestroyed(
                    new GarrisonBuildingDestroyedEvent(1L, BUILDING_ID));

            then(homeIslandRepository).should(never()).findByUserId(any());
        }
    }

    @Nested
    @DisplayName("getUnitTypeCatalog")
    class GetUnitTypeCatalog {

        @Test
        @DisplayName("보유 여부와 무관하게 전체 유닛 종류를 카탈로그로 반환")
        void getUnitTypeCatalog_returnsAllTypes() {
            given(unitTypeRepository.findAll()).willReturn(List.of(unitType));

            List<UnitTypeCatalogResponse> catalog = militaryService.getUnitTypeCatalog();

            assertThat(catalog).hasSize(1);
            assertThat(catalog.get(0).unitTypeId()).isEqualTo(1L);
            assertThat(catalog.get(0).name()).isEqualTo("INFANTRY");
            assertThat(catalog.get(0).costGp()).isEqualTo(100);
            assertThat(catalog.get(0).requiredBarracksLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("등록된 유닛 종류가 없으면 빈 리스트 반환")
        void getUnitTypeCatalog_empty() {
            given(unitTypeRepository.findAll()).willReturn(List.of());

            assertThat(militaryService.getUnitTypeCatalog()).isEmpty();
        }
    }
}
