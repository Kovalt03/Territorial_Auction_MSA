package com.territorial.auction.domain.building.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.building.dto.PurchaseDecorationResponse;
import com.territorial.auction.domain.building.entity.BuildingCategory;
import com.territorial.auction.domain.building.entity.BuildingInstance;
import com.territorial.auction.domain.building.entity.BuildingType;
import com.territorial.auction.domain.building.repository.BuildingInstanceRepository;
import com.territorial.auction.domain.building.repository.BuildingTypeRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wallet;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
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
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;

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

    private Wallet wallet(int ap) {
        Wallet w = Wallet.builder().user(user()).build();
        ReflectionTestUtils.setField(w, "availableAp", ap);
        return w;
    }

    private User user() {
        User u = User.builder().username("u").email("u@x").passwordHash("h").nickname("n").build();
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }

    @Test
    @DisplayName("장식 구매 성공 → AP 차감 + 인벤토리 생성")
    void purchase_success() {
        Wallet w = wallet(1000);
        given(buildingTypeRepository.findById(20L)).willReturn(Optional.of(decoration(300)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(w));
        given(buildingInstanceRepository.save(any()))
                .willAnswer(
                        inv -> {
                            BuildingInstance b = inv.getArgument(0);
                            ReflectionTestUtils.setField(b, "id", 99L);
                            return b;
                        });

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
    @DisplayName("AP 부족 → INSUFFICIENT_AP")
    void purchase_insufficientAp() {
        given(buildingTypeRepository.findById(20L)).willReturn(Optional.of(decoration(300)));
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        given(walletRepository.findByIdWithLock(1L)).willReturn(Optional.of(wallet(100)));

        assertThatThrownBy(() -> buildingShopService.purchase(1L, 20L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_AP);
        then(buildingInstanceRepository).should(never()).save(any());
    }
}
