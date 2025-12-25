package com.bitreiver.fetch_server.domain.article.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PublisherType {
    BLOCK_MEDIA(1, "블록미디어"),
    COIN_READERS(2, "coin readers"),
    YONHAP_INFOMAX(3, "연합인포맥스");
    
    private final Integer code;
    private final String name;
    
    public static PublisherType fromCode(Integer code) {
        for (PublisherType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown publisher type code: " + code);
    }
}