package com.bitreiver.fetch_server.domain.profit.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.domain.profit.entity.CoinHoldingPast;
import com.bitreiver.fetch_server.domain.profit.repository.CoinHoldingPastRepository;
import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;
import com.bitreiver.fetch_server.domain.trading.repository.TradingHistoryRepository;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradingProfitServiceImpl implements TradingProfitService {
    
    private final TradingProfitCalculator tradingProfitCalculator;
    private final TradingHistoryRepository tradingHistoryRepository;
    private final CoinHoldingPastRepository coinHoldingPastRepository;
    private final CoinRepository coinRepository;
    
    @Override
    @Transactional
    public Map<String, Object> calculateAndUpdateProfitLoss(UUID userId, Integer exchangeCode, Boolean isInitial) {
        try {
            List<TradingHistory> tradingHistories = tradingHistoryRepository
                .findByUserIdAndExchangeCodeOrderByTradeTimeAsc(userId, exchangeCode.shortValue());
            
            if (tradingHistories.isEmpty()) {
                log.warn("calculateAndUpdateProfitLoss - 거래 내역이 없습니다: user_id={}, exchange_code={}", userId, exchangeCode);
                Map<String, Object> result = new HashMap<>();
                result.put("updated_count", 0);
                result.put("holdings_count", 0);
                result.put("deleted_holdings_count", 0);
                return result;
            }
            
            List<CoinHoldingPast> existingHoldings = coinHoldingPastRepository
                .findByUserIdAndExchangeCode(userId, exchangeCode.shortValue());
            
            Map<Integer, Map<String, Object>> holdingsDict = existingHoldings.stream()
                .collect(Collectors.toMap(
                    CoinHoldingPast::getCoinId,
                    h -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("avg_buy_price", h.getAvgBuyPrice());
                        map.put("remaining_quantity", h.getRemainingQuantity());
                        map.put("symbol", h.getSymbol());
                        return map;
                    }
                ));
            
            if (holdingsDict.isEmpty()) {
                isInitial = true;
                log.info("calculateAndUpdateProfitLoss - coin_holdings_past에 데이터가 없어 최초 계산으로 판단: user_id={}, exchange_code={}", 
                    userId, exchangeCode);
            } else {
                isInitial = false;
                log.info("calculateAndUpdateProfitLoss - coin_holdings_past에 데이터가 있어 이후 업데이트로 판단: user_id={}, exchange_code={}, holdings_count={}", 
                    userId, exchangeCode, holdingsDict.size());
            }
            
            List<TradingHistory> updatedHistories;
            if (!isInitial && !holdingsDict.isEmpty()) {
                updatedHistories = calculateWithExistingHoldings(tradingHistories, holdingsDict);
            } else {
                updatedHistories = tradingProfitCalculator.calculateProfitLoss(tradingHistories);
            }
            
            for (TradingHistory history : updatedHistories) {
                if (history.getId() != null) {
                    tradingHistoryRepository.updateProfitLoss(
                        history.getId(),
                        history.getProfitLossRate(),
                        history.getAvgBuyPrice()
                    );
                } else {
                    tradingHistoryRepository.save(history);
                }
            }
            
            Map<Integer, Map<String, Object>> finalHoldings = calculateFinalHoldings(updatedHistories);
            
            List<Coin> coins = coinRepository.findAll();
            Map<Integer, String> coinMap = coins.stream()
                .collect(Collectors.toMap(Coin::getId, Coin::getSymbol, (a, b) -> a));
            
            for (Map.Entry<Integer, Map<String, Object>> entry : finalHoldings.entrySet()) {
                Integer coinId = entry.getKey();
                Map<String, Object> data = entry.getValue();
                
                Optional<CoinHoldingPast> existing = coinHoldingPastRepository
                    .findByUserIdAndCoinIdAndExchangeCode(userId, coinId, exchangeCode.shortValue());
                
                CoinHoldingPast holding;
                if (existing.isPresent()) {
                    holding = existing.get();
                    holding.setAvgBuyPrice((BigDecimal) data.get("avg_buy_price"));
                    holding.setRemainingQuantity((BigDecimal) data.get("remaining_quantity"));
                    holding.setSymbol((String) data.get("symbol"));
                    holding.setUpdatedAt(LocalDateTime.now());
                } else {
                    holding = CoinHoldingPast.builder()
                        .userId(userId)
                        .coinId(coinId)
                        .exchangeCode(exchangeCode.shortValue())
                        .symbol((String) data.get("symbol"))
                        .avgBuyPrice((BigDecimal) data.get("avg_buy_price"))
                        .remainingQuantity((BigDecimal) data.get("remaining_quantity"))
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                }
                
                coinHoldingPastRepository.save(holding);
            }
            
            Set<Integer> coinIdsWithHoldings = finalHoldings.entrySet().stream()
                .filter(e -> ((BigDecimal) e.getValue().get("remaining_quantity")).compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
            
            List<CoinHoldingPast> allHoldings = coinHoldingPastRepository
                .findByUserIdAndExchangeCode(userId, exchangeCode.shortValue());
            
            int deletedCount = 0;
            for (CoinHoldingPast holding : allHoldings) {
                if (!coinIdsWithHoldings.contains(holding.getCoinId())) {
                    coinHoldingPastRepository.delete(holding);
                    deletedCount++;
                }
            }
            
            log.info("calculateAndUpdateProfitLoss - 수익률 계산 및 업데이트 완료: user_id={}, exchange_code={}, updated={}, holdings={}, deleted={}", 
                userId, exchangeCode, updatedHistories.size(), finalHoldings.size(), deletedCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("updated_count", updatedHistories.size());
            result.put("holdings_count", finalHoldings.size());
            result.put("deleted_holdings_count", deletedCount);
            
            return result;
        } catch (CustomException e) {
            log.error("calculateAndUpdateProfitLoss - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("calculateAndUpdateProfitLoss - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "수익률 계산 및 업데이트 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    private List<TradingHistory> calculateWithExistingHoldings(
            List<TradingHistory> tradingHistories,
            Map<Integer, Map<String, Object>> existingHoldings) {
        
        Map<Integer, List<BigDecimal>> holdings = new HashMap<>();
        for (Map.Entry<Integer, Map<String, Object>> entry : existingHoldings.entrySet()) {
            Integer coinId = entry.getKey();
            Map<String, Object> data = entry.getValue();
            holdings.put(coinId, Arrays.asList(
                (BigDecimal) data.get("avg_buy_price"),
                (BigDecimal) data.get("remaining_quantity")
            ));
        }
        
        for (TradingHistory history : tradingHistories) {
            Integer coinId = history.getCoinId();
            Short tradeType = history.getTradeType();
            BigDecimal price = history.getPrice();
            BigDecimal quantity = history.getQuantity();
            
            if (tradeType == 0) {
                tradingProfitCalculator.processBuy(holdings, coinId, price, quantity, history);
            } else if (tradeType == 1) {
                tradingProfitCalculator.processSell(holdings, coinId, price, quantity, history);
            }
        }
        
        return tradingHistories;
    }
    
    private Map<Integer, Map<String, Object>> calculateFinalHoldings(List<TradingHistory> tradingHistories) {
        List<TradingHistory> sortedHistories = new ArrayList<>(tradingHistories);
        sortedHistories.sort(Comparator.comparing(TradingHistory::getTradeTime));
        
        Map<Integer, List<BigDecimal>> holdings = new HashMap<>();
        Map<Integer, String> coinSymbols = new HashMap<>();
        
        List<Coin> coins = coinRepository.findAll();
        Map<Integer, String> coinMap = coins.stream()
            .collect(Collectors.toMap(Coin::getId, Coin::getSymbol, (a, b) -> a));
        
        for (TradingHistory history : sortedHistories) {
            Integer coinId = history.getCoinId();
            Short tradeType = history.getTradeType();
            BigDecimal price = history.getPrice();
            BigDecimal quantity = history.getQuantity();
            
            if (!coinSymbols.containsKey(coinId)) {
                coinSymbols.put(coinId, coinMap.getOrDefault(coinId, "UNKNOWN"));
            }
            
            if (tradeType == 0) {
                tradingProfitCalculator.processBuy(holdings, coinId, price, quantity, history);
            } else if (tradeType == 1) {
                tradingProfitCalculator.processSell(holdings, coinId, price, quantity, history);
            }
        }
        
        Map<Integer, Map<String, Object>> finalHoldings = new HashMap<>();
        for (Map.Entry<Integer, List<BigDecimal>> entry : holdings.entrySet()) {
            Integer coinId = entry.getKey();
            List<BigDecimal> values = entry.getValue();
            BigDecimal avgBuyPrice = values.get(0);
            BigDecimal remainingQuantity = values.get(1);
            
            if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> data = new HashMap<>();
                data.put("symbol", coinSymbols.getOrDefault(coinId, "UNKNOWN"));
                data.put("avg_buy_price", avgBuyPrice);
                data.put("remaining_quantity", remainingQuantity);
                finalHoldings.put(coinId, data);
            }
        }
        
        return finalHoldings;
    }
}
