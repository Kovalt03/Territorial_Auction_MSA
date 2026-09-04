package com.territorial.map.internal.admin.dto;

import java.util.List;
import java.util.Map;

/** 관리자 영토/대륙 변경 요청 바디 모음(내부 계약). 감사 로그의 reason은 모놀리식 admin이 소유하므로 여기 계약엔 없다. */
public final class AdminMapRequests {

    public record GradeDistribution(Map<String, Integer> distribution) {}

    public record Toggle(boolean enabled) {}

    public record Grade(String grade) {}

    public record BulkGrade(String grade, List<Long> territoryIds) {}

    public record BulkToggle(boolean enabled, List<Long> territoryIds) {}

    public record BulkIds(List<Long> territoryIds) {}

    private AdminMapRequests() {}
}
