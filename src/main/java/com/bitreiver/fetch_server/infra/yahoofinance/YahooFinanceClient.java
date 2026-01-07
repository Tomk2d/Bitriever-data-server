package com.bitreiver.fetch_server.infra.yahoofinance;

import com.bitreiver.fetch_server.infra.yahoofinance.dto.YahooFinanceChartResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class YahooFinanceClient {
    private final WebClient yahooFinanceWebClient;
    
    public YahooFinanceClient(@Qualifier("yahooFinanceWebClient") WebClient yahooFinanceWebClient) {
        this.yahooFinanceWebClient = yahooFinanceWebClient;
    }

    /**
     * Yahoo Finance Chart API 호출
     * 
     * @param symbol 심볼 (예: ^GSPC, ^KS11, KRW=X)
     * @param interval 데이터 간격 (예: 1d)
     * @param range 데이터 범위 (예: 1y)
     * @return Yahoo Finance Chart 응답
     */
    public Mono<YahooFinanceChartResponse> getChart(String symbol, String interval, String range) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
            .fromPath("/v8/finance/chart/{symbol}")
            .queryParam("interval", interval)
            .queryParam("range", range);
        
        String uri = uriBuilder.buildAndExpand(symbol).toUriString();
        
        log.debug("Yahoo Finance API 호출: {}", uri);
        
        return yahooFinanceWebClient
            .get()
            .uri(uri)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .retrieve()
            .bodyToMono(YahooFinanceChartResponse.class)
            .doOnError(error -> log.error("Yahoo Finance API 호출 실패 - symbol: {}, error: {}", 
                symbol, error.getMessage()));
    }
}
