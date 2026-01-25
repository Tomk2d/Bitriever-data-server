package com.bitreiver.fetch_server.domain.coinone.service;

import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.global.util.TimeUtil;
import com.bitreiver.fetch_server.infra.coinone.CoinoneClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinoneServiceImpl implements CoinoneService {
    
    private final ObjectMapper objectMapper;
    private final CoinoneClient coinoneClient;
    
    @Override
    public Mono<List<Map<String, Object>>> fetchAllCoinList() {
        try {
            String baseUrl = "https://metadata.coinone.co.kr/info";
            
            WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
                    configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                    configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                })
                .defaultHeader(HttpHeaders.USER_AGENT, 
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .defaultHeader("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .defaultHeader("Accept-Encoding", "gzip, deflate, br, zstd")
                .defaultHeader(HttpHeaders.ORIGIN, "https://coinone.co.kr")
                .defaultHeader(HttpHeaders.REFERER, "https://coinone.co.kr/")
                .defaultHeader("Sec-Fetch-Dest", "empty")
                .defaultHeader("Sec-Fetch-Mode", "cors")
                .defaultHeader("Sec-Fetch-Site", "same-site")
                .build();
            
            return webClient.get()
                .uri("/coins.json")
                .retrieve()
                .bodyToMono(Object.class)
                .map(response -> {
                    List<Map<String, Object>> coinList = new ArrayList<>();
                    
                    if (response instanceof Map) {
                        Map<String, Object> responseMap = (Map<String, Object>) response;
                        Object dataObj = responseMap.get("data");
                        
                        if (dataObj instanceof Map) {
                            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                            Object coinsObj = dataMap.get("coins");
                            
                            if (coinsObj instanceof List) {
                                List<?> coinsList = (List<?>) coinsObj;
                                
                                for (Object coinObj : coinsList) {
                                    if (coinObj instanceof Map) {
                                        Map<String, Object> coinData = (Map<String, Object>) coinObj;
                                        
                                        // status 필드 확인 (isDeposit, isWithdrawal가 true인 경우만 처리)
                                        Object statusObj = coinData.get("status");
                                        if (statusObj instanceof Map) {
                                            Map<String, Object> status = (Map<String, Object>) statusObj;
                                            Boolean isDeposit = (Boolean) status.getOrDefault("isDeposit", false);
                                            Boolean isWithdrawal = (Boolean) status.getOrDefault("isWithdrawal", false);
                                            
                                            // 입출금이 가능한 코인만 처리
                                            if (Boolean.TRUE.equals(isDeposit) || Boolean.TRUE.equals(isWithdrawal)) {
                                                Map<String, Object> convertedCoin = convertCoinoneDataToStandardFormat(coinData);
                                                if (convertedCoin != null) {
                                                    coinList.add(convertedCoin);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    log.info("코인원 코인 목록 조회 완료: {}개", coinList.size());
                    return coinList;
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
    
    /**
     * 코인원 API 응답 데이터를 표준 형식으로 변환
     * 
     * @param coinData 코인원 API 응답의 코인 데이터
     * @return 표준 형식의 코인 데이터 (Map<String, Object>)
     */
    private Map<String, Object> convertCoinoneDataToStandardFormat(Map<String, Object> coinData) {
        try {
            String symbol = coinData.getOrDefault("symbol", "").toString();
            if (symbol == null || symbol.isEmpty()) {
                log.warn("코인원 코인 데이터에 symbol이 없습니다: {}", coinData);
                return null;
            }
            
            String nameKr = coinData.getOrDefault("nameKr", "").toString();
            String nameEn = coinData.getOrDefault("nameEn", "").toString();
            
            // description에서 iconColorUrl 추출
            String iconColorUrl = null;
            Object descriptionObj = coinData.get("description");
            if (descriptionObj instanceof Map) {
                Map<String, Object> description = (Map<String, Object>) descriptionObj;
                iconColorUrl = (String) description.getOrDefault("iconColorUrl", null);
            }
            
            // marketCode는 KRW-symbol 형식으로 생성
            String marketCode = "KRW-" + symbol;
            
            Map<String, Object> convertedCoin = new HashMap<>();
            convertedCoin.put("symbol", symbol);
            convertedCoin.put("baseCurrencyCode", symbol);
            convertedCoin.put("quoteCurrencyCode", "KRW");
            convertedCoin.put("pair", symbol + "/KRW");
            convertedCoin.put("koreanName", nameKr);
            convertedCoin.put("englishName", nameEn);
            convertedCoin.put("marketCode", marketCode);
            convertedCoin.put("exchange", "COINONE");
            convertedCoin.put("iconColorUrl", iconColorUrl);
            
            return convertedCoin;
        } catch (Exception e) {
            log.error("코인원 데이터 변환 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public Mono<Boolean> verifyCredentials(String accessToken, String secretKey) {
        try {
            // 빈 request body로 잔고 조회 API 호출
            Map<String, Object> requestBody = new HashMap<>();
            
            return coinoneClient.post("/v2.1/account/balance/all", accessToken, secretKey, requestBody)
                .map(response -> {
                    if (response instanceof Map) {
                        Map<String, Object> responseMap = (Map<String, Object>) response;
                        String result = (String) responseMap.getOrDefault("result", "");
                        
                        // result가 "success"이면 자격증명 유효
                        if ("success".equals(result)) {
                            log.info("코인원 자격증명 검증 성공");
                            return true;
                        } else {
                            // 에러 코드 확인
                            Object errorCodeObj = responseMap.get("error_code");
                            String errorCode = errorCodeObj != null ? errorCodeObj.toString() : "";
                            
                            // 자격증명 관련 에러 코드: 12, 23, 24, 27, 123
                            if ("12".equals(errorCode) || "23".equals(errorCode) || 
                                "24".equals(errorCode) || "27".equals(errorCode) || 
                                "123".equals(errorCode)) {
                                log.warn("코인원 자격증명 검증 실패 - 에러 코드: {}", errorCode);
                                return false;
                            } else {
                                log.warn("코인원 자격증명 검증 실패 - result: {}, error_code: {}", result, errorCode);
                                return false;
                            }
                        }
                    }
                    log.warn("코인원 자격증명 검증 실패 - 응답 형식 오류");
                    return false;
                })
                .onErrorResume(error -> {
                    log.error("코인원 자격증명 검증 중 오류 발생: {}", error.getMessage(), error);
                    return Mono.just(false);
                });
        } catch (Exception e) {
            log.error("코인원 자격증명 검증 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.just(false);
        }
    }
    
    @Override
    public Mono<List<Map<String, Object>>> fetchAllCompletedOrders(String accessToken, String secretKey, LocalDateTime fromTime, LocalDateTime toTime) {
        try {
            log.info("fetchAllCompletedOrders 호출 - fromTime: {}, toTime: {}", fromTime, toTime);
            
            if (fromTime == null) {
                fromTime = LocalDateTime.of(2017, 11, 1, 0, 0);
                log.info("fromTime이 null이므로 기본값 설정: {}", fromTime);
            }
            if (toTime == null) {
                toTime = TimeUtil.getCurrentKoreaTime();
                log.info("toTime이 null이므로 현재 시간 설정: {}", toTime);
            }
            
            // 시간 범위를 90일씩 나눠서 조회
            List<Long[]> timeRanges = TimeUtil.getCoinoneTimeRanges(fromTime, toTime);
            log.info("시간 범위 개수: {}, fromTime: {}, toTime: {}", timeRanges.size(), fromTime, toTime);
            
            List<Map<String, Object>> allCompletedOrders = new ArrayList<>();
            
            for (Long[] range : timeRanges) {
                long fromTs = range[0];
                long toTs = range[1];
                
                List<Map<String, Object>> rangeOrders = fetchCompletedOrdersInRange(
                    accessToken, secretKey, fromTs, toTs
                ).block();
                
                if (rangeOrders != null) {
                    allCompletedOrders.addAll(rangeOrders);
                }
                
                // API 호출 제한을 위한 대기
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            log.info("코인원 거래 내역 조회 완료: {}개", allCompletedOrders.size());
            return Mono.just(allCompletedOrders);
        } catch (CustomException e) {
            log.error("fetchAllCompletedOrders - {}", e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("fetchAllCompletedOrders - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래 내역 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    /**
     * 특정 시간 범위의 체결 주문 조회 (페이징 처리 포함)
     * 
     * @param accessToken 액세스 토큰
     * @param secretKey 시크릿 키
     * @param fromTs 시작 시간 (UTC millisecond)
     * @param toTs 종료 시간 (UTC millisecond)
     * @return 체결 주문 목록
     */
    private Mono<List<Map<String, Object>>> fetchCompletedOrdersInRange(
            String accessToken, String secretKey, long fromTs, long toTs) {
        try {
            List<Map<String, Object>> allOrders = new ArrayList<>();
            String lastTradeId = null;
            int maxSize = 100; // 코인원 API 최대값
            
            while (true) {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("from_ts", fromTs);
                requestBody.put("to_ts", toTs);
                requestBody.put("size", maxSize);
                
                if (lastTradeId != null) {
                    requestBody.put("to_trade_id", lastTradeId);
                }
                
                Object response = coinoneClient.post(
                    "/v2.1/order/completed_orders/all", 
                    accessToken, 
                    secretKey, 
                    requestBody
                ).block();
                
                if (response == null) {
                    break;
                }
                
                if (response instanceof Map) {
                    Map<String, Object> responseMap = (Map<String, Object>) response;
                    String result = (String) responseMap.getOrDefault("result", "");
                    
                    if (!"success".equals(result)) {
                        Object errorCodeObj = responseMap.get("error_code");
                        String errorCode = errorCodeObj != null ? errorCodeObj.toString() : "";
                        log.warn("코인원 거래 내역 조회 실패 - result: {}, error_code: {}", result, errorCode);
                        break;
                    }
                    
                    Object completedOrdersObj = responseMap.get("completed_orders");
                    if (completedOrdersObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> orders = (List<Map<String, Object>>) completedOrdersObj;
                        
                        if (orders.isEmpty()) {
                            break; // 더 이상 데이터가 없음
                        }
                        
                        allOrders.addAll(orders);
                        
                        // 마지막 trade_id를 저장하여 다음 페이지 조회
                        Map<String, Object> lastOrder = orders.get(orders.size() - 1);
                        lastTradeId = (String) lastOrder.get("trade_id");
                        
                        // 100개 미만이면 마지막 페이지
                        if (orders.size() < maxSize) {
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
                
                // API 호출 제한을 위한 대기
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            return Mono.just(allOrders);
        } catch (Exception e) {
            log.error("fetchCompletedOrdersInRange - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "거래 내역 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Override
    public Mono<List<Map<String, Object>>> fetchAccounts(String accessToken, String secretKey) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            
            return coinoneClient.post("/v2.1/account/balance/all", accessToken, secretKey, requestBody)
                .flatMap(response -> {
                    try {
                        if (response instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> responseMap = (Map<String, Object>) response;
                            String result = (String) responseMap.getOrDefault("result", "");
                            
                            if (!"success".equals(result)) {
                                Object errorCodeObj = responseMap.get("error_code");
                                String errorCode = errorCodeObj != null ? errorCodeObj.toString() : "";
                                log.warn("코인원 계정 잔고 조회 실패 - result: {}, error_code: {}", result, errorCode);
                                return Mono.just(new ArrayList<Map<String, Object>>());
                            }
                            
                            Object balancesObj = responseMap.get("balances");
                            if (balancesObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> balances = (List<Map<String, Object>>) balancesObj;
                                
                                // 코인원 잔고 데이터를 업비트 형식으로 변환
                                List<Map<String, Object>> accounts = new ArrayList<>();
                                for (Map<String, Object> balance : balances) {
                                    Map<String, Object> account = convertCoinoneBalanceToAccount(balance);
                                    if (account != null) {
                                        accounts.add(account);
                                    }
                                }
                                
                                log.info("코인원 계정 잔고 조회 완료: {}개", accounts.size());
                                return Mono.just(accounts);
                            }
                        }
                        log.warn("코인원 계정 잔고 조회 실패 - 응답 형식 오류");
                        return Mono.just(new ArrayList<Map<String, Object>>());
                    } catch (Exception e) {
                        log.error("코인원 계정 잔고 데이터 처리 중 오류 발생: {}", e.getMessage(), e);
                        return Mono.just(new ArrayList<Map<String, Object>>());
                    }
                })
                .onErrorResume(error -> {
                    log.error("코인원 계정 잔고 조회 중 오류 발생: {}", error.getMessage(), error);
                    if (error instanceof CustomException) {
                        return Mono.error(error);
                    }
                    return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                        "계정 잔고 조회 중 오류가 발생했습니다: " + error.getMessage()));
                });
        } catch (Exception e) {
            log.error("코인원 계정 잔고 조회 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "계정 잔고 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    /**
     * 코인원 잔고 데이터를 업비트 형식의 계정 데이터로 변환
     * 
     * @param balance 코인원 잔고 데이터
     * @return 업비트 형식의 계정 데이터
     */
    private Map<String, Object> convertCoinoneBalanceToAccount(Map<String, Object> balance) {
        try {
            String currency = balance.getOrDefault("currency", "").toString();
            if (currency == null || currency.isEmpty()) {
                return null;
            }
            
            // 코인원은 항상 KRW 기준
            String unitCurrency = "KRW";
            
            // available과 limit의 합이 전체 잔고
            String availableStr = balance.getOrDefault("available", "0").toString();
            String limitStr = balance.getOrDefault("limit", "0").toString();
            BigDecimal available = new BigDecimal(availableStr);
            BigDecimal limit = new BigDecimal(limitStr);
            BigDecimal balanceTotal = available.add(limit);
            
            // 잔고가 0인 코인은 제외
            if (balanceTotal.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            
            // 평단가
            String avgBuyPriceStr = balance.getOrDefault("average_price", "0").toString();
            
            Map<String, Object> account = new HashMap<>();
            account.put("currency", currency);
            account.put("unit_currency", unitCurrency);
            account.put("balance", balanceTotal.toString());
            account.put("locked", limit.toString());
            account.put("avg_buy_price", avgBuyPriceStr);
            account.put("avg_buy_price_modified", false);
            
            return account;
        } catch (Exception e) {
            log.error("코인원 잔고 데이터 변환 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
}
