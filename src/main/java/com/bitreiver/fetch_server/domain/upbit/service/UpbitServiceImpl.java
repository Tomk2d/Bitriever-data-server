package com.bitreiver.fetch_server.domain.upbit.service;

import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.global.util.TimeUtil;
import com.bitreiver.fetch_server.infra.upbit.UpbitClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitServiceImpl implements UpbitService {
    
    private final UpbitClient upbitClient;
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<List<String>> fetchAllTradingUuids(String accessKey, String secretKey, LocalDateTime startTime) {
        try {
            if (startTime == null) {
                startTime = LocalDateTime.of(2017, 11, 1, 0, 0);
            }
            
            LocalDateTime currentTime = TimeUtil.getCurrentKoreaTime();
            List<String[]> timeRanges = TimeUtil.getAllTradingTimeRanges(startTime, currentTime);
            
            List<String> allUuids = new ArrayList<>();
            
            for (int i = 0; i < timeRanges.size(); i++) {
                String[] range = timeRanges.get(i);
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("states[]", Arrays.asList("done", "cancel"));
                params.put("start_time", range[0]);
                params.put("end_time", range[1]);
                params.put("limit", 1000);

                
                Object response = upbitClient.get("/v1/orders/closed", accessKey, secretKey, params, true)
                    .block();
                
                if (response == null) {
                    continue;
                }
                
                if (response instanceof List) {
                    List<?> responseList = (List<?>) response;
                    for (Object r : responseList) {
                        if (r instanceof Map) {
                            Map<String, Object> item = (Map<String, Object>) r;
                            Object executedVolume = item.get("executed_volume");
                            if (executedVolume != null && !"0".equals(executedVolume.toString())) {
                                Object uuid = item.get("uuid");
                                if (uuid != null) {
                                    allUuids.add(uuid.toString());
                                }
                            }
                        }
                    }
                }
                
                if ((i + 1) % 25 == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            return Mono.just(allUuids);
        } catch (CustomException e) {
            log.error("fetchAllTradingUuids - {}", e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("fetchAllTradingUuids - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래 UUID 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Override
    public Mono<List<Map<String, Object>>> fetchAllTradingHistory(String accessKey, String secretKey, List<String> uuids) {
        try {
            List<Map<String, Object>> tradingHistories = new ArrayList<>();
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            for (int i = 0; i < uuids.size(); i++) {
                String uuid = uuids.get(i);
                
                Map<String, Object> params = new HashMap<>();
                params.put("uuid", uuid);
                
                Object response = upbitClient.get("/v1/order", accessKey, secretKey, params, true)
                    .block();
                
                if (response != null && response instanceof Map) {
                    tradingHistories.add((Map<String, Object>) response);
                }
                
                if ((i + 1) % 25 == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            return Mono.just(tradingHistories);
        } catch (CustomException e) {
            log.error("fetchAllTradingHistory - {}", e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("fetchAllTradingHistory - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래내역 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Override
    public Mono<List<Map<String, Object>>> fetchAllCoinList() {
        try {
            String baseUrl = "https://crix-static.upbit.com/crix_master";
            
            long nonce = System.currentTimeMillis();
            
            WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                })
                .defaultHeader(HttpHeaders.USER_AGENT, 
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8")
                .defaultHeader("Accept-Encoding", "gzip, deflate, br")
                .defaultHeader(HttpHeaders.ORIGIN, "https://upbit.com")
                .defaultHeader(HttpHeaders.REFERER, "https://upbit.com/")
                .defaultHeader(HttpHeaders.CONNECTION, "keep-alive")
                .defaultHeader("Sec-Fetch-Dest", "empty")
                .defaultHeader("Sec-Fetch-Mode", "cors")
                .defaultHeader("Sec-Fetch-Site", "cross-site")
                .build();
            
            return webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .queryParam("nonce", nonce)
                    .build())
                .retrieve()
                .bodyToMono(Object.class)
                .map(response -> {
                    if (response instanceof List) {
                        List<?> responseList = (List<?>) response;
                        List<Map<String, Object>> coinList = new ArrayList<>();
                        
                        for (Object item : responseList) {
                            if (item instanceof Map) {
                                Map<String, Object> coinData = (Map<String, Object>) item;
                                
                                String marketState = coinData.getOrDefault("marketState", "").toString();
                                if ("ACTIVE".equals(marketState)) {
                                    coinList.add(coinData);
                                }
                            }
                        }
                        return coinList;
                    } else if (response instanceof Map) {
                        Map<String, Object> coinData = (Map<String, Object>) response;
                        String marketState = coinData.getOrDefault("marketState", "").toString();
                        
                        if ("ACTIVE".equals(marketState)) {
                            List<Map<String, Object>> coinList = new ArrayList<>();
                            coinList.add(coinData);
                            return coinList;
                        } else {
                            return new ArrayList<Map<String, Object>>();
                        }
                    } else {
                        log.warn("fetchAllCoinList - 예상하지 못한 응답 형식: {}", response != null ? response.getClass() : "null");
                        return new ArrayList<Map<String, Object>>();
                    }
                })
                .doOnError(error -> log.error("fetchAllCoinList - API 호출 중 에러 발생: {}", error.getMessage(), error))
                .onErrorResume(error -> {
                    log.error("fetchAllCoinList - 코인 목록 가져오기 실패: {}", error.getMessage(), error);
                    if (error instanceof CustomException) {
                        return Mono.error(error);
                    }
                    return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                        "코인 목록 조회 중 오류가 발생했습니다: " + error.getMessage()));
                });
        } catch (CustomException e) {
            log.error("fetchAllCoinList - {}", e.getMessage());
            return Mono.error(e);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.error("fetchAllCoinList - 데이터 검증 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.BAD_REQUEST, 
                "코인 목록 조회 중 데이터 처리 오류가 발생했습니다: " + e.getMessage()));
        } catch (Exception e) {
            log.error("fetchAllCoinList - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "코인 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Override
    public Mono<List<Map<String, Object>>> fetchAccounts(String accessKey, String secretKey) {
        try {
            Object response = upbitClient.get("/v1/accounts", accessKey, secretKey, null, true)
                .block();
            
            if (response == null) {
                log.warn("fetchAccounts - 계정 잔고 조회 결과가 null입니다");
                return Mono.just(new ArrayList<>());
            }
            
            List<Map<String, Object>> accounts;
            if (response instanceof List) {
                accounts = (List<Map<String, Object>>) response;
            } else if (response instanceof Map) {
                accounts = Collections.singletonList((Map<String, Object>) response);
            } else {
                accounts = new ArrayList<>();
            }
            
            return Mono.just(accounts);
        } catch (CustomException e) {
            log.error("fetchAccounts - {}", e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("fetchAccounts - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "계정 잔고 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}
