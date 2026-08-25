package com.territorial.auction.domain.admin.dto;

import java.time.LocalDateTime;

// startedAt 미지정 시 즉시 시작(now). endedAt은 선택(미지정 시 무기한 → 관리자가 나중에 종료).
public record AdminCreateSeasonRequest(LocalDateTime startedAt, LocalDateTime endedAt) {}
