package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

// apDelta/gpDelta는 증감 모두 허용(null = 0). 사유는 필수.
public record AdminAdjustWalletRequest(Integer apDelta, Integer gpDelta, @NotBlank String reason) {}
