package com.territorial.user.domain.user.dto;

import com.territorial.user.domain.user.entity.User;
import java.time.LocalDateTime;

// 관리 콘솔(admin-service) 위임 조회용 유저 표시 뷰. 신원은 user-service 소유.
public record AdminUserView(
        Long userId,
        String username,
        String nickname,
        String email,
        String status,
        String role,
        LocalDateTime createdAt) {

    public static AdminUserView from(User user) {
        return new AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getStatus(),
                user.getRole(),
                user.getCreatedAt());
    }
}
