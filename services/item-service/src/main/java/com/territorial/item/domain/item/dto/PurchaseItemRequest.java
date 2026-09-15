package com.territorial.item.domain.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// idempotencyKey: 구매 재시도(응답 유실·인터셉터 재시도) 시 AP 이중 차감을 막는 멱등 키.
// 클라가 구매 시도당 고정 값을 보낸다. 없으면 서버가 랜덤 생성(하위 호환, 요청 단위 멱등 없음).
public record PurchaseItemRequest(
        @NotNull Long itemId, @Min(1) int quantity, String idempotencyKey) {}
