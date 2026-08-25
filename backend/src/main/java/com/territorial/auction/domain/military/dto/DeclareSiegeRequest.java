package com.territorial.auction.domain.military.dto;

import com.territorial.auction.domain.military.entity.SiegeStructureType;
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

    /** 공격에 커밋할 유닛 타입·레벨별 수량. 공성 병기+호위 등 혼합 편성. level이 null이면 1. */
    public record ForceEntry(
            @NotNull Long unitTypeId, @NotNull @Min(1) Integer quantity, @Min(1) Integer level) {}

    /** 대상 영토 인접 타일에 지을 공성 건물. 주둔지 최소 1개 필수. */
    public record StructureEntry(
            @NotNull SiegeStructureType type,
            @NotNull @Min(0) Integer coordX,
            @NotNull @Min(0) Integer coordY) {}
}
