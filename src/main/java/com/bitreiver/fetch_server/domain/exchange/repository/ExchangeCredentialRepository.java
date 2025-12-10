package com.bitreiver.fetch_server.domain.exchange.repository;

import com.bitreiver.fetch_server.domain.exchange.entity.ExchangeCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExchangeCredentialRepository extends JpaRepository<ExchangeCredential, UUID> {
    Optional<ExchangeCredential> findByUserIdAndExchangeProvider(UUID userId, Short exchangeProvider);
    List<ExchangeCredential> findByUserId(UUID userId);
    boolean existsByUserIdAndExchangeProvider(UUID userId, Short exchangeProvider);
    void deleteByUserIdAndExchangeProvider(UUID userId, Short exchangeProvider);
}

