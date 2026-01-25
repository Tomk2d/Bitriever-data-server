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
}
