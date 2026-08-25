package com.territorial.auction.domain.auction;

import java.util.Map;

public final class AuctionPolicy {

    // ── 입찰 검증 ──────────────────────────────────────────────────────────────

    /** 입찰 최소 인상률 (현재가 × 이 값 이상) */
    public static final double BID_MIN_PERCENT_RATE = 1.05;

    /** 입찰 최소 고정 인상액 (현재가 + 이 값 이상) */
    public static final int BID_MIN_FLAT_INCREMENT = 10;

    // ── Anti-Sniping ──────────────────────────────────────────────────────────

    /** Anti-sniping 감지 구간: 종료까지 이 초 이내면 연장 트리거 */
    public static final int ANTI_SNIPE_WINDOW_SECONDS = 60;

    /** Anti-sniping 연장 시간 (엔티티에서 maxExtendUntil 상한으로 cap) */
    public static final int ANTI_SNIPE_EXTEND_SECONDS = 30;

    // ── 경매 생성 ──────────────────────────────────────────────────────────────

    /** 경매 진행 기간 (시간) */
    public static final int AUCTION_DURATION_HOURS = 24;

    /** maxExtendUntil = endAt + 이 값 (분) */
    public static final int MAX_EXTEND_UNTIL_MINUTES = 30;

    /** 등급별 경매 시작가 */
    public static final Map<String, Integer> GRADE_START_PRICES =
            Map.of(
                    "S", 10000,
                    "A", 5000,
                    "B", 2000,
                    "C", 1000,
                    "D", 500);

    /** GRADE_START_PRICES에 없는 등급의 기본 시작가 */
    public static final int DEFAULT_START_PRICE = 1000;

    // ── 점유 / 재경매 ─────────────────────────────────────────────────────────

    /** 낙찰 후 영토 점유 기간 (일) — 만료 시 자동 재경매 */
    public static final int OCCUPATION_DURATION_DAYS = 3;

    /** 획득 후 공성 보호 기간 (시간) — 이 기간 동안 공성전 불가. 점유 기간보다 짧다. */
    public static final int PROTECTION_DURATION_HOURS = 12;

    /** 무낙찰 경매 종료 후 재경매 생성 대기 시간 (시간) */
    public static final int IDLE_REAUCTION_DELAY_HOURS = 1;

    private AuctionPolicy() {}
}
