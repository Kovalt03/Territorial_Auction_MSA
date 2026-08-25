package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.user.entity.User;
import java.time.LocalDateTime;

public record AdminUserResponse(
        Long userId,
        String username,
        String nickname,
        String email,
        String status,
        String role,
        LocalDateTime createdAt) {

    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(),
                u.getUsername(),
                u.getNickname(),
                u.getEmail(),
                u.getStatus().name(),
                u.getRole().name(),
                u.getCreatedAt());
    }
}
