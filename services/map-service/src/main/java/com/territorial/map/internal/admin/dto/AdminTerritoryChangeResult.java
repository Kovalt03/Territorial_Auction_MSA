package com.territorial.map.internal.admin.dto;

/**
 * 관리자 영토 변경 결과. 모놀리식 admin이 감사 로그(before/after)를 남길 수 있도록 변경 전 값을 함께 반환한다. beforeGrade/
 * beforeAuctionEnabled는 해당 변경에만 채워지고 나머지는 null이다.
 */
public record AdminTerritoryChangeResult(
        String beforeGrade, Boolean beforeAuctionEnabled, AdminTerritoryView territory) {}
