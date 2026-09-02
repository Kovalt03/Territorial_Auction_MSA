package com.territorial.combat.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.combat.domain.building.dto.PurchaseDecorationResponse;
import com.territorial.combat.domain.building.entity.BuildingCategory;
import com.territorial.combat.domain.building.entity.BuildingInstance;
import com.territorial.combat.domain.building.entity.BuildingType;
import com.territorial.combat.domain.building.port.WalletPort;
import com.territorial.combat.domain.building.port.WalletPort.WalletSnapshot;
import com.territorial.combat.domain.building.repository.BuildingInstanceRepository;
import com.territorial.combat.domain.building.repository.BuildingTypeRepository;
import com.territorial.combat.domain.building.repository.CombatUserSnapshotRepository;
import com.territorial.combat.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BuildingShopServiceTest {

    @InjectMocks private BuildingShopService buildingShopService;

    @Mock private BuildingTypeRepository buildingTypeRepository;
    @Mock private BuildingInstanceRepository buildingInstanceRepository;
    @Mock private CombatUserSnapshotRepository userSnapshotRepository;
    @Mock private WalletPort walletPort;

    private BuildingType decoration(Integer apCost) {
        BuildingType t =
                BuildingType.builder()
                        .name("STATUE")
                        .category(BuildingCategory.DECORATIVE)
                        .width(1)
                        .height(1)
                        .maxHp(80)
                        .baseCostGp(0)
                        .apCost(apCost)
                        .build();
        ReflectionTestUtils.setField(t, "id", 20L);
        return t;
    }

    @Test
    @DisplayName("장식 구매 성공 → AP 차감(user-service) + 인벤토리 생성")
    void purchase_success() {
        given(buildingTypeRepository.findById(20L)).willReturn(Optional.of(decoration(300)));
        given(userSnapshotRepository.existsById(1L)).willReturn(true);
        given(buildingInstanceRepository.save(any()))
                .willAnswer(
                        inv -> {
                            BuildingInstance b = inv.getArgument(0);
                            ReflectionTestUtils.setField(b, "id", 99L);
                            return b;
                        });
        given(walletPort.spend(eq(1L), eq(300), anyString())).willReturn(new WalletSnapshot(700));

        PurchaseDecorationResponse res = buildingShopService.purchase(1L, 20L);

        assertThat(res.apRemaining()).isEqualTo(700);
        assertThat(res.inventoryId()).isEqualTo(99L);
        then(buildingInstanceRepository).should().save(any(BuildingInstance.class));
    }

    @Test
    @DisplayName("판매가 없는 건물 구매 → BUILDING_NOT_PURCHASABLE")
    void purchase_notPurchasable() {
        given(buildingTypeRepository.findById(20L)).willReturn(Optional.of(decoration(null)));

        assertThatThrownBy(() -> buildingShopService.purchase(1L, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUILDING_NOT_PURCHASABLE);
        then(buildingInstanceRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("AP 부족(user-service 거부) → INSUFFICIENT_AP")
    void purchase_insufficientAp() {
        given(buildingTypeRepository.findById(20L)).willReturn(Optional.of(decoration(300)));
        given(userSnapshotRepository.existsById(1L)).willReturn(true);
        given(buildingInstanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        willThrow(new CustomException(ErrorCode.INSUFFICIENT_AP))
                .given(walletPort)
                .spend(eq(1L), eq(300), anyString());

        // 저장은 트랜잭션 안에서 일어나고 spend 실패 시 롤백된다(런타임 @Transactional). 단위테스트는 예외 전파만 검증.
        assertThatThrownBy(() -> buildingShopService.purchase(1L, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
    }
}
