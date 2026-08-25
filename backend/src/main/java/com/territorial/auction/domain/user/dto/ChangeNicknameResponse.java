package com.territorial.auction.domain.user.dto;

import java.time.LocalDateTime;

public record ChangeNicknameResponse(Long userId, String nickname, LocalDateTime updatedAt) {}
