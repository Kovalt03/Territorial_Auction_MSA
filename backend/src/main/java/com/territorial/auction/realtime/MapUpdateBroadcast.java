package com.territorial.auction.realtime;

/**
 * 맵 갱신 STOMP 페이로드(/sub/map/update). map 도메인이 map-service로 추출돼 삭제됐으므로, 실시간 허브(모놀리식)가 자체 record로
 * 보유한다. 필드명은 map-service·auction-service 발행 JSON과 일치해야 한다(필드 기반).
 */
public record MapUpdateBroadcast(
        Long territoryId,
        int coordX,
        int coordY,
        Long ownerId, // null if IDLE
        String ownerNickname, // null if IDLE
        String status // "OCCUPIED" or "IDLE"
        ) {}
