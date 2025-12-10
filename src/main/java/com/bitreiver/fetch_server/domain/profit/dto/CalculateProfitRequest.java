package com.bitreiver.fetch_server.domain.profit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Schema(description = "수익률 계산 요청")
public class CalculateProfitRequest {
    @Schema(description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true)
    @NotBlank(message = "사용자 ID는 필수입니다.")
    private String userId;
    
    @Schema(description = "거래소 코드 (1:Upbit, 2:Bithumb, 3:Binance, 4:OKX)", example = "1", required = true)
    @NotNull(message = "거래소 코드는 필수입니다.")
    private Integer exchangeCode;
    
    @Schema(description = "최초 계산 여부 (true: 전체 거래내역 계산, false: 기존 보유 종목 평단 사용)", example = "false", defaultValue = "false")
    private Boolean isInitial = false;
}

