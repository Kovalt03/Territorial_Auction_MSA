package com.territorial.auction.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.dto.GlobalVaultResponse;
import com.territorial.auction.domain.building.dto.VaultTransferRequest;
import com.territorial.auction.domain.building.dto.VaultTransferResponse;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.entity.GlobalVault;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.GlobalVaultRepository;
import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.military.event.TerritoryLostEvent;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
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
class GlobalVaultServiceTest {

    @InjectMocks private GlobalVaultService globalVaultService;

    @Mock private GlobalVaultRepository globalVaultRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private UserRepository userRepository;

    private User user;
    private GlobalVault vault;
    private Territory territory;
    private BuildingInstance storage; // 저장소 Lv2 = 용량 10,000

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .username("testuser")
                        .email("test@test.com")
                        .passwordHash("hashed")
                        .nickname("테스터")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        BuildingType storageType =
                BuildingType.builder()
                        .name("STORAGE")
                        .width(2)
                        .height(2)
                        .maxHp(100)
                        .baseCostGp(0)
                        .build();
        storage =
                BuildingInstance.builder()
                        .buildingType(storageType)
                        .posX(0)
                        .posY(0)
                        .hp(100)
                        .zone(2)
                        .build();
        ReflectionTestUtils.setField(storage, "level", 2); // 용량 10,000
        ReflectionTestUtils.setField(storage, "storedGp", 10000);

        vault = GlobalVault.builder().user(user).build();
        ReflectionTestUtils.setField(vault, "storedGp", 5000);
        ReflectionTestUtils.setField(vault, "capacity", 50000);

        territory = Territory.builder().coordX(1).coordY(1).build();
        ReflectionTestUtils.setField(territory, "id", 42L);
        ReflectionTestUtils.setField(territory, "owner", user);
    }

    // ─── getVault() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getVault()")
    class GetVault {

        @Test
        @DisplayName("최초 조회 (lastTransferAt=null) → isTransferAvailable=true")
        void noLastTransfer_isAvailable() {
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            GlobalVaultResponse response = globalVaultService.getVault(1L);

            assertThat(response.storedGP()).isEqualTo(5000);
            assertThat(response.capacity()).isEqualTo(50000);
            assertThat(response.lastTransferAt()).isNull();
            assertThat(response.nextTransferAvailableAt()).isNull();
            assertThat(response.isTransferAvailable()).isTrue();
        }

        @Test
        @DisplayName("쿨다운 중 → isTransferAvailable=false, nextTransferAvailableAt 반환")
        void cooldownActive_isNotAvailable() {
            LocalDateTime recent = LocalDateTime.now().minusMinutes(5);
            ReflectionTestUtils.setField(vault, "lastTransferAt", recent);
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            GlobalVaultResponse response = globalVaultService.getVault(1L);

            assertThat(response.isTransferAvailable()).isFalse();
            assertThat(response.nextTransferAvailableAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("쿨다운 만료 → isTransferAvailable=true")
        void cooldownExpired_isAvailable() {
            LocalDateTime past = LocalDateTime.now().minusMinutes(15);
            ReflectionTestUtils.setField(vault, "lastTransferAt", past);
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            GlobalVaultResponse response = globalVaultService.getVault(1L);

            assertThat(response.isTransferAvailable()).isTrue();
        }

        @Test
        @DisplayName("금고 없음 + 유저 존재 → 기본 금고 신규 생성하여 반환")
        void vaultMissing_createsDefault() {
            GlobalVault created = GlobalVault.builder().user(user).build();
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.empty());
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(globalVaultRepository.save(org.mockito.ArgumentMatchers.any(GlobalVault.class)))
                    .willReturn(created);

            GlobalVaultResponse response = globalVaultService.getVault(1L);

            assertThat(response.storedGP()).isEqualTo(0);
            assertThat(response.capacity()).isEqualTo(10000);
            assertThat(response.isTransferAvailable()).isTrue();
        }

        @Test
        @DisplayName("금고·유저 모두 없음 → USER_NOT_FOUND")
        void vaultAndUserMissing() {
            given(globalVaultRepository.findByIdWithLock(99L)).willReturn(Optional.empty());
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> globalVaultService.getVault(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ─── transfer() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("transfer() — TO_VAULT")
    class TransferToVault {

        @Test
        @DisplayName("저장소→금고 이전 성공 — 저장소 GP 감소, 금고 GP 증가")
        void toVault_success() {
            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of(storage));

            VaultTransferRequest request = new VaultTransferRequest("TO_VAULT", 42L, 3000L);
            VaultTransferResponse response = globalVaultService.transfer(1L, request);

            assertThat(response.direction()).isEqualTo("TO_VAULT");
            assertThat(response.transferredAmount()).isEqualTo(3000L);
            assertThat(response.territoryStorageAfter()).isEqualTo(7000L); // 10000 - 3000
            assertThat(response.vaultStoredAfter()).isEqualTo(8000L); // 5000 + 3000
            assertThat(storage.getStoredGp()).isEqualTo(7000);
            assertThat(response.nextTransferAvailableAt()).isNotNull();
        }

        @Test
        @DisplayName("저장 공간 GP 부족 → INSUFFICIENT_GP")
        void toVault_insufficientStorageGp() {
            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of(storage));

            VaultTransferRequest request = new VaultTransferRequest("TO_VAULT", 42L, 20000L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_GP);
        }

        @Test
        @DisplayName("저장 건물 없음 → STORAGE_NOT_FOUND")
        void toVault_noStorage() {
            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of());

            VaultTransferRequest request = new VaultTransferRequest("TO_VAULT", 42L, 1000L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.STORAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("금고 용량 초과 → VAULT_CAPACITY_EXCEEDED")
        void toVault_capacityExceeded() {
            ReflectionTestUtils.setField(vault, "storedGp", 49000);
            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of(storage));

            VaultTransferRequest request = new VaultTransferRequest("TO_VAULT", 42L, 5000L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VAULT_CAPACITY_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("transfer() — FROM_VAULT")
    class TransferFromVault {

        @Test
        @DisplayName("금고→저장소 이전 성공 — 금고 GP 감소, 저장소 GP 증가")
        void fromVault_success() {
            // 저장소 용량 10,000 에 이미 3,000 → 여유 7,000
            ReflectionTestUtils.setField(storage, "storedGp", 3000);
            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of(storage));

            VaultTransferRequest request = new VaultTransferRequest("FROM_VAULT", 42L, 2000L);
            VaultTransferResponse response = globalVaultService.transfer(1L, request);

            assertThat(response.direction()).isEqualTo("FROM_VAULT");
            assertThat(response.territoryStorageAfter()).isEqualTo(5000L); // 3000 + 2000
            assertThat(response.vaultStoredAfter()).isEqualTo(3000L); // 5000 - 2000
            assertThat(storage.getStoredGp()).isEqualTo(5000);
        }

        @Test
        @DisplayName("금고 GP 부족 → INSUFFICIENT_GP")
        void fromVault_insufficientVaultGp() {
            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of(storage));

            VaultTransferRequest request = new VaultTransferRequest("FROM_VAULT", 42L, 9999L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INSUFFICIENT_GP);
        }

        @Test
        @DisplayName("저장 공간 부족 → STORAGE_CAPACITY_EXCEEDED")
        void fromVault_storageCapacityExceeded() {
            // 저장소 이미 만재(10,000) → 받을 여유 없음
            ReflectionTestUtils.setField(storage, "storedGp", 10000);
            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of(storage));

            VaultTransferRequest request = new VaultTransferRequest("FROM_VAULT", 42L, 2000L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.STORAGE_CAPACITY_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("transfer() — 공통 검증")
    class TransferValidation {

        @Test
        @DisplayName("영토 없음 → TERRITORY_NOT_FOUND")
        void territoryNotFound() {
            given(territoryRepository.findById(999L)).willReturn(Optional.empty());

            VaultTransferRequest request = new VaultTransferRequest("TO_VAULT", 999L, 1000L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TERRITORY_NOT_FOUND);
        }

        @Test
        @DisplayName("영토 점유자 아님 → NOT_TERRITORY_OWNER")
        void notTerritoryOwner() {
            User other =
                    User.builder()
                            .username("other")
                            .email("other@test.com")
                            .passwordHash("hash")
                            .nickname("타인")
                            .build();
            ReflectionTestUtils.setField(other, "id", 2L);
            ReflectionTestUtils.setField(territory, "owner", other);

            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));

            VaultTransferRequest request = new VaultTransferRequest("TO_VAULT", 42L, 1000L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("쿨다운 중 이전 시도 → TRANSFER_COOLDOWN_ACTIVE")
        void cooldownActive() {
            LocalDateTime recent = LocalDateTime.now().minusMinutes(3);
            ReflectionTestUtils.setField(vault, "lastTransferAt", recent);

            given(territoryRepository.findById(42L)).willReturn(Optional.of(territory));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            VaultTransferRequest request = new VaultTransferRequest("TO_VAULT", 42L, 1000L);
            assertThatThrownBy(() -> globalVaultService.transfer(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.TRANSFER_COOLDOWN_ACTIVE);
        }
    }

    // ─── handleTerritoryLost() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("handleTerritoryLost()")
    class HandleTerritoryLost {

        @Test
        @DisplayName("영토 상실 → 저장 GP 80% 원소유자 금고 환수, 나머지·식량 소멸")
        void routesGpAndDestroysFood() {
            ReflectionTestUtils.setField(storage, "storedFood", 3000);
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of(storage));
            given(globalVaultRepository.findByIdWithLock(1L)).willReturn(Optional.of(vault));

            globalVaultService.handleTerritoryLost(new TerritoryLostEvent(42L, 1L));

            // GP 10,000 → 80%(8,000) 금고(기존 5,000 + 8,000 = 13,000), 저장소·식량 0
            assertThat(vault.getStoredGp()).isEqualTo(13000);
            assertThat(storage.getStoredGp()).isZero();
            assertThat(storage.getStoredFood()).isZero();
        }

        @Test
        @DisplayName("저장 공간 없음 → 금고 미접근")
        void noStorage_skipsVault() {
            given(buildingInstanceRepository.findStorageBuildingsByTerritoryIdWithLock(42L))
                    .willReturn(List.of());

            globalVaultService.handleTerritoryLost(new TerritoryLostEvent(42L, 1L));

            then(globalVaultRepository).should(never()).findById(any());
        }
    }
}
