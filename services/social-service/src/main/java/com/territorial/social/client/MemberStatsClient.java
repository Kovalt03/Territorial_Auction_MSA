package com.territorial.social.client;

import com.territorial.social.client.MapTerritoryClient.OwnerCount;
import com.territorial.social.client.SeasonTrophyClient.UserScore;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 멤버 표시 통계(영토 수·트로피)를 map·season에서 직접 배치 조회·합성한다. 기존에는 모놀리식 /internal/members/stats를 경유했으나
 * user-BFF/member-stats 흡수로 소유 서비스를 직접 호출한다.
 */
@Component
@RequiredArgsConstructor
public class MemberStatsClient {

    private final MapTerritoryClient mapTerritoryClient;
    private final SeasonTrophyClient seasonTrophyClient;

    public Map<Long, MemberStat> fetch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> territory =
                mapTerritoryClient.getOwnerCounts(userIds).stream()
                        .collect(Collectors.toMap(OwnerCount::ownerId, OwnerCount::count));
        Map<Long, Long> trophy =
                seasonTrophyClient.sumScores(userIds).stream()
                        .collect(Collectors.toMap(UserScore::userId, UserScore::totalScore));
        return userIds.stream()
                .distinct()
                .collect(
                        Collectors.toMap(
                                id -> id,
                                id ->
                                        new MemberStat(
                                                id,
                                                territory.getOrDefault(id, 0L),
                                                trophy.getOrDefault(id, 0L))));
    }

    public record MemberStat(Long userId, long territoryCount, long trophyScore) {}
}
