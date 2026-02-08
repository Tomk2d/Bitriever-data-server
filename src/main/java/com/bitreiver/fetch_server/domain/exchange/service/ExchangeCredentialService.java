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

    /**
     * 자격인증 등록 후 연동 실패 시 보상: 해당 거래소 자격인증 삭제 + User connectedExchanges 원복.
     */
    void rollbackCredentialSave(UUID userId, Short exchangeProvider);
}
