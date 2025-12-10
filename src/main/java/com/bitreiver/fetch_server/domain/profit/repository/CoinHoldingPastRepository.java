package com.bitreiver.fetch_server.domain.profit.repository;

import com.bitreiver.fetch_server.domain.profit.entity.CoinHoldingPast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoinHoldingPastRepository extends JpaRepository<CoinHoldingPast, Integer> {
    List<CoinHoldingPast> findByUserIdAndExchangeCode(UUID userId, Short exchangeCode);
    Optional<CoinHoldingPast> findByUserIdAndCoinIdAndExchangeCode(UUID userId, Integer coinId, Short exchangeCode);
    
    // 삭제는 Service에서 직접 처리
}

