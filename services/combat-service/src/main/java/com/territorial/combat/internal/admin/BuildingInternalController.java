package com.territorial.combat.internal.admin;

import com.territorial.combat.domain.building.dto.InitialCastleRequest;
import com.territorial.combat.domain.building.service.CastleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/buildings")
@RequiredArgsConstructor
public class BuildingInternalController {
    private final CastleService castleService;

    @PostMapping("/initial-castle")
    public ResponseEntity<Void> initialCastle(@RequestBody InitialCastleRequest request) {
        castleService.createInitialCastle(request.territoryId());
        return ResponseEntity.ok().build();
    }
}
