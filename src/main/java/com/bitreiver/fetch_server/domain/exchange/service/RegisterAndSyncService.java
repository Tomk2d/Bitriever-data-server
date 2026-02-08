package com.bitreiver.fetch_server.domain.exchange.service;

import com.bitreiver.fetch_server.domain.exchange.dto.ExchangeCredentialRequest;
import com.bitreiver.fetch_server.domain.exchange.dto.RegisterAndSyncJobResult;

import java.util.UUID;

public interface RegisterAndSyncService {
    /**
     * 비동기로 "자격인증 저장 + 해당 거래소만 연동"을 시작하고 job_id를 반환.
     */
    String startRegisterAndSyncAsync(UUID userId, ExchangeCredentialRequest request);

    /**
     * job_id로 등록·연동 결과 조회 (폴링용).
     */
    RegisterAndSyncJobResult getRegisterStatus(String jobId);

    /**
     * 비동기 실행용 내부 메서드. RegisterAndSyncAsyncRunner에서 호출.
     */
    void runRegisterAndSync(String jobId, UUID userId, ExchangeCredentialRequest request);
}
