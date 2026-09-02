package com.territorial.combat.domain.military.dto;

import com.territorial.combat.domain.military.entity.SiegeStructureType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DeclareSiegeRequest(
        @NotNull Long targetTerritoryId,
        Long targetBuildingId,
        @NotNull @Min(1) @Max(3) Integer attackZone,
        @NotEmpty @Valid List<ForceEntry> forces,
        @NotEmpty @Valid List<StructureEntry> structures) {

    public record ForceEntry(
            @NotNull Long unitTypeId, @NotNull @Min(1) Integer quantity, @Min(1) Integer level) {}

    public record StructureEntry(
            @NotNull SiegeStructureType type,
            @NotNull @Min(0) Integer coordX,
            @NotNull @Min(0) Integer coordY) {}
}
