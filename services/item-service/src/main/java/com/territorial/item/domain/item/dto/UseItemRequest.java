package com.territorial.item.domain.item.dto;

import jakarta.validation.constraints.NotNull;

public record UseItemRequest(@NotNull Long itemId, Long targetTerritoryId) {}
