package com.territorial.map.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    CONTINENT_NOT_FOUND(HttpStatus.NOT_FOUND, "대륙을 찾을 수 없습니다."),
    TERRITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "영토를 찾을 수 없습니다."),
    NOT_TERRITORY_OWNER(HttpStatus.FORBIDDEN, "해당 영토의 점유자가 아닙니다."),
    TERRITORY_NOT_OCCUPIED(HttpStatus.BAD_REQUEST, "점유 중인 영토가 아닙니다."),
    COLOR_CHANGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "색상 변경 횟수를 초과했습니다."),
    TERRITORY_GRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "영토 등급을 찾을 수 없습니다."),
    GRADE_DISTRIBUTION_MISMATCH(HttpStatus.BAD_REQUEST, "등급 분배 합계가 영토 수와 일치하지 않습니다."),
    TERRITORY_NOT_IDLE(HttpStatus.BAD_REQUEST, "대기 중인 영토가 아닙니다."),
    TERRITORY_GRADE_LOCKED_OCCUPIED(HttpStatus.BAD_REQUEST, "점유 중인 영토는 등급을 변경할 수 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
