package com.bitreiver.fetch_server.domain.exchange.service;

import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterAndSyncAsyncRunner {

    private final RegisterAndSyncService registerAndSyncService;

    @Async("syncExecutor")
    public void runRegisterAndSync(String jobId, UUID userId, ExchangeCredentialRequest request) {
        registerAndSyncService.runRegisterAndSync(jobId, userId, request);
    }
}
