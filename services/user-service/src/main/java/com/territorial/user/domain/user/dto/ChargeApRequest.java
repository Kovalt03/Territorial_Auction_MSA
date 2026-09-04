package com.territorial.user.domain.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChargeApRequest(
        @NotNull @Min(1) Integer amount, @NotBlank String paymentKey, @NotBlank String orderId) {}
