package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AdminBulkTerritoryAuctionRequest(
        @NotEmpty List<Long> territoryIds, @NotNull Boolean enabled, String reason) {}
