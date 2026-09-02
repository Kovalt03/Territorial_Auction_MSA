package com.territorial.combat.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    TERRITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "영토를 찾을 수 없습니다."),
    NOT_TERRITORY_OWNER(HttpStatus.FORBIDDEN, "해당 영토의 점유자가 아닙니다."),
    BUILDING_NOT_FOUND(HttpStatus.NOT_FOUND, "건물을 찾을 수 없습니다."),
    BUILDING_NOT_UNDER_CONSTRUCTION(HttpStatus.BAD_REQUEST, "건설/업그레이드 중인 건물이 아닙니다."),
    BUILDING_BUSY(HttpStatus.BAD_REQUEST, "건설·업그레이드·수리 중인 건물은 다시 작업할 수 없습니다."),
    BUILDING_ALREADY_FULL_HP(HttpStatus.BAD_REQUEST, "이미 HP가 가득 찬 건물입니다."),
    BUILDING_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "건물 타입을 찾을 수 없습니다."),
    BUILDING_NOT_PURCHASABLE(HttpStatus.BAD_REQUEST, "상점에서 구매할 수 없는 건물입니다."),
    BUILDING_MAX_LEVEL(HttpStatus.BAD_REQUEST, "이미 최대 레벨에 도달한 건물입니다."),
    BUILDING_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "현재 성 레벨에서 더 지을 수 없는 건물입니다."),
    BUILDING_UNDER_CONSTRUCTION(HttpStatus.CONFLICT, "아직 건설 중인 건물입니다."),
    BUILDER_SLOT_FULL(HttpStatus.CONFLICT, "건축 장인이 모두 작업 중입니다."),
    ISLAND_NOT_FOUND(HttpStatus.NOT_FOUND, "섬을 찾을 수 없습니다."),
    PRODUCTION_BOOST_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 생산 부스터가 적용 중입니다."),
    CASTLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "성은 하나만 지을 수 있습니다."),
    CASTLE_CANNOT_BE_STORED(HttpStatus.BAD_REQUEST, "Castle은 보관할 수 없습니다."),
    CASTLE_CANNOT_BE_MOVED(HttpStatus.BAD_REQUEST, "Castle은 이동할 수 없습니다."),
    INVALID_POSITION(HttpStatus.BAD_REQUEST, "배치 불가능한 위치입니다."),
    ZONE_RESTRICTION_VIOLATED(HttpStatus.BAD_REQUEST, "Zone 제약 위반입니다."),
    INSUFFICIENT_AP(HttpStatus.CONFLICT, "AP 잔액이 부족합니다."),
    STORAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "저장 공간이 없습니다."),
    INSUFFICIENT_GP(HttpStatus.BAD_REQUEST, "GP 잔액이 부족합니다."),
    STORAGE_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "저장 공간이 가득 찼습니다."),
    VAULT_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "금고 용량을 초과합니다."),
    TRANSFER_COOLDOWN_ACTIVE(HttpStatus.TOO_MANY_REQUESTS, "이전 쿨다운 중입니다. 잠시 후 다시 시도하세요.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
