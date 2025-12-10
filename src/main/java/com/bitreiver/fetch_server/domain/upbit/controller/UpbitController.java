package com.bitreiver.fetch_server.domain.upbit.controller;

import com.bitreiver.fetch_server.domain.asset.dto.AssetsSyncRequest;
import com.bitreiver.fetch_server.domain.asset.service.AssetService;
import com.bitreiver.fetch_server.domain.coin.service.CoinImageService;
import com.bitreiver.fetch_server.domain.coin.service.CoinService;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.exchange.service.ExchangeCredentialService;
import com.bitreiver.fetch_server.domain.upbit.service.UpbitService;
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
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/upbit")
@RequiredArgsConstructor
@Tag(name = "Upbit", description = "Upbit API 연동 및 데이터 수집 API")
public class UpbitController {
    
    private final UpbitService upbitService;
    private final ExchangeCredentialService exchangeCredentialService;
    private final AssetService assetService;
    private final CoinService coinService;
    private final CoinImageService coinImageService;
    
    @Operation(summary = "거래내역 조회 (테스트용)", description = "Upbit API에서 모든 거래내역을 조회합니다. 현재는 테스트용으로 구현되어 있습니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/allTradingHistory")
    public ResponseEntity<ApiResponse<Object>> fetchTradingHistory() {
        // 환경변수에서 키 가져오기 (테스트용)
        // 실제로는 인증된 사용자의 자격증명을 사용해야 함
        return ResponseEntity.ok(ApiResponse.success(null, "거래 내역 조회가 완료되었습니다"));
    }
    
    @Operation(summary = "코인 목록 조회 및 저장", description = "Upbit에서 지원하는 모든 코인 목록을 조회하여 데이터베이스에 저장합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 및 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/fetchAllCoinList")
    public Mono<ResponseEntity<ApiResponse<Object>>> fetchAndSaveAllCoinList() {
        // 외부 API에서 코인 목록 조회 후 저장
        return upbitService.fetchAllCoinList()
            // 재시도: 1초 간격으로 최대 3회 재시도
            .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)))
            // 1. 외부 api 조회 선작업. 비동기 + 체이닝 
            .flatMap(fetchedCoinList -> {
                if (fetchedCoinList == null || fetchedCoinList.isEmpty()) {
                    throw new CustomException(ErrorCode.INTERNAL_ERROR, "코인 목록을 가져올 수 없습니다");
                }
                // 2. 후행 작업: 조회 결과로 저장
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
    
    @Operation(summary = "Upbit 계정 잔고 조회", description = "Upbit API를 통해 사용자의 계정 잔고를 조회합니다. " +
            "사용자의 Upbit 자격증명이 필요합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Upbit 자격증명을 찾을 수 없습니다"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "자격증명 복호화 실패 또는 서버 내부 오류")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @GetMapping("/accounts/{user_id}")
    public ResponseEntity<ApiResponse<Object>> fetchAccounts(@PathVariable("user_id") UUID userId) {
        ExchangeCredentialResponse credentials = exchangeCredentialService
            .getCredentials(userId, (short) ExchangeType.UPBIT.getCode())
            .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                "Upbit 자격증명을 찾을 수 없습니다"));
        
        if (credentials.getAccessKey() == null || credentials.getSecretKey() == null) {
            throw new CustomException(ErrorCode.CREDENTIALS_DECRYPTION_FAILED, 
                "자격증명 복호화에 실패했습니다");
        }
        
        List<Map<String, Object>> accounts = upbitService.fetchAccounts(
            credentials.getAccessKey(),
            credentials.getSecretKey()
        ).block();
        
        return ResponseEntity.ok(ApiResponse.success(accounts, "계정 잔고 조회가 완료되었습니다"));
    }
    
    @Operation(summary = "자산 동기화", description = "Upbit 계정 잔고를 조회하여 assets 테이블에 동기화합니다. " +
            "기존 자산은 업데이트되고, 잔고에 없는 자산은 삭제됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Upbit 자격증명을 찾을 수 없습니다"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "자격증명 복호화 실패 또는 서버 내부 오류")
    })
    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<Object>> syncAccounts(@RequestBody AssetsSyncRequest request) {
        UUID userId = UUID.fromString(request.getUserId());
        Map<String, Object> result = assetService.syncUpbitAssets(userId);
        
        return ResponseEntity.ok(ApiResponse.success(result,
            "자산 동기화가 완료되었습니다. 저장: " + result.get("saved_count") + 
            "개, 삭제: " + result.get("deleted_count") + "개"));
    }
}

