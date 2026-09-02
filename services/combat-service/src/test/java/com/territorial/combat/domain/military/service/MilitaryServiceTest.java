package com.territorial.combat.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.entity.HomeIsland;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository.MilitaryLocationSummary;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.military.LocationType;
import com.territorial.combat.domain.military.config.MilitaryBalanceProperties;
import com.territorial.combat.domain.military.dto.*;
import com.territorial.combat.domain.military.entity.AttackToken;
import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.port.MilitaryTerritoryPort;
import com.territorial.combat.domain.military.port.MilitaryTerritoryPort.TerritoryLocation;
import com.territorial.combat.domain.military.repository.*;
import com.territorial.combat.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MilitaryServiceTest {

    private static final long USER_ID = 1L;
    private static final long TERRITORY_ID = 10L;
    private static final long ISLAND_ID = 20L;

    @InjectMocks private MilitaryService service;
    @Mock private AttackTokenRepository attackTokenRepository;
    @Mock private UnitInstanceRepository unitInstanceRepository;
    @Mock private UnitTypeRepository unitTypeRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private MilitaryTerritoryPort territoryPort;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private UnitResearchRepository unitResearchRepository;
    @Mock private UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    @Mock private MilitaryBalanceProperties balanceProperties;

    private UnitType unitType;

    @BeforeEach
    void setUp() {
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
    }

    private TerritoryLocation territory(long ownerId) {
        return new TerritoryLocation(TERRITORY_ID, ownerId, 3, 4);
    }

    private HomeIsland island() {
        HomeIsland island = HomeIsland.builder().userId(USER_ID).build();
        ReflectionTestUtils.setField(island, "id", ISLAND_ID);
        return island;
    }

    private BuildingInstance storage(int gp, int food) {
        BuildingType type =
                BuildingType.builder().name("STORAGE").width(1).height(1).maxHp(60).build();
        BuildingInstance storage =
                BuildingInstance.builder()
                        .territoryId(TERRITORY_ID)
                        .buildingType(type)
                        .posX(0)
                        .posY(0)
                        .hp(60)
                        .zone(2)
                        .build();
        ReflectionTestUtils.setField(storage, "storedGp", gp);
        ReflectionTestUtils.setField(storage, "storedFood", food);
        return storage;
    }

    private UnitInstance idle(int quantity) {
        return UnitInstance.builder()
                .userId(USER_ID)
                .unitType(unitType)
                .quantity(quantity)
                .homeTerritoryId(TERRITORY_ID)
                .build();
    }

    private MilitaryLocationSummary summary(int barracks, int castle) {
        return new MilitaryLocationSummary() {
            public Integer getMaxBarracksLevel() {
                return barracks;
            }

            public Integer getCastleLevel() {
                return castle;
            }

            public Integer getResidenceCapacity() {
                return 0;
            }
        };
    }

    private void productionReady(int currentUnits, BuildingInstance storage) {
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
        given(territoryPort.findById(TERRITORY_ID)).willReturn(Optional.of(territory(USER_ID)));
        given(
                        buildingInstanceRepository.findMilitaryLocationSummaryByTerritoryId(
                                eq(TERRITORY_ID), any()))
                .willReturn(summary(1, 1));
        given(unitInstanceRepository.sumQuantityByHomeTerritoryId(TERRITORY_ID))
                .willReturn(currentUnits);
        if (storage != null) {
            given(
                            buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(
                                    TERRITORY_ID))
                    .willReturn(List.of(storage));
        }
    }

    @Test
    @DisplayName("공격권이 있으면 수량을 반환하고 없으면 0을 반환한다")
    void getAttackTokens() {
        AttackToken token = AttackToken.builder().userId(USER_ID).build();
        token.addNormal(3);
        token.addPrecision();
        given(attackTokenRepository.findByUserId(USER_ID)).willReturn(Optional.of(token));
        assertThat(service.getAttackTokens(USER_ID)).isEqualTo(new AttackTokenResponse(3, 1));

        given(attackTokenRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        assertThat(service.getAttackTokens(USER_ID)).isEqualTo(new AttackTokenResponse(0, 0));
    }

    @Test
    @DisplayName("유닛 생산은 위치 저장소의 GP·식량을 차감하고 새 스택을 저장한다")
    void produceUnit_success() {
        BuildingInstance storage = storage(5000, 500);
        productionReady(0, storage);
        given(unitInstanceRepository.findReadyIdleAtTerritory(USER_ID, 1L, 1, TERRITORY_ID))
                .willReturn(Optional.empty());

        ProduceUnitResponse response =
                service.produceUnit(
                        USER_ID,
                        new ProduceUnitRequest(1L, 3, 1, TERRITORY_ID, LocationType.TERRITORY));

        assertThat(response.gpRemaining()).isEqualTo(4700);
        assertThat(storage.getStoredFood()).isEqualTo(494);
        then(unitInstanceRepository).should().save(any(UnitInstance.class));
    }

    @Test
    @DisplayName("병영이 없으면 생산을 거부한다")
    void produceUnit_noBarracks() {
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
        given(territoryPort.findById(TERRITORY_ID)).willReturn(Optional.of(territory(USER_ID)));
        given(
                        buildingInstanceRepository.findMilitaryLocationSummaryByTerritoryId(
                                eq(TERRITORY_ID), any()))
                .willReturn(summary(0, 1));

        assertThatThrownBy(
                        () ->
                                service.produceUnit(
                                        USER_ID,
                                        new ProduceUnitRequest(
                                                1L, 1, 1, TERRITORY_ID, LocationType.TERRITORY)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_BARRACKS);
    }

    @Test
    @DisplayName("유닛 수용량을 넘으면 생산을 거부한다")
    void produceUnit_capacityExceeded() {
        productionReady(5, null);
        assertThatThrownBy(
                        () ->
                                service.produceUnit(
                                        USER_ID,
                                        new ProduceUnitRequest(
                                                1L, 1, 1, TERRITORY_ID, LocationType.TERRITORY)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNIT_CAPACITY_EXCEEDED);
    }

    @Test
    @DisplayName("소유 영토의 건물에 대기 유닛을 배치한다")
    void deployUnit_success() {
        UnitInstance idle = idle(10);
        BuildingType type =
                BuildingType.builder().name("CASTLE").width(1).height(1).maxHp(300).build();
        BuildingInstance castle =
                BuildingInstance.builder()
                        .territoryId(TERRITORY_ID)
                        .buildingType(type)
                        .posX(0)
                        .posY(0)
                        .hp(300)
                        .zone(1)
                        .build();
        ReflectionTestUtils.setField(castle, "id", 50L);
        given(territoryPort.findById(TERRITORY_ID)).willReturn(Optional.of(territory(USER_ID)));
        given(buildingInstanceRepository.findById(50L)).willReturn(Optional.of(castle));
        given(balanceProperties.garrisonCapacity("CASTLE")).willReturn(5);
        given(unitInstanceRepository.sumQuantityByDeployedBuildingId(50L)).willReturn(0);
        given(unitInstanceRepository.findReadyIdleAtTerritory(USER_ID, 1L, 1, TERRITORY_ID))
                .willReturn(Optional.of(idle));
        given(unitInstanceRepository.findDeployedFromTerritory(USER_ID, 1L, 1, TERRITORY_ID, 50L))
                .willReturn(Optional.empty());

        service.deployUnit(
                USER_ID,
                new DeployUnitRequest(
                        TERRITORY_ID, 50L, 1L, 4, 1, TERRITORY_ID, LocationType.TERRITORY));

        assertThat(idle.getQuantity()).isEqualTo(6);
        then(unitInstanceRepository).should().save(any(UnitInstance.class));
    }

    @Test
    @DisplayName("다른 사용자의 영토에는 배치할 수 없다")
    void deployUnit_notOwner() {
        given(territoryPort.findById(TERRITORY_ID)).willReturn(Optional.of(territory(2L)));
        DeployUnitRequest request =
                new DeployUnitRequest(
                        TERRITORY_ID, 50L, 1L, 1, 1, TERRITORY_ID, LocationType.TERRITORY);
        assertThatThrownBy(() -> service.deployUnit(USER_ID, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
    }

    @Test
    @DisplayName("배치 유닛은 원래 영토의 대기 스택으로 회수된다")
    void recallUnit_success() {
        UnitInstance deployed = idle(10);
        deployed.deployTo(TERRITORY_ID, null);
        UnitInstance homeIdle = idle(1);
        given(territoryPort.findById(TERRITORY_ID)).willReturn(Optional.of(territory(USER_ID)));
        given(unitInstanceRepository.findDeployedAtTerritory(USER_ID, 1L, 1, TERRITORY_ID))
                .willReturn(List.of(deployed));
        given(unitInstanceRepository.findReadyIdleAtTerritory(USER_ID, 1L, 1, TERRITORY_ID))
                .willReturn(Optional.of(homeIdle));

        service.recallUnit(USER_ID, new RecallUnitRequest(TERRITORY_ID, 1L, 4, 1));

        assertThat(deployed.getQuantity()).isEqualTo(6);
        assertThat(homeIdle.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("영토에서 섬으로 이동하면 GP를 차감하고 이동 중 스택을 저장한다")
    void moveUnit_success() {
        HomeIsland island = island();
        UnitInstance idle = idle(10);
        given(territoryPort.findById(TERRITORY_ID)).willReturn(Optional.of(territory(USER_ID)));
        given(homeIslandRepository.findByUserId(USER_ID)).willReturn(Optional.of(island));
        given(unitInstanceRepository.findReadyIdleAtTerritory(USER_ID, 1L, 1, TERRITORY_ID))
                .willReturn(Optional.of(idle));
        given(unitInstanceRepository.sumQuantityByHomeIslandId(ISLAND_ID)).willReturn(0);
        given(buildingInstanceRepository.findCastleLevelByIslandId(ISLAND_ID))
                .willReturn(Optional.of(1));
        given(buildingInstanceRepository.sumResidenceCapacityByIslandId(eq(ISLAND_ID), any()))
                .willReturn(0);
        BuildingInstance storage = storage(5000, 0);
        given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(TERRITORY_ID))
                .willReturn(List.of(storage));

        MoveUnitResponse response =
                service.moveUnit(
                        USER_ID,
                        new MoveUnitRequest(
                                1L,
                                4,
                                1,
                                TERRITORY_ID,
                                LocationType.TERRITORY,
                                ISLAND_ID,
                                LocationType.ISLAND));

        assertThat(response.gpRemaining()).isEqualTo(4960);
        assertThat(response.moveCompleteAt()).isNotNull();
        then(unitInstanceRepository).should().save(any(UnitInstance.class));
    }

    @Test
    @DisplayName("유닛 목록은 소유 위치별 수용량·식량·유닛을 묶는다")
    void getUnitList_groupsByLocation() {
        given(unitInstanceRepository.findByUserId(USER_ID)).willReturn(List.of(idle(7)));
        given(territoryPort.findOwnedByUserId(USER_ID)).willReturn(List.of(territory(USER_ID)));
        given(homeIslandRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(buildingInstanceRepository.findStorageBuildingsByTerritoryId(TERRITORY_ID))
                .willReturn(List.of(storage(0, 120)));
        given(buildingInstanceRepository.findCastleLevelByTerritoryId(TERRITORY_ID))
                .willReturn(Optional.of(1));
        given(buildingInstanceRepository.sumResidenceCapacityByTerritoryId(eq(TERRITORY_ID), any()))
                .willReturn(0);

        UnitListResponse response = service.getUnitList(USER_ID);

        assertThat(response.locations()).singleElement();
        assertThat(response.locations().get(0).storedFood()).isEqualTo(120);
        assertThat(response.locations().get(0).units().get(0).idleCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("유닛 카탈로그는 전체 타입을 반환한다")
    void getUnitTypeCatalog() {
        given(unitTypeRepository.findAll()).willReturn(List.of(unitType));
        assertThat(service.getUnitTypeCatalog())
                .singleElement()
                .extracting("name")
                .isEqualTo("INFANTRY");
    }
}
