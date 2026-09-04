package com.territorial.ranking.internal;

import com.territorial.ranking.domain.ranking.service.RankingService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** map(모놀리식)이 영토 점유 만료 시 위임 호출한다. 시작 기록은 ranking이 auction.settled로 자체 처리한다. */
@RestController
@RequestMapping("/internal/holds")
@RequiredArgsConstructor
public class RankingInternalController {

    private final RankingService rankingService;

    @PostMapping("/close")
    public ResponseEntity<Void> closeHold(@RequestBody CloseHoldRequest request) {
        rankingService.closeTerritoryHold(
                request.userId(), request.seasonId(), request.territoryId(), request.heldUntil());
        return ResponseEntity.ok().build();
    }

    public record CloseHoldRequest(
            Long userId, Long seasonId, Long territoryId, LocalDateTime heldUntil) {}
}
