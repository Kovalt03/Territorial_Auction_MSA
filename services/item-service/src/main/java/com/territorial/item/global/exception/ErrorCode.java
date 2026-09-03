package com.territorial.item.global.exception;

import com.territorial.auction.global.exception.ErrorCodeType;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode implements ErrorCodeType {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INSUFFICIENT_AP(HttpStatus.CONFLICT, "AP 잔액이 부족합니다."),

    TERRITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "영토를 찾을 수 없습니다."),
    NOT_TERRITORY_OWNER(HttpStatus.FORBIDDEN, "해당 영토의 점유자가 아닙니다."),

    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "아이템을 찾을 수 없습니다."),
    ITEM_OUT_OF_STOCK(HttpStatus.CONFLICT, "보유 수량이 없습니다."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "일일 구매 한도를 초과했습니다."),
    TARGET_TERRITORY_REQUIRED(HttpStatus.BAD_REQUEST, "대상 영토를 입력해주세요."),
    ALREADY_INVINCIBLE(HttpStatus.CONFLICT, "이미 무적 상태인 영토입니다."),
    ITEM_NOT_USABLE(HttpStatus.BAD_REQUEST, "해당 아이템은 구매 시 자동으로 적용됩니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
