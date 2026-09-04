package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminChangeGradeRequest(@NotBlank String grade, String reason) {}
