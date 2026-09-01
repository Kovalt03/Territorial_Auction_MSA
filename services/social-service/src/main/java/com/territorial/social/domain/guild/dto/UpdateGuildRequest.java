package com.territorial.social.domain.guild.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateGuildRequest(
        @Size(max = 200) String description,
        @Size(max = 255) String emblem,
        @Pattern(regexp = "^(OPEN|CLOSED)$") String recruitingStatus) {}
