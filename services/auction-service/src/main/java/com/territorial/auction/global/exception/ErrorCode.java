package com.territorial.auction.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** auction 서비스 도메인 에러 코드. 공통 코드(INVALID_INPUT 등)는 common의 CommonErrorCode 사용. */
@Getter
public enum ErrorCode implements ErrorCodeType {

    // 크로스도메인 참조 — 5번(통신 치환)에서 외부 호출/이벤트로 대체될 수 있음
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    TERRITORY_NOT_FOUND(HttpStatus.NOT_FOUND, "영토를 찾을 수 없습니다."),
    BUILDING_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "건물 타입을 찾을 수 없습니다."),

    // Auction (이 서비스 소유)
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "경매를 찾을 수 없습니다."),
    AUCTION_ALREADY_ENDED(HttpStatus.BAD_REQUEST, "이미 종료된 경매입니다."),
    AUCTION_ALREADY_SETTLED(HttpStatus.CONFLICT, "이미 정산된 경매입니다."),
    AUCTION_NO_BIDDER_TO_SETTLE(HttpStatus.CONFLICT, "입찰자가 없어 강제 낙찰할 수 없습니다. 강제 취소를 사용하세요."),
    BID_AMOUNT_TOO_LOW(HttpStatus.BAD_REQUEST, "입찰 금액이 현재 최고가보다 낮습니다."),
    ALREADY_HIGHEST_BIDDER(HttpStatus.BAD_REQUEST, "이미 최고 입찰자입니다."),
    INSUFFICIENT_AP(HttpStatus.CONFLICT, "AP 잔액이 부족합니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
