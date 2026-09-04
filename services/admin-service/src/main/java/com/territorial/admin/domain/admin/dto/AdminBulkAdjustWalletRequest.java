package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AdminBulkAdjustWalletRequest(
        @NotEmpty List<Long> userIds, Integer apDelta, Integer gpDelta, @NotBlank String reason) {}
