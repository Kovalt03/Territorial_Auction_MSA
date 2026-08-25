package com.territorial.auction.domain.admin.dto;

public record AdminLoginResponse(String accessToken, boolean totpEnrolled) {}
