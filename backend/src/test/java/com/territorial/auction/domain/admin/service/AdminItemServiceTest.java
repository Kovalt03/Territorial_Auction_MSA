package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.territorial.auction.domain.admin.client.ItemAdminClient;
import com.territorial.auction.domain.admin.client.ItemAdminClient.GrantResult;
import com.territorial.auction.domain.admin.client.ItemAdminClient.ItemView;
import com.territorial.auction.domain.admin.dto.AdminGrantItemRequest;
import com.territorial.auction.domain.admin.dto.AdminItemResponse;
import com.territorial.auction.domain.admin.dto.AdminUpdateItemRequest;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminItemServiceTest {

    @InjectMocks private AdminItemService adminItemService;

    @Mock private ItemAdminClient itemAdminClient;
    @Mock private UserRepository userRepository;
    @Mock private AdminAuditLogger adminAuditLogger;

    private ItemView itemView(long id) {
        return new ItemView(id, "무적권", "INVINCIBILITY", "설명", 1000, 0, 3, null, null);
    }

    @Test
    @DisplayName("아이템 정책 수정 → item-service 위임 + 감사 로그")
    void updateItem_success() {
        given(itemAdminClient.listItems()).willReturn(List.of(itemView(1L)));
        given(itemAdminClient.updatePolicy(1L, 2000, 50, 5))
                .willReturn(
                        new ItemView(1L, "무적권", "INVINCIBILITY", "설명", 2000, 50, 5, null, null));

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
        given(itemAdminClient.listItems()).willReturn(List.of(itemView(1L)));

        assertThatThrownBy(
                        () ->
                                adminItemService.updateItem(
                                        10L, 9L, new AdminUpdateItemRequest(1, 1, 1)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
        then(itemAdminClient).should(never()).updatePolicy(any(), any(), any(), any());
    }

    @Test
    @DisplayName("아이템 지급 → item-service 위임 + 감사 로그")
    void grantItem_success() {
        given(userRepository.existsById(1L)).willReturn(true);
        given(itemAdminClient.grantById(1L, 2L, 3)).willReturn(new GrantResult(2L, "무적권", 4));

        adminItemService.grantItem(10L, new AdminGrantItemRequest(1L, 2L, 3, "CS 보상"));

        then(itemAdminClient).should().grantById(1L, 2L, 3);
        then(adminAuditLogger)
                .should()
                .record(eq(10L), eq("ITEM_GRANT"), eq("USER"), eq(1L), any());
    }

    @Test
    @DisplayName("없는 유저에게 지급 → USER_NOT_FOUND, 위임 없음")
    void grantItem_userNotFound() {
        given(userRepository.existsById(9L)).willReturn(false);

        assertThatThrownBy(
                        () ->
                                adminItemService.grantItem(
                                        10L, new AdminGrantItemRequest(9L, 2L, 1, "x")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        then(itemAdminClient).should(never()).grantById(any(), any(), anyInt());
    }
}
