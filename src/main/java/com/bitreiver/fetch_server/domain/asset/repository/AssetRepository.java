package com.bitreiver.fetch_server.domain.asset.repository;

import com.bitreiver.fetch_server.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Integer> {
    List<Asset> findByUserIdAndExchangeCode(UUID userId, Short exchangeCode);
    Optional<Asset> findByUserIdAndExchangeCodeAndSymbolAndTradeBySymbol(
        UUID userId, Short exchangeCode, String symbol, String tradeBySymbol
    );
    
    // 삭제는 Service에서 직접 처리
}

