package com.bitreiver.fetch_server.domain.longshort.controller;

import com.bitreiver.fetch_server.domain.longshort.service.LongShortRatioService;
import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/binance/long-short-ratio")
@RequiredArgsConstructor
@Tag(name = "Binance Long/Short Ratio", description = "Binance USDⓈ-M Futures Long/Short Ratio API")
public class LongShortRatioController {
    
    private static final Set<String> VALID_PERIODS = Set.of("1h", "4h","12h", "1d");
    private static final Long DEFAULT_LIMIT = 30L;
    private static final Long MAX_LIMIT = 500L;
    
    private final LongShortRatioService longShortRatioService;
    
    @Operation(
        summary = "Binance Long/Short Ratio 조회 (테스트용)", 
        description = "모든 활성 코인에 대한 Binance USDⓈ-M Futures Long/Short Ratio를 조회합니다. " +
                     "결과는 저장하지 않고 바로 반환합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", 
            description = "잘못된 요청 파라미터 (period 또는 limit)"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류"
        )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLongShortRatio(
        @Parameter(description = "기간 (5m, 15m, 30m, 1h, 2h, 4h, 6h, 12h, 1d)", required = true)
        @RequestParam("period") String period,
        
        @Parameter(description = "제한 (기본값: 30, 최대: 500)")
        @RequestParam(value = "limit", required = false) Long limit
    ) {
        // period 유효성 검증
        if (!VALID_PERIODS.contains(period)) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                    "INVALID_PERIOD",
                    "유효하지 않은 period 값입니다. 허용된 값: " + String.join(", ", VALID_PERIODS)
                ));
        }
        
        // limit 유효성 검증
        if (limit != null) {
            if (limit <= 0) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                        "INVALID_LIMIT",
                        "limit은 1 이상이어야 합니다."
                    ));
            }
            if (limit > MAX_LIMIT) {
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                        "INVALID_LIMIT",
                        "limit은 " + MAX_LIMIT + " 이하여야 합니다."
                    ));
            }
        }
        
        // limit이 null이면 기본값 사용
        Long finalLimit = limit != null ? limit : DEFAULT_LIMIT;
        
        // 서비스 호출
        Map<String, Object> result = longShortRatioService.fetchAll(period, finalLimit);
        
        return ResponseEntity.ok(
            ApiResponse.success(
                result,
                "Binance Long/Short Ratio 조회가 완료되었습니다."
            )
        );
    }
}

