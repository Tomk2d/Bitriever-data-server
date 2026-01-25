package com.bitreiver.fetch_server.domain.trading.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.domain.coinone.service.CoinoneService;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;
import com.bitreiver.fetch_server.domain.exchange.enums.ExchangeType;
import com.bitreiver.fetch_server.domain.exchange.service.ExchangeCredentialService;
import com.bitreiver.fetch_server.domain.trading.dto.TradingHistoryListResponse;
import com.bitreiver.fetch_server.domain.trading.dto.TradingHistoryResponse;
import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;
import com.bitreiver.fetch_server.domain.trading.repository.TradingHistoryRepository;
import com.bitreiver.fetch_server.domain.upbit.service.UpbitService;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingHistoryServiceImpl implements TradingHistoryService {
    
    private final TradingHistoryRepository tradingRepository;
    private final CoinRepository coinRepository;
    private final ExchangeCredentialService exchangeCredentialService;
    private final UpbitService upbitService;
    private final CoinoneService coinoneService;
    
    @Override
    public List<Map<String, Object>> getTradingHistories(UUID userId, String exchangeProviderStr, LocalDateTime startTime) {
        try {
            ExchangeType exchangeType = ExchangeType.fromName(exchangeProviderStr);
            Short exchangeProvider = (short) exchangeType.getCode();
            
            ExchangeCredentialResponse credentials = exchangeCredentialService
                .getCredentials(userId, exchangeProvider)
                .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                    "User not found"));
            
            // 거래소별 분기 처리
            if (exchangeType == ExchangeType.COINONE) {
                // 코인원: 체결 주문 조회
                LocalDateTime toTime = TimeUtil.getCurrentKoreaTime();
                List<Map<String, Object>> completedOrders = coinoneService.fetchAllCompletedOrders(
                    credentials.getAccessKey(),
                    credentials.getSecretKey(),
                    startTime,
                    toTime
                ).block();
                
                return completedOrders != null ? completedOrders : new ArrayList<>();
            } else {
                // 업비트: UUID 목록 조회 후 상세 조회
                List<String> uuids = upbitService.fetchAllTradingUuids(
                    credentials.getAccessKey(), 
                    credentials.getSecretKey(), 
                    startTime
                ).block();
                
                if (uuids == null || uuids.isEmpty()) {
                    return new ArrayList<>();
                }
                
                List<Map<String, Object>> tradingHistories = upbitService.fetchAllTradingHistory(
                    credentials.getAccessKey(),
                    credentials.getSecretKey(),
                    uuids
                ).block();
                
                return tradingHistories != null ? tradingHistories : new ArrayList<>();
            }
        } catch (CustomException e) {
            log.error("getTradingHistories - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("getTradingHistories - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public List<TradingHistory> processTradingHistories(UUID userId, String exchangeProviderStr, 
                                                       List<Map<String, Object>> tradingHisties) {
        try {
            ExchangeType exchangeType = ExchangeType.fromName(exchangeProviderStr);
            Short exchangeCode = (short) exchangeType.getCode();
            
            // 거래소별 분기 처리
            if (exchangeType == ExchangeType.COINONE) {
                return processCoinoneTradingHistories(userId, exchangeCode, tradingHisties);
            } else {
                return processUpbitTradingHistories(userId, exchangeCode, tradingHisties);
            }
        } catch (CustomException e) {
            log.error("processTradingHistories - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("processTradingHistories - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 업비트 거래 내역 처리
     */
    private List<TradingHistory> processUpbitTradingHistories(UUID userId, Short exchangeCode, 
                                                               List<Map<String, Object>> tradingHisties) {
        List<Coin> coins = coinRepository.findAll();
        Map<String, Integer> coinMap = coins.stream()
            .collect(Collectors.toMap(Coin::getMarketCode, Coin::getId, (a, b) -> a));
        
        List<TradingHistory> tradingHistoryList = new ArrayList<>();
        
        for (Map<String, Object> tradingHistory : tradingHisties) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trades = (List<Map<String, Object>>) tradingHistory.get("trades");
            
            if (trades == null || trades.isEmpty()) {
                continue;
            }
            
            BigDecimal totalQuantity = BigDecimal.ZERO;
            BigDecimal totalPrice = BigDecimal.ZERO;
            
            for (Map<String, Object> trade : trades) {
                BigDecimal volume = new BigDecimal(trade.getOrDefault("volume", "0").toString());
                BigDecimal funds = new BigDecimal(trade.getOrDefault("funds", "0").toString());
                
                totalQuantity = totalQuantity.add(volume);
                totalPrice = totalPrice.add(funds);
            }
            
            BigDecimal avgPrice = totalQuantity.compareTo(BigDecimal.ZERO) > 0 
                ? totalPrice.divide(totalQuantity, 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            
            String side = tradingHistory.getOrDefault("side", "").toString();
            Short tradeType = "bid".equals(side) ? (short) 0 : (short) 1;
            
            String market = tradingHistory.getOrDefault("market", "").toString();
            Integer coinId = coinMap.get(market);
            
            if (coinId == null) {
                log.warn("processUpbitTradingHistories - 코인을 찾을 수 없습니다: market={}", market);
                continue;
            }
            
            String uuid = tradingHistory.getOrDefault("uuid", "").toString();
            String createdAtStr = tradingHistory.getOrDefault("created_at", "").toString();
            LocalDateTime tradeTime = parseDateTime(createdAtStr);
            
            BigDecimal fee = new BigDecimal(tradingHistory.getOrDefault("paid_fee", "0").toString());
            
            TradingHistory history = TradingHistory.builder()
                .userId(userId)
                .coinId(coinId)
                .exchangeCode(exchangeCode)
                .tradeUuid(uuid)
                .tradeType(tradeType)
                .price(avgPrice)
                .quantity(totalQuantity)
                .totalPrice(totalPrice)
                .fee(fee)
                .tradeTime(tradeTime)
                .createdAt(LocalDateTime.now())
                .build();
            
            tradingHistoryList.add(history);
        }
        
        return tradingHistoryList;
    }
    
    /**
     * 코인원 거래 내역 처리
     */
    private List<TradingHistory> processCoinoneTradingHistories(UUID userId, Short exchangeCode, 
                                                                 List<Map<String, Object>> completedOrders) {
        List<Coin> coins = coinRepository.findAll();
        Map<String, Integer> coinMap = coins.stream()
            .collect(Collectors.toMap(Coin::getMarketCode, Coin::getId, (a, b) -> a));
        
        List<TradingHistory> tradingHistoryList = new ArrayList<>();
        
        for (Map<String, Object> order : completedOrders) {
            try {
                // 코인원 응답 필드 추출
                String tradeId = (String) order.getOrDefault("trade_id", "");
                if (tradeId == null || tradeId.isEmpty()) {
                    log.warn("processCoinoneTradingHistories - trade_id가 없습니다: {}", order);
                    continue;
                }
                
                String targetCurrency = (String) order.getOrDefault("target_currency", "");
                String quoteCurrency = (String) order.getOrDefault("quote_currency", "KRW");
                // marketCode는 KRW-symbol 형식 (거래통화-symbol)
                String marketCode = quoteCurrency + "-" + targetCurrency;
                
                Integer coinId = coinMap.get(marketCode);
                if (coinId == null) {
                    log.warn("processCoinoneTradingHistories - 코인을 찾을 수 없습니다: marketCode={}", marketCode);
                    continue;
                }
                
                // is_ask: true=매도(1), false=매수(0)
                Boolean isAsk = (Boolean) order.getOrDefault("is_ask", false);
                Short tradeType = Boolean.TRUE.equals(isAsk) ? (short) 1 : (short) 0;
                
                // 가격 및 수량
                String priceStr = order.getOrDefault("price", "0").toString();
                String qtyStr = order.getOrDefault("qty", "0").toString();
                BigDecimal price = new BigDecimal(priceStr);
                BigDecimal quantity = new BigDecimal(qtyStr);
                BigDecimal totalPrice = price.multiply(quantity);
                
                // 수수료
                String feeStr = order.getOrDefault("fee", "0").toString();
                BigDecimal fee = new BigDecimal(feeStr);
                
                // 타임스탬프 (millisecond) → LocalDateTime 변환
                Object timestampObj = order.get("timestamp");
                LocalDateTime tradeTime;
                if (timestampObj instanceof Number) {
                    long timestamp = ((Number) timestampObj).longValue();
                    tradeTime = TimeUtil.fromUtcMilliseconds(timestamp);
                } else if (timestampObj instanceof String) {
                    long timestamp = Long.parseLong(timestampObj.toString());
                    tradeTime = TimeUtil.fromUtcMilliseconds(timestamp);
                } else {
                    log.warn("processCoinoneTradingHistories - timestamp 형식 오류: {}, 현재 시간 사용", timestampObj);
                    tradeTime = LocalDateTime.now();
                }
                
                TradingHistory history = TradingHistory.builder()
                    .userId(userId)
                    .coinId(coinId)
                    .exchangeCode(exchangeCode)
                    .tradeUuid(tradeId)
                    .tradeType(tradeType)
                    .price(price)
                    .quantity(quantity)
                    .totalPrice(totalPrice)
                    .fee(fee)
                    .tradeTime(tradeTime)
                    .createdAt(LocalDateTime.now())
                    .build();
                
                tradingHistoryList.add(history);
            } catch (Exception e) {
                log.error("processCoinoneTradingHistories - 거래 내역 처리 중 오류 발생: {}, order: {}", 
                    e.getMessage(), order, e);
                continue;
            }
        }
        
        return tradingHistoryList;
    }
    
    @Override
    @Transactional
    public List<TradingHistory> saveTradingHistories(List<TradingHistory> tradingHistories) {
        try {
            if (tradingHistories == null || tradingHistories.isEmpty()) {
                return new ArrayList<>();
            }
            
            List<TradingHistory> savedHistories = new ArrayList<>();
            
            for (TradingHistory history : tradingHistories) {
                boolean exists = tradingRepository.existsByUserIdAndExchangeCodeAndTradeUuid(
                    history.getUserId(),
                    history.getExchangeCode(),
                    history.getTradeUuid()
                );
                
                if (!exists) {
                    TradingHistory saved = tradingRepository.save(history);
                    savedHistories.add(saved);
                }
            }
            
            log.info("saveTradingHistories - 거래내역 저장 완료: {}개", savedHistories.size());
            return savedHistories;
        } catch (Exception e) {
            log.error("saveTradingHistories - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public TradingHistoryListResponse getAllTradingHistoriesByUserFormatted(UUID userId) {
        try {
            List<TradingHistory> histories = tradingRepository.findByUserIdOrderByTradeTimeDesc(userId);
            
            List<TradingHistoryResponse> formattedHistories = new ArrayList<>();
            
            for (TradingHistory history : histories) {
                try {
                    TradingHistoryResponse response = TradingHistoryResponse.from(history);
                    formattedHistories.add(response);
                } catch (Exception e) {
                    log.warn("getAllTradingHistoriesByUserFormatted - 거래내역 포맷 중 오류 발생 (ID: {}): {}", 
                        history.getId(), e.getMessage());
                }
            }
            
            return TradingHistoryListResponse.builder()
                .totalCount(histories.size())
                .tradingHistories(formattedHistories)
                .build();
        } catch (Exception e) {
            log.error("getAllTradingHistoriesByUserFormatted - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> getAllTradingHistoriesByUserFormattedAsMap(UUID userId) {
        try {
            TradingHistoryListResponse response = getAllTradingHistoriesByUserFormatted(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_count", response.getTotalCount());
            result.put("trading_histories", response.getTradingHistories());
            
            return result;
        } catch (CustomException e) {
            log.error("getAllTradingHistoriesByUserFormattedAsMap - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("getAllTradingHistoriesByUserFormattedAsMap - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateTimeStr.replace("Z", ""), DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("parseDateTime - 날짜 파싱 실패: {}, 현재 시간 사용", dateTimeStr);
            return LocalDateTime.now();
        }
    }
}
