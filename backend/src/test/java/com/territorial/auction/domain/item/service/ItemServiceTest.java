package com.territorial.auction.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.territorial.auction.domain.item.dto.UseItemRequest;
import com.territorial.auction.domain.item.dto.UseItemResponse;
import com.territorial.auction.domain.item.entity.Item;
import com.territorial.auction.domain.item.entity.Item.ItemType;
import com.territorial.auction.domain.item.entity.UserItem;
import com.territorial.auction.domain.item.repository.ItemPurchaseRepository;
import com.territorial.auction.domain.item.repository.ItemRepository;
import com.territorial.auction.domain.item.repository.UserItemRepository;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.military.entity.AttackToken;
import com.territorial.auction.domain.military.repository.AttackTokenRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WalletRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private TerritoryRepository territoryRepository;
    @Mock private AttackTokenRepository attackTokenRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private User user;
    private Item normalTokenItem;
    private Item precisionTokenItem;
    private AttackToken attackToken;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .username("user1")
                        .email("u@e.com")
                        .passwordHash("hash")
                        .nickname("유저1")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        normalTokenItem =
                Item.builder().name("일반 공격권").itemType(ItemType.ATTACK_NORMAL).costAp(100).build();
        ReflectionTestUtils.setField(normalTokenItem, "id", 10L);

        precisionTokenItem =
                Item.builder()
                        .name("정밀 공격권")
                        .itemType(ItemType.ATTACK_PRECISION)
                        .costAp(200)
                        .build();
        ReflectionTestUtils.setField(precisionTokenItem, "id", 11L);

        attackToken = AttackToken.builder().user(user).build();
        ReflectionTestUtils.setField(attackToken, "normalCount", 2);
        ReflectionTestUtils.setField(attackToken, "precisionCount", 1);
    }

    private UserItem makeUserItem(Item item, int quantity) {
        UserItem ui =
                UserItem.builder()
                        .user(user)
                        .item(item)
                        .quantity(quantity)
                        .createdAt(LocalDateTime.now())
                        .build();
        ReflectionTestUtils.setField(ui, "id", 99L);
        return ui;
    }

    @Nested
    @DisplayName("useItem — 공격권")
    class UseAttackToken {

        @Test
        @DisplayName("일반 공격권 사용 → 기존 AttackToken normalCount +1, remainingCount 감소")
        void useNormalToken_existingToken_incrementsNormalCount() {
            UserItem userItem = makeUserItem(normalTokenItem, 3);

            given(itemRepository.findById(10L)).willReturn(Optional.of(normalTokenItem));
            given(userItemRepository.findByUser_IdAndItem_Id(1L, 10L))
                    .willReturn(Optional.of(userItem));
            given(attackTokenRepository.findByUserIdWithLock(1L))
                    .willReturn(Optional.of(attackToken));

            UseItemResponse response = itemService.useItem(1L, new UseItemRequest(10L, null));

            assertThat(response.itemType()).isEqualTo("ATTACK_NORMAL");
            assertThat(response.remainingCount()).isEqualTo(2); // 3 - 1
            assertThat(response.result().normalCount()).isEqualTo(3); // 2 + 1
            assertThat(response.result().precisionCount()).isEqualTo(1);
            assertThat(response.result().territoryId()).isNull();
        }

        @Test
        @DisplayName("정밀 공격권 사용 → 기존 AttackToken precisionCount +1")
        void usePrecisionToken_existingToken_incrementsPrecisionCount() {
            UserItem userItem = makeUserItem(precisionTokenItem, 1);

            given(itemRepository.findById(11L)).willReturn(Optional.of(precisionTokenItem));
            given(userItemRepository.findByUser_IdAndItem_Id(1L, 11L))
                    .willReturn(Optional.of(userItem));
            given(attackTokenRepository.findByUserIdWithLock(1L))
                    .willReturn(Optional.of(attackToken));

            UseItemResponse response = itemService.useItem(1L, new UseItemRequest(11L, null));

            assertThat(response.itemType()).isEqualTo("ATTACK_PRECISION");
            assertThat(response.remainingCount()).isEqualTo(0); // 1 - 1
            assertThat(response.result().precisionCount()).isEqualTo(2); // 1 + 1
            assertThat(response.result().normalCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("AttackToken 행이 없으면 → 신규 생성 후 normalCount +1")
        void useNormalToken_noExistingToken_createsAndIncrements() {
            UserItem userItem = makeUserItem(normalTokenItem, 1);
            AttackToken newToken = AttackToken.builder().user(user).build();

            given(itemRepository.findById(10L)).willReturn(Optional.of(normalTokenItem));
            given(userItemRepository.findByUser_IdAndItem_Id(1L, 10L))
                    .willReturn(Optional.of(userItem));
            given(attackTokenRepository.findByUserIdWithLock(1L)).willReturn(Optional.empty());
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(attackTokenRepository.save(any(AttackToken.class))).willReturn(newToken);

            UseItemResponse response = itemService.useItem(1L, new UseItemRequest(10L, null));

            then(attackTokenRepository).should().save(any(AttackToken.class));
            assertThat(response.result().normalCount()).isEqualTo(1); // 0 + 1
            assertThat(response.result().precisionCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("보유 수량 0 → ITEM_OUT_OF_STOCK 예외")
        void useToken_quantityZero_throws() {
            UserItem userItem = makeUserItem(normalTokenItem, 0);

            given(itemRepository.findById(10L)).willReturn(Optional.of(normalTokenItem));
            given(userItemRepository.findByUser_IdAndItem_Id(1L, 10L))
                    .willReturn(Optional.of(userItem));

            assertThatThrownBy(() -> itemService.useItem(1L, new UseItemRequest(10L, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_OUT_OF_STOCK);
        }

        @Test
        @DisplayName("인벤토리에 없는 아이템 사용 → ITEM_NOT_FOUND 예외")
        void useToken_notInInventory_throws() {
            given(itemRepository.findById(10L)).willReturn(Optional.of(normalTokenItem));
            given(userItemRepository.findByUser_IdAndItem_Id(1L, 10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> itemService.useItem(1L, new UseItemRequest(10L, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
        }

        @Test
        @DisplayName("GP_PURCHASE 아이템 사용 시도 → ITEM_NOT_USABLE 예외")
        void useGpPurchaseItem_throws() {
            Item gpItem =
                    Item.builder()
                            .name("GP 100")
                            .itemType(ItemType.GP_PURCHASE)
                            .costAp(50)
                            .gpReward(100)
                            .build();
            ReflectionTestUtils.setField(gpItem, "id", 20L);

            given(itemRepository.findById(20L)).willReturn(Optional.of(gpItem));

            assertThatThrownBy(() -> itemService.useItem(1L, new UseItemRequest(20L, null)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ITEM_NOT_USABLE);
        }
    }
}
