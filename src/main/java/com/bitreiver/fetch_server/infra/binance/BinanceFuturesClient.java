package com.bitreiver.fetch_server.infra.binance;

import com.bitreiver.fetch_server.infra.binance.dto.BinanceLongShortRatioResponse;
import com.bitreiver.fetch_server.infra.binance.dto.BinanceTakerLongShortRatioResponse;
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
     * Binance USDⓈ-M Futures Taker Long/Short Ratio 조회 (거래량 기반)
     * 
     * @param symbol 심볼 (예: BTCUSDT)
     * @param period 기간 ("5m","15m","30m","1h","2h","4h","6h","12h","1d")
     * @param limit 제한 (기본값 30, 최대 500)
     * @return Taker Long/Short Ratio 응답 리스트 (거래량 기반)
     */
    public Mono<List<BinanceTakerLongShortRatioResponse>> getTakerLongShortRatio(
            String symbol, 
            String period, 
            Long limit) {
        
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
            .fromPath("/futures/data/takerlongshortRatio")
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
            .bodyToFlux(BinanceTakerLongShortRatioResponse.class)
            .collectList()
            .doOnError(error -> log.debug("Binance API 호출 실패 - symbol: {}, period: {}, error: {}", 
                symbol, period, error.getMessage()));
    }

    /**
     * Binance USDⓈ-M Futures Top Trader Long/Short Ratio (Positions) 조회 (포지션 비중 기반)
     * 상위 20% 트레이더(마진 잔고 기준)의 롱/숏 포지션 규모 비율
     *
     * @param symbol 심볼 (예: BTCUSDT)
     * @param period 기간 ("5m","15m","30m","1h","2h","4h","6h","12h","1d")
     * @param limit 제한 (기본값 30, 최대 500)
     * @return 롱/숏 포지션 비율 응답 리스트
     */
    public Mono<List<BinanceLongShortRatioResponse>> getTopLongShortPositionRatio(
            String symbol,
            String period,
            Long limit) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
            .fromPath("/futures/data/topLongShortPositionRatio")
            .queryParam("symbol", symbol)
            .queryParam("period", period);

        if (limit != null) {
            uriBuilder.queryParam("limit", limit);
        }

        String uri = uriBuilder.toUriString();
        log.debug("Binance API 호출 (상위 트레이더 포지션): {}", uri);

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

