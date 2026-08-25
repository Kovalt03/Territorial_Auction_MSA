package com.territorial.auction.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.territorial.auction.domain.admin.dto.AdminAuditLogListResponse;
import com.territorial.auction.domain.admin.entity.AdminAuditLog;
import com.territorial.auction.domain.admin.repository.AdminAuditLogRepository;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogServiceTest {

    @InjectMocks private AdminAuditLogService adminAuditLogService;

    @Mock private AdminAuditLogRepository adminAuditLogRepository;
    @Mock private UserRepository userRepository;

    private AdminAuditLog log(long id, long adminId, String action) {
        AdminAuditLog l =
                AdminAuditLog.builder()
                        .adminUserId(adminId)
                        .action(action)
                        .targetType("USER")
                        .targetId(1L)
                        .detailJson("{}")
                        .build();
        ReflectionTestUtils.setField(l, "id", id);
        return l;
    }

    private User user(long id, String nickname) {
        User u =
                User.builder()
                        .username("u" + id)
                        .email("e" + id + "@x.com")
                        .passwordHash("h")
                        .nickname(nickname)
                        .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    @DisplayName("빈 필터는 null로 변환되어 조회 + 관리자 닉네임 매핑")
    void getLogs_blankFilterToNull_andResolveNickname() {
        PageRequest pageable = PageRequest.of(0, 30);
        given(adminAuditLogRepository.searchForAdmin(isNull(), isNull(), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(log(5L, 10L, "WALLET_ADJUST")), pageable, 1));
        given(userRepository.findAllById(any())).willReturn(List.of(user(10L, "운영자")));

        AdminAuditLogListResponse res = adminAuditLogService.getLogs("", "  ", pageable);

        assertThat(res.totalCount()).isEqualTo(1);
        assertThat(res.logs().get(0).action()).isEqualTo("WALLET_ADJUST");
        assertThat(res.logs().get(0).adminNickname()).isEqualTo("운영자");
    }

    @Test
    @DisplayName("action/targetType 필터는 그대로 전달")
    void getLogs_passesFilters() {
        PageRequest pageable = PageRequest.of(0, 30);
        given(adminAuditLogRepository.searchForAdmin(eq("WALLET_ADJUST"), eq("USER"), eq(pageable)))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        AdminAuditLogListResponse res =
                adminAuditLogService.getLogs("WALLET_ADJUST", "USER", pageable);

        assertThat(res.totalCount()).isZero();
    }
}
