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
}
