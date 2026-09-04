package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminToggleAuctionRequest(@NotNull Boolean enabled, String reason) {}
