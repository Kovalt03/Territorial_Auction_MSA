package com.territorial.season.domain.season.entity;

// 시즌패스 ITEM 보상이 지급할 아이템 종류. 값은 item-service의 Item.ItemType과 일치(계약은 name 문자열).
public enum RewardItemType {
    INVINCIBILITY,
    ATTACK_NORMAL,
    ATTACK_PRECISION,
    GP_PURCHASE
}
