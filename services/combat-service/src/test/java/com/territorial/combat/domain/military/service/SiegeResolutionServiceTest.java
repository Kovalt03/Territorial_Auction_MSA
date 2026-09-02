package com.territorial.combat.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.entity.CombatUserSnapshot;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.repository.*;
import com.territorial.combat.domain.military.entity.*;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort.TerritoryCombatContext;
import com.territorial.combat.domain.military.repository.*;
import com.territorial.combat.event.CombatOutboxService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SiegeResolutionServiceTest {

    @InjectMocks private SiegeResolutionService service;
    @Mock private SiegeEventRepository siegeEventRepository;
    @Mock private SiegeResultRepository siegeResultRepository;
    @Mock private SiegeForceRepository siegeForceRepository;
    @Mock private SiegeStructureRepository siegeStructureRepository;
    @Mock private UnitInstanceRepository unitInstanceRepository;
    @Mock private UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BuildingLevelSpecRepository buildingLevelSpecRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private CombatUserSnapshotRepository userSnapshotRepository;
    @Mock private SiegeTerritoryPort territoryPort;
    @Mock private UnitRetreatService unitRetreatService;
    @Mock private CombatOutboxService outboxService;

    private SiegeEvent event;

    @BeforeEach
    void setUp() {
        event =
                SiegeEvent.builder()
                        .attackerId(1L)
                        .defenderId(2L)
                        .targetTerritoryId(10L)
                        .attackZone(3)
                        .siegeStartAt(LocalDateTime.now().minusMinutes(6))
                        .resolveAt(LocalDateTime.now().minusMinutes(1))
                        .build();
        ReflectionTestUtils.setField(event, "id", 100L);
        lenient().when(siegeEventRepository.findById(100L)).thenReturn(Optional.of(event));
        lenient()
                .when(territoryPort.findById(10L))
                .thenReturn(Optional.of(new TerritoryCombatContext(10L, 2L, 4, 5, true, null)));
        lenient()
                .when(userSnapshotRepository.findById(1L))
                .thenReturn(Optional.of(snapshot(1L, "공격자")));
        lenient()
                .when(userSnapshotRepository.findById(2L))
                .thenReturn(Optional.of(snapshot(2L, "방어자")));
        lenient()
                .when(buildingLevelSpecRepository.findAllByBuildingType_IdIn(any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("Zone 3 승리는 저장 GP를 약탈하고 결과·outbox를 기록한다")
    void resolveZone3Loot() {
        SiegeForce force = force(100, 0, 10, 0);
        UnitInstance defender = unit(2L, 0, 50, 5);
        BuildingInstance storage = building("STORAGE", 200, 200, null, 3);
        ReflectionTestUtils.setField(storage, "storedGp", 1000);
        GlobalVault vault = GlobalVault.builder().userId(1L).build();

        given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(force));
        given(unitInstanceRepository.findDefendersInZone(2L, 10L, 3)).willReturn(List.of(defender));
        given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                .willReturn(List.of(storage));
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

        service.resolveOneSiege(event);

        assertThat(storage.getStoredGp()).isEqualTo(500);
        assertThat(vault.getStoredGp()).isEqualTo(500);
        assertThat(force.getQuantity()).isEqualTo(7);
        assertThat(defender.getQuantity()).isEqualTo(3);
        ArgumentCaptor<SiegeResult> result = ArgumentCaptor.forClass(SiegeResult.class);
        then(siegeResultRepository).should().save(result.capture());
        assertThat(result.getValue().getResultType()).isEqualTo(SiegeResult.ResultType.LOOT);
        assertThat(result.getValue().getLootedGp()).isEqualTo(500);
        then(outboxService)
                .should()
                .append(eq("SIEGE"), eq(100L), eq("combat.siege.resolved"), any());
        then(outboxService).should().append(eq("USER"), eq(1L), eq("combat.siege.victory"), any());
        assertThat(event.getStatus()).isEqualTo(SiegeEvent.SiegeStatus.RESOLVED);
    }

    @Test
    @DisplayName("공격 실패는 50% 손실과 보급소가 줄인 쿨다운만 기록한다")
    void resolveDefenderWinWithSupplyCooldown() {
        ReflectionTestUtils.setField(event, "attackZone", 1);
        SiegeForce force = force(10, 0, 5, 0);
        UnitInstance defender = unit(2L, 0, 100, 5);
        SiegeStructure supply =
                SiegeStructure.builder()
                        .type(SiegeStructureType.SUPPLY)
                        .coordX(3)
                        .coordY(5)
                        .build();
        given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(force));
        given(siegeStructureRepository.findBySiegeId(100L)).willReturn(List.of(supply));
        given(unitInstanceRepository.findDefendersInZone(2L, 10L, 1)).willReturn(List.of(defender));
        given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                .willReturn(List.of());

        service.resolveOneSiege(event);

        assertThat(force.getQuantity()).isEqualTo(2);
        assertThat(defender.getQuantity()).isEqualTo(5);
        ArgumentCaptor<SiegeResult> result = ArgumentCaptor.forClass(SiegeResult.class);
        then(siegeResultRepository).should().save(result.capture());
        assertThat(result.getValue().getIsAttackerWin()).isFalse();
        assertThat(result.getValue().getAttackerUnitsLost()).isEqualTo(3);
        assertThat(result.getValue().getAppliedCooldownHours()).isEqualTo(1);
        then(outboxService)
                .should(never())
                .append(eq("USER"), eq(1L), eq("combat.siege.victory"), any());
    }

    @Test
    @DisplayName("Zone 1 성 파괴는 저장 자원을 정산하고 영토 인계 outbox를 기록한다")
    void resolveCastleDestroyedRequestsTakeover() {
        ReflectionTestUtils.setField(event, "attackZone", 1);
        SiegeForce force = force(100, 0, 10, 10);
        BuildingInstance castle = building("CASTLE", 200, 50, null, 1);
        BuildingInstance storage = building("STORAGE", 200, 200, null, 1);
        ReflectionTestUtils.setField(storage, "storedGp", 1000);
        ReflectionTestUtils.setField(storage, "storedFood", 500);
        UnitInstance associated = unit(2L, 0, 10, 3);
        GlobalVault vault = GlobalVault.builder().userId(1L).build();

        given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(force));
        given(unitInstanceRepository.findDefendersInZone(2L, 10L, 1)).willReturn(List.of());
        given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                .willReturn(List.of(castle));
        given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                .willReturn(List.of(storage));
        given(unitInstanceRepository.findByOwnerAndTerritoryAssociation(2L, 10L))
                .willReturn(List.of(associated));
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

        service.resolveOneSiege(event);

        assertThat(castle.isDestroyed()).isTrue();
        assertThat(storage.getStoredGp()).isZero();
        assertThat(storage.getStoredFood()).isZero();
        assertThat(vault.getStoredGp()).isEqualTo(800);
        then(unitInstanceRepository).should().deleteAll(List.of(associated));
        then(outboxService)
                .should()
                .append(eq("TERRITORY"), eq(10L), eq("combat.territory.takeover-requested"), any());
    }

    @Test
    @DisplayName("Zone 2 건물 파괴는 주둔 병력 퇴각을 요청한다")
    void resolveDestroyedWorkshopRetreatsGarrison() {
        ReflectionTestUtils.setField(event, "attackZone", 2);
        SiegeForce force = force(100, 0, 10, 0);
        BuildingInstance workshop = building("WORKSHOP", 100, 50, null, 2);
        ReflectionTestUtils.setField(workshop, "id", 77L);
        given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(force));
        given(unitInstanceRepository.findDefendersInZone(2L, 10L, 2)).willReturn(List.of());
        given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 2))
                .willReturn(List.of(workshop));

        service.resolveOneSiege(event);

        assertThat(workshop.isDestroyed()).isTrue();
        assertThat(workshop.getWorkshopDebuffUntil()).isAfter(LocalDateTime.now());
        then(unitRetreatService).should().retreatFromDestroyedBuilding(2L, 77L);
    }

    private CombatUserSnapshot snapshot(Long id, String nickname) {
        return CombatUserSnapshot.builder().userId(id).nickname(nickname).status("ACTIVE").build();
    }

    private SiegeForce force(int attack, int defense, int quantity, int buildingDamage) {
        return SiegeForce.builder()
                .unitType(unitType(attack, defense, buildingDamage))
                .quantity(quantity)
                .level(1)
                .build();
    }

    private UnitInstance unit(Long userId, int attack, int defense, int quantity) {
        return UnitInstance.builder()
                .userId(userId)
                .unitType(unitType(attack, defense, 0))
                .quantity(quantity)
                .level(1)
                .homeTerritoryId(10L)
                .build();
    }

    private UnitType unitType(int attack, int defense, int buildingDamage) {
        return UnitType.builder()
                .name("INFANTRY")
                .attackPower(attack)
                .defensePower(defense)
                .buildingDamage(buildingDamage)
                .costGp(100)
                .foodCost(1)
                .level(1)
                .build();
    }

    private BuildingInstance building(String name, int maxHp, int hp, Integer defense, int zone) {
        BuildingType type =
                BuildingType.builder()
                        .name(name)
                        .width(1)
                        .height(1)
                        .maxHp(maxHp)
                        .baseCostGp(100)
                        .defensePower(defense)
                        .build();
        return BuildingInstance.builder()
                .territoryId(10L)
                .buildingType(type)
                .posX(0)
                .posY(0)
                .hp(hp)
                .zone(zone)
                .build();
    }
}
