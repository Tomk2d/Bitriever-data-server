package com.bitreiver.fetch_server.domain.coinone.controller;

import com.bitreiver.fetch_server.domain.asset.dto.AssetsSyncRequest;
import com.bitreiver.fetch_server.domain.asset.service.AssetService;
import com.bitreiver.fetch_server.domain.coin.service.CoinImageService;
import com.bitreiver.fetch_server.domain.coin.service.CoinService;
import com.bitreiver.fetch_server.domain.coinone.service.CoinoneService;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.exchange.service.ExchangeCredentialService;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/coinone")
@RequiredArgsConstructor
@Tag(name = "Coinone", description = "코인원 API 연동 및 데이터 수집 API")
public class CoinoneController {
    
    private final CoinoneService coinoneService;
    private final CoinService coinService;
    private final CoinImageService coinImageService;
    private final ExchangeCredentialService exchangeCredentialService;
    private final AssetService assetService;
    
    @Operation(summary = "코인 목록 조회 및 저장", 
               description = "코인원에서 지원하는 모든 코인 목록을 조회하여 데이터베이스에 저장합니다. 업비트에 없는 종목만 저장됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 및 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/fetchAllCoinList")
    public Mono<ResponseEntity<ApiResponse<Object>>> fetchAndSaveAllCoinList() {
        // 외부 API에서 코인 목록 조회 후 저장
        return coinoneService.fetchAllCoinList()
            // 재시도: 1초 간격으로 최대 3회 재시도
            .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
            // 1. 외부 api 조회 선작업. 비동기 + 체이닝 
            .flatMap(fetchedCoinList -> {
                if (fetchedCoinList == null || fetchedCoinList.isEmpty()) {
                    throw new CustomException(ErrorCode.INTERNAL_ERROR, "코인 목록을 가져올 수 없습니다");
                }
                // 2. 후행 작업: 조회 결과로 저장 (업비트에 없는 종목만 저장)
                Map<String, Object> result = coinService.saveAllCoinList(fetchedCoinList);
                
                // 3. 아이콘 다운로드
                try {
                    int downloadedCount = coinImageService.downloadCoinImages(fetchedCoinList);
                    result.put("downloaded_images", downloadedCount);
                } catch (Exception e) {
                    log.warn("아이콘 다운로드 중 오류 발생: {}", e.getMessage());
                }
                
                // 저장 결과 반환
                ApiResponse<Object> successResponse = ApiResponse.<Object>success(
                    result, 
                    "코인 목록 조회 및 저장 완료"
                );
                return Mono.<ResponseEntity<ApiResponse<Object>>>just(
                    ResponseEntity.ok(successResponse)
                );
            })
            .onErrorResume(error -> {
                // 에러를 CustomException으로 변환하여 전역 에러 핸들러가 처리하도록
                if (error instanceof CustomException) {
                    return Mono.error(error);
                }
                log.error("코인 목록 조회 및 저장 중 오류 발생: {}", error.getMessage(), error);
                return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "코인 목록 조회 및 저장 중 오류가 발생했습니다: " + error.getMessage()));
            });
    }
    
    @Operation(summary = "코인원 계정 잔고 조회", description = "코인원 API를 통해 사용자의 계정 잔고를 조회합니다. " +
            "사용자의 코인원 자격증명이 필요합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코인원 자격증명을 찾을 수 없습니다"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "자격증명 복호화 실패 또는 서버 내부 오류")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @GetMapping("/accounts/{user_id}")
    public ResponseEntity<ApiResponse<Object>> fetchAccounts(@PathVariable("user_id") UUID userId) {
        ExchangeCredentialResponse credentials = exchangeCredentialService
            .getCredentials(userId, (short) ExchangeType.COINONE.getCode())
            .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                "코인원 자격증명을 찾을 수 없습니다"));
        
        if (credentials.getAccessKey() == null || credentials.getSecretKey() == null) {
            throw new CustomException(ErrorCode.CREDENTIALS_DECRYPTION_FAILED, 
                "자격증명 복호화에 실패했습니다");
        }
        
        List<Map<String, Object>> accounts = coinoneService.fetchAccounts(
            credentials.getAccessKey(),
            credentials.getSecretKey()
        ).block();
        
        return ResponseEntity.ok(ApiResponse.success(accounts, "계정 잔고 조회가 완료되었습니다"));
    }
    
    @Operation(summary = "자산 동기화", description = "코인원 계정 잔고를 조회하여 assets 테이블에 동기화합니다. " +
            "기존 자산은 업데이트되고, 잔고에 없는 자산은 삭제됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "코인원 자격증명을 찾을 수 없습니다"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "자격증명 복호화 실패 또는 서버 내부 오류")
    })
    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<Object>> syncAccounts(@RequestBody AssetsSyncRequest request) {
        UUID userId = UUID.fromString(request.getUserId());
        Map<String, Object> result = assetService.syncCoinoneAssets(userId);
        
        return ResponseEntity.ok(ApiResponse.success(result,
            "자산 동기화가 완료되었습니다. 저장: " + result.get("saved_count") + 
            "개, 삭제: " + result.get("deleted_count") + "개"));
    }
    
    @Operation(summary = "일봉 데이터 동기화", description = "모든 코인원 활성 코인의 일봉 데이터를 동기화합니다. " +
            "DB에 저장된 마지막 날짜 이후부터 현재까지 데이터를 수집합니다. " +
            "각 코인별로 개별 처리되어 실패해도 성공한 코인은 저장됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/syncDailyCandles")
    public Mono<ResponseEntity<ApiResponse<Object>>> syncDailyCandles() {
        return coinoneService.syncAllCoinDailyCandles()
            .<ResponseEntity<ApiResponse<Object>>>map(result -> ResponseEntity.ok(ApiResponse.<Object>success(result, 
                "일봉 데이터 동기화가 완료되었습니다. 전체: " + result.get("totalCoins") + 
                "개, 성공: " + result.get("successCount") + 
                "개, 실패: " + result.get("errorCount") + 
                "개, 저장된 캔들: " + result.get("totalCandlesSaved") + "개")))
            .onErrorResume(error -> {
                if (error instanceof CustomException) {
                    return Mono.error(error);
                }
                log.error("일봉 데이터 동기화 중 오류 발생: {}", error.getMessage(), error);
                return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "일봉 데이터 동기화 중 오류가 발생했습니다: " + error.getMessage()));
            });
    }
}
