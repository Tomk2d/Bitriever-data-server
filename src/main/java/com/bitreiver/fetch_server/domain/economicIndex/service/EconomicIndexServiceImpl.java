package com.bitreiver.fetch_server.domain.economicIndex.service;

import com.bitreiver.fetch_server.domain.economicIndex.dto.EconomicIndexRedisDto;
import com.bitreiver.fetch_server.domain.economicIndex.enums.EconomicIndexType;
import com.bitreiver.fetch_server.global.cache.RedisCacheService;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.bitreiver.fetch_server.infra.yahoofinance.YahooFinanceClient;
import com.bitreiver.fetch_server.infra.yahoofinance.dto.YahooFinanceChartResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EconomicIndexServiceImpl implements EconomicIndexService {
    
    private final YahooFinanceClient yahooFinanceClient;
    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;

    private static final String REDIS_KEY_PREFIX = "economic-index:";
    private static final long TTL_SECONDS = 3600;   // 1시간
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void fetchAndCacheByIndexType(EconomicIndexType indexType) {
        try{
            YahooFinanceChartResponse response = yahooFinanceClient
                .getChart(indexType.getSymbol(), "5m", "1d")
                .block();

            if (response == null || response.getChart() == null) {
                throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "Yahoo Finance API 응답이 null입니다.");
            }
            
            if (response.getChart().getError() != null) {
                throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "Yahoo Finance API 에러: " + response.getChart().getError());
            }

            List<YahooFinanceChartResponse.Result> results = response.getChart().getResult();
            if (results == null || results.isEmpty()) {
                throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "Yahoo Finance API 결과가 비어있습니다.");
            }

            YahooFinanceChartResponse.Result result = results.get(0);
            Double previousDayClose = result.getMeta() != null ? result.getMeta().getChartPreviousClose() : null;
            List<Long> timestamps = result.getTimestamp();
            List<YahooFinanceChartResponse.Quote> quotes = result.getIndicators().getQuote();
            
            if (timestamps == null || timestamps.isEmpty() || quotes == null || quotes.isEmpty()) {
                throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "Yahoo Finance API 데이터가 비어있습니다.");
            }
            
            YahooFinanceChartResponse.Quote quote = quotes.get(0);
            List<Double> closePrices = quote.getClose();
            
            if (closePrices == null || closePrices.size() != timestamps.size()) {
                throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "Yahoo Finance API 데이터 길이가 일치하지 않습니다.");
            }

            // 데이터 변환
            List<EconomicIndexRedisDto> dtoList = new ArrayList<>();
            Double previousClose = null;
            
            for (int i = 0; i < timestamps.size(); i++) {
                Long timestamp = timestamps.get(i);
                Double closePrice = closePrices.get(i);
                
                if (timestamp == null || closePrice == null) {
                    continue;
                }
                
                LocalDateTime dateTime = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
                
                String dateTimeString = dateTime.format(DATE_TIME_FORMATTER);

                BigDecimal changeAmount = null;
                BigDecimal changeRate = null;
                if (previousDayClose != null && previousDayClose != 0 && closePrice != null) {
                    double diff = closePrice - previousDayClose;
                    changeAmount = BigDecimal.valueOf(diff).setScale(2, RoundingMode.HALF_UP);
                    double rate = (diff / previousDayClose) * 100.0;
                    changeRate = BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP);
                }
                
                EconomicIndexRedisDto dto = EconomicIndexRedisDto.builder()
                    .dateTime(dateTime)
                    .dateTimeString(dateTimeString)
                    .price(BigDecimal.valueOf(closePrice))
                    .previousClose(previousClose != null ? BigDecimal.valueOf(previousClose) : null)
                    .changeAmount(changeAmount)
                    .changeRate(changeRate)
                    .build();
                
                dtoList.add(dto);
                previousClose = closePrice;
            }

            // Redis에 저장
            String redisKey = REDIS_KEY_PREFIX + indexType.name();
            redisCacheService.set(redisKey, dtoList, TTL_SECONDS);
            
            log.info("경제 지표 수집 완료 - type: {}, 데이터 개수: {}", indexType, dtoList.size());
        }catch (CustomException e) {
            log.error("경제 지표 수집 실패 - type: {}, error: {}", indexType, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("경제 지표 수집 중 예외 발생 - type: {}, error: {}", indexType, e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "경제 지표 수집 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Override
    public void fetchAndCacheAll() {
        
        List<EconomicIndexType> indexTypes = Arrays.asList(EconomicIndexType.values());
        int successCount = 0;
        int failCount = 0;
        
        for (EconomicIndexType indexType : indexTypes) {
            try {
                fetchAndCacheByIndexType(indexType);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("경제 지표 수집 실패 - type: {}, error: {}", indexType, e.getMessage());
                // 개별 지표 실패해도 다음 지표는 계속 진행
            }
        }
        
        log.info("모든 경제 지표 수집 완료 - 성공: {}, 실패: {}", successCount, failCount);
    }
    
    @Override
    public List<EconomicIndexRedisDto> getByIndexType(EconomicIndexType indexType) {
        String redisKey = REDIS_KEY_PREFIX + indexType.name();
        TypeReference<List<EconomicIndexRedisDto>> typeRef = new TypeReference<List<EconomicIndexRedisDto>>() {};
        
        return redisCacheService.get(redisKey, typeRef)
            .orElse(new ArrayList<>());
    }
    
    @Override
    public List<EconomicIndexRedisDto> getByIndexTypeAndDateRange(
        EconomicIndexType indexType, 
        LocalDate startDate, 
        LocalDate endDate
    ) {
        List<EconomicIndexRedisDto> allData = getByIndexType(indexType);
        
        return allData.stream()
            .filter(dto -> {
                LocalDate date = dto.getDateTime().toLocalDate();
                return (date.isEqual(startDate) || date.isAfter(startDate)) &&
                       (date.isEqual(endDate) || date.isBefore(endDate));
            })
            .collect(Collectors.toList());
    }
}
