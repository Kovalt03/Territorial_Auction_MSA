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
    DUPLICATE_BUILDING_TYPE_NAME(HttpStatus.CONFLICT, "이미 존재하는 건물 이름입니다."),
    FUNCTIONAL_BUILDING_NOT_CREATABLE(HttpStatus.BAD_REQUEST, "기능 건물 타입은 추가할 수 없습니다."),
    INVALID_BUILDING_LEVEL(HttpStatus.BAD_REQUEST, "유효하지 않은 건물 레벨입니다."),
    BUILDING_TYPE_IN_USE(HttpStatus.CONFLICT, "이미 배치된 건물이 있어 삭제할 수 없습니다."),
    CASTLE_LIMIT_NOT_CONFIGURABLE(HttpStatus.BAD_REQUEST, "성은 개수 제한을 설정할 수 없습니다."),
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
    WALLET_COMMAND_CONFLICT(HttpStatus.CONFLICT, "동일한 지갑 명령 키에 다른 요청이 전달되었습니다."),
    STORAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "저장 공간이 없습니다."),
    INSUFFICIENT_GP(HttpStatus.BAD_REQUEST, "GP 잔액이 부족합니다."),
    STORAGE_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "저장 공간이 가득 찼습니다."),
    VAULT_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "금고 용량을 초과합니다."),
    TRANSFER_COOLDOWN_ACTIVE(HttpStatus.TOO_MANY_REQUESTS, "이전 쿨다운 중입니다. 잠시 후 다시 시도하세요."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    NO_BARRACKS(HttpStatus.BAD_REQUEST, "병영이 없습니다."),
    BARRACKS_LEVEL_INSUFFICIENT(HttpStatus.BAD_REQUEST, "병영 레벨이 부족합니다."),
    INSUFFICIENT_UNITS(HttpStatus.BAD_REQUEST, "유닛 수량이 부족합니다."),
    UNIT_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "유닛 수용 한도를 초과했습니다."),
    FOOD_INSUFFICIENT(HttpStatus.BAD_REQUEST, "식량이 부족합니다."),
    UNIT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "유닛 타입을 찾을 수 없습니다."),
    INVALID_UNIT_LEVEL(HttpStatus.BAD_REQUEST, "유효하지 않은 유닛 레벨입니다."),
    INCOMPLETE_UNIT_LEVEL_SPEC(HttpStatus.BAD_REQUEST, "훈련 스펙은 모든 값을 함께 입력해야 합니다."),
    RESEARCH_LAB_LEVEL_INSUFFICIENT(HttpStatus.BAD_REQUEST, "연구소 레벨이 부족합니다."),
    RESEARCH_IN_PROGRESS(HttpStatus.CONFLICT, "이미 연구가 진행 중입니다."),
    RESEARCH_MAX_REACHED(HttpStatus.BAD_REQUEST, "더 연구할 레벨이 없습니다."),
    RESEARCH_SPEC_NOT_FOUND(HttpStatus.BAD_REQUEST, "해당 레벨의 유닛 스펙이 없습니다."),
    UNIT_LEVEL_NOT_RESEARCHED(HttpStatus.BAD_REQUEST, "아직 연구하지 않은 유닛 레벨입니다."),
    TERRITORY_NOT_OCCUPIED(HttpStatus.BAD_REQUEST, "점유 중인 영토가 아닙니다."),
    NO_ATTACK_TOKEN(HttpStatus.BAD_REQUEST, "공격권이 없습니다."),
    ZONE_NOT_CLEARED(HttpStatus.BAD_REQUEST, "이전 Zone을 클리어해야 합니다."),
    TERRITORY_PROTECTED(HttpStatus.BAD_REQUEST, "보호 기간 중인 영토입니다."),
    ATTACK_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "공격 쿨다운 중입니다."),
    SIEGE_NOT_FOUND(HttpStatus.NOT_FOUND, "공성전을 찾을 수 없습니다."),
    SIEGE_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 공성전의 관계자가 아닙니다."),
    SIEGE_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "공성전 결과가 아직 처리되지 않았습니다."),
    CANNOT_ATTACK_OWN_TERRITORY(HttpStatus.FORBIDDEN, "자신의 영토는 공격할 수 없습니다."),
    SIEGE_STAGING_REQUIRED(HttpStatus.BAD_REQUEST, "공성에는 주둔지가 최소 1개 필요합니다."),
    SIEGE_STRUCTURE_PLACEMENT_INVALID(HttpStatus.BAD_REQUEST, "공성 건물은 대상 영토 인접 타일에만 배치할 수 있습니다."),
    SIEGE_STRUCTURE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "공성 건물 개수 상한을 초과했습니다."),
    SIEGE_FORCE_EXCEEDS_CAPACITY(HttpStatus.BAD_REQUEST, "공격 병력이 주둔지 수용량을 초과합니다."),
    SIEGE_TARGET_BUILDING_INVALID(HttpStatus.BAD_REQUEST, "정밀 공격 대상 건물이 유효하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
