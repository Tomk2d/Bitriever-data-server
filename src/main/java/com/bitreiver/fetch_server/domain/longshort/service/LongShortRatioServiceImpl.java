package com.bitreiver.fetch_server.domain.longshort.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.global.cache.RedisCacheService;
import com.bitreiver.fetch_server.infra.binance.BinanceFuturesClient;
import com.bitreiver.fetch_server.infra.binance.dto.BinanceLongShortRatioResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LongShortRatioServiceImpl implements LongShortRatioService {
    
    private final CoinRepository coinRepository;
    private final BinanceFuturesClient binanceFuturesClient;
    private final RedisCacheService redisCacheService;
    
    private static final String REDIS_KEY_PREFIX = "binance:longShortRatio:";
    
    @Override
    public Map<String, Object> fetchAll(String period, Long limit) {
        // 1. 활성 코인 목록 조회
        List<Coin> activeCoins = coinRepository.findByIsActiveTrue();
        log.info("활성 코인 수: {}", activeCoins.size());
        
        // 2. symbol 기준 중복 제거
        Set<String> uniqueSymbols = activeCoins.stream()
            .map(Coin::getSymbol)
            .collect(Collectors.toSet());
        
        log.info("중복 제거 후 심볼 수: {}", uniqueSymbols.size());
        
        // 3. 각 심볼에 대해 "USDT" 추가하여 Binance API 호출
        Map<String, List<BinanceLongShortRatioResponse>> resultData = new HashMap<>();
        List<String> unsupportedSymbols = Collections.synchronizedList(new ArrayList<>());
        
        List<CompletableFuture<Void>> futures = uniqueSymbols.stream()
            .map(symbol -> {
                String symbolWithQuote = symbol + "USDT";
                return CompletableFuture.runAsync(() -> {
                    try {
                        List<BinanceLongShortRatioResponse> response = binanceFuturesClient
                            .getGlobalLongShortAccountRatio(symbolWithQuote, period, limit)
                            .block();
                        
                        if (response != null && !response.isEmpty()) {
                            resultData.put(symbol, response);
                            log.debug("성공적으로 조회된 심볼: {}", symbolWithQuote);
                        } else {
                            unsupportedSymbols.add(symbolWithQuote);
                            log.debug("응답이 비어있는 심볼: {}", symbolWithQuote);
                        }
                    } catch (Exception e) {
                        // HTTP 4xx 에러 (특히 400, 404)는 미지원 심볼로 간주
                        if (e instanceof WebClientResponseException) {
                            WebClientResponseException webClientException = (WebClientResponseException) e;
                            HttpStatus status = HttpStatus.resolve(webClientException.getStatusCode().value());
                            if (status != null) {
                                if (status == HttpStatus.TOO_MANY_REQUESTS) {
                                    // Rate Limit 에러 (429)
                                    log.warn("Binance API Rate Limit 초과 - symbol: {}, HTTP 429", symbolWithQuote);
                                } else if (status.is4xxClientError()) {
                                    // 4xx 에러는 미지원 심볼로 간주
                                    unsupportedSymbols.add(symbolWithQuote);
                                    log.debug("미지원 심볼로 판단됨: {} (HTTP {}: {})", 
                                        symbolWithQuote, 
                                        webClientException.getStatusCode().value(),
                                        webClientException.getMessage());
                                } else {
                                    // 5xx 등 기타 HTTP 에러
                                    log.warn("Binance API 호출 중 HTTP 에러 발생 - symbol: {}, HTTP {}: {}", 
                                        symbolWithQuote, 
                                        webClientException.getStatusCode().value(),
                                        e.getMessage());
                                }
                            }
                        } else {
                            // 네트워크/타임아웃 등 기타 예외
                            log.warn("Binance API 호출 중 예외 발생 - symbol: {}, error: {}", 
                                symbolWithQuote, e.getMessage());
                        }
                    }
                });
            })
            .collect(Collectors.toList());
        
        // 4. 모든 요청 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 5. 미지원 심볼 로그 출력
        if (!unsupportedSymbols.isEmpty()) {
        } else {
            log.info("모든 심볼이 정상적으로 조회되었습니다.");
        }
        
        // 6. 결과 반환
        Map<String, Object> response = new HashMap<>();
        response.put("data", resultData);
        response.put("unsupportedSymbols", unsupportedSymbols);
        
        log.info("Long/Short Ratio 조회 완료 - 성공: {}개, 미지원: {}개", 
            resultData.size(), unsupportedSymbols.size());
        
        return response;
    }
    
    @Override
    public void fetchAllAndSaveToRedis(String period, Long limit) {
        Map<String, Object> result = fetchAll(period, limit);
        
        long ttlSeconds = switch (period) {
            case "1h" -> 3600L;
            case "4h" -> 4 * 3600L;
            case "12h" -> 12 * 3600L;
            case "1d" -> 24 * 3600L;
            default -> 3600L;
        } + 600L;
        
        @SuppressWarnings("unchecked")
        Map<String, List<BinanceLongShortRatioResponse>> data =
                (Map<String, List<BinanceLongShortRatioResponse>>) result.get("data");
        
        if (data == null || data.isEmpty()) {
            log.warn("Binance Long/Short Ratio 데이터가 비어 있습니다. period: {}", period);
            return;
        }
        
        data.forEach((symbol, ratios) -> {
            String redisKey = REDIS_KEY_PREFIX + symbol + ":" + period;
            redisCacheService.set(redisKey, ratios, ttlSeconds);
        });
    }
}

