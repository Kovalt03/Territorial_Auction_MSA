package com.territorial.auction.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AdminBulkGradeRequest(
        @NotEmpty List<Long> territoryIds, @NotBlank String grade, String reason) {}
