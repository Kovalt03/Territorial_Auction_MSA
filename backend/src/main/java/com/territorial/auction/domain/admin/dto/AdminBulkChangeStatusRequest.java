package com.territorial.auction.domain.admin.dto;

import com.territorial.auction.domain.user.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AdminBulkChangeStatusRequest(
        @NotEmpty List<Long> userIds, @NotNull UserStatus status, @NotBlank String reason) {}
