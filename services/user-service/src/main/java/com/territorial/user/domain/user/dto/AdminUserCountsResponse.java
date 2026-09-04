package com.territorial.user.domain.user.dto;

// 관리자 대시보드 유저 집계(admin-service 위임 소비).
public record AdminUserCountsResponse(long total, long active, long suspended) {}
