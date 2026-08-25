package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// active=true인데 message가 비면 배너가 노출되지 않는다(프론트에서 방지). 최대 200자.
public record AdminUpdateAnnouncementRequest(
        @NotNull Boolean active, @Size(max = 200) String message) {}
