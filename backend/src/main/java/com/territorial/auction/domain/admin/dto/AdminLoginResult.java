package com.territorial.auction.domain.admin.dto;

// Service → Controller 내부 전달용 (클라이언트 미노출)
public record AdminLoginResult(String accessToken, String refreshToken, boolean totpEnrolled) {}
