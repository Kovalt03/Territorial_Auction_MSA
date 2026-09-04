package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminSendNotificationRequest(@NotBlank @Size(max = 500) String message) {}
