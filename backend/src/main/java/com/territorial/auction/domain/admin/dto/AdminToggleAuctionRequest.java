package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminToggleAuctionRequest(@NotNull Boolean enabled, String reason) {}
