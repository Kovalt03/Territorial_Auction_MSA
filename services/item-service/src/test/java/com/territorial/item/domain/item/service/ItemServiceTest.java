package com.territorial.item.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.item.client.CombatResourceClient;
import com.territorial.item.client.CombatResourceClient.AttackTokenBalance;
import com.territorial.item.client.TerritoryOwnershipClient;
import com.territorial.item.client.WalletClient;
import com.territorial.item.client.WalletClient.WalletSnapshot;
import com.territorial.item.domain.item.dto.PurchaseItemRequest;
import com.territorial.item.domain.item.dto.PurchaseItemResponse;
import com.territorial.item.domain.item.dto.UseItemRequest;
import com.territorial.item.domain.item.entity.Item;
import com.territorial.item.domain.item.entity.Item.ItemType;
import com.territorial.item.domain.item.entity.UserItem;
import com.territorial.item.domain.item.repository.ItemPurchaseRepository;
import com.territorial.item.domain.item.repository.ItemRepository;
import com.territorial.item.domain.item.repository.UserItemRepository;
import com.territorial.item.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @InjectMocks private ItemService itemService;

    @Mock private ItemRepository itemRepository;
    @Mock private ItemPurchaseRepository itemPurchaseRepository;
    @Mock private UserItemRepository userItemRepository;
    @Mock private WalletClient walletClient;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private TerritoryOwnershipClient territoryOwnershipClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private Item item(long id, ItemType type, int costAp) {
        Item i = Item.builder().name("아이템").itemType(type).costAp(costAp).build();
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    @Nested
    @DisplayName("purchaseItem")
    class Purchase {

        @Test
        @DisplayName("일반 아이템 구매 → 인벤토리 지급 + AP 소비")
        void purchase_normalItem() {
            Item i = item(1L, ItemType.ATTACK_NORMAL, 100);
            given(itemRepository.findById(1L)).willReturn(Optional.of(i));
            given(userItemRepository.findByUserIdAndItem_Id(1L, 1L)).willReturn(Optional.empty());
            given(userItemRepository.save(any(UserItem.class)))
                    .willAnswer(inv -> inv.getArgument(0));
            given(walletClient.spend(eq(1L), eq(200), anyString()))
                    .willReturn(new WalletSnapshot(800, 0));

            PurchaseItemResponse res = itemService.purchaseItem(1L, new PurchaseItemRequest(1L, 2));

            assertThat(res.costAP()).isEqualTo(200);
            assertThat(res.remainingAP()).isEqualTo(800);
            verify(userItemRepository).save(any(UserItem.class));
            verify(walletClient).spend(eq(1L), eq(200), anyString());
        }

        @Test
        @DisplayName("GP 구매권 → combat 금고 적립, 인벤토리 미지급")
        void purchase_gpItem() {
            Item i =
                    Item.builder()
                            .itemType(ItemType.GP_PURCHASE)
                            .costAp(50)
                            .gpReward(10000)
                            .build();
            ReflectionTestUtils.setField(i, "id", 2L);
            given(itemRepository.findById(2L)).willReturn(Optional.of(i));
            given(walletClient.spend(eq(1L), eq(50), anyString()))
                    .willReturn(new WalletSnapshot(950, 0));

            itemService.purchaseItem(1L, new PurchaseItemRequest(2L, 1));

            verify(combatResourceClient).creditGp(eq(1L), eq(10000), anyString());
            verify(userItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("없는 아이템 → ITEM_NOT_FOUND")
        void purchase_notFound() {
            given(itemRepository.findById(9L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.purchaseItem(1L, new PurchaseItemRequest(9L, 1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("useItem")
    class Use {

        @Test
        @DisplayName("공격권 사용 → combat 공격권 지급 + 수량 차감")
        void use_attackToken() {
            Item i = item(1L, ItemType.ATTACK_NORMAL, 100);
            UserItem owned =
                    UserItem.builder()
                            .userId(1L)
                            .item(i)
                            .quantity(2)
                            .createdAt(LocalDateTime.now())
                            .build();
            ReflectionTestUtils.setField(owned, "id", 5L);
            given(itemRepository.findById(1L)).willReturn(Optional.of(i));
            given(userItemRepository.findByUserIdAndItem_Id(1L, 1L)).willReturn(Optional.of(owned));
            given(combatResourceClient.creditAttackTokens(eq(1L), eq(1), eq(0), anyString()))
                    .willReturn(new AttackTokenBalance(3, 0));
            given(redisTemplate.delete(anyString())).willReturn(true);

            itemService.useItem(1L, new UseItemRequest(1L, null));

            assertThat(owned.getQuantity()).isEqualTo(1);
            verify(combatResourceClient).creditAttackTokens(eq(1L), eq(1), eq(0), anyString());
        }

        @Test
        @DisplayName("무적권 사용 → 소유 검증 후 Redis 무적키 설정")
        void use_invincibility() {
            Item i = item(1L, ItemType.INVINCIBILITY, 200);
            UserItem owned =
                    UserItem.builder()
                            .userId(1L)
                            .item(i)
                            .quantity(1)
                            .createdAt(LocalDateTime.now())
                            .build();
            ReflectionTestUtils.setField(owned, "id", 5L);
            given(itemRepository.findById(1L)).willReturn(Optional.of(i));
            given(userItemRepository.findByUserIdAndItem_Id(1L, 1L)).willReturn(Optional.of(owned));
            given(territoryOwnershipClient.getOwnerId(77L)).willReturn(1L);
            given(redisTemplate.hasKey("invincible:77")).willReturn(false);
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(redisTemplate.delete(anyString())).willReturn(true);

            itemService.useItem(1L, new UseItemRequest(1L, 77L));

            verify(valueOps).set(eq("invincible:77"), eq(true), any(java.time.Duration.class));
        }

        @Test
        @DisplayName("무적권 - 타인 영토 → NOT_TERRITORY_OWNER")
        void use_invincibility_notOwner() {
            Item i = item(1L, ItemType.INVINCIBILITY, 200);
            UserItem owned =
                    UserItem.builder()
                            .userId(1L)
                            .item(i)
                            .quantity(1)
                            .createdAt(LocalDateTime.now())
                            .build();
            given(itemRepository.findById(1L)).willReturn(Optional.of(i));
            given(userItemRepository.findByUserIdAndItem_Id(1L, 1L)).willReturn(Optional.of(owned));
            given(territoryOwnershipClient.getOwnerId(77L)).willReturn(999L);

            assertThatThrownBy(() -> itemService.useItem(1L, new UseItemRequest(1L, 77L)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_TERRITORY_OWNER);
        }

        @Test
        @DisplayName("GP 구매권은 사용 불가 → ITEM_NOT_USABLE")
        void use_gpItem_notUsable() {
            Item i = Item.builder().itemType(ItemType.GP_PURCHASE).build();
            ReflectionTestUtils.setField(i, "id", 2L);
            given(itemRepository.findById(2L)).willReturn(Optional.of(i));

            assertThatThrownBy(() -> itemService.useItem(1L, new UseItemRequest(2L, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_USABLE);
        }
    }
}
