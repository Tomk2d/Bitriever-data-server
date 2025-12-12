package com.bitreiver.fetch_server.domain.feargreed.service;

import com.bitreiver.fetch_server.domain.feargreed.dto.FearGreedApiResponse;
import com.bitreiver.fetch_server.domain.feargreed.dto.FearGreedApiTestResponse;
import com.bitreiver.fetch_server.domain.feargreed.entity.FearGreedIndex;
import com.bitreiver.fetch_server.domain.feargreed.repository.FearGreedIndexRepository;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class FearGreedServiceImpl implements FearGreedService {
    
    private final WebClient alternativeMeWebClient;
    private final FearGreedIndexRepository fearGreedIndexRepository;
    
    public FearGreedServiceImpl(
            @Qualifier("alternativeMeWebClient") WebClient alternativeMeWebClient,
            FearGreedIndexRepository fearGreedIndexRepository) {
        this.alternativeMeWebClient = alternativeMeWebClient;
        this.fearGreedIndexRepository = fearGreedIndexRepository;
    }
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH);

    @Override
    public FearGreedApiResponse getByDate(LocalDate date) {
        FearGreedIndex index = fearGreedIndexRepository.findByDate(date)
            .orElseThrow(() -> new CustomException(ErrorCode.FEAR_GREED_NOT_FOUND));
        
        return FearGreedApiResponse.from(index);
    }

    @Override
    public void fetchRecentData() {
        String cookieHeader = "_gid=GA1.2.483577747.1765277934; " +
            "_ga=GA1.1.1708142447.1765028180; " +
            "__gads=ID=3b86a6e7710fdbf4:T=1765028180:RT=1765277933:S=ALNI_MZESCVX-nSSNdEpt9f_xlS0hMZSng; " +
            "__gpi=UID=000011c4160bd281:T=1765028180:RT=1765277933:S=ALNI_MaZdbH7yp-dnf9P7nzbigtFCEE9og; " +
            "__eoi=ID=100bbcf37b370720:T=1765028180:RT=1765277933:S=AA-AfjYvF43fs7EaOOBETrOiRCK0; " +
            "_ga_SZ8WP2F46W=GS2.1.s1765277931$o2$g1$t1765277938$j53$l0$h0; " +
            "FCCDCF=%5Bnull%2Cnull%2Cnull%2Cnull%2Cnull%2Cnull%2C%5B%5B32%2C%22%5B%5C%22e88613a6-8972-4d6c-b437-f7341e2fc562%5C%22%2C%5B1765028181%2C674000000%5D%5D%22%5D%5D%5D; " +
            "FCNEC=%5B%5B%22AKsRol8OgTk1KbT1_J5vYnfLV2mY5mluE6n8rE70zHTlQzVt0yDShAKK4kJwki7kLfgkbMtTeulVxRT3m9qyEmqACIDGeC-93FPchpXPyMz0el0ttISrKajXXbO6IDCyj4VfY2-Ka86nLO5EOpkMpgxvv7rAOTyBtQ%3D%3D%22%5D%5D; " +
            "dancer.session=X0WjtCSBDIPYifakKjLP-uheGPH_7S29oRHtEG9pRr0~1796814145~YxN6NeynWiU-m2XI9qwzXQ~5cJz2KvaOyq6gxjwMkI2_0BwiIB9pYaesRHoVByBIuo~2";
        
        String responseBody = alternativeMeWebClient.post()
            .uri("/api/crypto/fear-and-greed-index/history")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", "https://alternative.me/crypto/fear-and-greed-index/")
            .header("Origin", "https://alternative.me")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            .header("Sec-Ch-Ua", "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Google Chrome\";v=\"140\"")
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"macOS\"")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Priority", "u=1, i")
            .header("Cookie", cookieHeader)
            .bodyValue("{\"days\":7}")
            .retrieve()
            .bodyToMono(String.class)
            .doOnError(error -> log.error("fetchRecentData - API 호출 실패: {}", error.getMessage()))
            .block();
        
        if (responseBody == null || responseBody.isEmpty()) {
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR);
        }
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        try {
            FearGreedApiTestResponse response = objectMapper.readValue(responseBody, FearGreedApiTestResponse.class);
            
            if (response == null || response.getData() == null) {
                throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR);
            }
            
            if (response.getData().getDatasets() != null && !response.getData().getDatasets().isEmpty()) {
                FearGreedApiTestResponse.Dataset dataset = response.getData().getDatasets().get(0);
                List<Integer> values = dataset.getData();
                List<String> labels = response.getData().getLabels();
                
                if (values.size() == labels.size() && !values.isEmpty()) {
                    int latestValue = values.get(values.size() - 1);
                    String latestDate = labels.get(labels.size() - 1);
                    log.info("공포/탐욕 지수 최근 데이터 조회 완료 - 최신 지수: {} (날짜: {})", latestValue, latestDate);
                }
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("fetchRecentData - 공포/탐욕 지수 최근 데이터 조회 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR, e);
        }
    }
    
    @Override
    @Transactional
    public Map<String, Object> fetchAndSaveAllHistory() {
        String cookieHeader = "_gid=GA1.2.483577747.1765277934; " +
            "_ga=GA1.1.1708142447.1765028180; " +
            "__gads=ID=3b86a6e7710fdbf4:T=1765028180:RT=1765277933:S=ALNI_MZESCVX-nSSNdEpt9f_xlS0hMZSng; " +
            "__gpi=UID=000011c4160bd281:T=1765028180:RT=1765277933:S=ALNI_MaZdbH7yp-dnf9P7nzbigtFCEE9og; " +
            "__eoi=ID=100bbcf37b370720:T=1765028180:RT=1765277933:S=AA-AfjYvF43fs7EaOOBETrOiRCK0; " +
            "_ga_SZ8WP2F46W=GS2.1.s1765277931$o2$g1$t1765277938$j53$l0$h0; " +
            "FCCDCF=%5Bnull%2Cnull%2Cnull%2Cnull%2Cnull%2Cnull%2C%5B%5B32%2C%22%5B%5C%22e88613a6-8972-4d6c-b437-f7341e2fc562%5C%22%2C%5B1765028181%2C674000000%5D%5D%22%5D%5D%5D; " +
            "FCNEC=%5B%5B%22AKsRol8OgTk1KbT1_J5vYnfLV2mY5mluE6n8rE70zHTlQzVt0yDShAKK4kJwki7kLfgkbMtTeulVxRT3m9qyEmqACIDGeC-93FPchpXPyMz0el0ttISrKajXXbO6IDCyj4VfY2-Ka86nLO5EOpkMpgxvv7rAOTyBtQ%3D%3D%22%5D%5D; " +
            "dancer.session=X0WjtCSBDIPYifakKjLP-uheGPH_7S29oRHtEG9pRr0~1796814145~YxN6NeynWiU-m2XI9qwzXQ~5cJz2KvaOyq6gxjwMkI2_0BwiIB9pYaesRHoVByBIuo~2";
        
        String responseBody = alternativeMeWebClient.post()
            .uri("/api/crypto/fear-and-greed-index/history")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", "https://alternative.me/crypto/fear-and-greed-index/")
            .header("Origin", "https://alternative.me")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36")
            .header("Sec-Ch-Ua", "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"Google Chrome\";v=\"140\"")
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"macOS\"")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Priority", "u=1, i")
            .header("Cookie", cookieHeader)
            .bodyValue("{\"days\":100000}")
            .retrieve()
            .bodyToMono(String.class)
            .doOnError(error -> log.error("fetchAndSaveAllHistory - API 호출 실패: {}", error.getMessage()))
            .block();
        
        if (responseBody == null || responseBody.isEmpty()) {
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR);
        }
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        FearGreedApiTestResponse response;
        try {
            response = objectMapper.readValue(responseBody, FearGreedApiTestResponse.class);
        } catch (Exception e) {
            log.error("fetchAndSaveAllHistory - JSON 파싱 실패: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR, e);
        }
        
        if (response == null || response.getData() == null) {
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR);
        }
        
        if (response.getData().getDatasets() == null || response.getData().getDatasets().isEmpty()) {
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR);
        }
        
        if (response.getData().getLabels() == null || response.getData().getLabels().isEmpty()) {
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR);
        }
        
        FearGreedApiTestResponse.Dataset dataset = response.getData().getDatasets().get(0);
        List<Integer> values = dataset.getData();
        List<String> labels = response.getData().getLabels();
        
        if (values.size() != labels.size()) {
            throw new CustomException(ErrorCode.FEAR_GREED_API_ERROR);
        }
        
        List<FearGreedIndex> indicesToSave = new ArrayList<>();
        int savedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;
        
        for (int i = 0; i < values.size(); i++) {
            try {
                String dateString = labels.get(i);
                Integer value = values.get(i);
                
                LocalDate date = LocalDate.parse(dateString, DATE_FORMATTER);
                
                FearGreedIndex existingIndex = fearGreedIndexRepository.findByDate(date)
                    .orElse(null);
                
                LocalDateTime now = LocalDateTime.now();
                
                if (existingIndex != null) {
                    existingIndex.setValue(value);
                    existingIndex.setUpdatedAt(now);
                    indicesToSave.add(existingIndex);
                    updatedCount++;
                } else {
                    FearGreedIndex newIndex = FearGreedIndex.builder()
                        .date(date)
                        .value(value)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                    indicesToSave.add(newIndex);
                    savedCount++;
                }
            } catch (Exception e) {
                log.error("fetchAndSaveAllHistory - 데이터 처리 중 오류 발생 (인덱스: {}): {}", i, e.getMessage(), e);
                errorCount++;
            }
        }
        
        if (!indicesToSave.isEmpty()) {
            fearGreedIndexRepository.saveAll(indicesToSave);
        }
        
        return Map.of(
            "total_fetched", values.size(),
            "saved", savedCount,
            "updated", updatedCount,
            "errors", errorCount
        );
    }
}

