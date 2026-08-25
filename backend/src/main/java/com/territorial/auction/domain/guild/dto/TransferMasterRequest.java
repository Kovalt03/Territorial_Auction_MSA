package com.territorial.auction.domain.guild.dto;

import jakarta.validation.constraints.NotNull;

public record TransferMasterRequest(@NotNull Long newMasterId) {}
