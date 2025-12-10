package com.bitreiver.fetch_server.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "거래내역 업데이트 요청")
public class UpdateTradingHistoryRequest {
    @Schema(description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    @NotBlank(message = "사용자 ID는 필수입니다.")
    @JsonProperty("user_id")
    private String userId;
    
    @Schema(description = "거래소명 (UPBIT, BITHUMB, BINANCE, OKX)", example = "UPBIT", required = true)
    @NotBlank(message = "거래소 제공자는 필수입니다.")
    @JsonProperty("exchange_provider_str")
    private String exchangeProviderStr;
}

