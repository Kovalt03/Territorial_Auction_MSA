package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminGrantItemRequest(
        @NotNull Long userId,
        @NotNull Long itemId,
        @Positive int quantity,
        @NotBlank String reason) {}
