package com.bitreiver.fetch_server.domain.price.repository;

import com.bitreiver.fetch_server.domain.price.entity.CoinPriceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoinPriceDayRepository extends JpaRepository<CoinPriceDay, Integer> {
    
    /**
     * 특정 코인의 최신 캔들 조회 (증분 수집용)
     */
    Optional<CoinPriceDay> findTopByCoinIdOrderByCandleDateTimeUtcDesc(Integer coinId);
    
    /**
     * 마켓 코드로 최신 캔들 조회
     */
    Optional<CoinPriceDay> findTopByMarketCodeOrderByCandleDateTimeUtcDesc(String marketCode);
    
    /**
     * 특정 코인의 모든 캔들 조회 (최신순)
     */
    List<CoinPriceDay> findAllByCoinIdOrderByCandleDateTimeUtcDesc(Integer coinId);
    
    /**
     * 특정 코인의 날짜 범위 캔들 조회
     */
    @Query("SELECT c FROM CoinPriceDay c WHERE c.coinId = :coinId " +
        "AND c.candleDateTimeUtc >= :startDate AND c.candleDateTimeUtc < :endDate " +
        "ORDER BY c.candleDateTimeUtc DESC")
    List<CoinPriceDay> findByCoinIdAndUtcDateRange(
        @Param("coinId") Integer coinId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * 마켓 코드와 날짜로 캔들 존재 여부 확인
     */
    boolean existsByMarketCodeAndCandleDateTimeUtc(String marketCode, LocalDateTime candleDateTimeUtc);
    
    /**
     * 마켓 코드로 캔들 조회
     */
    Optional<CoinPriceDay> findByMarketCodeAndCandleDateTimeUtc(String marketCode, LocalDateTime candleDateTimeUtc);
}
