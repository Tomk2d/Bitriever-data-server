package com.bitreiver.fetch_server.domain.trading.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
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
    
    @Override
    public List<Map<String, Object>> getTradingHistories(UUID userId, String exchangeProviderStr, LocalDateTime startTime) {
        try {
            ExchangeType exchangeType = ExchangeType.fromName(exchangeProviderStr);
            Short exchangeProvider = (short) exchangeType.getCode();
            
            ExchangeCredentialResponse credentials = exchangeCredentialService
                .getCredentials(userId, exchangeProvider)
                .orElseThrow(() -> new CustomException(ErrorCode.EXCHANGE_CREDENTIAL_NOT_FOUND, 
                    "User not found"));
            
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
            
            List<Coin> coins = coinRepository.findAll();
            Map<String, Integer> coinMap = coins.stream()
                .collect(Collectors.toMap(Coin::getMarketCode, Coin::getId, (a, b) -> a));
            
            List<TradingHistory> tradingHistoryList = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
            
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
                    log.warn("processTradingHistories - 코인을 찾을 수 없습니다: market={}", market);
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
        } catch (CustomException e) {
            log.error("processTradingHistories - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("processTradingHistories - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
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
