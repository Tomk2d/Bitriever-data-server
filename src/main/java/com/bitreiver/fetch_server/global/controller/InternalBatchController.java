package com.bitreiver.fetch_server.global.controller;

import com.bitreiver.fetch_server.global.batch.BatchScheduler;
import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UTC 00시대 스케줄 배치를 수동 1회 실행하는 내부 전용 API.
 * 요청 시 헤더 X-Internal-Batch-Key 값이 환경변수 INTERNAL_BATCH_API_KEY와 일치해야 함.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/batch")
@Tag(name = "Internal Batch", description = "배치 수동 실행 (내부 전용, API 키 필요)")
public class InternalBatchController {

    private static final String HEADER_KEY = "X-Internal-Batch-Key";

    @Value("${INTERNAL_BATCH_API_KEY:}")
    private String internalBatchApiKey;

    private final BatchScheduler batchScheduler;

    private ResponseEntity<ApiResponse<String>> requireAuth(String keyHeader) {
        if (internalBatchApiKey == null || internalBatchApiKey.isBlank()) {
            log.warn("내부 배치 API 호출 거부: INTERNAL_BATCH_API_KEY 미설정");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "내부 배치 API가 비활성화되어 있습니다."));
        }
        if (keyHeader == null || !internalBatchApiKey.equals(keyHeader)) {
            log.warn("내부 배치 API 호출 거부: API 키 불일치");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("FORBIDDEN", "유효하지 않은 API 키입니다."));
        }
        return null;
    }

    @Operation(summary = "Binance Long/Short 1d 수동 실행", description = "매일 0시 1분(UTC)에 돌던 Binance 1d 배치를 1회 실행")
    @PostMapping("/binance-long-short-1d")
    public ResponseEntity<ApiResponse<String>> triggerBinanceLongShort1d(
            @RequestHeader(value = HEADER_KEY, required = false) String keyHeader) {
        ResponseEntity<ApiResponse<String>> authFailure = requireAuth(keyHeader);
        if (authFailure != null) return authFailure;
        try {
            batchScheduler.runBinanceLongShort1dManual();
            return ResponseEntity.ok(ApiResponse.success("Binance Long/Short 1d 배치를 수동 실행했습니다."));
        } catch (Exception e) {
            log.error("Binance Long/Short 1d 수동 실행 실패", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("BATCH_FAILED", e.getMessage()));
        }
    }

    @Operation(summary = "공포/탐욕 지수 DB 증분 저장 수동 실행", description = "UTC 00:15에 돌던 FearGreed DB 증분 저장 배치를 1회 실행")
    @PostMapping("/feargreed-to-db")
    public ResponseEntity<ApiResponse<String>> triggerFearGreedToDb(
            @RequestHeader(value = HEADER_KEY, required = false) String keyHeader) {
        ResponseEntity<ApiResponse<String>> authFailure = requireAuth(keyHeader);
        if (authFailure != null) return authFailure;
        try {
            batchScheduler.runFetchFearGreedToDbManual();
            return ResponseEntity.ok(ApiResponse.success("공포/탐욕 지수 DB 증분 저장 배치를 수동 실행했습니다."));
        } catch (Exception e) {
            log.error("FearGreed DB 수동 실행 실패", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("BATCH_FAILED", e.getMessage()));
        }
    }

    @Operation(summary = "업비트 종목 패치 + 일봉 동기화 수동 실행", description = "UTC 00:01에 돌던 업비트 종목 패치 및 일봉 동기화를 1회 실행 (수 분 소요 가능)")
    @PostMapping("/upbit-coin-fetch")
    public ResponseEntity<ApiResponse<String>> triggerUpbitCoinFetch(
            @RequestHeader(value = HEADER_KEY, required = false) String keyHeader) {
        ResponseEntity<ApiResponse<String>> authFailure = requireAuth(keyHeader);
        if (authFailure != null) return authFailure;
        try {
            batchScheduler.runUpbitCoinListFetchManual();
            return ResponseEntity.ok(ApiResponse.success("업비트 코인 종목 패치 및 일봉 동기화를 수동 실행했습니다."));
        } catch (Exception e) {
            log.error("업비트 코인 패치 수동 실행 실패", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("BATCH_FAILED", e.getMessage()));
        }
    }

    @Operation(summary = "코인원 종목 패치 + 일봉 동기화 수동 실행", description = "UTC 00:03에 돌던 코인원 종목 패치 및 일봉 동기화를 1회 실행 (수 분 소요 가능)")
    @PostMapping("/coinone-coin-fetch")
    public ResponseEntity<ApiResponse<String>> triggerCoinoneCoinFetch(
            @RequestHeader(value = HEADER_KEY, required = false) String keyHeader) {
        ResponseEntity<ApiResponse<String>> authFailure = requireAuth(keyHeader);
        if (authFailure != null) return authFailure;
        try {
            batchScheduler.runCoinoneCoinListFetchManual();
            return ResponseEntity.ok(ApiResponse.success("코인원 코인 종목 패치 및 일봉 동기화를 수동 실행했습니다."));
        } catch (Exception e) {
            log.error("코인원 코인 패치 수동 실행 실패", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("BATCH_FAILED", e.getMessage()));
        }
    }

    @Operation(summary = "00시대 배치 일괄 수동 실행", description = "Binance 1d → FearGreed DB → 업비트 종목+일봉 → 코인원 종목+일봉 순으로 1회씩 실행. 순차 실행으로 시간이 걸릴 수 있음.")
    @PostMapping("/midnight")
    public ResponseEntity<ApiResponse<String>> triggerMidnightBatches(
            @RequestHeader(value = HEADER_KEY, required = false) String keyHeader) {
        ResponseEntity<ApiResponse<String>> authFailure = requireAuth(keyHeader);
        if (authFailure != null) return authFailure;
        try {
            batchScheduler.runBinanceLongShort1dManual();
            batchScheduler.runFetchFearGreedToDbManual();
            batchScheduler.runUpbitCoinListFetchManual();
            batchScheduler.runCoinoneCoinListFetchManual();
            return ResponseEntity.ok(ApiResponse.success("00시대 배치 4종을 순차 수동 실행했습니다."));
        } catch (Exception e) {
            log.error("00시대 배치 일괄 수동 실행 실패", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("BATCH_FAILED", e.getMessage()));
        }
    }
}
