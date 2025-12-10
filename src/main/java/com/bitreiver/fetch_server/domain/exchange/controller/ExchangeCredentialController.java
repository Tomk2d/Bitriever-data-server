package com.bitreiver.fetch_server.domain.exchange.controller;

import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exchange-credentials")
@RequiredArgsConstructor
@Tag(name = "Exchange Credentials", description = "거래소 자격증명 관리 API")
public class ExchangeCredentialController {
    
    private final ExchangeCredentialService credentialService;
    
    @Operation(summary = "거래소 자격증명 저장/업데이트", description = "거래소 API 키를 암호화하여 저장하거나 업데이트합니다. 사용자의 connected_exchanges 목록도 자동으로 업데이트됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력 데이터 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없습니다")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @PostMapping("/{user_id}")
    public ResponseEntity<ApiResponse<ExchangeCredentialResponse>> saveCredentials(
            @PathVariable("user_id") UUID userId,
            @RequestBody ExchangeCredentialRequest request) {
        ExchangeCredentialResponse response = credentialService.saveCredentials(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, 
            ExchangeType.fromCode(request.getExchangeProvider()).getName() + " 자격증명이 저장되었습니다."));
    }
    
    @Operation(summary = "거래소 자격증명 조회", description = "특정 거래소의 자격증명을 조회합니다. 복호화된 access_key와 secret_key가 포함됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "해당 거래소의 자격증명을 찾을 수 없습니다"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 거래소명")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @Parameter(name = "exchange_provider", description = "거래소명 (UPBIT, BITHUMB, BINANCE, OKX)", example = "UPBIT", required = true, in = ParameterIn.PATH)
    @GetMapping("/{user_id}/{exchange_provider}")
    public ResponseEntity<ApiResponse<ExchangeCredentialResponse>> getCredentials(
            @PathVariable("user_id") UUID userId,
            @PathVariable("exchange_provider") String exchangeProviderStr) {
        try {
            ExchangeType exchangeType = ExchangeType.fromName(exchangeProviderStr);
            Short exchangeProvider = (short) exchangeType.getCode();
            
            ExchangeCredentialResponse response = credentialService.getCredentials(userId, exchangeProvider)
                .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                    "해당 거래소의 자격증명을 찾을 수 없습니다."));
            
            return ResponseEntity.ok(ApiResponse.success(response, 
                response.getProviderName() + " 자격증명 조회 완료"));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_EXCHANGE_PROVIDER, 
                "잘못된 거래소명입니다. UPBIT, BITHUMB, BINANCE, OKX 중 하나를 입력해주세요.");
        }
    }
    
    @Operation(summary = "모든 거래소 자격증명 조회", description = "사용자가 등록한 모든 거래소의 자격증명을 조회합니다. 복호화된 키는 포함되지 않습니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @GetMapping("/{user_id}")
    public ResponseEntity<ApiResponse<List<ExchangeCredentialResponse>>> getAllCredentials(
            @PathVariable("user_id") UUID userId) {
        List<ExchangeCredentialResponse> responses = credentialService.getAllCredentials(userId);
        return ResponseEntity.ok(ApiResponse.success(responses, 
            "총 " + responses.size() + "개의 거래소 자격증명 조회 완료"));
    }
    
    @Operation(summary = "거래소 자격증명 삭제", description = "특정 거래소의 자격증명을 삭제합니다. 사용자의 connected_exchanges 목록도 자동으로 업데이트됩니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "삭제할 거래소 자격증명을 찾을 수 없습니다"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 거래소명")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @Parameter(name = "exchange_provider", description = "거래소명 (UPBIT, BITHUMB, BINANCE, OKX)", example = "UPBIT", required = true, in = ParameterIn.PATH)
    @DeleteMapping("/{user_id}/{exchange_provider}")
    public ResponseEntity<ApiResponse<Object>> deleteCredentials(
            @PathVariable("user_id") UUID userId,
            @PathVariable("exchange_provider") String exchangeProviderStr) {
        try {
            ExchangeType exchangeType = ExchangeType.fromName(exchangeProviderStr);
            Short exchangeProvider = (short) exchangeType.getCode();
            
            boolean success = credentialService.deleteCredentials(userId, exchangeProvider);
            if (!success) {
                throw new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                    "삭제할 거래소 자격증명을 찾을 수 없습니다.");
            }
            
            return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("deleted", true),
                exchangeType.getName() + " 자격증명이 삭제되었습니다"));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_EXCHANGE_PROVIDER, 
                "잘못된 거래소명입니다.");
        }
    }
    
    @Operation(summary = "거래소 자격증명 검증", description = "저장된 거래소 자격증명의 유효성을 검증합니다. 복호화가 정상적으로 되는지 확인합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검증 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 거래소명")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @Parameter(name = "exchange_provider", description = "거래소명 (UPBIT, BITHUMB, BINANCE, OKX)", example = "UPBIT", required = true, in = ParameterIn.PATH)
    @PostMapping("/{user_id}/{exchange_provider}/verify")
    public ResponseEntity<ApiResponse<Object>> verifyCredentials(
            @PathVariable("user_id") UUID userId,
            @PathVariable("exchange_provider") String exchangeProviderStr) {
        try {
            ExchangeType exchangeType = ExchangeType.fromName(exchangeProviderStr);
            Short exchangeProvider = (short) exchangeType.getCode();
            
            boolean isValid = credentialService.verifyCredentials(userId, exchangeProvider);
            return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("is_valid", isValid),
                exchangeType.getName() + " 자격증명 검증 완료"));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_EXCHANGE_PROVIDER, 
                "잘못된 거래소명입니다.");
        }
    }
}

