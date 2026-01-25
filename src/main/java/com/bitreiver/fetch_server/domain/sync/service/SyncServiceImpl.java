package com.bitreiver.fetch_server.domain.sync.service;

import com.bitreiver.fetch_server.domain.asset.service.AssetService;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.profit.service.TradingProfitService;
import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;
import com.bitreiver.fetch_server.domain.trading.service.TradingHistoryService;
import com.bitreiver.fetch_server.domain.user.entity.User;
import com.bitreiver.fetch_server.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {
    
    private final AssetService assetService;
    private final TradingHistoryService tradingHistoryService;
    private final TradingProfitService tradingProfitService;
    private final UserService userService;
    private final RestTemplate restTemplate;
    
    @Value("${external.app.server.url}")
    private String appServerUrl;
    
    @Override
    @Async("syncExecutor")
    public void syncAssetsAsync(UUID userId, String callbackUrl) {
        Map<String, Object> callbackData = new HashMap<>();
        callbackData.put("user_id", userId.toString());
        callbackData.put("sync_type", "ASSET");
        
        try {
            // 자산 동기화 수행
            Map<String, Object> result = assetService.syncAllExchangeAssets(userId);
            
            callbackData.put("success", true);
            callbackData.put("data", result);
            callbackData.put("message", "자산 동기화 완료");
            
        } catch (Exception e) {
            log.error("비동기 자산 동기화 실패: userId={}, error={}", userId, e.getMessage(), e);
            
            callbackData.put("success", false);
            callbackData.put("error", e.getMessage());
            callbackData.put("message", "자산 동기화 실패");
        }
        
        // 콜백 호출
        sendCallback(callbackUrl, callbackData);
    }
    
    @Override
    @Async("syncExecutor")
    public void syncTradingHistoryAsync(UUID userId, List<String> exchanges, String callbackUrl) {
        Map<String, Object> callbackData = new HashMap<>();
        callbackData.put("user_id", userId.toString());
        callbackData.put("sync_type", "TRADING_HISTORY");
        
        List<String> successExchanges = new ArrayList<>();
        List<String> failedExchanges = new ArrayList<>();
        List<Integer> allSavedIds = new ArrayList<>();
        
        try {
            User user = userService.getUser(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + userId));
            
            for (String exchangeStr : exchanges) {
                try {
                    Map<String, Object> exchangeResult = syncSingleExchangeTradingHistory(user, exchangeStr);
                    
                    successExchanges.add(exchangeStr);
                    
                    @SuppressWarnings("unchecked")
                    List<Integer> savedIds = (List<Integer>) exchangeResult.get("saved_ids");
                    if (savedIds != null) {
                        allSavedIds.addAll(savedIds);
                    }
                    
                } catch (Exception e) {
                    log.error("거래내역 동기화 실패: userId={}, exchange={}, error={}", 
                        userId, exchangeStr, e.getMessage());
                    failedExchanges.add(exchangeStr);
                }
            }
            
            callbackData.put("success", !successExchanges.isEmpty());
            callbackData.put("success_exchanges", successExchanges);
            callbackData.put("failed_exchanges", failedExchanges);
            callbackData.put("saved_ids", allSavedIds);
            callbackData.put("message", successExchanges.size() + "개 거래소 동기화 완료" + 
                (failedExchanges.isEmpty() ? "" : ", " + failedExchanges.size() + "개 실패"));
            
        } catch (Exception e) {
            log.error("비동기 거래내역 동기화 전체 실패: userId={}, error={}", userId, e.getMessage(), e);
            
            callbackData.put("success", false);
            callbackData.put("error", e.getMessage());
            callbackData.put("message", "거래내역 동기화 실패");
        }
        
        // 콜백 호출
        sendCallback(callbackUrl, callbackData);
    }
    
    /**
     * 단일 거래소 거래내역 동기화
     */
    private Map<String, Object> syncSingleExchangeTradingHistory(User user, String exchangeStr) {
        UUID userId = user.getId();
        
        ExchangeType exchangeType;
        try {
            exchangeType = ExchangeType.fromName(exchangeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("잘못된 거래소명: " + exchangeStr);
        }
        
        // 거래소별 마지막 업데이트 시간 조회
        LocalDateTime startTime = user.getLastTradingHistoryUpdateAtByExchange(exchangeStr);
        boolean isInitial = user.isInitialSyncByExchange(exchangeStr);
        
        // 거래내역 조회
        List<Map<String, Object>> tradingHistories = tradingHistoryService.getTradingHistories(
            userId, exchangeStr, startTime);
        
        // 거래내역 처리
        List<TradingHistory> processedHistories = tradingHistoryService.processTradingHistories(
            userId, exchangeStr, tradingHistories);
        
        // 거래내역 저장
        List<TradingHistory> savedHistories = tradingHistoryService.saveTradingHistories(processedHistories);
        
        // 수익률 계산
        Map<String, Object> profitResult = null;
        if (!savedHistories.isEmpty()) {
            try {
                profitResult = tradingProfitService.calculateAndUpdateProfitLoss(
                    userId, exchangeType.getCode(), isInitial);
            } catch (Exception e) {
                log.warn("수익률 계산 실패 (거래내역 저장은 성공): userId={}, exchange={}, error={}", 
                    userId, exchangeStr, e.getMessage());
            }
        }
        
        // 업데이트 시간 갱신
        userService.updateUserTradingHistoryUpdatedAt(userId, exchangeStr);
        
        // 결과 반환
        Map<String, Object> result = new HashMap<>();
        result.put("saved_count", savedHistories.size());
        result.put("saved_ids", savedHistories.stream()
            .map(TradingHistory::getId)
            .toList());
        result.put("profit_calculation", profitResult);
        
        return result;
    }
    
    /**
     * app-server로 콜백 전송
     */
    private void sendCallback(String callbackUrl, Map<String, Object> data) {
        try {
            String fullUrl = appServerUrl + callbackUrl;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
            
            restTemplate.postForEntity(fullUrl, request, Void.class);
            
        } catch (Exception e) {
            log.error("콜백 전송 실패: url={}, error={}", callbackUrl, e.getMessage());
            // 콜백 실패해도 동기화 자체는 성공했으므로 예외를 던지지 않음
        }
    }
}
