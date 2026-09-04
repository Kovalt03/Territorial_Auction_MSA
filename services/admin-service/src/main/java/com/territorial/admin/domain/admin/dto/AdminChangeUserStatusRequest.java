package com.territorial.admin.domain.admin.dto;

import com.territorial.admin.client.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminChangeUserStatusRequest(@NotNull UserStatus status, @NotBlank String reason) {}
