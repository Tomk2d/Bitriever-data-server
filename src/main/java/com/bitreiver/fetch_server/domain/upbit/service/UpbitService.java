package com.bitreiver.fetch_server.domain.upbit.service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UpbitService {
    Mono<List<String>> fetchAllTradingUuids(String accessKey, String secretKey, LocalDateTime startTime);
    Mono<List<Map<String, Object>>> fetchAllTradingHistory(String accessKey, String secretKey, List<String> uuids);
    Mono<List<Map<String, Object>>> fetchAllCoinList();
    Mono<List<Map<String, Object>>> fetchAccounts(String accessKey, String secretKey);
    
    /**
     * 모든 활성 코인의 일봉 데이터를 동기화 (증분 수집)
     * DB에 저장된 마지막 날짜 이후부터 현재까지 수집
     * 
     * @return 동기화 결과 (성공/실패 개수, 수집된 캔들 수 등)
     */
    Mono<Map<String, Object>> syncAllCoinDailyCandles();
}
