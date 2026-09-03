package com.territorial.auction.global.client;

import java.util.List;
import java.util.Optional;

/**
 * season-service 트로피 조회 계약. ranking(랭킹 산출)·user(프로필)·social 멤버통계가 위임 조회한다. nickname은 조회측이
 * users 프로젝션으로 붙이므로 여기서는 userId·score·league만 제공한다.
 */
public interface SeasonTrophyClient {

    Optional<Trophy> getTrophy(Long userId);

    List<Trophy> getRanking(int page, int size);

    long countAbove(int score);

    List<Trophy> getBand(int lower, int upper, int page, int size);

    long countBand(int score, int upper);

    List<UserScore> sumScores(List<Long> userIds);

    record Trophy(Long userId, int score, String league) {}

    record UserScore(Long userId, long totalScore) {}
}
