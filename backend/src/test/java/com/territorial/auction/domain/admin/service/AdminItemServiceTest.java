package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.dto.AdminGrantItemRequest;
import com.territorial.auction.domain.admin.dto.AdminItemResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateItemRequest;
import com.territorial.auction.domain.item.entity.Item;
import com.territorial.auction.domain.item.entity.UserItem;
import com.territorial.auction.domain.item.repository.ItemRepository;
import com.territorial.auction.domain.item.repository.UserItemRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
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
class AdminItemServiceTest {

    @InjectMocks private AdminItemService adminItemService;

    @Mock private ItemRepository itemRepository;
    @Mock private UserItemRepository userItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private Item item(long id) {
        Item i =
                Item.builder()
                        .name("무적권")
                        .itemType(Item.ItemType.INVINCIBILITY)
                        .costAp(1000)
                        .costGp(0)
                        .dailyLimit(3)
                        .build();
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    private User user(long id) {
        User u = User.builder().username("u").email("u@x").passwordHash("h").nickname("유저").build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("아이템 정책 수정 → 가격·한도 반영 + 감사 로그")
    void updateItem_success() {
        Item i = item(1L);
        given(itemRepository.findById(1L)).willReturn(Optional.of(i));

        AdminItemResponse res =
                adminItemService.updateItem(10L, 1L, new AdminUpdateItemRequest(2000, 50, 5));

        assertThat(res.costAp()).isEqualTo(2000);
        assertThat(res.costGp()).isEqualTo(50);
        assertThat(res.dailyLimit()).isEqualTo(5);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("ITEM_POLICY_UPDATE"), eq("ITEM"), eq(1L), any());
    }

    @Test
    @DisplayName("없는 아이템 수정 → ITEM_NOT_FOUND")
    void updateItem_notFound() {
        given(itemRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                adminItemService.updateItem(
                                        10L, 9L, new AdminUpdateItemRequest(1, 1, 1)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("아이템 지급 - 기존 보유분 있으면 수량 증가")
    void grantItem_existing() {
        User u = user(1L);
        Item i = item(2L);
        UserItem existing = UserItem.builder().user(u).item(i).quantity(1).createdAt(null).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(u));
        given(itemRepository.findById(2L)).willReturn(Optional.of(i));
        given(userItemRepository.findByUser_IdAndItem_Id(1L, 2L)).willReturn(Optional.of(existing));

        adminItemService.grantItem(10L, new AdminGrantItemRequest(1L, 2L, 3, "CS 보상"));

        assertThat(existing.getQuantity()).isEqualTo(4);
        then(userItemRepository).should(never()).save(any());
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("ITEM_GRANT"), eq("USER"), eq(1L), any());
    }

    @Test
    @DisplayName("아이템 지급 - 미보유면 신규 생성")
    void grantItem_new() {
        User u = user(1L);
        Item i = item(2L);
        given(userRepository.findById(1L)).willReturn(Optional.of(u));
        given(itemRepository.findById(2L)).willReturn(Optional.of(i));
        given(userItemRepository.findByUser_IdAndItem_Id(1L, 2L)).willReturn(Optional.empty());

        adminItemService.grantItem(10L, new AdminGrantItemRequest(1L, 2L, 2, "지급"));

        then(userItemRepository).should().save(any(UserItem.class));
    }

    @Test
    @DisplayName("없는 유저에게 지급 → USER_NOT_FOUND")
    void grantItem_userNotFound() {
        given(userRepository.findById(9L)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                adminItemService.grantItem(
                                        10L, new AdminGrantItemRequest(9L, 2L, 1, "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
