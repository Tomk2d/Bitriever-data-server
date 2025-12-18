package com.bitreiver.fetch_server.domain.coin.repository;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoinRepository extends JpaRepository<Coin, Integer> {
    Optional<Coin> findByMarketCode(String marketCode);
    Optional<Coin> findBySymbolAndQuoteCurrency(String symbol, String quoteCurrency);
    List<Coin> findByMarketCodeIn(List<String> marketCodes);
    List<Coin> findByExchange(String exchange);
    List<Coin> findByIsActiveTrue();
}

