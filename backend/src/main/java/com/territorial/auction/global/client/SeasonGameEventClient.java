package com.territorial.auction.global.client;

/**
 * season-service 게임 이벤트 위임 계약. 경매 낙찰·공성 승리를 season-service로 전달해 XP 적립·미션 진행을 맡긴다. 활성 시즌
 * 판단은 season-service가 자체 수행한다.
 */
public interface SeasonGameEventClient {

    void sendGameEvent(Long userId, String eventType);
}
