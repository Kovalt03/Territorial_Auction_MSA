package com.territorial.auction.domain.military.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.building.repository.HomeIslandRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.military.entity.SiegeEvent;
import com.territorial.auction.domain.military.entity.SiegeForce;
import com.territorial.auction.domain.military.entity.SiegeResult;
import com.territorial.auction.domain.military.entity.SiegeStructure;
import com.territorial.auction.domain.military.entity.SiegeStructureType;
import com.territorial.auction.domain.military.entity.UnitInstance;
import com.territorial.auction.domain.military.entity.UnitType;
import com.territorial.auction.domain.military.event.SiegeVictoryEvent;
import com.territorial.auction.domain.military.repository.SiegeForceRepository;
import com.territorial.auction.domain.military.repository.SiegeResultRepository;
import com.territorial.auction.domain.military.repository.UnitInstanceRepository;
import com.territorial.auction.domain.season.entity.Season;
import com.territorial.auction.domain.season.repository.SeasonRepository;
import com.territorial.auction.domain.user.entity.User;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SiegeServiceTest {

    @InjectMocks private SiegeService siegeService;

    @Mock
    private com.territorial.auction.domain.military.repository.SiegeEventRepository
            siegeEventRepository;

    @Mock private SiegeResultRepository siegeResultRepository;
    @Mock private SiegeForceRepository siegeForceRepository;

    @Mock
    private com.territorial.auction.domain.military.repository.SiegeStructureRepository
            siegeStructureRepository;

    @Mock private UnitInstanceRepository unitInstanceRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private SeasonRepository seasonRepository;

    @Mock
    private com.territorial.auction.domain.notification.service.NotificationService
            notificationService;

    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.territorial.auction.domain.building.repository.BuildingLevelSpecRepository
            buildingLevelSpecRepository;

    private SiegeEvent event;
    private User attacker;
    private User defender;
    private Territory territory;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        // 공격자 패배 케이스에서 findActiveSeason이 호출되지 않으므로 UnnecessaryStubbingException 방지
        lenient()
                .when(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        lenient()
                .when(buildingLevelSpecRepository.findAllByBuildingType_IdIn(any()))
                .thenReturn(java.util.List.of());

        attacker = mock(User.class);
        given(attacker.getId()).willReturn(1L);

        defender = mock(User.class);
        given(defender.getId()).willReturn(2L);

        territory = mock(Territory.class);
        given(territory.getId()).willReturn(10L);

        event = mock(SiegeEvent.class);
        given(event.getId()).willReturn(100L);
        // resolveOneSiege가 트랜잭션 내에서 event를 다시 로드한다 → 같은 mock을 돌려준다.
        lenient().when(siegeEventRepository.findById(100L)).thenReturn(Optional.of(event));
        given(event.getAttacker()).willReturn(attacker);
        given(event.getDefender()).willReturn(defender);
        given(event.getTargetTerritory()).willReturn(territory);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private UnitInstance makeUnit(int attackPower, int defensePower, int quantity) {
        return makeUnit(attackPower, defensePower, quantity, 0);
    }

    private UnitInstance makeUnit(
            int attackPower, int defensePower, int quantity, int buildingDamage) {
        UnitType unitType =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(attackPower)
                        .defensePower(defensePower)
                        .costGp(100)
                        .foodCost(1)
                        .buildingDamage(buildingDamage)
                        .level(1)
                        .build();
        return UnitInstance.builder().user(attacker).unitType(unitType).quantity(quantity).build();
    }

    // 공격자가 커밋한 병력(SiegeForce). makeUnit과 같은 시그니처(defensePower는 공격 병력엔 무의미).
    private SiegeForce makeForce(int attackPower, int defensePower, int quantity) {
        return makeForce(attackPower, defensePower, quantity, 0);
    }

    private SiegeForce makeForce(
            int attackPower, int defensePower, int quantity, int buildingDamage) {
        UnitType unitType =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(attackPower)
                        .defensePower(defensePower)
                        .costGp(100)
                        .foodCost(1)
                        .buildingDamage(buildingDamage)
                        .level(1)
                        .build();
        return SiegeForce.builder().unitType(unitType).quantity(quantity).build();
    }

    private SiegeStructure makeStructure(SiegeStructureType type) {
        return SiegeStructure.builder().type(type).coordX(0).coordY(0).build();
    }

    private BuildingInstance makeBuilding(
            String typeName,
            int maxHp,
            int currentHp,
            Integer defensePower,
            int storedGp,
            int zone) {
        BuildingType buildingType =
                BuildingType.builder()
                        .name(typeName)
                        .width(1)
                        .height(1)
                        .maxHp(maxHp)
                        .baseCostGp(100)
                        .zoneRestriction(null)
                        .defensePower(defensePower)
                        .build();
        BuildingInstance building =
                BuildingInstance.builder()
                        .territory(territory)
                        .buildingType(buildingType)
                        .posX(0)
                        .posY(0)
                        .hp(currentHp)
                        .zone(zone)
                        .build();
        ReflectionTestUtils.setField(building, "storedGp", storedGp);
        return building;
    }

    @Nested
    @DisplayName("resolveOneSiege")
    class ResolveOneSiege {

        @Test
        @DisplayName("Zone 3 공격 성공 → LOOT, Storage storedGp 50% 약탈 후 공격자 금고 이전")
        void resolveOneSiege_zone3_attackerWins_loots() {
            // given
            given(event.getAttackZone()).willReturn(3);

            SiegeForce attackerForce = makeForce(100, 0, 10); // ATK = 1000
            UnitInstance defenderUnit = makeUnit(0, 50, 5); // DEF = 250 → 공격자 승

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of(defenderUnit));

            // storedGp=1000, defensePower=null → DEF 계산에서 제외, loot 대상
            BuildingInstance storage = makeBuilding("STORAGE", 200, 200, null, 1000, 3);
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of(storage));

            GlobalVault vault = mock(GlobalVault.class);
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            // when
            siegeService.resolveOneSiege(event);

            // then — 500GP(50%) 약탈이 공격자 금고로
            then(vault).should().receiveGp(500);
            assertThat(storage.getStoredGp()).isEqualTo(500);

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getIsAttackerWin()).isTrue();
            assertThat(captor.getValue().getResultType()).isEqualTo(SiegeResult.ResultType.LOOT);
            assertThat(captor.getValue().getLootedGp()).isEqualTo(500);
            then(event).should().resolve();
        }

        @Test
        @DisplayName("Zone 3 공격 성공, Storage storedGp = 0 → lootedGp = 0, 금고 미접근")
        void resolveOneSiege_zone3_emptyStorage_lootedGpZero() {
            // given
            given(event.getAttackZone()).willReturn(3);

            SiegeForce attackerForce = makeForce(100, 0, 10);
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());
            BuildingInstance emptyStorage = makeBuilding("STORAGE", 200, 200, null, 0, 3);
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of(emptyStorage));

            // when
            siegeService.resolveOneSiege(event);

            // then — totalLooted=0, 금고 미접근
            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getLootedGp()).isZero();
            then(globalVaultRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("공성 타워 버프로 교전 역전 → 공격자 승리 + 공성 건물 삭제")
        void resolveOneSiege_towerBonus_flipsResult() {
            given(event.getAttackZone()).willReturn(3);
            // 기본 ATK 1000 < DEF 1100 → 타워 없으면 패배. 타워 1개(+20%) → 1200 > 1100 승리.
            SiegeForce attackerForce = makeForce(100, 0, 10);
            UnitInstance defenderUnit = makeUnit(0, 110, 10);
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of(defenderUnit));
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of());
            List<SiegeStructure> structures = List.of(makeStructure(SiegeStructureType.TOWER));
            given(siegeStructureRepository.findBySiegeId(100L)).willReturn(structures);

            siegeService.resolveOneSiege(event);

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getIsAttackerWin()).isTrue();
            then(siegeStructureRepository).should().deleteAll(structures);
        }

        @Test
        @DisplayName("공격 실패 + 보급소 1개 → 적용 쿨다운 2h→1h로 완화 기록")
        void resolveOneSiege_supplyReducesCooldown() {
            given(event.getAttackZone()).willReturn(1);
            // ATK 1000 < DEF 2000 → 공격 실패.
            SiegeForce attackerForce = makeForce(100, 0, 10);
            UnitInstance defenderUnit = makeUnit(0, 200, 10);
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of(defenderUnit));
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                    .willReturn(List.of());
            given(siegeStructureRepository.findBySiegeId(100L))
                    .willReturn(List.of(makeStructure(SiegeStructureType.SUPPLY)));

            siegeService.resolveOneSiege(event);

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getIsAttackerWin()).isFalse();
            assertThat(captor.getValue().getAppliedCooldownHours()).isEqualTo(1);
        }

        @Test
        @DisplayName("Zone 2 공격 성공 → DEBUFF, Zone 2 건물 HP maxHp/2 감소")
        void resolveOneSiege_zone2_attackerWins_debuffs() {
            // given
            given(event.getAttackZone()).willReturn(2);

            SiegeForce attackerForce = makeForce(100, 0, 10); // ATK = 1000
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());

            // Workshop maxHp=200, HP=200 → 데미지 100 → 잔여 HP=100
            BuildingInstance workshop = makeBuilding("WORKSHOP", 200, 200, null, 0, 2);
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 2))
                    .willReturn(List.of(workshop));

            // when
            siegeService.resolveOneSiege(event);

            // then
            assertThat(workshop.getHp()).isEqualTo(100);
            assertThat(workshop.isDestroyed()).isFalse();
            assertThat(workshop.getWorkshopDebuffUntil()).isNull(); // 파괴 안 됐으면 디버프 없음

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getResultType()).isEqualTo(SiegeResult.ResultType.DEBUFF);
        }

        @Test
        @DisplayName("Zone 2 공격 성공, WORKSHOP HP 0 → isDestroyed=true, workshopDebuffUntil 설정")
        void resolveOneSiege_zone2_workshopDestroyed_debuffApplied() {
            // given
            given(event.getAttackZone()).willReturn(2);

            SiegeForce attackerForce = makeForce(100, 0, 10); // ATK = 1000
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());

            // Workshop maxHp=100, HP=50 → 데미지 50 → HP 0 → isDestroyed=true
            BuildingInstance workshop = makeBuilding("WORKSHOP", 100, 50, null, 0, 2);
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 2))
                    .willReturn(List.of(workshop));

            // when
            siegeService.resolveOneSiege(event);

            // then
            assertThat(workshop.isDestroyed()).isTrue();
            assertThat(workshop.getWorkshopDebuffUntil()).isNotNull();
            assertThat(workshop.getWorkshopDebuffUntil()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Zone 1 공격 성공하나 건물피해 0(공성 유닛 없음) → 성 무피해, 인계 없음")
        void resolveOneSiege_zone1_normalAttack_castleNotDestroyed_noTakeover() {
            // given
            given(event.getAttackZone()).willReturn(1);
            given(event.getTargetBuilding()).willReturn(null);

            // buildingDamage=0 유닛 → 교전은 이겨도 성 HP를 못 깎는다
            SiegeForce attackerForce = makeForce(100, 0, 10);
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());

            BuildingInstance castle = makeBuilding("CASTLE", 200, 200, null, 0, 1);
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                    .willReturn(List.of(castle));

            // when
            siegeService.resolveOneSiege(event);

            // then — 건물피해 0이라 성 HP 그대로, 인계 없음
            assertThat(castle.getHp()).isEqualTo(200);
            assertThat(castle.isDestroyed()).isFalse();
            then(territory).should(never()).occupy(any(), any(), any());
            then(unitInstanceRepository)
                    .should(never())
                    .findByOwnerAndTerritoryAssociation(any(), any());

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getResultType()).isEqualTo(SiegeResult.ResultType.AUCTION);
        }

        @Test
        @DisplayName("Zone 1 일반 공격 성공, Castle HP 0 → 공격자 인계: GP 80% 금고·방어 유닛 전멸·영토 점유")
        void resolveOneSiege_zone1_normalAttack_castleDestroyed_takesOver() {
            // given
            given(event.getAttackZone()).willReturn(1);
            given(event.getTargetBuilding()).willReturn(null);

            // 공성 유닛(buildingDamage=10) 10기 → 손실 30% 후 생존 7기 × 10 = 70 건물피해
            SiegeForce attackerForce = makeForce(100, 0, 10, 10);
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());

            // Castle HP=50, 건물피해 70 → HP 0 → isDestroyed=true
            BuildingInstance castle = makeBuilding("CASTLE", 200, 50, null, 0, 1);
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                    .willReturn(List.of(castle));

            // 저장 공간 GP 1000, 식량 500 → 80%(800) 공격자 금고, 20%·식량 소멸
            BuildingInstance storage = makeBuilding("STORAGE", 200, 200, null, 1000, 1);
            ReflectionTestUtils.setField(storage, "storedFood", 500);
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                    .willReturn(List.of(storage));

            List<UnitInstance> defenderUnits = List.of(makeUnit(0, 50, 3));
            given(unitInstanceRepository.findByOwnerAndTerritoryAssociation(2L, 10L))
                    .willReturn(defenderUnits);

            GlobalVault vault = mock(GlobalVault.class);
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            // when
            siegeService.resolveOneSiege(event);

            // then — 성 파괴 후 인계 효과
            assertThat(castle.isDestroyed()).isTrue();
            assertThat(storage.getStoredGp()).isZero();
            assertThat(storage.getStoredFood()).isZero();
            then(vault).should().receiveGp(800);
            then(unitInstanceRepository).should().deleteAll(defenderUnits);
            then(territory)
                    .should()
                    .occupy(eq(attacker), any(LocalDateTime.class), any(LocalDateTime.class));
            then(eventPublisher).should(never()).publishEvent(any());

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getResultType()).isEqualTo(SiegeResult.ResultType.AUCTION);
        }

        @Test
        @DisplayName("Zone 1 정밀 공격 → 지정 건물에 건물피해 전량 집중, 인계 없음")
        void resolveOneSiege_zone1_precisionAttack_onlyTargetBuildingDamaged() {
            // given
            // Castle HP=200, 건물피해 70(생존 7기×10) → 잔여 HP=130, 파괴 안 됨
            BuildingInstance targetCastle = makeBuilding("CASTLE", 200, 200, null, 0, 1);

            given(event.getAttackZone()).willReturn(1);
            given(event.getTargetBuilding()).willReturn(targetCastle);

            SiegeForce attackerForce = makeForce(100, 0, 10, 10);
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                    .willReturn(List.of(targetCastle));

            // when
            siegeService.resolveOneSiege(event);

            // then
            assertThat(targetCastle.getHp()).isEqualTo(130);
            assertThat(targetCastle.isDestroyed()).isFalse();
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("Zone 3 정밀 공격 → 지정 저장소만 약탈, 나머지 저장소는 무피해")
        void resolveOneSiege_zone3_precisionAttack_lootsOnlyTargetStorage() {
            // given — Zone 3에 저장소 2개(A=지정, B=비지정)
            BuildingInstance targetStorage = makeBuilding("STORAGE", 200, 200, null, 1000, 3);
            BuildingInstance otherStorage = makeBuilding("STORAGE", 200, 200, null, 2000, 3);

            given(event.getAttackZone()).willReturn(3);
            given(event.getTargetBuilding()).willReturn(targetStorage);

            SiegeForce attackerForce = makeForce(100, 0, 10); // ATK = 1000, DEF = 0 → 승
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());
            // calculateDef가 Zone 3 건물을 조회(저장소는 defensePower null → DEF 0)
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of(targetStorage, otherStorage));

            GlobalVault vault = mock(GlobalVault.class);
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            // when
            siegeService.resolveOneSiege(event);

            // then — 지정 저장소만 50% 약탈, 나머지는 그대로
            assertThat(targetStorage.getStoredGp()).isEqualTo(500);
            assertThat(otherStorage.getStoredGp()).isEqualTo(2000);
            then(vault).should().receiveGp(500);

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getResultType()).isEqualTo(SiegeResult.ResultType.LOOT);
            assertThat(captor.getValue().getLootedGp()).isEqualTo(500);
        }

        @Test
        @DisplayName("ATK <= DEF → 공격자 패배, 결과 효과 없음, 이벤트 미발행")
        void resolveOneSiege_defenderWins_noResultEffect() {
            // given
            given(event.getAttackZone()).willReturn(3);

            SiegeForce attackerForce = makeForce(10, 0, 10); // ATK = 100
            UnitInstance defenderUnit = makeUnit(0, 50, 5); // DEF = 250 → 방어자 승

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of(defenderUnit));
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of());

            // when
            siegeService.resolveOneSiege(event);

            // then
            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            SiegeResult result = captor.getValue();
            assertThat(result.getIsAttackerWin()).isFalse();
            assertThat(result.getResultType()).isNull();
            assertThat(result.getLootedGp()).isZero();
            assertThat(result.getDefenderUnitsLost()).isZero();
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("공격자 패배 시 유닛 손실률 50% ceil 적용")
        void resolveOneSiege_defenderWins_attackerLoseFiftyPercent() {
            // given
            given(event.getAttackZone()).willReturn(3);

            // ATK = 10*5 = 50, DEF = 50*5 = 250 → 패배
            // 공격자 손실: ceil(5 * 0.5) = 3
            SiegeForce attackerForce = makeForce(10, 0, 5);
            UnitInstance defenderUnit = makeUnit(0, 50, 5);

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of(defenderUnit));
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of());

            // when
            siegeService.resolveOneSiege(event);

            // then — 공격자 5-3=2, 방어자 변동 없음
            assertThat(attackerForce.getQuantity()).isEqualTo(2);
            assertThat(defenderUnit.getQuantity()).isEqualTo(5);

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getAttackerUnitsLost()).isEqualTo(3);
        }

        @Test
        @DisplayName("공격자 승리 시 쌍방 유닛 손실률 30% ceil 적용")
        void resolveOneSiege_attackerWins_bothSideLooseThirtyPercent() {
            // given
            given(event.getAttackZone()).willReturn(3);

            // ATK = 100*10 = 1000, DEF = 10*7 = 70 → 공격자 승
            // 공격자 손실: ceil(10 * 0.3) = 3, 방어자 손실: ceil(7 * 0.3) = 3
            SiegeForce attackerForce = makeForce(100, 0, 10);
            UnitInstance defenderUnit = makeUnit(0, 10, 7);

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of(defenderUnit));
            // 빈 Storage → totalLooted=0 → wallet 미호출
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of());

            // when
            siegeService.resolveOneSiege(event);

            // then
            assertThat(attackerForce.getQuantity()).isEqualTo(7); // 10 - 3
            assertThat(defenderUnit.getQuantity()).isEqualTo(4); // 7 - 3

            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getAttackerUnitsLost()).isEqualTo(3);
            assertThat(captor.getValue().getDefenderUnitsLost()).isEqualTo(3);
        }

        @Test
        @DisplayName("유닛 손실 후 quantity <= 0 → UnitInstance 삭제")
        void resolveOneSiege_unitQuantityZero_unitDeleted() {
            // given
            given(event.getAttackZone()).willReturn(3);

            // ATK = 100*10 = 1000, DEF = 10*1 = 10 → 공격자 승
            // 방어자 손실: ceil(1 * 0.3) = 1 → quantity 0 → 삭제
            SiegeForce attackerForce = makeForce(100, 0, 10);
            UnitInstance defenderUnit = makeUnit(0, 10, 1);

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of(defenderUnit));
            // 빈 Storage → totalLooted=0 → wallet 미호출
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of());

            // when
            siegeService.resolveOneSiege(event);

            // then
            assertThat(defenderUnit.getQuantity()).isZero();
            then(unitInstanceRepository).should().delete(defenderUnit);
        }

        @Test
        @DisplayName("건물 defensePower가 DEF에 합산됨 → 건물 방어력만으로 공격자 패배")
        void resolveOneSiege_buildingDefensePowerContributesToDef() {
            // given
            given(event.getAttackZone()).willReturn(1);

            // ATK = 10*5 = 50, 유닛 DEF = 0, WALL defensePower=100 → 총 DEF=100 → 공격자 패배
            SiegeForce attackerForce = makeForce(10, 0, 5);
            BuildingInstance wall = makeBuilding("WALL", 200, 200, 100, 0, 1);

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                    .willReturn(List.of(wall));

            // when
            siegeService.resolveOneSiege(event);

            // then — 공격자 패배, 이벤트 미발행
            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getIsAttackerWin()).isFalse();
            then(eventPublisher).should(never()).publishEvent(any());
        }

        @Test
        @DisplayName("건설 중인 방어 건물 → 방어력 0, 공격자 승리")
        void resolveOneSiege_underConstructionBuilding_contributesNoDefense() {
            // given
            given(event.getAttackZone()).willReturn(1);

            // ATK = 10*5 = 50, WALL defensePower=100 이지만 건설 중이라 DEF=0 → 공격자 승
            SiegeForce attackerForce = makeForce(10, 0, 5);
            BuildingInstance wall = makeBuilding("WALL", 200, 200, 100, 0, 1);
            wall.startConstruction(LocalDateTime.now().plusMinutes(5));

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 1))
                    .willReturn(List.of(wall));

            // when
            siegeService.resolveOneSiege(event);

            // then — 방어력이 0이라 공격자 승리. 건물은 여전히 공격 대상(HP 존재)
            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getIsAttackerWin()).isTrue();
        }

        @Test
        @DisplayName("건물 defensePower null → DEF 합산에서 제외, 공격자 승리")
        void resolveOneSiege_buildingWithNullDefensePower_notCountedInDef() {
            // given
            given(event.getAttackZone()).willReturn(3);

            // ATK = 10*5 = 50, STORAGE defensePower=null → DEF=0 → 공격자 승
            SiegeForce attackerForce = makeForce(10, 0, 5);
            BuildingInstance storage = makeBuilding("STORAGE", 200, 200, null, 0, 3);

            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of(storage));

            // when
            siegeService.resolveOneSiege(event);

            // then — DEF=0이므로 공격자 승, storedGp=0이므로 금고 미호출
            ArgumentCaptor<SiegeResult> captor = ArgumentCaptor.forClass(SiegeResult.class);
            then(siegeResultRepository).should().save(captor.capture());
            assertThat(captor.getValue().getIsAttackerWin()).isTrue();
            then(globalVaultRepository).should(never()).findById(any());
        }

        @Test
        @DisplayName("공격자 승리 + 활성 시즌 존재 → SiegeVictoryEvent 발행 (attackerId, seasonId 포함)")
        void resolveOneSiege_attackerWins_activeSeason_publishesSiegeVictoryEvent() {
            // given
            given(event.getAttackZone()).willReturn(3);

            Season season = mock(Season.class);
            given(season.getId()).willReturn(99L);
            given(seasonRepository.findActiveSeason(any(LocalDateTime.class)))
                    .willReturn(Optional.of(season));

            SiegeForce attackerForce = makeForce(100, 0, 10); // ATK=1000, DEF=0 → 공격자 승
            given(siegeForceRepository.findBySiegeId(100L)).willReturn(List.of(attackerForce));
            given(unitInstanceRepository.findDefendersInZone(eq(2L), eq(10L), anyInt()))
                    .willReturn(List.of());
            given(buildingInstanceRepository.findActiveByTerritoryIdAndZone(10L, 3))
                    .willReturn(List.of());

            // when
            siegeService.resolveOneSiege(event);

            // then
            ArgumentCaptor<SiegeVictoryEvent> captor =
                    ArgumentCaptor.forClass(SiegeVictoryEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().attackerId()).isEqualTo(1L);
            assertThat(captor.getValue().seasonId()).isEqualTo(99L);
        }
    }
}
