package com.territorial.user.domain.user.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
