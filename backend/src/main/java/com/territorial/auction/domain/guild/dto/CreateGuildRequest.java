package com.territorial.auction.domain.guild.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGuildRequest(
        @NotBlank @Size(min = 2, max = 20) String name,
        @Size(max = 200) String description,
        String emblem) {}
