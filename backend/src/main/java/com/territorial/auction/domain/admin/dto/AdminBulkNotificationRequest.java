package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminBulkNotificationRequest(
        @NotEmpty List<Long> userIds, @NotBlank @Size(max = 500) String message) {}
