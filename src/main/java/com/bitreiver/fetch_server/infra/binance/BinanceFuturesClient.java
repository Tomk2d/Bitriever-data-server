package com.bitreiver.fetch_server.infra.binance;

import com.bitreiver.fetch_server.infra.binance.dto.BinanceLongShortRatioResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class BinanceFuturesClient {
    
    private final WebClient binanceWebClient;
    
    public BinanceFuturesClient(@Qualifier("binanceWebClient") WebClient binanceWebClient) {
        this.binanceWebClient = binanceWebClient;
    }
    
    /**
     * Binance USDⓈ-M Futures Long/Short Account Ratio 조회
     * 
     * @param symbol 심볼 (예: BTCUSDT)
     * @param period 기간 ("5m","15m","30m","1h","2h","4h","6h","12h","1d")
     * @param limit 제한 (기본값 30, 최대 500)
     * @return Long/Short Ratio 응답 리스트
     */
    public Mono<List<BinanceLongShortRatioResponse>> getGlobalLongShortAccountRatio(
            String symbol, 
            String period, 
            Long limit) {
        
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
            .fromPath("/futures/data/globalLongShortAccountRatio")
            .queryParam("symbol", symbol)
            .queryParam("period", period);
        
        if (limit != null) {
            uriBuilder.queryParam("limit", limit);
        }
        
        String uri = uriBuilder.toUriString();
        
        log.debug("Binance API 호출: {}", uri);
        
        return binanceWebClient
            .get()
            .uri(uri)
            .retrieve()
            .bodyToFlux(BinanceLongShortRatioResponse.class)
            .collectList()
            .doOnError(error -> log.debug("Binance API 호출 실패 - symbol: {}, period: {}, error: {}", 
                symbol, period, error.getMessage()));
    }
}

