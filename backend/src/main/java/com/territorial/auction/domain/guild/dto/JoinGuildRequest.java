package com.territorial.auction.domain.guild.dto;

import jakarta.validation.constraints.Size;

public record JoinGuildRequest(@Size(max = 200) String message) {}
