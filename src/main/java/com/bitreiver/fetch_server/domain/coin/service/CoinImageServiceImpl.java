package com.bitreiver.fetch_server.domain.coin.service;

import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class CoinImageServiceImpl implements CoinImageService {
    
    private final WebClient imageWebClient;
    
    @Value("${coin.image.save.path:/Users/shin-uijin/BITRIEVER/client/public/data/image}")
    private String imageSavePath;
    
    @Value("${coin.image.base.url:https://static.upbit.com/logos}")
    private String imageBaseUrl;
    
    public CoinImageServiceImpl(@Value("${coin.image.base.url:https://static.upbit.com/logos}") String imageBaseUrl) {
        this.imageWebClient = WebClient.builder()
            .baseUrl(imageBaseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .defaultHeader(HttpHeaders.ACCEPT, "image/png,image/*,*/*")
            .build();
    }
    
    @Override
    public int downloadCoinImages(List<Map<String, Object>> coinList) {
        try {
            Path imageDir = Paths.get(imageSavePath);
            Files.createDirectories(imageDir);
            
            List<Map<String, Object>> upbitCoins = coinList.stream()
                .filter(coin -> "UPBIT".equals(coin.getOrDefault("exchange", "").toString()))
                .toList();
            
            List<Map<String, Object>> coinoneCoins = coinList.stream()
                .filter(coin -> "COINONE".equals(coin.getOrDefault("exchange", "").toString()))
                .toList();
            
            log.info("아이콘 다운로드 시작: 업비트 {}개, 코인원 {}개", upbitCoins.size(), coinoneCoins.size());
            
            AtomicInteger downloadedCount = new AtomicInteger(0);
            AtomicInteger skippedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);
            
            // 업비트 코인 이미지 다운로드
            for (int i = 0; i < upbitCoins.size(); i++) {
                Map<String, Object> coin = upbitCoins.get(i);
                String symbol = coin.getOrDefault("baseCurrencyCode", "").toString();
                
                if (symbol == null || symbol.isEmpty()) {
                    continue;
                }
                
                Path imagePath = imageDir.resolve(symbol + ".png");
                
                if (Files.exists(imagePath)) {
                    skippedCount.incrementAndGet();
                    continue;
                }
                
                if (downloadedCount.get() > 0 && downloadedCount.get() % 5 == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                try {
                    downloadImage(symbol, imagePath)
                        .subscribeOn(Schedulers.boundedElastic())
                        .block();
                    downloadedCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("업비트 이미지 다운로드 실패: {} - {}", symbol, e.getMessage());
                    failedCount.incrementAndGet();
                }
            }
            
            // 코인원 코인 이미지 다운로드
            for (int i = 0; i < coinoneCoins.size(); i++) {
                Map<String, Object> coin = coinoneCoins.get(i);
                String symbol = coin.getOrDefault("symbol", "").toString();
                
                if (symbol == null || symbol.isEmpty()) {
                    continue;
                }
                
                Path imagePath = imageDir.resolve(symbol + ".png");
                
                if (Files.exists(imagePath)) {
                    skippedCount.incrementAndGet();
                    continue;
                }
                
                // iconColorUrl 추출
                String iconColorUrl = (String) coin.getOrDefault("iconColorUrl", null);
                if (iconColorUrl == null || iconColorUrl.isEmpty()) {
                    log.warn("코인원 코인 {}의 iconColorUrl이 없습니다", symbol);
                    failedCount.incrementAndGet();
                    continue;
                }
                
                if (downloadedCount.get() > 0 && downloadedCount.get() % 5 == 0) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                try {
                    downloadCoinoneImage(iconColorUrl, imagePath)
                        .subscribeOn(Schedulers.boundedElastic())
                        .block();
                    downloadedCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("코인원 이미지 다운로드 실패: {} - {}", symbol, e.getMessage());
                    failedCount.incrementAndGet();
                }
            }
            
            log.info("아이콘 다운로드 완료: 다운로드 {}개, 건너뛰기 {}개, 실패 {}개", 
                downloadedCount.get(), skippedCount.get(), failedCount.get());
            
            return downloadedCount.get();
        } catch (Exception e) {
            log.error("downloadCoinImages - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "아이콘 다운로드 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    private Mono<Void> downloadImage(String symbol, Path savePath) {
        return imageWebClient.get()
            .uri("/{symbol}.png", symbol)
            .retrieve()
            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                response -> {
                    log.warn("아이콘 다운로드 HTTP 에러: {} - 상태 코드: {}", symbol, response.statusCode());
                    return response.createException();
                })
            .bodyToFlux(DataBuffer.class)
            .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)))
            .collectList()
            .flatMap(dataBuffers -> {
                if (dataBuffers.isEmpty()) {
                    return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                        "이미지 데이터가 비어있습니다: " + symbol));
                }
                try {
                    Files.createDirectories(savePath.getParent());
                    try (var channel = Files.newByteChannel(savePath, 
                            StandardOpenOption.CREATE, 
                            StandardOpenOption.WRITE, 
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                        
                        for (DataBuffer dataBuffer : dataBuffers) {
                            channel.write(dataBuffer.asByteBuffer());
                            DataBufferUtils.release(dataBuffer);
                        }
                    }
                    return Mono.<Void>empty();
                } catch (IOException e) {
                    return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                        "이미지 저장 실패: " + e.getMessage()));
                }
            })
            .onErrorResume(throwable -> {
                if (throwable instanceof CustomException) {
                    return Mono.error(throwable);
                }
                return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "이미지 다운로드 실패: " + throwable.getMessage()));
            });
    }
    
    private Mono<Void> downloadCoinoneImage(String imageUrl, Path savePath) {
        WebClient webClient = WebClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, 
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36")
            .defaultHeader(HttpHeaders.ACCEPT, "image/png,image/*,*/*")
            .defaultHeader(HttpHeaders.ORIGIN, "https://coinone.co.kr")
            .defaultHeader(HttpHeaders.REFERER, "https://coinone.co.kr/")
            .build();
        
        return webClient.get()
            .uri(imageUrl)
            .retrieve()
            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                response -> {
                    log.warn("코인원 이미지 다운로드 HTTP 에러: {} - 상태 코드: {}", imageUrl, response.statusCode());
                    return response.createException();
                })
            .bodyToFlux(DataBuffer.class)
            .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)))
            .collectList()
            .flatMap(dataBuffers -> {
                if (dataBuffers.isEmpty()) {
                    return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                        "이미지 데이터가 비어있습니다: " + imageUrl));
                }
                try {
                    Files.createDirectories(savePath.getParent());
                    try (var channel = Files.newByteChannel(savePath, 
                            StandardOpenOption.CREATE, 
                            StandardOpenOption.WRITE, 
                            StandardOpenOption.TRUNCATE_EXISTING)) {
                        
                        for (DataBuffer dataBuffer : dataBuffers) {
                            channel.write(dataBuffer.asByteBuffer());
                            DataBufferUtils.release(dataBuffer);
                        }
                    }
                    return Mono.<Void>empty();
                } catch (IOException e) {
                    return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                        "이미지 저장 실패: " + e.getMessage()));
                }
            })
            .onErrorResume(throwable -> {
                if (throwable instanceof CustomException) {
                    return Mono.error(throwable);
                }
                return Mono.error(new CustomException(ErrorCode.INTERNAL_ERROR, 
                    "이미지 다운로드 실패: " + throwable.getMessage()));
            });
    }
}
