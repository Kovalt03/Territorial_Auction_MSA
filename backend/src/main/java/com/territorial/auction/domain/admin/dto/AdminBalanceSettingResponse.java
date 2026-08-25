package com.territorial.auction.domain.admin.dto;

/** 밸런스 상수 1개의 현재 상태. {@code value}는 관리자가 덮어썼으면 그 값, 아니면 {@code defaultValue}. */
public record AdminBalanceSettingResponse(String key, int value, int defaultValue, String label) {}
