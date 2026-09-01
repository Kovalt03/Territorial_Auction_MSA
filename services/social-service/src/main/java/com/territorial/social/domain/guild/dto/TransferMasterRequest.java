package com.territorial.social.domain.guild.dto;

import jakarta.validation.constraints.NotNull;

public record TransferMasterRequest(@NotNull Long newMasterId) {}
