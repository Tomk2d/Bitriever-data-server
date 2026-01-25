package com.bitreiver.fetch_server.domain.coinone.service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CoinoneService {
    Mono<List<Map<String, Object>>> fetchAllCoinList();
    Mono<Boolean> verifyCredentials(String accessToken, String secretKey);
    Mono<List<Map<String, Object>>> fetchAllCompletedOrders(String accessToken, String secretKey, LocalDateTime fromTime, LocalDateTime toTime);
    Mono<List<Map<String, Object>>> fetchAccounts(String accessToken, String secretKey);
    
    /**
     * 모든 활성 코인의 일봉 데이터를 동기화 (증분 수집)
     * DB에 저장된 마지막 날짜 이후부터 현재까지 수집
     * 각 코인별로 개별 트랜잭션 처리 (실패해도 성공한 코인은 저장됨)
     * 
     * @return 동기화 결과 (성공/실패 개수, 수집된 캔들 수 등)
     */
    Mono<Map<String, Object>> syncAllCoinDailyCandles();
}
