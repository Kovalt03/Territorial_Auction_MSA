package com.territorial.admin.domain.admin.dto;

public record TotpSetupResponse(String secret, String otpAuthUri) {}
