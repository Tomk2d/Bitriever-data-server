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
}
