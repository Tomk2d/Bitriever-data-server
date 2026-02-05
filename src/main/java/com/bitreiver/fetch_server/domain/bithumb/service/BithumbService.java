package com.bitreiver.fetch_server.domain.bithumb.service;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface BithumbService {

    /**
     * 전체 계좌 조회 (GET /v1/accounts)
     */
    Mono<List<Map<String, Object>>> fetchAccounts(String accessKey, String secretKey);

    /**
     * 완료/취소 주문 UUID 목록 조회 (GET /v1/orders 페이지네이션, created_at >= startTime 필터)
     */
    Mono<List<String>> fetchAllOrderUuids(String accessKey, String secretKey, LocalDateTime startTime);

    /**
     * 주문 UUID로 개별 주문 상세 조회 (trades 포함)
     */
    Mono<Map<String, Object>> fetchOrderByUuid(String accessKey, String secretKey, String uuid);

    /**
     * UUID 목록에 대해 개별 주문 조회 후 리스트 반환 (rate limit 고려)
     */
    Mono<List<Map<String, Object>>> fetchAllTradingHistory(String accessKey, String secretKey, List<String> uuids);
}
