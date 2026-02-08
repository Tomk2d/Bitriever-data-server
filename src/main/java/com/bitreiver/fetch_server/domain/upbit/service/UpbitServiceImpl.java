package com.bitreiver.fetch_server.domain.upbit.service;

import com.bitreiver.fetch_server.domain.coin.entity.Coin;
import com.bitreiver.fetch_server.domain.coin.repository.CoinRepository;
import com.bitreiver.fetch_server.domain.price.entity.CoinPriceDay;
import com.bitreiver.fetch_server.domain.price.repository.CoinPriceDayRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitServiceImpl implements UpbitService {
    
    private final UpbitClient upbitClient;
    private final ObjectMapper objectMapper;
    private final CoinRepository coinRepository;
    private final CoinPriceDayRepository coinPriceDayRepository;
    
    private static final LocalDateTime DEFAULT_START_DATE = LocalDateTime.of(2017, 1, 1, 0, 0);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    
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
            boolean isKeyError = e.getMessage() != null && e.getMessage().contains("JWT 토큰 생성 실패")
                || (e.getCause() != null && e.getCause().getClass().getName().contains("WeakKeyException"));
            if (isKeyError) {
                log.warn("fetchAccounts - API 키/시크릿 오류 (형식 또는 길이 확인): {}", e.getMessage());
            } else {
                log.error("fetchAccounts - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            }
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "계정 잔고 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
    
    @Override
    public Mono<Map<String, Object>> syncAllCoinDailyCandles() {
        try {
            log.info("업비트 일봉 데이터 동기화 시작");
            
            // 업비트 활성 코인 목록 조회
            List<Coin> upbitCoins = coinRepository.findByExchange("UPBIT");
            List<Coin> activeCoins = upbitCoins.stream()
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
                        log.info("[업비트] {} 동기화 성공: {}개 캔들 저장", coin.getMarketCode(), savedCount);
                    }
                    
                    // Rate limit: 초당 10회 제한 -> 100ms 대기
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    errorCount++;
                    errorCoins.add(coin.getMarketCode());
                    log.error("[업비트] {} 동기화 실패: {}", coin.getMarketCode(), e.getMessage());
                }
            }
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalCoins", activeCoins.size());
            result.put("successCount", successCount);
            result.put("errorCount", errorCount);
            result.put("totalCandlesSaved", totalCandlesSaved);
            result.put("errorCoins", errorCoins);
            
            log.info("업비트 일봉 데이터 동기화 완료 - 전체: {}, 성공: {}, 실패: {}, 저장된 캔들: {}", 
                activeCoins.size(), successCount, errorCount, totalCandlesSaved);
            
            return Mono.just(result);
            
        } catch (Exception e) {
            log.error("업비트 일봉 동기화 중 에러 발생: {}", e.getMessage(), e);
            return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                "업비트 일봉 동기화 중 오류가 발생했습니다: " + e.getMessage()));
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
            log.debug("{}: 동기화할 새 데이터 없음", coin.getMarketCode());
            return 0;
        }
        
        log.debug("{}: {} 부터 동기화 시작", coin.getMarketCode(), startDate);
        
        List<CoinPriceDay> allCandles = new ArrayList<>();
        LocalDateTime currentTo = now;
        
        // 역순으로 데이터 수집 (현재 -> 과거)
        while (currentTo.isAfter(startDate)) {
            String toStr = currentTo.format(ISO_FORMATTER);
            
            List<Map<String, Object>> candles = upbitClient.getDailyCandles(
                coin.getMarketCode(), toStr, 200
            ).block();
            
            if (candles == null || candles.isEmpty()) {
                break;
            }
            
            for (Map<String, Object> candleData : candles) {
                CoinPriceDay candle = convertToCoinPriceDay(coin, candleData);
                if (candle != null && candle.getCandleDateTimeUtc().isAfter(startDate.minusDays(1))) {
                    // 중복 체크
                    if (!coinPriceDayRepository.existsByMarketCodeAndCandleDateTimeUtc(
                            candle.getMarketCode(), candle.getCandleDateTimeUtc())) {
                        allCandles.add(candle);
                    }
                }
            }
            
            // 가장 오래된 캔들 날짜 확인
            String oldestDateStr = (String) candles.get(candles.size() - 1).get("candle_date_time_utc");
            if (oldestDateStr != null) {
                LocalDateTime oldestDate = LocalDateTime.parse(oldestDateStr.replace("T", "T").substring(0, 19));
                if (oldestDate.isBefore(startDate) || oldestDate.equals(startDate)) {
                    break;
                }
                currentTo = oldestDate.minusDays(1);
            } else {
                break;
            }
            
            // 200개 미만이면 더 이상 데이터 없음
            if (candles.size() < 200) {
                break;
            }
            
            // Rate limit 방지
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
            log.info("{}: {}개 캔들 저장 완료", coin.getMarketCode(), allCandles.size());
        }
        
        return allCandles.size();
    }
    
    /**
     * API 응답을 CoinPriceDay 엔티티로 변환
     */
    private CoinPriceDay convertToCoinPriceDay(Coin coin, Map<String, Object> candleData) {
        try {
            String dateUtcStr = (String) candleData.get("candle_date_time_utc");
            String dateKstStr = (String) candleData.get("candle_date_time_kst");
            
            LocalDateTime dateUtc = LocalDateTime.parse(dateUtcStr.substring(0, 19));
            LocalDateTime dateKst = LocalDateTime.parse(dateKstStr.substring(0, 19));
            
            return CoinPriceDay.builder()
                .coinId(coin.getId())
                .marketCode((String) candleData.get("market"))
                .candleDateTimeUtc(dateUtc)
                .candleDateTimeKst(dateKst)
                .openingPrice(toBigDecimal(candleData.get("opening_price")))
                .highPrice(toBigDecimal(candleData.get("high_price")))
                .lowPrice(toBigDecimal(candleData.get("low_price")))
                .tradePrice(toBigDecimal(candleData.get("trade_price")))
                .timestamp(toLong(candleData.get("timestamp")))
                .candleAccTradePrice(toBigDecimal(candleData.get("candle_acc_trade_price")))
                .candleAccTradeVolume(toBigDecimal(candleData.get("candle_acc_trade_volume")))
                .prevClosingPrice(toBigDecimal(candleData.getOrDefault("prev_closing_price", 0)))
                .changePrice(toBigDecimal(candleData.getOrDefault("change_price", 0)))
                .changeRate(toBigDecimal(candleData.getOrDefault("change_rate", 0)))
                .convertedTradePrice(toBigDecimal(candleData.get("converted_trade_price")))
                .build();
                
        } catch (Exception e) {
            log.warn("캔들 데이터 변환 실패: {}", e.getMessage());
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
