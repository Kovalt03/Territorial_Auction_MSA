package com.territorial.combat.domain.building.entity;

import java.util.Set;

public enum BuildingCategory {
    // 백엔드 로직이 코드(name)로 매칭하는 고정 기능 건물
    FUNCTIONAL,
    // 관리자가 자유 생성. HP·방어력만 유효(방어력은 이름 무관하게 합산됨)
    DECORATIVE;

    // 이름(코드)에 하드코딩된 기능이 있는 건물 집합. 이 집합만 FUNCTIONAL로 취급한다.
    public static final Set<String> FUNCTIONAL_CODES =
            Set.of(
                    "CASTLE",
                    "WORKSHOP",
                    "FARMLAND",
                    "RESIDENCE",
                    "STORAGE",
                    "BARRACKS",
                    "RESEARCH_LAB");

    public static BuildingCategory of(String code) {
        return FUNCTIONAL_CODES.contains(code) ? FUNCTIONAL : DECORATIVE;
    }
}
