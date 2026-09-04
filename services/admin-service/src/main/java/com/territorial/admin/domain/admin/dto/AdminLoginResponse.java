package com.territorial.admin.domain.admin.dto;

public record AdminLoginResponse(String accessToken, boolean totpEnrolled) {}
