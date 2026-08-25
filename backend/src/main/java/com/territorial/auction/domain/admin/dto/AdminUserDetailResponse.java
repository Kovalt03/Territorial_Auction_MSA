package com.territorial.auction.domain.admin.dto;

import java.time.LocalDateTime;

public record AdminUserDetailResponse(
        Long userId,
        String username,
        String nickname,
        String email,
        String status,
        String role,
        LocalDateTime createdAt,
        int availableAp,
        int lockedAp,
        int availableGp,
        int availableFood,
        long territoryCount) {}
