package com.territorial.auction.global.internal;

import com.territorial.auction.global.client.MapTerritoryClient;
import com.territorial.auction.global.client.MapTerritoryClient.OwnerCount;
import com.territorial.auction.global.client.SeasonTrophyClient;
import com.territorial.auction.global.client.SeasonTrophyClient.UserScore;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 멤버 표시 통계(영토 수·트로피)를 userId 배치로 반환. social-service 길드 화면 등이 호출한다. map·season은 모놀리식 소유라 여기서 집계해
 * 계약으로 노출한다.
 */
@RestController
@RequestMapping("/internal/members")
@RequiredArgsConstructor
public class MemberStatsInternalController {

    private final MapTerritoryClient mapTerritoryClient;
    private final SeasonTrophyClient seasonTrophyClient;

    @PostMapping("/stats")
    public ResponseEntity<List<MemberStat>> stats(@RequestBody MemberStatsRequest request) {
        List<Long> ids = request.userIds();
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Map<Long, Long> territory =
                mapTerritoryClient.getOwnerCounts(ids).stream()
                        .collect(Collectors.toMap(OwnerCount::ownerId, OwnerCount::count));
        Map<Long, Long> trophy =
                seasonTrophyClient.sumScores(ids).stream()
                        .collect(Collectors.toMap(UserScore::userId, UserScore::totalScore));
        List<MemberStat> stats =
                ids.stream()
                        .map(
                                id ->
                                        new MemberStat(
                                                id,
                                                territory.getOrDefault(id, 0L),
                                                trophy.getOrDefault(id, 0L)))
                        .toList();
        return ResponseEntity.ok(stats);
    }

    public record MemberStatsRequest(List<Long> userIds) {}

    public record MemberStat(Long userId, long territoryCount, long trophyScore) {}
}
