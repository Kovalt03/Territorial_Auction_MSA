package com.territorial.admin.domain.admin.dto;

import com.territorial.admin.client.UserAdminClient;
import java.time.LocalDateTime;

public record AdminUserResponse(
        Long userId,
        String username,
        String nickname,
        String email,
        String status,
        String role,
        LocalDateTime createdAt) {

    public static AdminUserResponse from(UserAdminClient.UserView u) {
        return new AdminUserResponse(
                u.userId(),
                u.username(),
                u.nickname(),
                u.email(),
                u.status(),
                u.role(),
                u.createdAt());
    }
}
