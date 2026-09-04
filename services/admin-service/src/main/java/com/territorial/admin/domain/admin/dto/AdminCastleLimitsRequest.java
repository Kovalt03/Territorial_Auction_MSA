package com.territorial.admin.domain.admin.dto;

import java.util.Map;

// {성 레벨: 최대 개수}. 값이 null이면 해당 성 레벨에서는 제한 없음.
public record AdminCastleLimitsRequest(Map<Integer, Integer> limits) {}
