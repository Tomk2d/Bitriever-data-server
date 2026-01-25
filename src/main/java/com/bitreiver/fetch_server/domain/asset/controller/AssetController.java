package com.bitreiver.fetch_server.domain.asset.controller;

import com.bitreiver.fetch_server.domain.asset.dto.AssetsSyncRequest;
import com.bitreiver.fetch_server.domain.asset.dto.AssetsSyncAsyncRequest;
import com.bitreiver.fetch_server.domain.asset.service.AssetService;
import com.bitreiver.fetch_server.domain.sync.service.SyncService;
import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "Asset", description = "자산 동기화 API")
public class AssetController {
    
    private final AssetService assetService;
    private final SyncService syncService;
    
    @Operation(summary = "모든 거래소 자산 동기화 (동기)", description = "사용자의 모든 연동 거래소 자산을 동기화합니다. " +
            "기존 자산은 업데이트되고, 잔고에 없는 자산은 삭제됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/sync-all")
    public ResponseEntity<ApiResponse<Object>> syncAllExchangeAssets(@RequestBody AssetsSyncRequest request) {
        UUID userId = UUID.fromString(request.getUserId());
        Map<String, Object> result = assetService.syncAllExchangeAssets(userId);
        
        return ResponseEntity.ok(ApiResponse.success(result,
            "모든 거래소 자산 동기화가 완료되었습니다. 총 저장: " + result.get("total_saved_count") + 
            "개, 총 삭제: " + result.get("total_deleted_count") + "개"));
    }
    
    @Operation(summary = "모든 거래소 자산 동기화 (비동기)", 
        description = "사용자의 모든 연동 거래소 자산을 비동기로 동기화합니다. " +
            "즉시 응답을 반환하고, 완료 시 콜백 URL로 결과를 전송합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동기화 시작됨"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/sync-all/async")
    public ResponseEntity<ApiResponse<Object>> syncAllExchangeAssetsAsync(@RequestBody AssetsSyncAsyncRequest request) {
        UUID userId = UUID.fromString(request.getUserId());
        String callbackUrl = request.getCallbackUrl();
        
        log.info("비동기 자산 동기화 요청: userId={}, callbackUrl={}", userId, callbackUrl);
        
        // 비동기 처리 시작 (즉시 반환)
        syncService.syncAssetsAsync(userId, callbackUrl);
        
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("status", "PROCESSING", "user_id", userId.toString()),
            "자산 동기화가 시작되었습니다. 완료 시 콜백 URL로 결과가 전송됩니다."));
    }
}
