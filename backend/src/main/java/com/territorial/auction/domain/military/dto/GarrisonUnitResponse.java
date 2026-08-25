package com.territorial.auction.domain.military.dto;

/** 특정 영토에 배치(주둔)된 소유자 유닛의 타입별 합계. 회수 UI가 이 목록으로 회수 대상을 표시한다. */
public record GarrisonUnitResponse(
        Long unitTypeId,
        String name,
        String displayName,
        String icon,
        String colorHex,
        Integer deployedCount) {}
