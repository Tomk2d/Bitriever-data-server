package com.bitreiver.fetch_server.domain.trading.dto;

import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "매매 내역 응답")
public class TradingHistoryResponse {
    @Schema(description = "매매 내역 ID", example = "1")
    private Integer id;
    
    @Schema(description = "사용자 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID userId;
    
    @Schema(description = "코인 ID", example = "1")
    private Integer coinId;
    
    @Schema(description = "거래소 코드 (1:UPBIT, 2:BITHUMB, 3:COINONE, 11:BINANCE, 12:BYBIT, 13:COINBASE, 14:OKX)", example = "1")
    private Short exchangeCode;
    
    @Schema(description = "거래 고유 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String tradeUuid;
    
    @Schema(description = "거래 타입 (0:매수, 1:매도)", example = "0")
    private Short tradeType;
    
    @Schema(description = "거래 가격", example = "50000.0")
    private BigDecimal price;
    
    @Schema(description = "거래 수량", example = "0.001")
    private BigDecimal quantity;
    
    @Schema(description = "총 거래 금액", example = "50.0")
    private BigDecimal totalPrice;
    
    @Schema(description = "수수료", example = "0.05")
    private BigDecimal fee;
    
    @Schema(description = "거래 일시", example = "2024-01-01T00:00:00")
    private LocalDateTime tradeTime;
    
    @Schema(description = "상승하락률 (0.50% 상승 = 0.50, 50% 상승 = 50, 50% 하락 = -50)", example = "0.50", nullable = true)
    private BigDecimal profitLossRate;
    
    @Schema(description = "구매 시 평균 단가 (매도 시에만 값 존재)", example = "50000.0", nullable = true)
    private BigDecimal avgBuyPrice;
    
    @Schema(description = "생성 일시", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;
    
    public static TradingHistoryResponse from(TradingHistory history) {
        return TradingHistoryResponse.builder()
            .id(history.getId())
            .userId(history.getUserId())
            .coinId(history.getCoinId())
            .exchangeCode(history.getExchangeCode())
            .tradeUuid(history.getTradeUuid())
            .tradeType(history.getTradeType())
            .price(history.getPrice())
            .quantity(history.getQuantity())
            .totalPrice(history.getTotalPrice())
            .fee(history.getFee())
            .tradeTime(history.getTradeTime())
            .profitLossRate(history.getProfitLossRate())
            .avgBuyPrice(history.getAvgBuyPrice())
            .createdAt(history.getCreatedAt())
            .build();
    }
}

