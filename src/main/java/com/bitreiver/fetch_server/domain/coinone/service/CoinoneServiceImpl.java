package com.bitreiver.fetch_server.domain.coinone.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.domain.price.entity.CoinPriceDay;
import com.bitreiver.fetch_server.domain.price.repository.CoinPriceDayRepository;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoinoneServiceImpl implements CoinoneService {
    
    private final ObjectMapper objectMapper;
    private final CoinoneClient coinoneClient;
    private final CoinRepository coinRepository;
    private final CoinPriceDayRepository coinPriceDayRepository;
    
    private static final LocalDateTime DEFAULT_START_DATE = LocalDateTime.of(2017, 1, 1, 0, 0);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    
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
            if (fromTime == null) {
                fromTime = LocalDateTime.of(2017, 11, 1, 0, 0);
            }
            if (toTime == null) {
                toTime = TimeUtil.getCurrentKoreaTime();
            }
            
            // 시간 범위를 90일씩 나눠서 조회
            List<Long[]> timeRanges = TimeUtil.getCoinoneTimeRanges(fromTime, toTime);
            
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
                                // 인증/키 오류(12 등) 시 빈 리스트가 아닌 예외로 연동 실패·롤백 유도
                                String msg = "계정 잔고 조회 중 오류가 발생했습니다: 401 Unauthorized (코인원 error_code: " + errorCode + ")";
                                return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, msg));
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
    
    @Override
    public Mono<Map<String, Object>> syncAllCoinDailyCandles() {
        try {
            log.info("코인원 일봉 데이터 동기화 시작");
            
            // 코인원 활성 코인 목록 조회
            List<Coin> coinoneCoins = coinRepository.findByExchange("COINONE");
            List<Coin> activeCoins = coinoneCoins.stream()
                .filter(coin -> Boolean.TRUE.equals(coin.getIsActive()))
                .toList();
            
            log.info("동기화 대상 코인 수: {}", activeCoins.size());
            
            int successCount = 0;
            int errorCount = 0;
            int totalCandlesSaved = 0;
            List<String> errorCoins = new ArrayList<>();
            
            for (Coin coin : activeCoins) {
                try {
                    int savedCount = syncCoinDailyCandles(coin);
                    totalCandlesSaved += savedCount;
                    successCount++;
                    
                    if (savedCount > 0) {
                        log.info("[코인원] {} 동기화 성공: {}개 캔들 저장", coin.getSymbol(), savedCount);
                    }
                    
                    // Rate limit: 업비트와 동일하게 100ms 대기
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    errorCount++;
                    errorCoins.add(coin.getSymbol());
                    log.error("[코인원] {} 동기화 실패: {}", coin.getSymbol(), e.getMessage());
                }
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalCoins", activeCoins.size());
            result.put("successCount", successCount);
            result.put("errorCount", errorCount);
            result.put("totalCandlesSaved", totalCandlesSaved);
            result.put("errorCoins", errorCoins);
            
            log.info("코인원 일봉 데이터 동기화 완료 - 전체: {}, 성공: {}, 실패: {}, 저장된 캔들: {}", 
                activeCoins.size(), successCount, errorCount, totalCandlesSaved);
            
            return Mono.just(result);
            
        } catch (Exception e) {
            log.error("코인원 일봉 동기화 중 에러 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "코인원 일봉 동기화 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    /**
     * 단일 코인의 일봉 데이터 동기화
     */
    private int syncCoinDailyCandles(Coin coin) {
        // DB에서 마지막 캔들 날짜 조회
        Optional<CoinPriceDay> latestCandle = coinPriceDayRepository
            .findTopByCoinIdOrderByCandleDateTimeUtcDesc(coin.getId());
        
        LocalDateTime startDate;
        if (latestCandle.isPresent()) {
            // 마지막 캔들 다음 날부터 시작
            startDate = latestCandle.get().getCandleDateTimeUtc().plusDays(1);
        } else {
            // 데이터가 없으면 기본 시작 날짜 사용
            startDate = DEFAULT_START_DATE;
        }
        
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        
        // 시작 날짜가 현재보다 미래면 동기화할 데이터 없음
        if (startDate.isAfter(now)) {
            log.debug("{}: 동기화할 새 데이터 없음", coin.getSymbol());
            return 0;
        }
        
        log.debug("{}: {} 부터 동기화 시작", coin.getSymbol(), startDate);
        
        List<CoinPriceDay> allCandles = new ArrayList<>();
        Long currentTimestamp = now.toInstant(ZoneOffset.UTC).toEpochMilli();
        long startTimestamp = startDate.toInstant(ZoneOffset.UTC).toEpochMilli();
        
        // 역순으로 데이터 수집 (현재 -> 과거)
        while (currentTimestamp > startTimestamp) {
            Map<String, Object> response = coinoneClient.getDailyChart(
                coin.getSymbol(), currentTimestamp, 500
            ).block();
            
            if (response == null) {
                break;
            }
            
            String result = (String) response.getOrDefault("result", "");
            if (!"success".equals(result)) {
                log.warn("코인원 일봉 차트 조회 실패 - {}: {}", coin.getSymbol(), response.get("error_code"));
                break;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chartData = (List<Map<String, Object>>) response.get("chart");
            
            if (chartData == null || chartData.isEmpty()) {
                break;
            }
            
            for (Map<String, Object> candleData : chartData) {
                CoinPriceDay candle = convertCoinoneToCoinPriceDay(coin, candleData);
                if (candle != null) {
                    // 시작 날짜 이후 데이터만 수집
                    if (candle.getCandleDateTimeUtc().isAfter(startDate.minusDays(1))) {
                        // 중복 체크
                        if (!coinPriceDayRepository.existsByMarketCodeAndCandleDateTimeUtc(
                                candle.getMarketCode(), candle.getCandleDateTimeUtc())) {
                            allCandles.add(candle);
                        }
                    }
                }
            }
            
            // 가장 오래된 캔들 타임스탬프 확인
            Map<String, Object> oldestCandle = chartData.get(chartData.size() - 1);
            Object timestampObj = oldestCandle.get("timestamp");
            long oldestTimestamp = toLong(timestampObj);
            
            if (oldestTimestamp <= startTimestamp) {
                break;
            }
            
            currentTimestamp = oldestTimestamp - 86400000L; // 하루 전으로
            
            // is_last가 true면 마지막 데이터
            Boolean isLast = (Boolean) response.getOrDefault("is_last", false);
            if (Boolean.TRUE.equals(isLast)) {
                break;
            }
            
            // 500개 미만이면 더 이상 데이터 없음
            if (chartData.size() < 500) {
                break;
            }
            
            // Rate limit 방지 (업비트와 동일하게 100ms)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 데이터 저장
        if (!allCandles.isEmpty()) {
            coinPriceDayRepository.saveAll(allCandles);
        }
        
        return allCandles.size();
    }
    
    /**
     * 코인원 차트 데이터를 CoinPriceDay 엔티티로 변환
     */
    private CoinPriceDay convertCoinoneToCoinPriceDay(Coin coin, Map<String, Object> candleData) {
        try {
            Object timestampObj = candleData.get("timestamp");
            long timestamp = toLong(timestampObj);
            
            // timestamp를 LocalDateTime으로 변환
            Instant instant = Instant.ofEpochMilli(timestamp);
            LocalDateTime dateUtc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            LocalDateTime dateKst = LocalDateTime.ofInstant(instant, KST);
            
            String marketCode = "KRW-" + coin.getSymbol();
            
            return CoinPriceDay.builder()
                .coinId(coin.getId())
                .marketCode(marketCode)
                .candleDateTimeUtc(dateUtc)
                .candleDateTimeKst(dateKst)
                .openingPrice(toBigDecimal(candleData.get("open")))
                .highPrice(toBigDecimal(candleData.get("high")))
                .lowPrice(toBigDecimal(candleData.get("low")))
                .tradePrice(toBigDecimal(candleData.get("close")))
                .timestamp(timestamp)
                .candleAccTradePrice(toBigDecimal(candleData.get("quote_volume")))
                .candleAccTradeVolume(toBigDecimal(candleData.get("target_volume")))
                .prevClosingPrice(BigDecimal.ZERO) // 코인원 API에서 제공하지 않음
                .changePrice(BigDecimal.ZERO) // 코인원 API에서 제공하지 않음
                .changeRate(BigDecimal.ZERO) // 코인원 API에서 제공하지 않음
                .build();
                
        } catch (Exception e) {
            log.warn("코인원 캔들 데이터 변환 실패: {}", e.getMessage());
            return null;
        }
    }
    
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
    
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
