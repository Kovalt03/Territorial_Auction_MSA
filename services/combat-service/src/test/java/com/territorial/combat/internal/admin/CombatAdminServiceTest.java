package com.territorial.combat.internal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.BuildingCategory;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.repository.BuildingCastleLimitRepository;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingLevelSpecRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.military.entity.UnitType;
import com.territorial.combat.domain.military.repository.UnitTypeLevelSpecRepository;
import com.territorial.combat.domain.military.repository.UnitTypeRepository;
import com.territorial.combat.global.exception.ErrorCode;
import com.territorial.combat.internal.admin.CombatAdminContract.AdjustGpRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.CreateBuildingTypeRequest;
import com.territorial.combat.internal.admin.CombatAdminContract.UnitLevelValues;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CombatAdminServiceTest {

    @InjectMocks private CombatAdminService service;
    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BuildingLevelSpecRepository buildingLevelSpecRepository;
    @Mock private BuildingCastleLimitRepository buildingCastleLimitRepository;
    @Mock private UnitTypeRepository unitTypeRepository;
    @Mock private UnitTypeLevelSpecRepository unitTypeLevelSpecRepository;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private CombatUserSnapshotRepository userSnapshotRepository;
    @Mock private CombatCommandRepository commandRepository;

    @Test
    void createsDecorativeBuildingAndDropsProductionFields() {
        CreateBuildingTypeRequest request =
                new CreateBuildingTypeRequest(
                        "statue", "동상", 1, 1, 50, 300, null, null, null, 15, 99, 99, 99, null, null,
                        "🗽", "#cccccc");
        given(buildingTypeRepository.existsByName("STATUE")).willReturn(false);
        given(buildingTypeRepository.save(any()))
                .willAnswer(
                        invocation -> {
                            BuildingType type = invocation.getArgument(0);
                            ReflectionTestUtils.setField(type, "id", 11L);
                            return type;
                        });

        var result = service.createBuildingType(request);

        assertThat(result.name()).isEqualTo("STATUE");
        assertThat(result.category()).isEqualTo(BuildingCategory.DECORATIVE.name());
        assertThat(result.defensePower()).isEqualTo(15);
        assertThat(result.foodProductionRate()).isNull();
        assertThat(result.unitCapacityPerLevel()).isNull();
        assertThat(result.gpProductionRate()).isNull();
    }

    @Test
    void rejectsCreatingFunctionalBuildingCode() {
        CreateBuildingTypeRequest request =
                new CreateBuildingTypeRequest(
                        "workshop",
                        null,
                        1,
                        1,
                        50,
                        300,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        given(buildingTypeRepository.existsByName("WORKSHOP")).willReturn(false);

        assertThatThrownBy(() -> service.createBuildingType(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FUNCTIONAL_BUILDING_NOT_CREATABLE);
    }

    @Test
    void rejectsIncompleteUnitLevelSpec() {
        UnitType type =
                UnitType.builder()
                        .name("INFANTRY")
                        .attackPower(10)
                        .defensePower(10)
                        .costGp(100)
                        .foodCost(1)
                        .build();
        ReflectionTestUtils.setField(type, "id", 1L);
        given(unitTypeRepository.findById(1L)).willReturn(Optional.of(type));
        given(unitTypeLevelSpecRepository.findByUnitType_IdAndLevel(1L, 2))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.updateUnitLevelSpecs(
                                        1L, Map.of(2, new UnitLevelValues(13, null, 50, 2))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INCOMPLETE_UNIT_LEVEL_SPEC);
    }

    @Test
    void appliesGpAdjustmentOnceForSameCommand() {
        GlobalVault vault = GlobalVault.builder().userId(1L).build();
        vault.receiveGp(50);
        given(commandRepository.findById("cmd-1"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(new CombatCommand("cmd-1", "ADMIN_ADJUST_GP", "1:100")));
        given(commandRepository.existsById("cmd-1")).willReturn(false).willReturn(true);
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
        given(globalVaultRepository.findById(1L)).willReturn(Optional.of(vault));

        var first = service.adjustGp(new AdjustGpRequest(1L, 100, "cmd-1"));
        var retried = service.adjustGp(new AdjustGpRequest(1L, 100, "cmd-1"));

        assertThat(first.availableGp()).isEqualTo(150);
        assertThat(retried.availableGp()).isEqualTo(150);
        then(commandRepository).should().save(any(CombatCommand.class));
    }

    @Test
    void rejectsReusedCommandWithDifferentPayload() {
        given(commandRepository.findById("cmd-1"))
                .willReturn(Optional.of(new CombatCommand("cmd-1", "ADMIN_ADJUST_GP", "1:100")));

        assertThatThrownBy(() -> service.adjustGp(new AdjustGpRequest(1L, 200, "cmd-1")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WALLET_COMMAND_CONFLICT);
    }
}
