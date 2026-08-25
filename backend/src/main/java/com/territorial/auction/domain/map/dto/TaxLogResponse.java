package com.territorial.auction.domain.map.dto;

import com.territorial.auction.domain.map.entity.LandTaxLog.TaxStatus;
import java.time.LocalDateTime;
import java.util.List;

public record TaxLogResponse(long totalCount, List<TaxLogItem> logs) {

    public record TaxLogItem(
            long logId,
            LocalDateTime chargedAt,
            int territoryCount,
            int gpCharged,
            TaxStatus status) {}
}
