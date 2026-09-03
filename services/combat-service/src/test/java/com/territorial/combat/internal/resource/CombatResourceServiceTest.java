package com.territorial.combat.internal.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.entity.GlobalVault;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingLevelSpecRepository;
import com.territorial.combat.domain.building.repository.GlobalVaultRepository;
import com.territorial.combat.domain.building.repository.HomeIslandRepository;
import com.territorial.combat.domain.military.repository.AttackTokenRepository;
import com.territorial.combat.domain.military.repository.UnitInstanceRepository;
import com.territorial.combat.global.exception.ErrorCode;
import com.territorial.combat.internal.admin.CombatCommand;
import com.territorial.combat.internal.admin.CombatCommandRepository;
import com.territorial.combat.internal.resource.CombatResourceContract.ChargeTaxRequest;
import com.territorial.combat.internal.resource.CombatResourceContract.CreditGpRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CombatResourceServiceTest {

    @InjectMocks private CombatResourceService service;
    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private HomeIslandRepository homeIslandRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private BuildingLevelSpecRepository buildingLevelSpecRepository;
    @Mock private AttackTokenRepository attackTokenRepository;
    @Mock private UnitInstanceRepository unitInstanceRepository;
    @Mock private CombatCommandRepository commandRepository;

    @BeforeEach
    void setUp() {
        service =
                new CombatResourceService(
                        globalVaultRepository,
                        homeIslandRepository,
                        buildingInstanceRepository,
                        buildingLevelSpecRepository,
                        attackTokenRepository,
                        unitInstanceRepository,
                        commandRepository,
                        new ObjectMapper());
    }

    @Test
    void creditGp_successStoresResultForIdempotentRetry() {
        GlobalVault vault = GlobalVault.builder().userId(1L).build();
        vault.receiveGp(50);
        given(commandRepository.findById("reward-1")).willReturn(Optional.empty());
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

        var response = service.creditGp(new CreditGpRequest(1L, 100, "reward-1"));

        assertThat(response.vaultGp()).isEqualTo(150);
        assertThat(vault.getStoredGp()).isEqualTo(150);
        then(commandRepository).should().save(any(CombatCommand.class));
    }

    @Test
    void creditGp_retryReturnsOriginalResponseWithoutApplyingCreditAgain() {
        CombatCommand completed = new CombatCommand("reward-1", "CREDIT_GP", "1:100");
        completed.recordResponse("{\"vaultGp\":150}");
        given(commandRepository.findById("reward-1")).willReturn(Optional.of(completed));

        var response = service.creditGp(new CreditGpRequest(1L, 100, "reward-1"));

        assertThat(response.vaultGp()).isEqualTo(150);
        then(globalVaultRepository).should(never()).findByIdWithLock(any());
        then(commandRepository).should(never()).save(any());
    }

    @Test
    void creditGp_reusedCommandWithDifferentPayloadIsRejected() {
        CombatCommand completed = new CombatCommand("reward-1", "CREDIT_GP", "1:100");
        completed.recordResponse("{\"vaultGp\":150}");
        given(commandRepository.findById("reward-1")).willReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.creditGp(new CreditGpRequest(1L, 200, "reward-1")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WALLET_COMMAND_CONFLICT);
    }

    @Test
    void chargeTax_insufficientBalanceReturnsFalseWithoutMutation() {
        GlobalVault vault = GlobalVault.builder().userId(1L).build();
        vault.receiveGp(40);
        given(commandRepository.findById("tax-1")).willReturn(Optional.empty());
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
        given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                .willReturn(List.of());

        var response = service.chargeTax(new ChargeTaxRequest(1L, 100, List.of(10L), "tax-1"));

        assertThat(response.paid()).isFalse();
        assertThat(vault.getStoredGp()).isEqualTo(40);
        then(commandRepository).should().save(any(CombatCommand.class));
    }

    @Test
    void chargeTax_duplicateTerritoryIdsAreLoadedOnce() {
        given(commandRepository.findById("tax-1")).willReturn(Optional.empty());
        given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.empty());
        given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(10L))
                .willReturn(List.of());

        var response = service.chargeTax(new ChargeTaxRequest(1L, 100, List.of(10L, 10L), "tax-1"));

        assertThat(response.paid()).isFalse();
        then(buildingInstanceRepository).should().findStorageBuildingsByTerritoryIdWithLock(10L);
    }
}
