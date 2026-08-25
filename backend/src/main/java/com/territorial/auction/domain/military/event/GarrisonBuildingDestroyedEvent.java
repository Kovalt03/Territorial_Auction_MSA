package com.territorial.auction.domain.military.event;

/**
 * 공성 중 주둔 가능한 건물(성 제외)이 파괴됐을 때 발행된다. 그 건물에 주둔한 방어 유닛을 홈 아일랜드로 퇴각시킨다(섬 슬롯 초과분 소멸).
 *
 * <p>성 파괴는 영토 인계로 방어 유닛이 전멸하므로 이 이벤트를 쓰지 않는다.
 */
public record GarrisonBuildingDestroyedEvent(Long defenderId, Long buildingId) {}
