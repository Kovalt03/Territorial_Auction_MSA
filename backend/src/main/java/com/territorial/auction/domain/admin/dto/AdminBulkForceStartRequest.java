package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AdminBulkForceStartRequest(@NotEmpty List<Long> territoryIds, String reason) {}
