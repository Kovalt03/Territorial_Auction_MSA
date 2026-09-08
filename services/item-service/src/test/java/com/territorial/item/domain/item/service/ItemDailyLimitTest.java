package com.territorial.item.domain.item.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.territorial.auction.global.exception.CustomException;
import com.territorial.item.client.CombatResourceClient;
import com.territorial.item.client.TerritoryOwnershipClient;
import com.territorial.item.client.WalletClient;
import com.territorial.item.domain.item.dto.PurchaseItemRequest;
import com.territorial.item.domain.item.entity.Item;
import com.territorial.item.domain.item.entity.Item.ItemType;
import com.territorial.item.domain.item.repository.ItemPurchaseRepository;
import com.territorial.item.domain.item.repository.ItemRepository;
import com.territorial.item.domain.item.repository.UserItemRepository;
import com.territorial.item.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItemDailyLimitTest {

    @InjectMocks private ItemService itemService;

    @Mock private ItemRepository itemRepository;
    @Mock private ItemPurchaseRepository itemPurchaseRepository;
    @Mock private UserItemRepository userItemRepository;
    @Mock private WalletClient walletClient;
    @Mock private CombatResourceClient combatResourceClient;
    @Mock private TerritoryOwnershipClient territoryOwnershipClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    private static final Long USER_ID = 3L;

    private Item limitedItem(int dailyLimit) {
        Item i = Item.builder().name("무적권").itemType(ItemType.INVINCIBILITY).costAp(100).build();
        ReflectionTestUtils.setField(i, "id", 1L);
        ReflectionTestUtils.setField(i, "dailyLimit", dailyLimit);
        return i;
    }

    @DisplayName("일일 한도 초과 구매 — DAILY_LIMIT_EXCEEDED로 차단하고 AP를 소비하지 않는다")
    @Test
    void purchase_exceedsDailyLimit_throwsBeforeSpending() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(limitedItem(5)));
        // 오늘 이미 4개 구매 → 추가 2개면 6 > 한도 5
        when(itemPurchaseRepository.sumTodayQuantity(eq(USER_ID), eq(1L), any(LocalDateTime.class)))
                .thenReturn(4);

        assertThatThrownBy(() -> itemService.purchaseItem(USER_ID, new PurchaseItemRequest(1L, 2)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DAILY_LIMIT_EXCEEDED);

        verify(walletClient, never()).spend(any(), anyInt(), anyString());
        verify(userItemRepository, never()).save(any());
    }
}
