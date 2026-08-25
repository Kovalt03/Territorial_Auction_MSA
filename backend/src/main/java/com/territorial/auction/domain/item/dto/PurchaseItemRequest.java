package com.territorial.auction.domain.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PurchaseItemRequest(@NotNull Long itemId, @Min(1) int quantity) {}
