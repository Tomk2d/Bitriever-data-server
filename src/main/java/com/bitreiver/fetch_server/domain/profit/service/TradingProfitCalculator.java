package com.bitreiver.fetch_server.domain.profit.service;

import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Slf4j
public class TradingProfitCalculator {
    
    public List<TradingHistory> calculateProfitLoss(List<TradingHistory> tradingHistories) {
        // trade_time 순으로 정렬 (과거부터 현재 순)
        List<TradingHistory> sortedHistories = new ArrayList<>(tradingHistories);
        sortedHistories.sort(Comparator.comparing(TradingHistory::getTradeTime));
        
        // 보유량 추적: {coin_id: [avg_buy_price, quantity]}
        Map<Integer, List<BigDecimal>> holdings = new HashMap<>();
        
        for (TradingHistory history : sortedHistories) {
            Integer coinId = history.getCoinId();
            Short tradeType = history.getTradeType();
            BigDecimal price = history.getPrice();
            BigDecimal quantity = history.getQuantity();
            
            if (tradeType == 0) { // 매수
                processBuy(holdings, coinId, price, quantity, history);
            } else if (tradeType == 1) { // 매도
                processSell(holdings, coinId, price, quantity, history);
            }
        }
        
        log.info("수익률 계산 완료: 총 {}개 거래 내역 처리", sortedHistories.size());
        return sortedHistories;
    }
    
    public void processBuy(Map<Integer, List<BigDecimal>> holdings, Integer coinId, 
                          BigDecimal buyPrice, BigDecimal buyQuantity, TradingHistory history) {
        if (!holdings.containsKey(coinId)) {
            // 첫 매수
            holdings.put(coinId, Arrays.asList(buyPrice, buyQuantity));
        } else {
            // 기존 보유량이 있는 경우: 가중 평균 계산
            List<BigDecimal> existing = holdings.get(coinId);
            BigDecimal oldAvgPrice = existing.get(0);
            BigDecimal oldQuantity = existing.get(1);
            
            // 새로운 평균 단가 = (기존 총액 + 신규 총액) / (기존 수량 + 신규 수량)
            BigDecimal oldTotal = oldAvgPrice.multiply(oldQuantity);
            BigDecimal newTotal = buyPrice.multiply(buyQuantity);
            BigDecimal totalQuantity = oldQuantity.add(buyQuantity);
            
            BigDecimal newAvgPrice;
            if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                newAvgPrice = oldTotal.add(newTotal).divide(totalQuantity, 8, RoundingMode.HALF_UP);
            } else {
                newAvgPrice = buyPrice;
            }
            
            holdings.put(coinId, Arrays.asList(newAvgPrice, totalQuantity));
        }
        
        // 매수 시에는 profit_loss_rate와 avg_buy_price를 null로 설정
        history.setProfitLossRate(null);
        history.setAvgBuyPrice(null);
    }
    
    public void processSell(Map<Integer, List<BigDecimal>> holdings, Integer coinId,
                           BigDecimal sellPrice, BigDecimal sellQuantity, TradingHistory history) {
        if (!holdings.containsKey(coinId)) {
            // 보유량이 없거나 매수한 적이 없는 경우
            history.setProfitLossRate(null);
            history.setAvgBuyPrice(null);
            return;
        }
        
        List<BigDecimal> existing = holdings.get(coinId);
        BigDecimal avgBuyPrice = existing.get(0);
        BigDecimal remainingQuantity = existing.get(1);
        
        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            history.setProfitLossRate(null);
            history.setAvgBuyPrice(null);
            return;
        }
        
        if (remainingQuantity.compareTo(sellQuantity) < 0) {
            // 보유량이 매도 수량보다 적은 경우
            history.setProfitLossRate(null);
            history.setAvgBuyPrice(null);
            return;
        }
        
        // 수익률 계산: ((매도가 - 평균 구매가) / 평균 구매가) * 100
        BigDecimal profitLossRate;
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = sellPrice.subtract(avgBuyPrice);
            profitLossRate = diff.divide(avgBuyPrice, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        } else {
            profitLossRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        
        // history에 값 설정
        history.setProfitLossRate(profitLossRate);
        history.setAvgBuyPrice(avgBuyPrice);
        
        // 보유량 감소
        BigDecimal newQuantity = remainingQuantity.subtract(sellQuantity);
        
        if (newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            // 보유량이 0이 되면 딕셔너리에서 제거
            holdings.remove(coinId);
        } else {
            // 평균 단가는 유지 (FIFO가 아닌 평균 단가 방식)
            holdings.put(coinId, Arrays.asList(avgBuyPrice, newQuantity));
        }
    }
}

