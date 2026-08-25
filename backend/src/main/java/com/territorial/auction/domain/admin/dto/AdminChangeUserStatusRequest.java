package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.user.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminChangeUserStatusRequest(@NotNull UserStatus status, @NotBlank String reason) {}
