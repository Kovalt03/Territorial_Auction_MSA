package com.territorial.combat.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.CombatUserSnapshot;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.military.dto.DeclareSiegeRequest;
import com.territorial.combat.domain.military.entity.AttackToken;
import com.territorial.combat.domain.military.entity.SiegeEvent;
import com.territorial.combat.domain.military.entity.SiegeStructureType;
import com.territorial.combat.domain.military.entity.UnitInstance;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort;
import com.territorial.combat.domain.military.port.SiegeTerritoryPort.TerritoryCombatContext;
import com.territorial.combat.domain.military.repository.*;
import com.territorial.combat.event.CombatOutboxService;
import com.territorial.combat.global.exception.ErrorCode;
import java.time.LocalDateTime;
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
class SiegeCommandServiceTest {

    @InjectMocks private SiegeCommandService service;
    @Mock private SiegeTerritoryPort territoryPort;
    @Mock private CombatUserSnapshotRepository userSnapshotRepository;
    @Mock private SiegeEventRepository siegeEventRepository;
    @Mock private SiegeResultRepository siegeResultRepository;
    @Mock private SiegeForceRepository siegeForceRepository;
    @Mock private SiegeStructureRepository siegeStructureRepository;
    @Mock private AttackTokenRepository attackTokenRepository;
    @Mock private UnitInstanceRepository unitInstanceRepository;
    @Mock private UnitTypeRepository unitTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private CombatOutboxService outboxService;

    private UnitType unitType;

    @BeforeEach
    void setUp() {
        unitType =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(10)
                        .defensePower(10)
                        .costGp(100)
                        .foodCost(1)
                        .build();
        ReflectionTestUtils.setField(unitType, "id", 1L);
    }

    private TerritoryCombatContext target() {
        return new TerritoryCombatContext(20L, 2L, 5, 6, true, null);
    }

    private List<DeclareSiegeRequest.StructureEntry> structures() {
        return List.of(new DeclareSiegeRequest.StructureEntry(SiegeStructureType.STAGING, 5, 7));
    }

    private DeclareSiegeRequest request() {
        return new DeclareSiegeRequest(
                20L, null, 3, List.of(new DeclareSiegeRequest.ForceEntry(1L, 3, 1)), structures());
    }

    private CombatUserSnapshot snapshot(long id, String nickname) {
        return CombatUserSnapshot.builder().userId(id).nickname(nickname).status("ACTIVE").build();
    }

    private GlobalVault vault(int gp) {
        GlobalVault vault = GlobalVault.builder().userId(1L).build();
        ReflectionTestUtils.setField(vault, "storedGp", gp);
        return vault;
    }

    @Test
    @DisplayName("공성 선언은 병력·토큰·금고를 차감하고 declared outbox를 기록한다")
    void declareSiege_success() {
        AttackToken token = AttackToken.builder().userId(1L).build();
        token.addNormal(2);
        UnitInstance idle =
                UnitInstance.builder()
                        .userId(1L)
                        .unitType(unitType)
                        .quantity(10)
                        .homeTerritoryId(10L)
                        .build();
        GlobalVault vault = vault(1000);
        given(territoryPort.findById(20L)).willReturn(Optional.of(target()));
        given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                .willReturn(List.of());
        given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(10);
        given(attackTokenRepository.findByUserIdWithLock(1L)).willReturn(Optional.of(token));
        given(userSnapshotRepository.findById(1L)).willReturn(Optional.of(snapshot(1L, "공격자")));
        given(userSnapshotRepository.findById(2L)).willReturn(Optional.of(snapshot(2L, "방어자")));
        given(siegeEventRepository.save(any()))
                .willAnswer(
                        invocation -> {
                            SiegeEvent event = invocation.getArgument(0);
                            ReflectionTestUtils.setField(event, "id", 99L);
                            return event;
                        });
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(unitType));
        given(unitInstanceRepository.findReadyIdleByUserIdAndUnitTypeIdAndLevel(1L, 1L, 1))
                .willReturn(List.of(idle));
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

        var response = service.declareSiege(1L, request());

        assertThat(response.siegeId()).isEqualTo(99L);
        assertThat(response.attackTokenRemaining()).isEqualTo(1);
        assertThat(idle.getQuantity()).isEqualTo(7);
        assertThat(vault.getStoredGp()).isEqualTo(500);
        then(siegeForceRepository).should().save(any());
        then(siegeStructureRepository).should().save(any());
        then(outboxService)
                .should()
                .append(eq("SIEGE"), eq(99L), eq("combat.siege.declared"), any());
    }

    @Test
    @DisplayName("자기 영토 공격은 CANNOT_ATTACK_OWN_TERRITORY")
    void declareSiege_ownTerritory() {
        given(territoryPort.findById(20L))
                .willReturn(Optional.of(new TerritoryCombatContext(20L, 1L, 5, 6, true, null)));
        assertError(request(), ErrorCode.CANNOT_ATTACK_OWN_TERRITORY);
    }

    @Test
    @DisplayName("보호 기간 중인 영토는 TERRITORY_PROTECTED")
    void declareSiege_protected() {
        given(territoryPort.findById(20L))
                .willReturn(
                        Optional.of(
                                new TerritoryCombatContext(
                                        20L, 2L, 5, 6, true, LocalDateTime.now().plusHours(1))));
        assertError(request(), ErrorCode.TERRITORY_PROTECTED);
    }

    @Test
    @DisplayName("대기 병력이 부족하면 INSUFFICIENT_UNITS")
    void declareSiege_insufficientUnits() {
        given(territoryPort.findById(20L)).willReturn(Optional.of(target()));
        given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                .willReturn(List.of());
        given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(1);
        assertError(request(), ErrorCode.INSUFFICIENT_UNITS);
    }

    @Test
    @DisplayName("주둔지가 없으면 SIEGE_STAGING_REQUIRED")
    void declareSiege_stagingRequired() {
        given(territoryPort.findById(20L)).willReturn(Optional.of(target()));
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
        assertError(noStaging, ErrorCode.SIEGE_STAGING_REQUIRED);
    }

    @Test
    @DisplayName("주둔지 수용량을 넘으면 SIEGE_FORCE_EXCEEDS_CAPACITY")
    void declareSiege_forceExceedsCapacity() {
        given(territoryPort.findById(20L)).willReturn(Optional.of(target()));
        given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                .willReturn(List.of());
        given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(20);
        DeclareSiegeRequest tooMany =
                new DeclareSiegeRequest(
                        20L,
                        null,
                        3,
                        List.of(new DeclareSiegeRequest.ForceEntry(1L, 11, 1)),
                        structures());
        assertError(tooMany, ErrorCode.SIEGE_FORCE_EXCEEDS_CAPACITY);
    }

    @Test
    @DisplayName("인접하지 않은 공성 건물 좌표는 SIEGE_STRUCTURE_PLACEMENT_INVALID")
    void declareSiege_invalidPlacement() {
        given(territoryPort.findById(20L)).willReturn(Optional.of(target()));
        given(siegeEventRepository.findRecentByTerritoryAndAttacker(eq(20L), eq(1L), any()))
                .willReturn(List.of());
        given(unitInstanceRepository.sumReadyIdleQuantity(1L, 1L, 1)).willReturn(10);
        DeclareSiegeRequest far =
                new DeclareSiegeRequest(
                        20L,
                        null,
                        3,
                        List.of(new DeclareSiegeRequest.ForceEntry(1L, 3, 1)),
                        List.of(
                                new DeclareSiegeRequest.StructureEntry(
                                        SiegeStructureType.STAGING, 0, 0)));
        assertError(far, ErrorCode.SIEGE_STRUCTURE_PLACEMENT_INVALID);
    }

    private void assertError(DeclareSiegeRequest request, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.declareSiege(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
