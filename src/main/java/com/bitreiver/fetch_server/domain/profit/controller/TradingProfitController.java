package com.bitreiver.fetch_server.domain.profit.controller;

import com.bitreiver.fetch_server.domain.profit.dto.CalculateProfitRequest;
import com.bitreiver.fetch_server.domain.profit.service.TradingProfitService;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/trading-profit")
@RequiredArgsConstructor
@Tag(name = "Trading Profit", description = "거래 수익률 계산 API")
public class TradingProfitController {
    
    private final TradingProfitService tradingProfitService;
    
    @Operation(
        summary = "거래 수익률 계산", 
        description = "거래 내역을 기반으로 수익률을 계산하고 업데이트합니다. 보유 종목의 평균 단가도 함께 저장됩니다.\n\n" +
                     "- 최초 계산인 경우 (is_initial=true): 전체 거래 내역을 순회하며 계산\n" +
                     "- 이후 업데이트인 경우 (is_initial=false): coin_holdings_past 테이블의 기존 보유 종목 평단을 사용하여 계산"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "계산 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 거래소 코드"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<Object>> calculateProfit(@RequestBody CalculateProfitRequest request) {
        // 거래소 코드 검증
        if (request.getExchangeCode() < 1 || request.getExchangeCode() > 4) {
            throw new CustomException(ErrorCode.INVALID_EXCHANGE_CODE, 
                "거래소 코드는 1(Upbit), 2(Bithumb), 3(Binance), 4(OKX) 중 하나여야 합니다");
        }
        
        UUID userId = UUID.fromString(request.getUserId());
        Map<String, Object> result = tradingProfitService.calculateAndUpdateProfitLoss(
            userId,
            request.getExchangeCode(),
            request.getIsInitial()
        );
        
        return ResponseEntity.ok(ApiResponse.success(result,
            "수익률 계산이 완료되었습니다. 업데이트: " + result.get("updated_count") + 
            "개, 보유 종목: " + result.get("holdings_count") + 
            "개, 삭제: " + result.get("deleted_holdings_count") + "개"));
    }
}

