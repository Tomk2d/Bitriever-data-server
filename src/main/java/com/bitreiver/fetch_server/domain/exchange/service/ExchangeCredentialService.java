package com.bitreiver.fetch_server.domain.exchange.service;

import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialRequest;
import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeCredentialService {
    ExchangeCredentialResponse saveCredentials(UUID userId, ExchangeCredentialRequest request);
    Optional<ExchangeCredentialResponse> getCredentials(UUID userId, Short exchangeProvider);
    List<ExchangeCredentialResponse> getAllCredentials(UUID userId);
    boolean deleteCredentials(UUID userId, Short exchangeProvider);
    boolean verifyCredentials(UUID userId, Short exchangeProvider);
}
