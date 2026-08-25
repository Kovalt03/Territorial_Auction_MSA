package com.territorial.auction.domain.military.entity;

/** 공성 선언 시 대상 영토 인접 타일에 짓는 임시 구조물. 해당 공성에 종속돼 판정 후 소멸한다. */
public enum SiegeStructureType {
    /** 주둔지 — 공격 병력 상한을 제공한다(수용량 합 = 커밋 가능 병력). */
    STAGING,
    /** 공성 타워 — 공격력 버프. */
    TOWER,
    /** 보급소 — 실패 시 다음 공격 쿨다운을 완화. */
    SUPPLY
}
