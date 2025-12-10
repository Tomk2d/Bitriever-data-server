package com.bitreiver.fetch_server.domain.exchange.dto;

import com.bitreiver.fetch_server.domain.exchange.entity.ExchangeCredential;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "거래소 자격증명 응답")
public class ExchangeCredentialResponse {
    @Schema(description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;
    
    @Schema(description = "거래소 코드 (1:Upbit, 2:Bithumb, 3:Binance, 4:OKX)", example = "1")
    private Short exchangeProvider;
    
    @Schema(description = "거래소 이름", example = "UPBIT")
    private String providerName;
    
    @Schema(description = "생성 시간", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "마지막 업데이트 시간", example = "2024-01-01T00:00:00")
    private LocalDateTime lastUpdatedAt;
    
    @Schema(description = "복호화된 Access Key (개별 조회 시에만 포함)", example = "your_access_key")
    private String accessKey;
    
    @Schema(description = "복호화된 Secret Key (개별 조회 시에만 포함)", example = "your_secret_key")
    private String secretKey;
    
    public static ExchangeCredentialResponse from(ExchangeCredential credential) {
        return ExchangeCredentialResponse.builder()
            .userId(credential.getUserId())
            .exchangeProvider(credential.getExchangeProvider())
            .createdAt(credential.getCreatedAt())
            .lastUpdatedAt(credential.getLastUpdatedAt())
            .build();
    }
}

