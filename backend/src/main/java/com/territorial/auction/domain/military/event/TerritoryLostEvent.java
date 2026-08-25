package com.territorial.auction.domain.military.event;

/**
 * 소유자가 영토를 상실했을 때 발행된다(토지세 미납 강제 경매·점유 만료). 저장 GP 일부 환수·식량 소멸·방어 유닛 섬 퇴각을 각 도메인 리스너가 처리한다.
 *
 * <p>성 파괴 즉시 인계는 공격자에게 곧바로 넘어가므로 이 이벤트를 쓰지 않고 {@code SiegeService}가 직접 처리한다.
 */
public record TerritoryLostEvent(Long territoryId, Long formerOwnerId) {}
