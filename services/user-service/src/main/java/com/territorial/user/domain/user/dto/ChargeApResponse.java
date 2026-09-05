package com.territorial.user.domain.user.dto;

import java.time.LocalDateTime;

public record ChargeApResponse(int availableAP, int chargedAmount, LocalDateTime chargedAt) {}
