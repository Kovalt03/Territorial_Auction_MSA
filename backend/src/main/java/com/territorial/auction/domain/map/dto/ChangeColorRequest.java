package com.territorial.auction.domain.map.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeColorRequest(
        @NotBlank(message = "색상 코드는 필수입니다.")
                @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상 코드는 '#RRGGBB' 형식이어야 합니다.")
                String colorCode) {}
