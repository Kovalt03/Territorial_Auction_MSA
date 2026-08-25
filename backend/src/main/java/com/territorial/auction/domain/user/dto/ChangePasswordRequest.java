package com.territorial.auction.domain.user.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
