package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSendNotificationRequest(@NotBlank @Size(max = 500) String message) {}
