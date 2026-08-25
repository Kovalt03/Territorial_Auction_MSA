package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminChangeGradeRequest(@NotBlank String grade, String reason) {}
