package com.bitreiver.fetch_server.global.controller;

import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health", description = "서버 상태 확인 API")
public class HealthController {
    
    @Operation(summary = "서버 상태 확인", description = "서버가 정상적으로 동작 중인지 확인합니다.")
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Object>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "healthy"), "서버가 정상적으로 동작 중입니다"));
    }
    
    @Operation(summary = "API 정보", description = "API 기본 정보를 반환합니다.")
    @GetMapping("/")
    public ResponseEntity<ApiResponse<Object>> root() {
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("message", "Bitriever Fetch Server API", "version", "1.0.0"),
            "Bitriever Fetch Server"
        ));
    }
}

