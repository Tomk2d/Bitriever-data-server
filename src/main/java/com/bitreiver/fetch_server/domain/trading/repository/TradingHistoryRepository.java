package com.bitreiver.fetch_server.domain.trading.repository;

import com.bitreiver.fetch_server.domain.trading.entity.TradingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface TradingHistoryRepository extends JpaRepository<TradingHistory, Integer> {
    List<TradingHistory> findByUserIdOrderByTradeTimeDesc(UUID userId);
    List<TradingHistory> findByUserIdAndExchangeCodeOrderByTradeTimeAsc(UUID userId, Short exchangeCode);
    boolean existsByUserIdAndExchangeCodeAndTradeUuid(UUID userId, Short exchangeCode, String tradeUuid);
    
    @Modifying
    @Transactional
    @Query("UPDATE TradingHistory t SET t.profitLossRate = :profitLossRate, t.avgBuyPrice = :avgBuyPrice WHERE t.id = :id")
    void updateProfitLoss(@Param("id") Integer id, 
                         @Param("profitLossRate") java.math.BigDecimal profitLossRate, 
                         @Param("avgBuyPrice") java.math.BigDecimal avgBuyPrice);
}

