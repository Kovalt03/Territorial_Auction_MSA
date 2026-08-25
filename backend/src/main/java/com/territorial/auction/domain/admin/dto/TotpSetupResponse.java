package com.territorial.auction.domain.admin.dto;

public record TotpSetupResponse(String secret, String otpAuthUri) {}
