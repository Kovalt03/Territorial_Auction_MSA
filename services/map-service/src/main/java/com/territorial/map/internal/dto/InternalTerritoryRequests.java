package com.territorial.map.internal.dto;

import java.util.List;

/** 비-admin 내부 영토 계약 요청 바디. */
public final class InternalTerritoryRequests {

    public record OwnerIds(List<Long> userIds) {}

    public record Takeover(Long newOwnerId, Long formerOwnerId) {}

    private InternalTerritoryRequests() {}
}
