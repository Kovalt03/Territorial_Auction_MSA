package com.territorial.map.internal.admin.dto;

import com.territorial.map.internal.admin.dto.AdminContinentCompositionResponse.ContinentComposition;
import java.util.Map;

/** 대륙 등급 재분배 결과. before는 변경 전 등급별 개수(감사용), composition은 변경 후 구성이다. */
public record AdminGradeDistributionResult(
        Map<String, Long> before, ContinentComposition composition) {}
