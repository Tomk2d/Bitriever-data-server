package com.bitreiver.fetch_server.domain.user.controller;

import com.bitreiver.fetch_server.domain.user.dto.*;
import com.bitreiver.fetch_server.domain.user.service.UserService;
import com.bitreiver.fetch_server.domain.trading.service.TradingHistoryService;
import com.bitreiver.fetch_server.domain.profit.service.TradingProfitService;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 인증 및 거래내역 관리 API")
public class UserController {
    
    private final UserService userService;
    private final TradingHistoryService tradingHistoryService;
    private final TradingProfitService tradingProfitService;
    
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다. 로컬 가입 시 비밀번호가 필수입니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력 데이터 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 이메일/닉네임")
    })
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@RequestBody UserSignUpRequest request) {
        UserResponse response = userService.signup(request);
        return ResponseEntity.ok(ApiResponse.success(response, "회원가입이 완료되었습니다"));
    }
    
    @Operation(summary = "로그인", description = "사용자 로그인 및 JWT 토큰을 발급합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 - 이메일 또는 비밀번호가 일치하지 않습니다")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody UserLoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "로그인이 완료되었습니다"));
    }
    
    @Operation(summary = "이메일 중복 검사", description = "회원가입 전 이메일 중복 여부를 확인합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "중복 검사 완료")
    })
    @Parameter(name = "email", description = "확인할 이메일 주소", example = "user@example.com", required = true, in = ParameterIn.QUERY)
    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Object>> checkEmail(@RequestParam String email) {
        boolean isDuplicate = userService.checkEmailDuplicate(email);
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("email", email, "is_duplicate", isDuplicate),
            "이메일 중복 검사가 완료되었습니다"
        ));
    }
    
    @Operation(summary = "닉네임 중복 검사", description = "회원가입 전 닉네임 중복 여부를 확인합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "중복 검사 완료")
    })
    @Parameter(name = "nickname", description = "확인할 닉네임", example = "user123", required = true, in = ParameterIn.QUERY)
    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<Object>> checkNickname(@RequestParam String nickname) {
        boolean isDuplicate = userService.checkNicknameDuplicate(nickname);
        return ResponseEntity.ok(ApiResponse.success(
            Map.of("nickname", nickname, "is_duplicate", isDuplicate),
            "닉네임 중복 검사가 완료되었습니다"
        ));
    }
    
    @Operation(summary = "거래내역 조회", description = "사용자의 모든 거래내역을 조회합니다.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @Parameter(name = "user_id", description = "사용자 UUID", example = "123e4567-e89b-12d3-a456-426614174000", required = true, in = ParameterIn.PATH)
    @GetMapping("/getTradingHistory/{user_id}")
    public ResponseEntity<ApiResponse<Object>> getTradingHistory(@PathVariable("user_id") UUID userId) {
        Map<String, Object> data = tradingHistoryService.getAllTradingHistoriesByUserFormattedAsMap(userId);
        return ResponseEntity.ok(ApiResponse.success(data, 
            "거래내역 조회 완료 (총 " + data.get("total_count") + "개)"));
    }
    
    @Operation(
        summary = "거래내역 업데이트", 
        description = "거래소 API에서 거래내역을 조회하여 저장하고 수익률을 계산합니다.\n\n" +
                     "- 최초 동기화인 경우: 전체 거래내역을 순회하며 수익률 계산\n" +
                     "- 이후 업데이트인 경우: 기존 보유 종목 평단을 사용하여 계산\n" +
                     "- 거래내역이 저장된 경우에만 수익률 계산을 수행합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "업데이트 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 거래소명"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 또는 거래소 자격증명을 찾을 수 없습니다"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/updateTradingHistory")
    public ResponseEntity<ApiResponse<Object>> updateTradingHistory(@RequestBody UpdateTradingHistoryRequest request) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            String exchangeProviderStr = request.getExchangeProviderStr();
            
            // ExchangeProvider 검증
            ExchangeType exchangeType;
            try {
                exchangeType = ExchangeType.fromName(exchangeProviderStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CustomException(ErrorCode.INVALID_EXCHANGE_PROVIDER, 
                    "잘못된 거래소명입니다. UPBIT, BITHUMB, BINANCE, OKX 중 하나를 입력해주세요.");
            }
            
            // 사용자의 마지막 거래내역 업데이트 시간 조회
            LocalDateTime startTime = userService.getUser(userId)
                .map(com.bitreiver.fetch_server.domain.user.entity.User::getLastTradingHistoryUpdateAt)
                .orElse(null);
            
            // 최초 동기화 여부 판단
            boolean isInitial = startTime == null;
            
            // 거래내역 조회
            List<Map<String, Object>> tradingHisties = tradingHistoryService.getTradingHistories(
                userId, exchangeProviderStr, startTime);
            
            // 거래내역 처리
            List<com.bitreiver.fetch_server.domain.trading.entity.TradingHistory> processedHistories = 
                tradingHistoryService.processTradingHistories(userId, exchangeProviderStr, tradingHisties);
            
            // 거래내역 저장
            List<com.bitreiver.fetch_server.domain.trading.entity.TradingHistory> savedHistories = 
                tradingHistoryService.saveTradingHistories(processedHistories);
            
            // 거래 내역이 저장된 경우에만 수익률 계산 수행
            Map<String, Object> profitCalculationResult = null;
            if (!savedHistories.isEmpty()) {
                try {
                    profitCalculationResult = tradingProfitService.calculateAndUpdateProfitLoss(
                        userId,
                        exchangeType.getCode(),
                        isInitial
                    );
                } catch (Exception e) {
                    // 수익률 계산 실패해도 거래 내역 저장은 성공했으므로 로그만 남기고 계속 진행
                    // 로그는 TradingProfitService에서 처리
                }
            }
            
            // 모든 거래내역 조회
            Map<String, Object> allTradingHistoriesData = 
                tradingHistoryService.getAllTradingHistoriesByUserFormattedAsMap(userId);
            
            // 매매내역 업데이트가 성공적으로 완료되었으므로 업데이트 시간 갱신
            userService.updateUserTradingHistoryUpdatedAt(userId);
            
            Map<String, Object> responseData = new java.util.HashMap<>(allTradingHistoriesData);
            responseData.put("saved_count", savedHistories.size());
            
            if (profitCalculationResult != null) {
                responseData.put("profit_calculation", profitCalculationResult);
            }
            
            return ResponseEntity.ok(ApiResponse.success(responseData,
                exchangeType.getName() + " 거래내역 업데이트 완료 (저장: " + savedHistories.size() + 
                "개, 전체: " + allTradingHistoriesData.get("total_count") + "개)"));
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 업데이트 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
