package com.territorial.admin.domain.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record AdminGradeDistributionRequest(
        @NotNull Map<String, Integer> distribution, String reason) {}
