package com.bitreiver.fetch_server.domain.sync.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SyncService {
    
    /**
     * 비동기 자산 동기화 시작
     * 완료 시 app-server로 콜백 호출
     */
    void syncAssetsAsync(UUID userId, String callbackUrl);
    
    /**
     * 비동기 거래내역 동기화 시작
     * 완료 시 app-server로 콜백 호출
     */
    void syncTradingHistoryAsync(UUID userId, List<String> exchanges, String callbackUrl);

    /**
     * 단일 거래소 거래내역 연동 + 수익률 계산.
     * UPBIT, BITHUMB, COINONE만 지원. 수익률 계산 실패 시 예외 throw (자격인증 등록 후 연동 플로우용).
     */
    Map<String, Object> updateTradingHistoryForExchange(UUID userId, String exchangeProviderStr);
}
