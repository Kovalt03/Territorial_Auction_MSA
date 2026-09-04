package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.PositiveOrZero;

// 가격(AP/GP)·일일 한도 수정. null 허용(해당 수단 판매 안 함/무제한).
public record AdminUpdateItemRequest(
        @PositiveOrZero Integer costAp,
        @PositiveOrZero Integer costGp,
        @PositiveOrZero Integer dailyLimit) {}
