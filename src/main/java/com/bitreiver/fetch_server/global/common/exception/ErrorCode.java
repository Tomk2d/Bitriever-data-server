package com.bitreiver.fetch_server.global.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 공통
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
    
    // 인증/인가
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "EXPIRED_TOKEN", "만료된 토큰입니다."),
    WRONG_PASSWORD(HttpStatus.UNAUTHORIZED, "WRONG_PASSWORD", "비밀번호가 일치하지 않습니다."),
    
    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "이미 존재하는 사용자입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "닉네임은 온전한 문자만 사용 가능하며, 최대 20자까지 입력할 수 있습니다."),
    
    // 거래소
    EXCHANGE_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_NOT_FOUND", "거래소 정보를 찾을 수 없습니다."),
    EXCHANGE_CREDENTIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "EXCHANGE_CREDENTIAL_NOT_FOUND", "거래소 자격증명을 찾을 수 없습니다."),
    INVALID_EXCHANGE_KEY(HttpStatus.BAD_REQUEST, "INVALID_EXCHANGE_KEY", "유효하지 않은 거래소 API 키입니다."),
    INVALID_EXCHANGE_PROVIDER(HttpStatus.BAD_REQUEST, "INVALID_EXCHANGE_PROVIDER", "잘못된 거래소명입니다."),
    
    // 코인
    COIN_NOT_FOUND(HttpStatus.NOT_FOUND, "COIN_NOT_FOUND", "코인 정보를 찾을 수 없습니다."),
    
    // 매매 내역
    TRADING_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "TRADING_HISTORY_NOT_FOUND", "매매 내역을 찾을 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "시작 날짜는 종료 날짜보다 이전이어야 합니다."),
    
    // 수익률
    INVALID_EXCHANGE_CODE(HttpStatus.BAD_REQUEST, "INVALID_EXCHANGE_CODE", "잘못된 거래소 코드입니다."),
    
    // 자격증명
    CREDENTIALS_NOT_FOUND(HttpStatus.NOT_FOUND, "CREDENTIALS_NOT_FOUND", "자격증명을 찾을 수 없습니다."),
    CREDENTIALS_DECRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CREDENTIALS_DECRYPTION_FAILED", "자격증명 복호화에 실패했습니다."),
    
    // 공포/탐욕 지수
    FEAR_GREED_NOT_FOUND(HttpStatus.NOT_FOUND, "FEAR_GREED_NOT_FOUND", "해당 날짜의 공포/탐욕 지수를 찾을 수 없습니다."),
    FEAR_GREED_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "FEAR_GREED_API_ERROR", "공포/탐욕 지수 API 호출에 실패했습니다.");
    

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}

