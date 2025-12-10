package com.bitreiver.fetch_server.domain.exchange.enums;

import lombok.Getter;

@Getter
public enum ExchangeType {
    UPBIT(1, "업비트"),
    BITHUMB(2, "빗썸"),
    BINANCE(3, "바이낸스"),
    OKX(4, "OKX");
    
    private final int code;
    private final String name;
    
    ExchangeType(int code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public static ExchangeType fromCode(int code) {
        for (ExchangeType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid exchange code: " + code);
    }
    
    public static ExchangeType fromName(String name) {
        for (ExchangeType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid exchange name: " + name);
    }
}

