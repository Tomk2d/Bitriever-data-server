package com.bitreiver.fetch_server.domain.trading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "거래내역 목록 응답")
public class TradingHistoryListResponse {
    @Schema(description = "전체 거래내역 개수", example = "100")
    private Integer totalCount;
    
    @Schema(description = "거래내역 목록")
    private List<TradingHistoryResponse> tradingHistories;
}

