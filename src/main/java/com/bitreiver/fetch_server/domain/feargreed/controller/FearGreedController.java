package com.bitreiver.fetch_server.domain.feargreed.controller;

import com.bitreiver.fetch_server.domain.feargreed.dto.FearGreedApiResponse;
import com.bitreiver.fetch_server.domain.feargreed.service.FearGreedService;
import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/fear-greed")
@RequiredArgsConstructor
@Tag(name = "Fear & Greed Index", description = "공포/탐욕 지수 API")
public class FearGreedController {
    
    private final FearGreedService fearGreedService;

    
    @Operation(
        summary = "전체 공포/탐욕 지수 데이터 패치", 
        description = "alternative.me API에서 전체 공포/탐욕 지수 데이터를 가져와 데이터베이스에 저장합니다. " +
                     "최초 1회 실행용이며, 기존 데이터는 업데이트됩니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "데이터 패치 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류 또는 API 호출 실패"
        )
    })
    @PostMapping("/fetch-all-history")
    public ApiResponse<Map<String, Object>> fetchAllHistory() {
        Map<String, Object> result = fearGreedService.fetchAndSaveAllHistory();
        return ApiResponse.success(
            result, 
            "공포/탐욕 지수 전체 데이터 패치가 완료되었습니다. " +
            "신규: " + result.get("saved") + "개, " +
            "업데이트: " + result.get("updated") + "개"
        );
    }
    
    @Operation(
        summary = "공포/탐욕 지수 최근 데이터 조회 (테스트용)", 
        description = "alternative.me API에서 최근 7일 공포/탐욕 지수 데이터를 조회합니다. 로그만 출력하고 DB에는 저장하지 않습니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "데이터 조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500", 
            description = "서버 내부 오류"
        )
    })
    @PostMapping("/fetch-recent")
    public ApiResponse<Void> fetchRecentFearGreedData() {
        fearGreedService.fetchRecentData();
        return ApiResponse.success(null, "공포/탐욕 지수 최근 데이터 조회가 완료되었습니다.");
    }
}

