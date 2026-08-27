package com.territorial.auction.domain.map;

import java.util.Map;

/**
 * 영토 점유·가치 정책 상수. auction 도메인 분리 전 AuctionPolicy에 있던 값 중, 영토(점유 기간·보호 기간·등급 기준가)에 해당하는 것을 map 도메인이
 * 소유한다. 경매 생성·입찰 정책은 auction-service가 자기 복사본을 소유한다.
 */
public final class TerritoryPolicy {

    /** 낙찰 후 영토 점유 기간 (일) — 만료 시 자동 재경매 */
    public static final int OCCUPATION_DURATION_DAYS = 3;

    /** 획득 후 공성 보호 기간 (시간) — 이 기간 동안 공성전 불가. 점유 기간보다 짧다. */
    public static final int PROTECTION_DURATION_HOURS = 12;

    /** 등급별 기준가 — 토지세 강제 경매 전환 시 회수 가치 추정에 사용. (auction-service가 경매 시작가로도 사용) */
    public static final Map<String, Integer> GRADE_BASE_PRICES =
            Map.of(
                    "S", 10000,
                    "A", 5000,
                    "B", 2000,
                    "C", 1000,
                    "D", 500);

    /** GRADE_BASE_PRICES에 없는 등급의 기본 기준가 */
    public static final int DEFAULT_BASE_PRICE = 1000;

    private TerritoryPolicy() {}
}
